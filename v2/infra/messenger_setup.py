"""Credential readiness, and explicit Telegram webhook registration after hosting is approved.

Never loads secrets from the GitHub repository, prints secrets, or sets public-booking ON.
Usage on the private host:
  python v2/infra/messenger_setup.py check --env /private/v2/.env --base-url https://api.example.ru
  python v2/infra/messenger_setup.py register-telegram --env /private/v2/.env --base-url https://api.example.ru --apply
"""
import argparse
import ipaddress
import json
import re
import urllib.error
import urllib.request
from pathlib import Path
from urllib.parse import urlsplit

REQUIRED = {
    "telegram": ["TELEGRAM_BOT_TOKEN", "TELEGRAM_BOT_USERNAME",
                 "TELEGRAM_WEBHOOK_SECRET", "TELEGRAM_OWNER_CHAT_ID"],
    "vk": ["VK_GROUP_ID", "VK_GROUP_ACCESS_TOKEN", "VK_CALLBACK_SECRET",
           "VK_CONFIRMATION_CODE", "VK_COMMUNITY_SCREEN_NAME"],
}


def read_private_env(path):
    file = Path(path)
    if not file.is_file():
        raise ValueError("Private environment file is missing")
    if file.stat().st_mode & 0o077:
        raise ValueError("Private environment must have owner-only permissions (chmod 600)")
    values = {}
    for line in file.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        key, mark, value = line.partition("=")
        if mark and re.fullmatch("[A-Z][A-Z0-9_]*", key):
            values[key] = value.strip().strip("\"'")
    return values


def safe_base_url(raw):
    parsed = urlsplit(raw)
    if (parsed.scheme != "https" or not parsed.hostname or
            parsed.path not in ("", "/") or parsed.query or parsed.fragment or
            parsed.username or parsed.password or parsed.port not in (None, 443)):
        raise ValueError("Provide the public HTTPS API origin only (no path/credentials)")
    try:
        ipaddress.ip_address(parsed.hostname)
    except ValueError:
        pass
    else:
        raise ValueError("Literal IP addresses are not valid webhook domains")
    if parsed.hostname in ("localhost", "example.com", "api.invalid") or "." not in parsed.hostname:
        raise ValueError("A real approved HTTPS domain is required")
    return "https://" + parsed.hostname


def missing(values, channel):
    return [key for key in REQUIRED[channel] if not values.get(key)]


def telegram_payload(values, base_url):
    absent = missing(values, "telegram")
    if absent:
        raise ValueError("Missing Telegram fields: " + ", ".join(absent))
    secret = values["TELEGRAM_WEBHOOK_SECRET"]
    if not 16 <= len(secret) <= 256 or not re.fullmatch(r"[A-Za-z0-9_-]+", secret):
        raise ValueError("Telegram webhook secret must be 16-256 A-Z/a-z/0-9/_/- characters")
    return {
        "url": safe_base_url(base_url) + "/callbacks/telegram",
        "secret_token": secret,
        "allowed_updates": ["message", "callback_query"],
        "drop_pending_updates": False,
    }


def register_telegram(values, base_url):
    payload = telegram_payload(values, base_url)
    token = values["TELEGRAM_BOT_TOKEN"]
    url = "https://api.telegram.org/bot" + token + "/setWebhook"
    request = urllib.request.Request(
        url, data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"}, method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            body = json.loads(response.read())
    except (urllib.error.URLError, ValueError) as exc:
        raise RuntimeError("Telegram webhook registration failed; no secrets logged") from None
    if body.get("ok") is not True:
        raise RuntimeError("Telegram rejected webhook registration; no secrets logged")


def main():
    parser = argparse.ArgumentParser(description="Safe messenger environment doctor")
    parser.add_argument("action", choices=["check", "register-telegram"])
    parser.add_argument("--env", required=True, help="Private env file (chmod 600)")
    parser.add_argument("--base-url", required=True, help="Public HTTPS API origin")
    parser.add_argument("--apply", action="store_true", help="Explicitly register a real webhook")
    args = parser.parse_args()
    try:
        values = read_private_env(args.env)
        base = safe_base_url(args.base_url)
        for channel in ("telegram", "vk"):
            required = missing(values, channel)
            print(channel + ": " + ("all fields present" if not required else
                                    "missing " + ", ".join(required)))
        print("API host: " + base)
        if args.action == "register-telegram":
            if not args.apply:
                raise ValueError("Refusing external change without --apply")
            if values.get("STASY_PUBLIC_BOOKING_ENABLED") == "1":
                raise ValueError("Disable public booking while registering live webhook")
            register_telegram(values, base)
            print("Telegram webhook registered. Live synthetic acceptance still required.")
        else:
            print("CHECK-ONLY: no provider settings were changed")
    except (ValueError, RuntimeError) as exc:
        parser.error(str(exc))


if __name__ == "__main__":
    main()
