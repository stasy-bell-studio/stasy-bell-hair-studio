from datetime import UTC, datetime, time, timedelta
from uuid import UUID
from zoneinfo import ZoneInfo

import pytest
from fastapi import HTTPException
from sqlalchemy import select

from app.db import get_session_factory
from app.models import CalendarEntry, CalendarEvent, Client, ConfirmationToken
from app.public_booking import confirm_token, digest

TZ = ZoneInfo("Europe/Moscow")


@pytest.fixture
def public_setup(monkeypatch, api, owner_token):
    monkeypatch.setenv("STASY_PUBLIC_BOOKING_ENABLED", "1")
    monkeypatch.setenv("TELEGRAM_BOT_TOKEN", "synthetic-test-token")
    monkeypatch.setenv("TELEGRAM_BOT_USERNAME", "synthetic_bot")
    monkeypatch.setenv("TELEGRAM_WEBHOOK_SECRET", "synthetic-secret")
    monkeypatch.setenv("VK_GROUP_ID", "123")
    monkeypatch.setenv("VK_GROUP_ACCESS_TOKEN", "synthetic-group-token")
    monkeypatch.setenv("VK_CALLBACK_SECRET", "synthetic-vk-secret")
    monkeypatch.setenv("VK_CONFIRMATION_CODE", "synthetic-confirm")
    monkeypatch.setenv("VK_COMMUNITY_SCREEN_NAME", "synthetic_group")
    auth = {"Authorization": f"Bearer {owner_token}"}
    day = datetime.now(TZ).date() + timedelta(days=3)
    assert api.put(f"/owner/schedule/{day}", headers=auth, json={
        "windows": [{"starts_at": "09:00", "ends_at": "19:00"}],
    }).status_code == 200
    r = api.post("/owner/services", headers=auth, json={
        "code": "synthetic-public", "name": "Test only", "active": True,
        "duration_minutes": 60, "buffer_minutes": 15, "price_kopecks": 620000,
    })
    assert r.status_code == 201, r.text
    return r.json()["id"], day


def submit(api, service_id, day, hour=10, channel="telegram", **params):
    return api.post("/public/holds", json={
        "name": "Synthetic customer", "phone": None,
        "starts_at": datetime.combine(day, time(hour), TZ).isoformat(),
        "service_id": service_id, "channel": channel,
        "personal_data_consent": True,
        **params,
    })


def test_public_disabled_until_explicitly_enabled(api, monkeypatch):
    monkeypatch.delenv("STASY_PUBLIC_BOOKING_ENABLED", raising=False)
    assert api.get("/public/services").status_code == 503


def test_hold_45_token_15_and_idempotent_messenger_confirmation(
    api, public_setup,
):
    service, day = public_setup
    res = submit(api, service, day)
    assert res.status_code == 201, res.text
    held = res.json()
    assert held["status"] == "pending_channel"
    assert held["channel"] == "telegram"
    now = datetime.now(UTC)
    assert timedelta(minutes=44) < datetime.fromisoformat(held["hold_until"]) - now <= timedelta(minutes=45)
    assert timedelta(minutes=14) < (
        datetime.fromisoformat(held["confirmation_token_expires_at"]) - now
    ) <= timedelta(minutes=16)
    assert api.post("/public/holds", json={
        "name": "Another", "starts_at": datetime.combine(day, time(10), TZ).isoformat(),
        "service_id": service, "channel": "vk", "personal_data_consent": True,
    }).status_code == 409
    with get_session_factory()() as db:
        first = confirm_token(db, held["channel_confirmation_code"], "telegram", "12345")
        assert first["status"] == "confirmed"
        repeated = confirm_token(db, held["channel_confirmation_code"], "telegram", "12345")
        assert repeated["repeated"]
        client = db.get(Client, db.get(CalendarEntry, UUID(held["id"])).client_id)
        assert client.telegram_chat_id == "12345"
        assert client.phone is None
        assert client.consent_at is not None
        events = db.scalars(select(CalendarEvent).where(
            CalendarEvent.entry_id == UUID(held["id"])
        )).all()
        assert [e.action for e in events] == ["hold_created", "confirmed"]
    assert api.post("/public/holds/" + held["id"] + "/reissue",
                    json={"hold_secret": held["hold_secret"]}).status_code == 410


