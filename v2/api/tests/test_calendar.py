from datetime import datetime, time, timedelta
from zoneinfo import ZoneInfo

from app.db import get_session_factory
from app.models import CalendarEntry

TZ = ZoneInfo("Europe/Moscow")


def setup(api, token, day=None):
    headers = {"Authorization": f"Bearer {token}"}
    client = api.post("/owner/clients", headers=headers, json={"name": "Тестовая Клиентка"})
    service = api.post("/owner/services", headers=headers, json={
        "code": "test-service", "name": "Synthetic service", "active": True,
        "price_kopecks": 620000, "duration_minutes": 60, "buffer_minutes": 15,
    })
    assert client.status_code == 201, client.text
    assert service.status_code == 201, service.text
    if day is None:
        day = datetime.now(TZ).date() + timedelta(days=3)
    schedule = api.put(f"/owner/schedule/{day}", headers=headers, json={
        "windows": [{"starts_at": "09:00", "ends_at": "13:00"},
                    {"starts_at": "14:00", "ends_at": "19:00"}]
    })
    assert schedule.status_code == 200, schedule.text
    return headers, client.json()["id"], service.json()["id"], day


def book(api, headers, client_id, service_id, start, **extra):
    return api.post("/owner/calendar/appointments", headers=headers, json={
        "client_id": client_id, "service_id": service_id,
        "starts_at": start.isoformat(), "source": "phone", **extra,
    })


def at(day, hour, minute=0):
    return datetime.combine(day, time(hour, minute), TZ)


def test_schedule_validation_and_free_slots(api, owner_token):
    headers, cid, sid, day = setup(api, owner_token)
    avail = api.get("/owner/availability", headers=headers,
                    params={"day": day.isoformat(), "service_id": sid})
    assert avail.status_code == 200, avail.text
    assert at(day, 10).isoformat() in avail.json()["starts_at"]
    assert at(day, 13).isoformat() not in avail.json()["starts_at"]  # break
    assert book(api, headers, cid, sid, at(day, 10)).status_code == 201
    assert book(api, headers, cid, sid, at(day, 9, 45)).status_code == 409
    assert book(api, headers, cid, sid, at(day, 11, 15)).status_code == 201
    assert book(api, headers, cid, sid, at(day, 13)).status_code == 422
    avail2 = api.get("/owner/availability", headers=headers,
                     params={"day": day.isoformat(), "service_id": sid}).json()
    assert at(day, 10).isoformat() not in avail2["starts_at"]
    assert at(day, 14).isoformat() in avail2["starts_at"]
    assert api.get("/owner/availability", headers=headers, params={
        "day": (day + timedelta(days=50)).isoformat(), "service_id": sid
    }).status_code == 422


def test_schedule_change_cannot_invalidate_existing_appointments(api, owner_token):
    headers, cid, sid, day = setup(api, owner_token)
    assert book(api, headers, cid, sid, at(day, 10)).status_code == 201
    replace = api.put(f"/owner/schedule/{day}", headers=headers, json={
        "windows": [{"starts_at": "15:00", "ends_at": "18:00"}]
    })
    assert replace.status_code == 409
    overlapping = api.put(f"/owner/schedule/{day}", headers=headers, json={
        "windows": [{"starts_at": "09:00", "ends_at": "12:00"},
                    {"starts_at": "11:00", "ends_at": "15:00"}]
    })
    assert overlapping.status_code == 422
    current = api.get(f"/owner/schedule/{day}", headers=headers).json()["windows"]
    assert len(current) == 2


def test_move_cancel_delete_restore_with_history(api, owner_token):
    headers, cid, sid, day = setup(api, owner_token)
    initial = at(day, 10)
    res = book(api, headers, cid, sid, initial, quoted_price_kopecks=700000)
    assert res.status_code == 201, res.text
    ident = res.json()["id"]
    move = api.patch(f"/owner/calendar/{ident}/move", headers=headers,
                     json={"starts_at": at(day, 15).isoformat(), "duration_minutes": 90})
    assert move.status_code == 200, move.text
    wrong = api.post(f"/owner/calendar/{ident}/delete", headers=headers, json={
        "confirm": True, "expected_start": initial.isoformat(),
        "expected_client_name": "Тестовая Клиентка",
    })
    assert wrong.status_code == 409
    deleted = api.post(f"/owner/calendar/{ident}/delete", headers=headers, json={
        "confirm": True, "expected_start": at(day, 15).isoformat(),
        "expected_client_name": "Тестовая Клиентка",
    })
    assert deleted.status_code == 200, deleted.text
    other = book(api, headers, cid, sid, at(day, 15))
    assert other.status_code == 201
    assert api.post(f"/owner/calendar/{ident}/restore", headers=headers).status_code == 409
    assert api.post(f"/owner/calendar/{other.json()['id']}/cancel",
                    headers=headers).status_code == 200
    assert api.post(f"/owner/calendar/{ident}/restore", headers=headers).status_code == 200
    events = api.get(f"/owner/calendar/{ident}/events", headers=headers)
    assert [x["action"] for x in events.json()["events"]] == [
        "created", "moved", "soft_deleted", "restored"
    ]
    deleted_entries = api.get("/owner/calendar", headers=headers, params={
        "from_day": day.isoformat(), "to_day": day.isoformat(),
    }).json()["entries"]
    assert len(deleted_entries) == 2
    with get_session_factory()() as db:
        entry = db.get(CalendarEntry, ident)
        # Database stores the value agreed at booking, not the live service price.
        assert entry.quoted_price_kopecks == 700000
        assert entry.duration_minutes == 90


def test_auth_no_phone_blocks_and_completed_warning(api, owner_token):
    headers, cid, sid, day = setup(api, owner_token)
    assert api.get(f"/owner/schedule/{day}").status_code == 401
    assert api.get("/owner/calendar", params={
        "from_day": str(day), "to_day": str(day)
    }).status_code == 401
    # A time block may cover an otherwise non-working day.
    response = api.post("/owner/calendar/blocks", headers=headers, json={
        "starts_at": at(day + timedelta(days=1), 12).isoformat(),
        "ends_at": at(day + timedelta(days=1), 13).isoformat(),
    })
    assert response.status_code == 201, response.text
    res = book(api, headers, cid, sid, at(day, 10))
    ident = res.json()["id"]
    completed = api.post(f"/owner/calendar/{ident}/status", headers=headers,
                         json={"status": "completed"})
    assert completed.status_code == 200
    body = {"confirm": True, "expected_start": at(day, 10).isoformat(),
            "expected_client_name": "Тестовая Клиентка"}
    assert api.post(f"/owner/calendar/{ident}/delete", headers=headers,
                    json=body).status_code == 409
    body["completed_acknowledged"] = True
    assert api.post(f"/owner/calendar/{ident}/delete", headers=headers,
                    json=body).status_code == 200
