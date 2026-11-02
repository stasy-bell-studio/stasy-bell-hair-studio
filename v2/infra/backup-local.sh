#!/usr/bin/env bash
set -euo pipefail

# Local-only PostgreSQL backup. No credentials or client data in Git.
# Run on a trusted workstation with Docker/Compose and v2/.env configured.
# Usage: bash v2/infra/backup-local.sh /absolute/path/outside-repository/backup.dump
if [[ $# -ne 1 || "$1" != /* ]]; then
  echo "Usage: $0 /absolute/path/outside-repository/backup.dump" >&2
  exit 2
fi
target="$1"
if [[ -e "$target" ]]; then
  echo "Refusing to overwrite an existing backup." >&2
  exit 2
fi
umask 077
tmp="${target}.partial.$$"
trap 'rm -f "$tmp"' EXIT
docker compose --env-file v2/.env -f v2/infra/compose.yaml exec -T db \
  sh -c 'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc --no-owner --no-acl' > "$tmp"
test -s "$tmp"
mv "$tmp" "$target"
trap - EXIT
echo "Encrypted storage and periodic off-host copies are required before production."
