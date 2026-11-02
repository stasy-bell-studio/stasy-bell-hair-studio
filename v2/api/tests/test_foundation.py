from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
from threading import Barrier
from uuid import UUID

from fastapi import HTTPException
from sqlalchemy import select

from app.booking import persist_entry
from app.db import get_session_factory
from app.models import CalendarEntry


def setup_visit(api, token):
    headers = {"Authorization": f"Bearer {token}"}
    client = api.post("/owner/clients", json={"name": "Synthetic test only"}, headers=headers)
    service = api.post(
        "/owner/services",
        json={"code": "synthetic-test", "name": "Synthetic", "duration_minutes": 120,
              "buffer_minutes": 15, "active": True},
        headers=headers,
    )
    assert client.status_code == 201 and service.status_code == 201
    return headers, client.json()["id"], service.json()["id"]


def test_health_and_auth(api, owner_token):
    assert api.get("/health").json() == {"status": "ok", "version": "v2"}
    assert api.get("/ready").status_code == 200
    assert api.post("/owner/clients", json={"name": "Unauthenticated"}).status_code == 401
    assert api.post("/auth/login", json={"username": "master_test", "password": "wrong"}).status_code == 401
    assert api.post("/owner/clients", json={"name": "No phone"},
                    headers={"Authorization": f"Bearer {owner_token}"}).status_code == 201


def test_overlaps_buffer_and_blocks(api, owner_token):
    headers, client_id, service_id = setup_visit(api, owner_token)
    start = (datetime.now(timezone.utc) + timedelta(days=2)).replace(
        hour=9, minute=0, second=0, microsecond=0,
    )
    def book(at):
        return api.post("/owner/calendar/appointments", headers=headers, json={
            "client_id": client_id, "service_id": service_id,
            "starts_at": at.isoformat(), "source": "phone",
        })
    assert book(start).status_code == 201
    assert book(start + timedelta(minutes=120)).status_code == 409
    assert book(start + timedelta(minutes=135)).status_code == 201
    assert api.post("/owner/calendar/blocks", headers=headers, json={
        "starts_at": (start + timedelta(hours=1)).isoformat(),
        "ends_at": (start + timedelta(hours=1, minutes=30)).isoformat(),
    }).status_code == 409


def test_two_simultaneous_bookings_never_both_win(api, owner_token):
    _headers, client_id, service_id = setup_visit(api, owner_token)
    start = datetime.now(timezone.utc) + timedelta(days=5)
    barrier = Barrier(2)

    def contend(_):
        with get_session_factory()() as session:
            barrier.wait(timeout=10)
            try:
                return persist_entry(session, CalendarEntry(
                    kind="appointment", client_id=UUID(client_id), service_id=UUID(service_id),
                    source="phone", status="confirmed", starts_at=start,
                    ends_at=start + timedelta(hours=2),
                    occupied_until=start + timedelta(hours=2, minutes=15),
                ), actor="test")
            except HTTPException as exc:
                return exc.status_code

    with ThreadPoolExecutor(max_workers=2) as executor:
        results = list(executor.map(contend, [0, 1]))
    assert sum(isinstance(value, int) and value == 409 for value in results) == 1
    assert sum(not isinstance(value, int) for value in results) == 1


def test_elapsed_pending_hold_is_released_in_booking_transaction(api, owner_token):
    headers, client_id, service_id = setup_visit(api, owner_token)
    start = datetime.now(timezone.utc) + timedelta(days=3)
    with get_session_factory()() as session:
        session.add(CalendarEntry(
            kind="appointment", client_id=UUID(client_id), service_id=UUID(service_id),
            source="website", status="pending_channel",
            starts_at=start, ends_at=start + timedelta(hours=2),
            occupied_until=start + timedelta(hours=2, minutes=15),
            hold_until=datetime.now(timezone.utc) - timedelta(seconds=1),
        ))
        session.commit()
    res = api.post("/owner/calendar/appointments", headers=headers, json={
        "client_id": client_id, "service_id": service_id,
        "starts_at": start.isoformat(), "source": "phone",
    })
    assert res.status_code == 201
    with get_session_factory()() as session:
        pending = session.scalar(select(CalendarEntry).where(CalendarEntry.source == "website"))
        assert pending.status == "expired"
