#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$ROOT"
ENV="$ROOT/v2/.env"
test -f "$ENV" || { echo "Missing existing v2/.env" >&2; exit 1; }
# Restore original 127.0.0.1-only API binding; keep existing test DB intact.
STASY_API_BIND_IP=127.0.0.1 docker compose --env-file "$ENV" -f v2/infra/compose.yaml \
    up -d --no-deps --force-recreate api
echo "API restricted back to http://127.0.0.1:8000. Database untouched."
