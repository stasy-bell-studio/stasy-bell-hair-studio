"""Public booking holds: gated off by default until messenger rollout is approved."""
import hashlib
import hmac
import os
import secrets
from datetime import UTC, datetime, timedelta
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel, Field, model_validator
from sqlalchemy import delete, func, select, text
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.config import get_settings
from app.db import get_db, get_session_factory
from app.models import (
    CalendarEntry, CalendarEvent, Client, ConfirmationToken, PublicRateEvent, Service,
    WorkingWindow,
)
from app.scheduling import (
    ACTIVE, STUDIO_TZ, expire_holds, local_day_limits, require_working_window, snapshot,
    timeline_lock,
)

router = APIRouter(tags=["public-booking"])
HOLD_MINUTES = 45
TOKEN_MINUTES = 15
MAX_TOKENS_PER_HOLD = 3


class HoldRequest(BaseModel):
    name: str = Field(min_length=1, max_length=200)
    phone: str | None = Field(default=None, max_length=40)
    service_id: UUID
    starts_at: datetime
    channel: str = Field(pattern="^(telegram|vk)$")
    personal_data_consent: bool

    @model_validator(mode="after")
    def validate(self):
        if not self.personal_data_consent:
            raise ValueError("Personal data processing consent required")
        if self.starts_at.tzinfo is None or self.starts_at.utcoffset() is None:
            raise ValueError("Timezone-aware start is required")
        return self


class ReissueRequest(BaseModel):
    hold_secret: str = Field(min_length=32, max_length=128)


def require_public_enabled():
    if os.environ.get("STASY_PUBLIC_BOOKING_ENABLED") != "1":
        raise HTTPException(status_code=503, detail="Online booking is not enabled")


def digest(raw: str) -> str:
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


def rate_limit(request: Request, action: str, max_events: int, seconds: int) -> None:
    # Hash raw IP with private HMAC so no raw visitor IP is stored.
    ip = request.client.host if request.client else "unknown"
    key = hmac.new(get_settings().jwt_secret.encode(), ip.encode(), hashlib.sha256).hexdigest()
    now = datetime.now(UTC)
    # Separate committed transaction counts even failed/conflicting booking attempts.
    with get_session_factory()() as db:
        db.execute(text("SELECT pg_advisory_xact_lock(21941, 3)"))
        db.execute(delete(PublicRateEvent).where(
            PublicRateEvent.created_at < now - timedelta(days=1)
        ))
        count = db.scalar(select(func.count()).select_from(PublicRateEvent).where(
            PublicRateEvent.identity_hash == key, PublicRateEvent.action == action,
            PublicRateEvent.created_at > now - timedelta(seconds=seconds),
        ))
        db.add(PublicRateEvent(identity_hash=key, action=action))
        db.commit()
        if count >= max_events:
            raise HTTPException(status_code=429, detail="Too many booking requests")


def issue_token(db: Session, entry: CalendarEntry, channel: str, now: datetime) -> str:
    old_tokens = db.scalars(select(ConfirmationToken).where(
        ConfirmationToken.entry_id == entry.id,
        ConfirmationToken.used_at.is_(None), ConfirmationToken.revoked_at.is_(None),
    )).all()
    for old in old_tokens:
        old.revoked_at = now
    raw_token = secrets.token_urlsafe(24)
    expires = min(now + timedelta(minutes=TOKEN_MINUTES), entry.hold_until)
    db.add(ConfirmationToken(
        entry_id=entry.id, channel=channel, token_sha256=digest(raw_token),
        expires_at=expires,
    ))
    entry.token_count += 1
    return raw_token


def response_for_hold(entry: CalendarEntry, channel: str, token: str, access: str) -> dict:
    result = {
        "id": entry.id, "status": entry.status, "channel": channel,
        "hold_until": entry.hold_until, "confirmation_token_expires_at":
        min(datetime.now(UTC) + timedelta(minutes=TOKEN_MINUTES), entry.hold_until),
        "channel_confirmation_code": token, "hold_secret": access,
    }
    if channel == "telegram":
        username = os.environ.get("TELEGRAM_BOT_USERNAME", "").strip("@")
        if username and username.replace("_", "").isalnum():
            result["channel_link"] = f"https://t.me/{username}?start={token}"
    elif channel == "vk":
        screen_name = os.environ.get("VK_COMMUNITY_SCREEN_NAME", "")
        if screen_name and screen_name.replace("_", "").isalnum():
            result["channel_link"] = f"https://vk.me/{screen_name}"
            result["channel_instruction"] = "Отправьте сообществу сообщение CONFIRM " + token
    return result


