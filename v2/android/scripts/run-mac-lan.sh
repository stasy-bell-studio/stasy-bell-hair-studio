#!/usr/bin/env bash
set -euo pipefail
# Private MacBook LAN-only test of existing Docker + PostgreSQL database.
# No recreated database, production deployment, port-forwarding or real data.
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$ROOT"
ENV="$ROOT/v2/.env"
COMPOSE=(docker compose --env-file "$ENV" -f "$ROOT/v2/infra/compose.yaml")

abort() { echo "BLOCKED: $*" >&2; exit 1; }
[[ "$(uname -s)" == "Darwin" ]] || abort "This command is for macOS"
[[ -f "$ENV" ]] || abort "Existing private v2/.env not found; refusing to create a new DB"
command -v docker >/dev/null || abort "Docker Desktop is required"
command -v python3 >/dev/null || abort "Python 3 is required"
command -v curl >/dev/null || abort "curl is required"

python3 - "$ENV" <<'PY'
import pathlib, stat, sys
p = pathlib.Path(sys.argv[1])
if p.is_symlink() or stat.S_IMODE(p.stat().st_mode) & 0o077:
    raise SystemExit("BLOCKED: private v2/.env must not be symlinked or group/world-readable")
values = {}
for line in p.read_text().splitlines():
    if "=" in line and not line.lstrip().startswith("#"):
        k,v=line.split("=",1)
        values[k.strip()] = v.strip().strip('"').strip("'")
db = values.get("POSTGRES_DB", "")
if values.get("STASY_PUBLIC_BOOKING_ENABLED", "0") != "0":
    raise SystemExit("BLOCKED: public booking must be disabled")
if not (values.get("STASY_EMULATOR_DB") == "1" or
        any(marker in db.lower() for marker in ("test", "demo", "emulator", "s20"))):
    raise SystemExit("BLOCKED: database is not clearly isolated testing; review before proceeding")
PY

# Existing database must be running. Do not start or recreate it.
DB_CONTAINER="$("${COMPOSE[@]}" ps --status running -q db)"
[[ -n "$DB_CONTAINER" ]] || abort "Existing test PostgreSQL isn't running; won't create a replacement"
OWNER_COUNT="$("${COMPOSE[@]}" exec -T db sh -c \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At' <<'SQL'
SELECT count(*) FROM master_accounts WHERE username='s20tester';
SQL
)" || abort "Couldn't verify test owner s20tester in the existing DB"
[[ "$OWNER_COUNT" =~ ^[1-9][0-9]*$ ]] || abort "s20tester is missing from the existing DB"

IP="${STASY_LAN_IP:-}"
if [[ -z "$IP" ]]; then
  for dev in en0 en1 en2; do
    IP="$(ipconfig getifaddr "$dev" 2>/dev/null || true)"
    if [[ -n "$IP" ]]; then break; fi
  done
fi
[[ -n "$IP" ]] || abort "No Mac LAN IPv4. Set STASY_LAN_IP to your Mac's Wi-Fi IPv4"
python3 - "$IP" <<'PY'
import ipaddress, sys
try:
    address=ipaddress.IPv4Address(sys.argv[1])
except ipaddress.AddressValueError:
    raise SystemExit("BLOCKED: invalid IPv4 address")
if not any(address in ipaddress.ip_network(block) for block in (
        "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16")):
    raise SystemExit("BLOCKED: only private RFC1918 IPv4 accepted")
PY

# Keep backups outside the repository. Never print passwords.
umask 077
BACKUPS="$HOME/Library/Application Support/Stasy Bell OS/backups"
mkdir -p "$BACKUPS"
chmod 700 "$BACKUPS"
BACKUP="$BACKUPS/before-mac-lan-$(date -u +%Y%m%dT%H%M%SZ).dump"
echo "Backing up the EXISTING test database."
bash "$ROOT/v2/infra/backup-local.sh" "$BACKUP"
[[ -s "$BACKUP" ]] || abort "Backup failed"
echo "Building API with the checked-in migrations (DB volume untouched)."
"${COMPOSE[@]}" build api
echo "Applying Alembic migration in an isolated one-off API container."
"${COMPOSE[@]}" run --rm --no-deps api alembic upgrade head
echo "Switching ONLY the API container to Mac LAN address $IP:8000."
STASY_API_BIND_IP="$IP" "${COMPOSE[@]}" up -d --no-deps --force-recreate api
for _ in {1..20}; do
  if curl --fail --silent --max-time 3 "http://$IP:8000/ready" > /dev/null; then
    echo "MAC_LAN_READY=http://$IP:8000/"
    echo "On S20 (same Wi-Fi), open http://$IP:8000/health in the phone browser."
    echo "In the NEW debug APK: enter $IP in 'IP Mac' and tap 'Подключить сервер на Mac'."
    echo "Then log in with your existing s20tester credentials."
    echo "Existing PostgreSQL volume preserved. Backup: $BACKUP"
    echo "Keep MacBook awake and Docker running. Do not expose port 8000 on the router."
    exit 0
  fi
  sleep 2
done
abort "API didn't pass /ready from Mac LAN IP. Check Docker port binding / macOS firewall; backup at $BACKUP"
