# Stasy Bell OS 2.0 — development only

This directory is isolated from the current Vite/GitHub Pages site. No production deployments.

## Layout
- `api/`: Python FastAPI, SQLAlchemy, Alembic.
- `database/`: schema notes and PostgreSQL design.
- `web/`, `android/`, `worker/`, `migration/`: reserved for later delivery phases.
- `infra/`: development-only Docker Compose.

## Local development
1. Copy `v2/.env.example` to **untracked** `v2/.env`, replace every placeholder with local-only values. Use a strong independent JWT secret. The example values are NOT credentials.
2. From repository root run `docker compose --env-file v2/.env -f v2/infra/compose.yaml up -d --build db`.
3. Run `docker compose --env-file v2/.env -f v2/infra/compose.yaml run --rm api alembic upgrade head`.
4. Run `docker compose --env-file v2/.env -f v2/infra/compose.yaml up -d api`.
5. API: http://127.0.0.1:8000/health and /ready. OpenAPI is local-only during development.

No owner account is created by default; bootstrap must be an explicit secure operation later. No default or hardcoded master credentials. No public booking routes until Telegram/VK validation, abuse protection and consent checks are in place.

## Isolation / constraints
- Existing `src/`, `public/`, `index.html`, `package.json` and `.github/workflows/deploy.yml` are not changed.
- `v2/.env`, real Excel files, personal information and database dumps are excluded from version control.
- Prices and durations are NOT seeded: the current site's price rows include length variants and add-on prices; confirm mapping before loading data.
- Timezone: Europe/Moscow; timestamps in DB are timezone-aware.
- A single PostgreSQL exclusion constraint prevents overlapping active appointments and calendar blocks, including simultaneous inserts.
- This is stage 1 schema groundwork, NOT a production-ready booking or authentication release.
