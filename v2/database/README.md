# PostgreSQL schema (Alembic migrations 0001 and 0002)

Source of truth: `../api/alembic/versions/`; models: `../api/app/models.py`.

- master_accounts: explicit owner bootstrap; Argon2 password hash; JWT token for owner-only API.
- clients: no mandatory phone; no automatic Telegram/VK identity inference.
- services: length/variant-specific prices, nullable until confirmed, times and buffers master-editable later.
- working_windows: local studio schedule (Europe/Moscow), populated in stage 2.
- calendar_entries: one authoritative shared timeline for visits and blocks; occupied_until includes the service buffer.
- ex_calendar_no_overlap: PostgreSQL GiST exclusion `tstzrange(starts_at, occupied_until, '[)')`; rejection under concurrent inserts.
- calendar_events: action history; later editing/soft deletion must append events.
- confirmation_tokens: SHA-256 hashes only; stage 3 APIs will apply 15-minute token validity and 45-minute maximum hold.
- The worker and reservation transaction must transition elapsed holds to expired before they stop blocking time.

Stage 1 does not imply production readiness: restore drills, hardening, login throttling, schedule calculations, cancellation/undo and messenger/webhook verification remain.
