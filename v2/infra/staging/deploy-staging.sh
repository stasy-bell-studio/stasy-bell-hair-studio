#!/usr/bin/env bash
set -euo pipefail
# This runs ONLY on a privately managed Linux staging host.
# No automatic launch from CI; secrets are kept outside this repository.
if [[ $# -ne 1 || "$1" != /* ]]; then
  echo "Usage: $0 /absolute/private/path/staging.env" >&2
  exit 2
fi
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
ENV_FILE="$1"
STAGING="$ROOT/v2/infra/staging/compose.yaml"
python3 "$ROOT/v2/infra/staging/preflight.py" "$ENV_FILE"
command -v docker >/dev/null || { echo "Docker is required" >&2; exit 1; }
export STASY_ENV_FILE="$ENV_FILE"
docker compose --env-file "$ENV_FILE" -f "$STAGING" config --quiet
docker compose --env-file "$ENV_FILE" -f "$STAGING" up -d --build
docker compose --env-file "$ENV_FILE" -f "$STAGING" ps
echo "Verify https://${STASY_API_DOMAIN:-<domain>}/health from the S20 network before building the staging APK."
