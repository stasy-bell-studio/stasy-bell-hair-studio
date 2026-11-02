"""Owner-notification outbox. Opt-in worker, one event per confirmed booking."""
import logging
import os
from datetime import UTC, datetime, timedelta

import httpx
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import CalendarEntry, Client, OwnerNotification, Service
from app.scheduling import STUDIO_TZ

logger = logging.getLogger(__name__)
MAX_ATTEMPTS = 12


def owner_telegram_ready() -> bool:
    return bool(os.environ.get("TELEGRAM_BOT_TOKEN") and
                os.environ.get("TELEGRAM_OWNER_CHAT_ID"))


def send_owner_telegram(message: str) -> None:
    token = os.environ["TELEGRAM_BOT_TOKEN"]
    target = os.environ["TELEGRAM_OWNER_CHAT_ID"]
    # Never include the token, URL or request body in thrown/logged errors.
    try:
        response = httpx.post(
            f"https://api.telegram.org/bot{token}/sendMessage",
            json={"chat_id": target, "text": message},
            timeout=8,
        )
        if response.status_code != 200 or response.json().get("ok") is not True:
            raise RuntimeError("telegram_delivery_failed")
    except (httpx.HTTPError, ValueError):
        raise RuntimeError("telegram_delivery_failed") from None


def notification_text(db: Session, item: OwnerNotification) -> str:
    entry = db.get(CalendarEntry, item.entry_id)
    client = db.get(Client, entry.client_id) if entry and entry.client_id else None
    service = db.get(Service, entry.service_id) if entry and entry.service_id else None
    when = entry.starts_at.astimezone(STUDIO_TZ).strftime("%d.%m.%Y %H:%M")
    return (
        "Stasy Bell OS · подтверждена запись\n"
        f"Дата: {when} (МСК)\n"
        f"Клиент: {client.name if client else 'не указан'}\n"
        f"Услуга: {service.name if service else 'не указана'}\n"
        "Проверьте запись в календаре мастера."
    )


def process_owner_notifications(db: Session, send=send_owner_telegram, now=None) -> int:
    """At-least-once delivery. Crash after Telegram ACK may cause a repeated notification."""
    if not owner_telegram_ready():
        return 0
    now = now or datetime.now(UTC)
    batch = db.scalars(select(OwnerNotification).where(
        OwnerNotification.state == "pending",
        OwnerNotification.next_attempt_at <= now,
    ).order_by(OwnerNotification.created_at).with_for_update(skip_locked=True).limit(10)).all()
    processed = 0
    for item in batch:
        try:
            send(notification_text(db, item))
        except Exception:
            item.attempts += 1
            item.last_error_code = "delivery_failed"
            if item.attempts >= MAX_ATTEMPTS:
                item.state = "stopped"
            else:
                wait_minutes = min(60, 2 ** min(item.attempts, 6))
                item.next_attempt_at = now + timedelta(minutes=wait_minutes)
            logger.warning("Owner notification failed, scheduled safe retry")
        else:
            item.state = "sent"
            item.sent_at = now
            item.attempts += 1
            item.last_error_code = None
        processed += 1
    db.commit()
    return processed
