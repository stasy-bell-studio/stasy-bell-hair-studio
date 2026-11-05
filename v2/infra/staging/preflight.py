"""Fail-closed checks before launching the private owner staging stack."""
import argparse
import os
import re
import stat
import sys
from pathlib import Path
from urllib.parse import unquote, urlsplit

DOMAIN_RE = re.compile(
    r"^(?=.{4,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$"
)


def read_private_env(path: Path) -> dict[str, str]:
    if not path.is_absolute() or path.is_symlink() or not path.is_file():
        raise ValueError("Supply an absolute, non-symlink private env file")
    repo = Path(__file__).resolve().parents[3]
    if path == repo or repo in path.parents:
        raise ValueError("Secrets must stay outside the Git working tree")
    permissions = stat.S_IMODE(path.stat().st_mode)
    if permissions & 0o077:
        raise ValueError("Private env file must have mode 600 or stricter")
    values = {}
    for lineno, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export ") or "=" not in line:
            raise ValueError(f"Invalid env entry on line {lineno}")
        key, value = line.split("=", 1)
        if not re.fullmatch(r"[A-Z][A-Z0-9_]*", key):
            raise ValueError(f"Invalid env key on line {lineno}")
        if key in values:
            raise ValueError(f"Duplicate env key on line {lineno}")
        values[key] = value.strip().strip('"').strip("'")
    return values


def verify_environment(values: dict[str, str]) -> None:
    required = ("POSTGRES_USER", "POSTGRES_PASSWORD", "POSTGRES_DB",
                "DATABASE_URL", "JWT_SECRET", "STASY_API_DOMAIN",
                "STASY_ACME_EMAIL")
    if any(not values.get(key) for key in required):
        raise ValueError("Required staging env setting is missing")
    if values.get("STASY_PUBLIC_BOOKING_ENABLED") != "0":
        raise ValueError("Public booking must remain disabled in private staging")
    if not DOMAIN_RE.fullmatch(values["STASY_API_DOMAIN"]):
        raise ValueError("STASY_API_DOMAIN must be a plain valid DNS hostname")
    if not re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+", values["STASY_ACME_EMAIL"]):
        raise ValueError("A valid ACME email is required")
    if len(values["JWT_SECRET"]) < 32:
        raise ValueError("JWT_SECRET is too short")
    if len(values["POSTGRES_PASSWORD"]) < 20:
        raise ValueError("POSTGRES_PASSWORD must be at least 20 characters")
    db = urlsplit(values["DATABASE_URL"])
    if (db.scheme != "postgresql+psycopg" or db.hostname != "db" or
            unquote(db.username or "") != values["POSTGRES_USER"] or
            unquote(db.password or "") != values["POSTGRES_PASSWORD"] or
            db.path.lstrip("/") != values["POSTGRES_DB"]):
        raise ValueError("DATABASE_URL must point to the matching internal PostgreSQL service")
    if any(not origin.strip().startswith("https://") for origin in
           values.get("STASY_ALLOWED_WEB_ORIGINS", "").split(",") if origin.strip()):
        raise ValueError("Only HTTPS web origins are allowed")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("env_file", type=Path)
    args = parser.parse_args()
    try:
        verify_environment(read_private_env(args.env_file))
    except (ValueError, OSError) as exc:
        print(f"Staging preflight blocked: {exc}", file=sys.stderr)
        return 1
    print("Private HTTPS staging preflight OK (credentials withheld)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
