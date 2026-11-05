from pathlib import Path
import importlib.util

import pytest


FILE = Path(__file__).resolve().parents[2] / "infra" / "staging" / "preflight.py"
spec = importlib.util.spec_from_file_location("stage_preflight", FILE)
assert spec and spec.loader
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def valid():
    return {
        "STASY_API_DOMAIN": "stage-api.example.com",
        "STASY_ACME_EMAIL": "owner@example.com",
        "POSTGRES_USER": "stasy_stage",
        "POSTGRES_PASSWORD": "long_random_example_password_123",
        "POSTGRES_DB": "stasy_stage",
        "DATABASE_URL": "postgresql+psycopg://stasy_stage:long_random_example_password_123@db:5432/stasy_stage",
        "JWT_SECRET": "this_is_a_dummy_very_long_12345678901234567890",
        "STASY_PUBLIC_BOOKING_ENABLED": "0",
    }


def test_valid_private_staging_settings():
    module.verify_environment(valid())


@pytest.mark.parametrize("change", [
    {"STASY_PUBLIC_BOOKING_ENABLED": "1"},
    {"DATABASE_URL": "postgresql+psycopg://stasy_stage:long_random_example_password_123@127.0.0.1:5432/stasy_stage"},
    {"POSTGRES_PASSWORD": "short"},
    {"STASY_API_DOMAIN": "http://stage-api.example.com"},
    {"STASY_ALLOWED_WEB_ORIGINS": "http://localhost:5173"},
])
def test_unsafe_staging_settings_are_rejected(change):
    settings = valid()
    settings.update(change)
    with pytest.raises(ValueError):
        module.verify_environment(settings)


def test_private_env_must_not_be_world_readable(tmp_path):
    secret = tmp_path / "staging.env"
    secret.write_text("STASY_PUBLIC_BOOKING_ENABLED=0\n")
    secret.chmod(0o644)
    with pytest.raises(ValueError, match="mode 600"):
        module.read_private_env(secret)
