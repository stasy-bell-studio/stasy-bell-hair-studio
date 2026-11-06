#!/usr/bin/env bash
set -euo pipefail
# Mac-only private migration into EXISTING test DB. No new DB, no production.
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$ROOT"
die() { echo "BLOCKED: $*" >&2; exit 1; }
[[ "$(uname -s)" == "Darwin" ]] || die "Only macOS is supported"
[[ $# -eq 1 ]] || die "Usage: bash v2/migration/import-mac-archive.sh /private/path/ZIP"
ZIP="$1"
[[ "$ZIP" == /* && -f "$ZIP" && ! -L "$ZIP" ]] || die "Absolute existing ZIP required"
[[ -f "$ROOT/v2/.env" ]] || die "Existing private v2/.env required"
command -v docker >/dev/null || die "Docker Desktop not available"
command -v unzip >/dev/null || die "macOS unzip required"
command -v shasum >/dev/null || die "macOS shasum required"
command -v python3 >/dev/null || die "Python 3 required"
EXPECTED="c61c9ce1073864e376e345263a9f0603dd05d03c41d3c753c3086e4c8a93237d"
ACTUAL="$(shasum -a 256 "$ZIP" | awk '{print $1}')"
[[ "$ACTUAL" == "$EXPECTED" ]] || die "Private ZIP SHA-256 mismatch"
umask 077
EXTRACTED="$(mktemp -d /tmp/stasy-archive.XXXXXXXX)"
trap 'rm -rf "$EXTRACTED"' EXIT
unzip -p "$ZIP" archive.json > "$EXTRACTED/archive.json" || die "Missing private archive.json"
chmod 600 "$EXTRACTED/archive.json"
test -s "$EXTRACTED/archive.json" || die "Empty archive.json"
COMPOSE=(docker compose --env-file v2/.env -f v2/infra/compose.yaml)
DB_ID="$("${COMPOSE[@]}" ps --status running -q db)"
[[ -n "$DB_ID" ]] || die "Existing test PostgreSQL must be running"
python3 - <<'PY'
from pathlib import Path
import stat
p=Path("v2/.env")
if p.is_symlink() or (stat.S_IMODE(p.stat().st_mode) & 0o077):
    raise SystemExit("BLOCKED: private env must have mode 600")
values={}
for line in p.read_text(encoding="utf-8").splitlines():
    if "=" in line and not line.lstrip().startswith("#"):
        key,value=line.split("=",1)
        values[key.strip()]=value.strip().strip('"').strip("'")
db=values.get("POSTGRES_DB","")
if values.get("STASY_PUBLIC_BOOKING_ENABLED") != "0":
    raise SystemExit("BLOCKED: public booking must remain OFF")
if values.get("STASY_EMULATOR_DB")!="1" and not any(
    x in db.lower() for x in ("test","demo","emulator","s20")
):
    raise SystemExit("BLOCKED: database not marked testing")
PY
BACKUPS="$HOME/Library/Application Support/Stasy Bell OS/backups"
mkdir -p "$BACKUPS"
chmod 700 "$BACKUPS"
BACKUP="$BACKUPS/pre-legacy-import-$(date -u +%Y%m%dT%H%M%SZ).dump"
echo "Backing up existing database outside Git..."
bash v2/infra/backup-local.sh "$BACKUP"
test -s "$BACKUP" || die "Backup failed"
echo "Building only API image; PostgreSQL volume untouched..."
"${COMPOSE[@]}" build api
"${COMPOSE[@]}" run --rm --no-deps api alembic upgrade head
echo "Closing API LAN access before transferring customer records..."
STASY_API_BIND_IP=127.0.0.1 "${COMPOSE[@]}" up -d --no-deps --force-recreate api
echo "Archive dry-run..."
"${COMPOSE[@]}" exec -T api python -m app.import_legacy --input - --dry-run < "$EXTRACTED/archive.json"
echo "Atomic import..."
"${COMPOSE[@]}" exec -T api python -m app.import_legacy --input - --apply < "$EXTRACTED/archive.json"
"${COMPOSE[@]}" exec -T api python - <<'PY'
from sqlalchemy import select, func
from app.db import get_session_factory
from app.models import LegacyVisit, LegacyClientRef, LegacyCatalogItem, LegacyMonthlySummary
with get_session_factory()() as db:
    counts=tuple(db.scalar(select(func.count()).select_from(x)) for x in (
        LegacyClientRef,LegacyVisit,LegacyCatalogItem,LegacyMonthlySummary
    ))
    assert counts==(159,224,31,14),"Archive counts differ"
    print("ARCHIVE_VERIFIED client_refs=%d visits=%d catalog=%d months=%d" % counts)
PY
curl --fail --silent --max-time 4 http://127.0.0.1:8000/ready > /dev/null ||
    die "API readiness failed"
echo "LOCAL_PRIVATE_ARCHIVE_READY; existing PostgreSQL preserved"
echo "For S20 USB: adb reverse tcp:8000 tcp:8000"
echo "Before-import backup: $BACKUP"
