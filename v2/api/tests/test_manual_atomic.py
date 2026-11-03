from datetime import datetime, time, timedelta
from uuid import UUID
from zoneinfo import ZoneInfo

from sqlalchemy import func, select

from app.db import get_session_factory
from app.models import Client

TZ = ZoneInfo("Europe/Moscow")


def test_atomic_manual_client_plus_booking(api, owner_token):
    headers = {"Authorization": f"Bearer {owner_token}"}
    day = datetime.now(TZ).date() + timedelta(days=4)
    assert api.put(f"/owner/schedule/{day}", headers=headers, json={
        "windows": [{"starts_at": "09:00", "ends_at": "19:00"}],
    }).status_code == 200
    service = api.post("/owner/services", headers=headers, json={
        "code": "manual-atomic-test", "name": "Synthetic manual",
        "active": True, "duration_minutes": 90, "buffer_minutes": 15,
    })
    sid = service.json()["id"]
    at = datetime.combine(day, time(10), TZ).isoformat()
    payload = {"client_name": "Synthetic New", "service_id": sid, "starts_at": at}
    first = api.post("/owner/calendar/manual", headers=headers, json=payload)
    assert first.status_code == 201, first.text
    assert first.json()["status"] == "confirmed"
    with get_session_factory()() as db:
        before = db.scalar(select(func.count()).select_from(Client))
        assert db.get(Client, UUID(first.json()["client_id"])).phone is None
    conflict = api.post("/owner/calendar/manual", headers=headers, json={
        **payload, "client_name": "Should be rolled back",
    })
    assert conflict.status_code == 409
    with get_session_factory()() as db:
        after = db.scalar(select(func.count()).select_from(Client))
        assert after == before
        assert db.scalar(select(Client).where(Client.name == "Should be rolled back")) is None
    assert api.post("/owner/calendar/manual", headers=headers, json={
        "service_id": sid, "starts_at": at, "client_name": "One",
        "existing_client_id": first.json()["client_id"],
    }).status_code == 422
    other = api.post("/owner/calendar/manual", headers=headers, json={
        "existing_client_id": first.json()["client_id"], "service_id": sid,
        "starts_at": datetime.combine(day, time(13), TZ).isoformat(),
    })
    assert other.status_code == 201, other.text
