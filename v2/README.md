# Stasy Bell OS 2.0 — development only

The existing Vite/GitHub Pages website is left untouched. Nothing in `v2/` is deployed.

## Layout
`api/` FastAPI/SQLAlchemy/Alembic; `database/` schema; `web/`, `android/`, `worker/`, `migration/` later phases; `infra/` local Compose.

## Local launch (no production credentials)
1. Copy `v2/.env.example` to untracked `v2/.env`, replace all placeholders with independent local-only values. `JWT_SECRET` must be at least 32 characters.
2. `docker compose --env-file v2/.env -f v2/infra/compose.yaml up -d --build db`
3. `docker compose --env-file v2/.env -f v2/infra/compose.yaml run --rm api alembic upgrade head`
4. `docker compose --env-file v2/.env -f v2/infra/compose.yaml up -d api`
5. API is local-only: http://127.0.0.1:8000/health and /ready

## Initial master account
There is no default account. Run the one-time `python -m app.bootstrap_owner` command in the API container, providing `MASTER_BOOTSTRAP_USERNAME` and `MASTER_BOOTSTRAP_PASSWORD` in the command's environment (do not put these into Git or terminal history). Login at `POST /auth/login`, Bearer JWT expires after 30 minutes. This stage still needs login rate limiting, HTTPS/reverse proxy and final production security review before any deployment.

Owner-only early API routes: `POST /owner/clients`, `POST /owner/services`, `POST /owner/calendar/appointments`, `POST /owner/calendar/blocks`. These are groundwork for calendar; no public booking or working-window validation yet. The single SQL exclusion constraint rejects overlapping bookings and blocks (including concurrent transactions).

## Data integrity
- No service catalog is auto-seeded, prices and times must be validated against the live price list and master.
- PostgreSQL is the only authority for calendar intersections.
- Money = integer kopecks; duration = integer minutes; times = timezone-aware; studio local timezone Europe/Moscow.
- Pending holds must expire by worker and before a new reservation. Token confirmation is NOT active yet.
- Calendar events record creation. Cancel/move/soft delete/audit are stage 2; financial postings stage 5.
- Never commit `.env`, client spreadsheets, credentials, dumps or signed APK material.
- CI runs PostgreSQL migrations, lints Python and tests overlaps; it never deploys or changes Pages.
