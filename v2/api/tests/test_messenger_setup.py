import importlib.util
import json
from pathlib import Path

import pytest

path = Path(__file__).resolve().parents[2] / "infra" / "messenger_setup.py"
spec = importlib.util.spec_from_file_location("msetup", path)
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)


def test_webhook_registration_only_when_real_https_domain_and_credentials():
    with pytest.raises(ValueError):
        mod.safe_base_url("http://example.ru")
    with pytest.raises(ValueError):
        mod.safe_base_url("https://localhost")
    with pytest.raises(ValueError):
        mod.safe_base_url("https://api.invalid")
    values = {
        "TELEGRAM_BOT_TOKEN": "synthetic-token",
        "TELEGRAM_BOT_USERNAME": "synthetic_bot",
        "TELEGRAM_WEBHOOK_SECRET": "synthetic_secret_123456789",
        "TELEGRAM_OWNER_CHAT_ID": "12345",
    }
    payload = mod.telegram_payload(values, "https://approved-staging.ru")
    assert payload["allowed_updates"] == ["message", "callback_query"]
    assert payload["url"] == "https://approved-staging.ru/callbacks/telegram"
    assert payload["drop_pending_updates"] is False
    assert "synthetic-token" not in json.dumps(payload)
    assert mod.missing(values, "vk")


def test_environment_file_must_not_be_world_readable(tmp_path):
    cfg = tmp_path / "private.env"
    cfg.write_text("TELEGRAM_BOT_TOKEN=fake-secret\n")
    cfg.chmod(0o644)
    with pytest.raises(ValueError, match="owner-only"):
        mod.read_private_env(cfg)
    cfg.chmod(0o600)
    assert mod.read_private_env(cfg)["TELEGRAM_BOT_TOKEN"] == "fake-secret"
