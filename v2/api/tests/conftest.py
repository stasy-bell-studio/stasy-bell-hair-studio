import os
from urllib.parse import urlsplit

import pytest
from alembic import command
from alembic.config import Config
from fastapi.testclient import TestClient
from sqlalchemy import text

from app.auth import passwords
from app.db import get_session_factory
from app.main import app
from app.models import MasterAccount

TEST_URL = os.environ.get("DATABASE_URL", "")
# Tests TRUNCATE tables. Fail closed unless explicitly connected to localhost.
if urlsplit(TEST_URL).hostname not in ("localhost", "127.0.0.1"):
    raise RuntimeError("Integration tests require a dedicated localhost PostgreSQL database")


@pytest.fixture(scope="session", autouse=True)
def migrated_database():
    command.upgrade(Config("alembic.ini"), "head")


@pytest.fixture(autouse=True)
def reset_database(migrated_database):
    with get_session_factory()() as db:
        with db.begin():
            db.execute(text(
                "TRUNCATE calendar_events, confirmation_tokens, calendar_entries, "
                "master_accounts, clients, services, working_windows, public_rate_events CASCADE"
            ))


@pytest.fixture
def owner_token():
    with get_session_factory()() as db:
        account = MasterAccount(username="master_test", password_hash=passwords.hash("test-only-owner-password"))
        db.add(account)
        db.commit()
    with TestClient(app) as client:
        response = client.post("/auth/login", json={"username": "master_test", "password": "test-only-owner-password"})
        assert response.status_code == 200
        return response.json()["access_token"]


@pytest.fixture
def api():
    with TestClient(app) as client:
        yield client