@router.get("/public/services")
def public_services(db: Session = Depends(get_db)):
    require_public_enabled()
    rows = db.scalars(select(Service).where(
        Service.active.is_(True), Service.duration_minutes.is_not(None)
    ).order_by(Service.name, Service.variant)).all()
    return {"services": [{
        "id": r.id, "name": r.name, "variant": r.variant,
        "price_kopecks": r.price_kopecks, "duration_minutes": r.duration_minutes,
        "buffer_minutes": r.buffer_minutes,
    } for r in rows]}


@router.get("/public/availability")
def public_availability(
    request: Request, day: str, service_id: UUID, db: Session = Depends(get_db),
):
    require_public_enabled()
    rate_limit(request, "availability", max_events=30, seconds=60)
    from datetime import date
    try:
        parsed = date.fromisoformat(day)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail="Invalid date") from exc
    now = datetime.now(UTC)
    today = now.astimezone(STUDIO_TZ).date()
    if not today <= parsed < today + timedelta(days=30):
        raise HTTPException(status_code=422, detail="Outside 30-day horizon")
    service = db.get(Service, service_id)
    if not service or not service.active or not service.duration_minutes:
        raise HTTPException(status_code=404, detail="Service unavailable")
    windows = db.scalars(select(WorkingWindow).where(WorkingWindow.day == parsed)).all()
    day_start, day_end = local_day_limits(parsed)
    busy = db.scalars(select(CalendarEntry).where(
        CalendarEntry.deleted_at.is_(None), CalendarEntry.status.in_(ACTIVE),
        CalendarEntry.starts_at < day_end, CalendarEntry.occupied_until > day_start,
    )).all()
    starts = []
    for window in windows:
        cur = datetime.combine(parsed, window.starts_at, STUDIO_TZ)
        limit = datetime.combine(parsed, window.ends_at, STUDIO_TZ)
        occupied_duration = timedelta(minutes=service.duration_minutes + service.buffer_minutes)
        while cur + occupied_duration <= limit:
            if cur > now and not any(e.starts_at < cur + occupied_duration
                and e.occupied_until > cur
                and (e.status != "pending_channel" or (e.hold_until and e.hold_until > now))
                for e in busy):
                starts.append(cur.isoformat())
            cur += timedelta(minutes=15)
    return {"day": parsed.isoformat(), "service_id": service_id, "starts_at": starts}


@router.post("/public/holds", status_code=201)
def create_hold(
    data: HoldRequest, request: Request, db: Session = Depends(get_db),
):
    require_public_enabled()
    rate_limit(request, "hold", max_events=3, seconds=3600)
    now = datetime.now(UTC)
    studio_today = now.astimezone(STUDIO_TZ).date()
    local = data.starts_at.astimezone(STUDIO_TZ)
    if data.starts_at <= now or not studio_today <= local.date() < studio_today + timedelta(days=30):
        raise HTTPException(status_code=422, detail="Not a future slot in the 30-day horizon")
    service = db.get(Service, data.service_id)
    if not service or not service.active or not service.duration_minutes:
        raise HTTPException(status_code=422, detail="Service unavailable")
    end = data.starts_at + timedelta(minutes=service.duration_minutes)
    occupied = end + timedelta(minutes=service.buffer_minutes)
    timeline_lock(db)
    expire_holds(db)
    require_working_window(db, data.starts_at, occupied)
    access = secrets.token_urlsafe(32)
    client = Client(name=data.name, phone=data.phone, preferred_channel=data.channel, consent_at=now)
    db.add(client)
    try:
        db.flush()
        entry = CalendarEntry(
            kind="appointment", source="website", status="pending_channel",
            client_id=client.id, service_id=service.id,
            starts_at=data.starts_at, ends_at=end, occupied_until=occupied,
            duration_minutes=service.duration_minutes,
            quoted_price_kopecks=service.price_kopecks,
            hold_until=now + timedelta(minutes=HOLD_MINUTES),
            hold_access_sha256=digest(access), token_count=0,
        )
        db.add(entry)
        db.flush()
        token = issue_token(db, entry, data.channel, now)
        db.add(CalendarEvent(entry_id=entry.id, actor="website", action="hold_created",
                             before=None, after=snapshot(entry)))
        db.commit()
    except IntegrityError as exc:
        db.rollback()
        if getattr(exc.orig, "sqlstate", None) == "23P01":
            raise HTTPException(status_code=409, detail="Time interval already occupied") from exc
        raise
    return response_for_hold(entry, data.channel, token, access)


