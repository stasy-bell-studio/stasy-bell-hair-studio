from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

from sqlalchemy import select

from app.db import get_session_factory
from app.models import CalendarEntry, Service


def test_service_edits_do_not_reprice_existing_visits(api, owner_token):
    h = {"Authorization": f"Bearer {owner_token}"}
    tz = ZoneInfo("Europe/Moscow")
    day = datetime.now(tz).date() + timedelta(days=3)
    assert api.put(f"/owner/schedule/{day}", headers=h, json={
        "windows": [{"starts_at": "09:00", "ends_at": "19:00"}],
    }).status_code == 200
    service = api.post("/owner/services", headers=h, json={
        "code": "editable", "name": "Before", "active": True,
        "price_kopecks": 500000, "duration_minutes": 120, "buffer_minutes": 15,
    }).json()["id"]
    client = api.post("/owner/clients", headers=h, json={"name": "Synthetic"}).json()["id"]
    at = datetime.combine(day, datetime.min.time(), tz) + timedelta(hours=9)
    created = api.post("/owner/calendar/appointments", headers=h, json={
        "client_id": client, "service_id": service,
        "starts_at": at.isoformat(), "source": "phone",
    })
    assert created.status_code == 201, created.text
    changed = api.patch(f"/owner/services/{service}", headers=h, json={
        "name": "After", "price_kopecks": 650000, "duration_minutes": 180,
    })
    assert changed.status_code == 200, changed.text
    with get_session_factory()() as db:
        row = db.scalar(select(CalendarEntry))
        assert row.quoted_price_kopecks == 500000
        assert row.duration_minutes == 120
        assert db.get(Service, service).price_kopecks == 650000
    assert api.patch(f"/owner/services/{service}", headers=h, json={
        "active": False,
    }).status_code == 200
    assert api.patch(f"/owner/services/{service}", headers=h, json={
        "duration_minutes": None,
    }).status_code == 422
