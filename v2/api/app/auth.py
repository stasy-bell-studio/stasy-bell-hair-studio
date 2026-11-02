from datetime import datetime, timedelta, timezone
from uuid import UUID

import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pwdlib import PasswordHash
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.config import get_settings
from app.db import get_db
from app.models import MasterAccount

passwords = PasswordHash.recommended()
bearer = HTTPBearer(auto_error=False)
ISSUER = "stasy-bell-v2"
AUDIENCE = "stasy-bell-owner"


def make_access_token(account: MasterAccount) -> str:
    now = datetime.now(timezone.utc)
    return jwt.encode(
        {"sub": str(account.id), "iat": now, "exp": now + timedelta(minutes=30),
         "iss": ISSUER, "aud": AUDIENCE},
        get_settings().jwt_secret,
        algorithm="HS256",
    )


def require_owner(
    auth: HTTPAuthorizationCredentials | None = Depends(bearer),
    db: Session = Depends(get_db),
) -> MasterAccount:
    invalid = HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Not authenticated")
    if auth is None or auth.scheme.lower() != "bearer":
        raise invalid
    try:
        payload = jwt.decode(
            auth.credentials, get_settings().jwt_secret, algorithms=["HS256"],
            audience=AUDIENCE, issuer=ISSUER,
        )
        account_id = UUID(payload["sub"])
    except (jwt.InvalidTokenError, ValueError, KeyError, TypeError) as exc:
        raise invalid from exc
    account = db.get(MasterAccount, account_id)
    if account is None or not account.enabled:
        raise invalid
    return account


def verify_login(db: Session, username: str, password: str) -> MasterAccount | None:
    account = db.scalar(select(MasterAccount).where(MasterAccount.username == username))
    if account is None or not account.enabled or not passwords.verify(password, account.password_hash):
        return None
    return account
