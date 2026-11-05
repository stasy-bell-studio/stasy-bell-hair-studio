"""Owner authentication with rotating, revocable device refresh tokens."""
import hashlib
import hmac
import secrets
from datetime import datetime, timedelta, timezone
from uuid import UUID, uuid4

import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pwdlib import PasswordHash
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.config import get_settings
from app.db import get_db
from app.models import MasterAccount, OwnerSession

passwords = PasswordHash.recommended()
bearer = HTTPBearer(auto_error=False)
ISSUER = "stasy-bell-v2"
AUDIENCE = "stasy-bell-owner"
REFRESH_LIFETIME = timedelta(days=30)


def _unauthorized() -> HTTPException:
    return HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Not authenticated")


def _digest(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def _new_refresh(session_id: UUID) -> str:
    return f"{session_id}.{secrets.token_urlsafe(48)}"


def make_access_token(account: MasterAccount, session_id: UUID) -> str:
    now = datetime.now(timezone.utc)
    return jwt.encode(
        {"sub": str(account.id), "sid": str(session_id), "iat": now,
         "exp": now + timedelta(minutes=30), "iss": ISSUER, "aud": AUDIENCE},
        get_settings().jwt_secret,
        algorithm="HS256",
    )


def create_owner_session(db: Session, account: MasterAccount) -> tuple[str, str]:
    now = datetime.now(timezone.utc)
    session_id = uuid4()
    refresh = _new_refresh(session_id)
    db.add(OwnerSession(
        id=session_id, owner_id=account.id, refresh_hash=_digest(refresh),
        expires_at=now + REFRESH_LIFETIME,
    ))
    db.commit()
    return make_access_token(account, session_id), refresh


def _refresh_id(value: str) -> UUID:
    try:
        prefix, secret = value.split(".", 1)
        if len(value) > 300 or len(secret) < 48:
            raise ValueError("Malformed token")
        return UUID(prefix)
    except (ValueError, TypeError, AttributeError) as exc:
        raise _unauthorized() from exc


def rotate_owner_session(db: Session, refresh: str) -> tuple[str, str]:
    sid = _refresh_id(refresh)
    now = datetime.now(timezone.utc)
    session = db.scalar(select(OwnerSession).where(OwnerSession.id == sid).with_for_update())
    if session is None or session.revoked_at is not None or session.expires_at <= now:
        raise _unauthorized()
    supplied_hash = _digest(refresh)
    # Reusing the immediately preceding token revokes the affected device.
    if session.previous_hash is not None and hmac.compare_digest(
        supplied_hash, session.previous_hash
    ):
        session.revoked_at = now
        db.commit()
        raise _unauthorized()
    if not hmac.compare_digest(supplied_hash, session.refresh_hash):
        raise _unauthorized()
    owner = db.get(MasterAccount, session.owner_id)
    if owner is None or not owner.enabled:
        raise _unauthorized()
    next_refresh = _new_refresh(sid)
    session.previous_hash = session.refresh_hash
    session.refresh_hash = _digest(next_refresh)
    session.last_used_at = now
    db.commit()
    return make_access_token(owner, sid), next_refresh


def revoke_owner_session(db: Session, refresh: str) -> None:
    try:
        sid = _refresh_id(refresh)
    except HTTPException:
        return
    session = db.scalar(select(OwnerSession).where(OwnerSession.id == sid).with_for_update())
    if session is not None and hmac.compare_digest(_digest(refresh), session.refresh_hash):
        session.revoked_at = datetime.now(timezone.utc)
        db.commit()


def require_owner(
    auth: HTTPAuthorizationCredentials | None = Depends(bearer),
    db: Session = Depends(get_db),
) -> MasterAccount:
    if auth is None or auth.scheme.lower() != "bearer":
        raise _unauthorized()
    try:
        payload = jwt.decode(
            auth.credentials, get_settings().jwt_secret, algorithms=["HS256"],
            audience=AUDIENCE, issuer=ISSUER,
        )
        account_id = UUID(payload["sub"])
        session_id = UUID(payload["sid"])
    except (jwt.InvalidTokenError, ValueError, KeyError, TypeError) as exc:
        raise _unauthorized() from exc
    account = db.get(MasterAccount, account_id)
    session = db.get(OwnerSession, session_id)
    now = datetime.now(timezone.utc)
    if (account is None or not account.enabled or session is None
            or session.owner_id != account_id or session.revoked_at is not None
            or session.expires_at <= now):
        raise _unauthorized()
    return account


def verify_login(db: Session, username: str, password: str) -> MasterAccount | None:
    account = db.scalar(select(MasterAccount).where(MasterAccount.username == username))
    if account is None or not account.enabled or not passwords.verify(password, account.password_hash):
        return None
    return account
