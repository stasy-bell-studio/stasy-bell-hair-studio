"""Synthetic acceptance: website → Telegram → calendar → cash → materials."""
from datetime import datetime, time, timedelta
from uuid import UUID
from zoneinfo import ZoneInfo

from sqlalchemy import select

from app.db import get_session_factory
from app.models import CalendarEntry, CalendarEvent, OwnerNotification
from app.owner_notifications import process_owner_notifications

TZ = ZoneInfo("Europe/Moscow")


def test_full_studio_booking_and_cash_flow(api, owner_token, monkeypatch):
    monkeypatch.setenv("STASY_PUBLIC_BOOKING_ENABLED", "1")
    monkeypatch.setenv("TELEGRAM_BOT_TOKEN", "synthetic-token")
    monkeypatch.setenv("TELEGRAM_WEBHOOK_SECRET", "synthetic-secret")
    monkeypatch.setenv("TELEGRAM_BOT_USERNAME", "synthetic_bot")
    monkeypatch.setenv("TELEGRAM_OWNER_CHAT_ID", "fake-owner-chat")
    h = {"Authorization": f"Bearer {owner_token}"}
    day = datetime.now(TZ).date() + timedelta(days=5)
    assert api.put(f"/owner/schedule/{day}", headers=h, json={
        "windows": [{"starts_at": "09:00", "ends_at": "19:00"}],
    }).status_code == 200
    service = api.post("/owner/services", headers=h, json={
        "code": "studio-journey", "name": "Synthetic hair care",
        "price_kopecks": 600000, "duration_minutes": 120,
        "buffer_minutes": 15, "active": True,
    })
    assert service.status_code == 201
    sid = service.json()["id"]
    start = datetime.combine(day, time(10), TZ)
    availability = api.get("/public/availability", params={
        "day": str(day), "service_id": sid,
    })
    assert start.isoformat() in availability.json()["starts_at"]
    hold = api.post("/public/holds", json={
        "name": "Synthetic client", "service_id": sid, "starts_at": start.isoformat(),
        "channel": "telegram", "personal_data_consent": True,
    })
    assert hold.status_code == 201, hold.text
    booked = hold.json()
    assert api.post("/public/holds/" + booked["id"] + "/status", json={
        "hold_secret": booked["hold_secret"],
    }).json()["status"] == "pending_channel"
    assert api.get("/public/availability", params={
        "day": str(day), "service_id": sid,
    }).json()["starts_at"].count(start.isoformat()) == 0

    # Verified webhook drives the callback, but network deliveries are mocked.
    sent = []

    async def fake_telegram_send(method, payload):
        sent.append((method, payload))

    monkeypatch.setattr("app.messenger_webhooks.telegram_api", fake_telegram_send)
    callback = {
        "callback_query": {
            "id": "callback-accept", "from": {"id": 555},
            "message": {"chat": {"id": 555, "type": "private"}},
            "data": "confirm:" + booked["channel_confirmation_code"],
        }
    }
    th = {"X-Telegram-Bot-Api-Secret-Token": "synthetic-secret"}
    assert api.post("/callbacks/telegram", headers=th, json=callback).status_code == 200
    assert api.post("/callbacks/telegram", headers=th, json=callback).status_code == 200
    assert len(sent) == 2  # callback acknowledgement may be replayed, no new visit
    assert api.post("/public/holds/" + booked["id"] + "/status", json={
        "hold_secret": booked["hold_secret"],
    }).json()["status"] == "confirmed"
    alerts = []
    with get_session_factory()() as db:
        assert process_owner_notifications(db, send=alerts.append) == 1
        assert len(alerts) == 1 and "Synthetic client" in alerts[0]
        assert len(db.scalars(select(OwnerNotification)).all()) == 1
        assert len(db.scalars(select(CalendarEvent).where(
            CalendarEvent.entry_id == UUID(booked["id"]),
        )).all()) == 2
        assert db.get(CalendarEntry, UUID(booked["id"])).quoted_price_kopecks == 600000

    assert api.post(f"/owner/calendar/{booked['id']}/status", headers=h, json={
        "status": "completed",
    }).status_code == 200
    # Only a completed procedure is included in actual cash, never the hold itself.
    cash = api.post("/owner/finances/transactions", headers=h, json={
        "kind": "income", "category": "procedure",
        "amount_kopecks": 600000, "occurred_at": start.isoformat(),
        "entry_id": booked["id"],
    })
    assert cash.status_code == 201, cash.text
    material = api.post("/owner/materials", headers=h, json={
        "name": "Synthetic conditioner", "unit": "ml",
    }).json()["id"]
    assert api.post(f"/owner/materials/{material}/movement", headers=h, json={
        "change_milliunits": 200000, "reason": "Synthetic purchase",
    }).status_code == 201
    assert api.post(f"/owner/materials/{material}/movement", headers=h, json={
        "change_milliunits": -50000, "reason": "Synthetic visit consumption",
    }).status_code == 201
    summary = api.get("/owner/finances/summary", headers=h, params={
        "year": start.year, "month": start.month,
    }).json()
    assert summary["income_kopecks"] == 600000
    assert summary["balance_kopecks"] == 600000
    # An inventory deduction does not silently create an expense.
    assert summary["expense_kopecks"] == 0
    calendar = api.get("/owner/calendar", headers=h, params={
        "from_day": str(day), "to_day": str(day),
    }).json()["entries"]
    assert len(calendar) == 1 and calendar[0]["status"] == "completed"
