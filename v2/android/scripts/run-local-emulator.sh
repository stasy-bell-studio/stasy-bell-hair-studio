#!/usr/bin/env bash
set -euo pipefail
# Run in a trusted Mac/Linux development terminal ONLY. Never use production files.
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
ENVFILE="$ROOT/v2/.env"
COMPOSE=(docker compose --env-file "$ENVFILE" -f "$ROOT/v2/infra/compose.yaml")
command -v docker >/dev/null || { echo "Install Docker Desktop first" >&2; exit 1; }
command -v adb >/dev/null || { echo "Install Android platform-tools (adb) first" >&2; exit 1; }
command -v python3 >/dev/null || { echo "Python 3 required" >&2; exit 1; }
if [[ ! -f "$ENVFILE" ]]; then
    umask 077
    PASS="$(python3 -c 'import secrets;print(secrets.token_hex(24))')"
    KEY="$(python3 -c 'import secrets;print(secrets.token_hex(48))')"
    cat > "$ENVFILE" <<EOS
STASY_EMULATOR_DB=1
POSTGRES_USER=stasy_emulator
POSTGRES_DB=stasy_v2_emulator
POSTGRES_PASSWORD=$PASS
DATABASE_URL=postgresql+psycopg://stasy_emulator:$PASS@db:5432/stasy_v2_emulator
JWT_SECRET=$KEY
STASY_PUBLIC_BOOKING_ENABLED=0
STASY_ALLOWED_WEB_ORIGINS=
EOS
    chmod 600 "$ENVFILE"
    echo "Created private emulator-only environment. No real records imported."
fi
grep -qx 'STASY_EMULATOR_DB=1' "$ENVFILE" || {
    echo "Refusing to start against an unmarked database environment" >&2; exit 1;
}
grep -qx 'POSTGRES_DB=stasy_v2_emulator' "$ENVFILE" || {
    echo "Refusing to use anything other than the emulator database" >&2; exit 1;
}
grep -qx 'STASY_PUBLIC_BOOKING_ENABLED=0' "$ENVFILE" || {
    echo "Online booking must remain disabled during emulator tests" >&2; exit 1;
}
"${COMPOSE[@]}" up -d --build db api
"${COMPOSE[@]}" exec -T api alembic upgrade head
# One-time private owner setup; never use customer credentials.
if ! "${COMPOSE[@]}" exec -T api python -c 'from app.db import get_session_factory; from app.models import MasterAccount; from sqlalchemy import select; import sys; s=get_session_factory()(); exists=s.scalar(select(MasterAccount.id).limit(1)); s.close(); sys.exit(0 if exists else 1)'; then
  read -r -p "Test owner login: " STASY_LOGIN
  read -r -s -p "Test owner password (14+ characters): " STASY_PASS
  echo
  if [[ -z "$STASY_LOGIN" || ${#STASY_PASS} -lt 14 ]]; then
    echo "Invalid credentials; no owner account created." >&2
    exit 1
  fi
  "${COMPOSE[@]}" exec -T -e MASTER_BOOTSTRAP_USERNAME="$STASY_LOGIN" \
      -e MASTER_BOOTSTRAP_PASSWORD="$STASY_PASS" api python -m app.bootstrap_owner
  unset STASY_LOGIN STASY_PASS
else
  echo "Emulator master account already exists. Use its existing login."
fi
if [[ -z "${1:-}" ]]; then
    echo "API at http://127.0.0.1:8000/ready. Pass extracted app-debug.apk as the first argument to install."
    echo "The APK must be a DEBUG build targeting http://127.0.0.1:8000/."
    exit 0
fi
if [[ ! -f "$1" ]]; then echo "APK not found: $1" >&2; exit 1; fi
adb devices
adb reverse tcp:8000 tcp:8000
adb install -r "$1"
echo "Debug APK installed. Start Stasy Bell OS in the emulator."
echo "If this is a new emulator-only DB, create the owner once by invoking:"
echo '  docker compose --env-file v2/.env -f v2/infra/compose.yaml exec -e MASTER_BOOTSTRAP_USERNAME=<username> -e MASTER_BOOTSTRAP_PASSWORD=<password> api python -m app.bootstrap_owner'
echo "Use a unique username and a password >=14 characters. Do NOT send those credentials in chat."