def test_token_rotation_and_expiration(api, public_setup):
    service, day = public_setup
    first = submit(api, service, day).json()
    token = first["channel_confirmation_code"]
    with get_session_factory()() as db:
        entry = db.get(CalendarEntry, UUID(first["id"]))
        assert entry.token_count == 1
        token_row = db.scalar(select(ConfirmationToken).where(
            ConfirmationToken.token_sha256 == digest(token)
        ))
        token_row.expires_at = datetime.now(UTC) - timedelta(seconds=1)
        db.commit()
    with get_session_factory()() as db:
        with pytest.raises(HTTPException) as exc:
            confirm_token(db, token, "telegram", "123")
        assert exc.value.status_code == 410
    res = api.post(f"/public/holds/{first['id']}/reissue",
                   json={"hold_secret": first["hold_secret"]})
    assert res.status_code == 200, res.text
    new_token = res.json()["channel_confirmation_code"]
    assert new_token != token
    with get_session_factory()() as db:
        with pytest.raises(HTTPException):
            confirm_token(db, token, "telegram", "123")
        assert confirm_token(db, new_token, "telegram", "123")["status"] == "confirmed"


def test_expired_hold_cannot_be_confirmed_and_frees_interval(api, public_setup, owner_token):
    service, day = public_setup
    first = submit(api, service, day).json()
    with get_session_factory()() as db:
        entry = db.get(CalendarEntry, UUID(first["id"]))
        entry.hold_until = datetime.now(UTC) - timedelta(seconds=1)
        db.commit()
    with get_session_factory()() as db:
        with pytest.raises(HTTPException) as exc:
            confirm_token(db, first["channel_confirmation_code"], "telegram", "123")
        assert exc.value.status_code == 410
    headers = {"Authorization": f"Bearer {owner_token}"}
    person = api.post("/owner/clients", headers=headers, json={"name": "Master client"}).json()
    r = api.post("/owner/calendar/appointments", headers=headers, json={
        "client_id": person["id"], "service_id": service, "source": "phone",
        "starts_at": datetime.combine(day, time(10), TZ).isoformat(),
    })
    assert r.status_code == 201, r.text


def test_vk_callback_auth_and_confirmation(api, public_setup, monkeypatch):
    service, day = public_setup
    held = submit(api, service, day, channel="vk").json()
    monkeypatch.setenv("VK_CALLBACK_SECRET", "synthetic_webhook_secret")
    monkeypatch.setenv("VK_GROUP_ID", "123")
    monkeypatch.setenv("VK_CONFIRMATION_CODE", "synthetic-confirmation-only")
    monkeypatch.setenv("VK_GROUP_ACCESS_TOKEN", "synthetic-not-sent")
    sent = []

    async def fake_send(peer_id, message):
        sent.append((peer_id, message))

    monkeypatch.setattr("app.messenger_webhooks.vk_send_message", fake_send)
    payload = {
        "type": "message_new", "group_id": 123,
        "secret": "synthetic_webhook_secret",
        "object": {"message": {"from_id": 555,
                               "text": "CONFIRM " + held["channel_confirmation_code"]}},
    }
    assert api.post("/callbacks/vk", json={**payload, "secret": "wrong"}).status_code == 403
    assert api.post("/callbacks/vk", json={
        "type": "confirmation", "group_id": 123, "secret": "synthetic_webhook_secret",
    }).text == "synthetic-confirmation-only"
    assert api.post("/callbacks/vk", json=payload).text == "ok"
    assert api.post("/callbacks/vk", json=payload).text == "ok"
    assert len(sent) == 2
    with get_session_factory()() as db:
        entry = db.get(CalendarEntry, UUID(held["id"]))
        assert entry.status == "confirmed"
        assert db.get(Client, entry.client_id).vk_user_id == "555"
        assert len(db.scalars(select(CalendarEvent).where(
            CalendarEvent.entry_id == entry.id
        )).all()) == 2


