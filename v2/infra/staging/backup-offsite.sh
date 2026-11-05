#!/usr/bin/env bash
set -euo pipefail
# Encrypted-only, off-host PostgreSQL backup. Requires preconfigured rclone and age.
# Usage: AGE_RECIPIENT=age1... RCLONE_OFFSITE=remote:path bash backup-offsite.sh /absolute/private/staging.env
if [[ $# -ne 1 || "$1" != /* ]]; then
  echo "Usage: $0 /absolute/private/staging.env" >&2
  exit 2
fi
: "${AGE_RECIPIENT:?age recipient required}"
: "${RCLONE_OFFSITE:?rclone remote required}"
command -v age >/dev/null || { echo "age is required" >&2; exit 1; }
command -v rclone >/dev/null || { echo "rclone is required" >&2; exit 1; }
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
python3 "$ROOT/v2/infra/staging/preflight.py" "$1"
umask 077
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
STAMP="$(date -u '+%Y%m%dT%H%M%SZ')"
NAME="stasy-stage-${STAMP}.dump.age"
export STASY_ENV_FILE="$1"
COMPOSE=(docker compose --env-file "$1" -f "$ROOT/v2/infra/staging/compose.yaml")
# pg_dump is piped straight into age: no unencrypted archive is saved on disk.
"${COMPOSE[@]}" exec -T db sh -c 'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc --no-owner --no-acl' |
  age -r "$AGE_RECIPIENT" -o "$TMP/$NAME"
test -s "$TMP/$NAME"
rclone copyto "$TMP/$NAME" "${RCLONE_OFFSITE%/}/$NAME" --immutable
echo "Encrypted archive transferred to configured off-host storage: $NAME"