@router.post("/public/holds/{entry_id}/reissue")
def reissue(
    entry_id: UUID, data: ReissueRequest, request: Request,
    db: Session = Depends(get_db),
):
    require_public_enabled()
    rate_limit(request, "reissue", max_events=6, seconds=3600)
    timeline_lock(db)
    now = datetime.now(UTC)
    entry = db.scalar(select(CalendarEntry).where(
        CalendarEntry.id == entry_id, CalendarEntry.source == "website",
    ).with_for_update())
    supplied = digest(data.hold_secret)
    stored = entry.hold_access_sha256 if entry else "0" * 64
    if not hmac.compare_digest(supplied, stored) or entry is None:
        raise HTTPException(status_code=404, detail="Booking request not found")
    if entry.status != "pending_channel" or entry.hold_until <= now:
        if entry.status == "pending_channel":
            entry.status = "expired"
            db.commit()
        raise HTTPException(status_code=410, detail="Hold has expired or finished")
    if entry.token_count >= MAX_TOKENS_PER_HOLD:
        raise HTTPException(status_code=429, detail="Confirmation token reissue limit")
    client = db.get(Client, entry.client_id)
    token = issue_token(db, entry, client.preferred_channel, now)
    db.commit()
    return response_for_hold(entry, client.preferred_channel, token, data.hold_secret)


def confirm_token(db: Session, raw: str, channel: str, provider_user_id: str) -> dict:
    """Only callable by verified messenger webhook handlers. Never expose over public HTTP."""
    timeline_lock(db)
    token = db.scalar(select(ConfirmationToken).where(
        ConfirmationToken.token_sha256 == digest(raw),
        ConfirmationToken.channel == channel,
    ).with_for_update())
    if token is None:
        raise HTTPException(status_code=404, detail="Invalid confirmation code")
    entry = db.scalar(select(CalendarEntry).where(CalendarEntry.id == token.entry_id).with_for_update())
    client = db.get(Client, entry.client_id)
    existing = client.telegram_chat_id if channel == "telegram" else client.vk_user_id
    if token.used_at is not None:
        if entry.status == "confirmed" and existing == provider_user_id:
            return {"id": entry.id, "status": "confirmed", "repeated": True}
        raise HTTPException(status_code=409, detail="Token already consumed")
    now = datetime.now(UTC)
    if entry.status != "pending_channel" or entry.hold_until <= now:
        if entry.status == "pending_channel" and entry.hold_until <= now:
            entry.status = "expired"
            db.commit()
        raise HTTPException(status_code=410, detail="Hold expired")
    if token.revoked_at is not None or token.expires_at <= now:
        raise HTTPException(status_code=410, detail="Token expired or replaced")
    before = snapshot(entry)
    entry.status = "confirmed"
    token.used_at = now
    if channel == "telegram":
        client.telegram_chat_id = provider_user_id
    elif channel == "vk":
        client.vk_user_id = provider_user_id
    else:
        raise HTTPException(status_code=422, detail="Unsupported channel")
    db.add(CalendarEvent(entry_id=entry.id, actor=channel + ":" + provider_user_id,
                         action="confirmed", before=before, after=snapshot(entry)))
    db.commit()
    return {"id": entry.id, "status": "confirmed", "repeated": False}