def test_telegram_requires_start_then_click(api, public_setup, monkeypatch):
    service, day = public_setup
    held = submit(api, service, day).json()
    monkeypatch.setenv("TELEGRAM_WEBHOOK_SECRET", "synthetic_tg_secret")
    monkeypatch.setenv("TELEGRAM_BOT_TOKEN", "synthetic-not-sent")
    sent = []

    async def fake_call(method, payload):
        sent.append((method, payload))

    monkeypatch.setattr("app.messenger_webhooks.telegram_api", fake_call)
    header = {"X-Telegram-Bot-Api-Secret-Token": "synthetic_tg_secret"}
    msg = {"message": {"chat": {"id": 555, "type": "private"},
                       "text": "/start " + held["channel_confirmation_code"]}}
    assert api.post("/callbacks/telegram", headers={
        "X-Telegram-Bot-Api-Secret-Token": "wrong"
    }, json=msg).status_code == 403
    assert api.post("/callbacks/telegram", headers=header, json=msg).status_code == 200
    with get_session_factory()() as db:
        assert db.get(CalendarEntry, UUID(held["id"])).status == "pending_channel"
    callback = {"callback_query": {
        "id": "callback1", "from": {"id": 555},
        "message": {"chat": {"id": 555, "type": "private"}},
        "data": "confirm:" + held["channel_confirmation_code"],
    }}
    assert api.post("/callbacks/telegram", headers=header, json=callback).status_code == 200
    assert sent[0][0] == "sendMessage" and sent[1][0] == "answerCallbackQuery"
    with get_session_factory()() as db:
        assert db.get(CalendarEntry, UUID(held["id"])).status == "confirmed"


def test_public_rate_limit(api, public_setup):
    service, day = public_setup
    for hour in (10, 12, 14):
        r = submit(api, service, day, hour=hour)
        assert r.status_code == 201, r.text
    assert submit(api, service, day, hour=16).status_code == 429


def test_public_status_owner_alert_and_retry(api, public_setup, monkeypatch):
    from app.models import OwnerNotification
    from app.owner_notifications import process_owner_notifications
    service, day = public_setup
    held = submit(api, service, day).json()
    url = f"/public/holds/{held['id']}/status"
    body = {"hold_secret": held["hold_secret"]}
    assert api.post(url, json={"hold_secret": "x" * 35}).status_code == 404
    pending = api.post(url, json=body)
    assert pending.json()["status"] == "pending_channel"
    assert "phone" not in pending.text and "client" not in pending.text
    with get_session_factory()() as db:
        confirm_token(db, held["channel_confirmation_code"], "telegram", "123")
        confirm_token(db, held["channel_confirmation_code"], "telegram", "123")
        items = db.scalars(select(OwnerNotification)).all()
        assert len(items) == 1 and items[0].state == "pending"
        assert process_owner_notifications(db, send=lambda m: None) == 0
    monkeypatch.setenv("TELEGRAM_BOT_TOKEN", "fake-test-token")
    monkeypatch.setenv("TELEGRAM_OWNER_CHAT_ID", "fake-123")
    with get_session_factory()() as db:
        def fail_once(message):
            raise RuntimeError("synthetic provider outage")
        assert process_owner_notifications(db, send=fail_once) == 1
        row = db.scalar(select(OwnerNotification))
        assert row.state == "pending" and row.attempts == 1
        assert process_owner_notifications(db, send=fail_once) == 0
        row.next_attempt_at = datetime.now(UTC) - timedelta(seconds=1)
        db.commit()
        messages = []
        assert process_owner_notifications(db, send=messages.append) == 1
        assert "Synthetic customer" in messages[0]
        assert db.scalar(select(OwnerNotification)).state == "sent"
        assert process_owner_notifications(db, send=messages.append) == 0
    assert api.post(url, json=body).json()["status"] == "confirmed"


def test_channel_readiness_prevents_dead_holds(api, public_setup, monkeypatch):
    service, day = public_setup
    assert api.get("/public/channels").json() == {"telegram": True, "vk": True}
    monkeypatch.delenv("VK_GROUP_ACCESS_TOKEN")
    assert api.get("/public/channels").json()["vk"] is False
    response = submit(api, service, day, channel="vk")
    assert response.status_code == 503
    # This failed channel must not reserve the interval.
    assert submit(api, service, day, channel="telegram").status_code == 201


def test_public_available_days_shows_only_real_open_days(api, public_setup):
    service, day = public_setup
    response = api.get("/public/available-days", params={"service_id": service})
    assert response.status_code == 200, response.text
    payload = response.json()
    assert payload["timezone"] == "Europe/Moscow"
    assert len(payload["days"]) == 1
    assert payload["days"][0]["day"] == day.isoformat()
    before = payload["days"][0]["slot_count"]
    assert before > 0

    held = submit(api, service, day, hour=10)
    assert held.status_code == 201, held.text
    after = api.get("/public/available-days", params={"service_id": service}).json()
    assert 0 < after["days"][0]["slot_count"] < before

    assert api.get("/public/available-days", params={
        "service_id": "00000000-0000-4000-8000-000000000000"
    }).status_code == 404
