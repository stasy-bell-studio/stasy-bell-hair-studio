"""One-time local-only bootstrap: credentials supplied at execution via environment.

Usage inside API container (never commit credentials):
MASTER_BOOTSTRAP_USERNAME=... MASTER_BOOTSTRAP_PASSWORD=... python -m app.bootstrap_owner
"""
import os

from sqlalchemy import select

from app.auth import passwords
from app.db import get_session_factory
from app.models import MasterAccount


def main() -> None:
    username = os.environ.get("MASTER_BOOTSTRAP_USERNAME", "").strip()
    password = os.environ.get("MASTER_BOOTSTRAP_PASSWORD", "")
    if not username or len(password) < 14:
        raise RuntimeError("Supply a username and a password of at least 14 characters via environment")
    with get_session_factory()() as session:
        if session.scalar(select(MasterAccount.id).limit(1)) is not None:
            raise RuntimeError("Master already provisioned: bootstrap is intentionally single-use")
        session.add(MasterAccount(username=username, password_hash=passwords.hash(password)))
        session.commit()
    print("Master account created")


if __name__ == "__main__":
    main()
