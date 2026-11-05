"""Owner device sessions: rotation, replay, logout, remote revocation."""
from datetime import datetime, timedelta, timezone
from uuid import UUID

from sqlalchemy import select

from app.db import get_session_factory
from app.models import OwnerSession


def _login(api):
    result = api.post("/auth/login", json={
        "username": "master_test", "password": "test-only-owner-password",
    })
    assert result.status_code == 200, result.text
    return result.json()


def _headers(access):
    return {"Authorization": f"Bearer {access}"}


def test_refresh_rotates_without_password_and_revokes_on_replay(api, owner_token):
    original = _login(api)
    assert original["refresh_token"] != original["access_token"]
    assert api.get("/owner/clients", headers=_headers(original["access_token"])).status_code == 200

    rotated = api.post("/auth/refresh", json={"refresh_token": original["refresh_token"]})
    assert rotated.status_code == 200
    current = rotated.json()
    assert current["refresh_token"] != original["refresh_token"]
    assert api.get("/owner/clients", headers=_headers(current["access_token"])).status_code == 200

    # A stolen previously-used token is replayed: kill only this device session.
    assert api.post("/auth/refresh", json={"refresh_token": original["refresh_token"]}).status_code == 401
    assert api.get("/owner/clients", headers=_headers(current["access_token"])).status_code == 401
    assert api.post("/auth/refresh", json={"refresh_token": current["refresh_token"]}).status_code == 401
    # Unrelated valid device remains logged in.
    assert api.get("/owner/clients", headers=_headers(owner_token)).status_code == 200


def test_invalid_token_cannot_revoke_valid_session(api, owner_token):
    valid = _login(api)
    sid = valid["refresh_token"].split(".", 1)[0]
    forged = sid + "." + "A" * 64
    assert api.post("/auth/refresh", json={"refresh_token": forged}).status_code == 401
    assert api.get("/owner/clients", headers=_headers(valid["access_token"])).status_code == 200


def test_logout_invalidates_access_and_refresh(api, owner_token):
    valid = _login(api)
    result = api.post("/auth/logout", json={"refresh_token": valid["refresh_token"]})
    assert result.status_code == 204
    assert api.get("/owner/clients", headers=_headers(valid["access_token"])).status_code == 401
    assert api.post("/auth/refresh", json={"refresh_token": valid["refresh_token"]}).status_code == 401


def test_owner_can_revoke_one_device(api, owner_token):
    device = _login(api)
    sid = device["refresh_token"].split(".", 1)[0]
    listing = api.get("/owner/sessions", headers=_headers(owner_token))
    assert listing.status_code == 200
    assert sid in {s["id"] for s in listing.json()["sessions"]}
    result = api.post(f"/owner/sessions/{sid}/revoke", headers=_headers(owner_token))
    assert result.status_code == 204
    assert api.get("/owner/clients", headers=_headers(device["access_token"])).status_code == 401
    assert api.get("/owner/clients", headers=_headers(owner_token)).status_code == 200


def test_expired_refresh_is_refused(api, owner_token):
    valid = _login(api)
    sid = UUID(valid["refresh_token"].split(".", 1)[0])
    with get_session_factory()() as db:
        device = db.scalar(select(OwnerSession).where(OwnerSession.id == sid))
        device.expires_at = datetime.now(timezone.utc) - timedelta(days=1)
        db.commit()
    assert api.post("/auth/refresh", json={"refresh_token": valid["refresh_token"]}).status_code == 401
    assert api.get("/owner/clients", headers=_headers(valid["access_token"])).status_code == 401
