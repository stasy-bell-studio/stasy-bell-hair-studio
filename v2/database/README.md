# PostgreSQL 16 schema — migration 0001

Source of truth: `../api/alembic/versions/0001_foundation.py`; SQLAlchemy models `../api/app/models.py`.

- clients: a client may have no phone; messenger IDs optional, never inferred from phone.
- services: individual length/price variants; price in kopecks; duration nullable pending approval.
- working_windows: local studio working intervals (Europe/Moscow), no fake availability until set.
- calendar_entries: both customer appointments and blocked intervals; occupied_until includes service buffer.
- ex_calendar_no_overlap: GiST exclusion of `tstzrange(starts_at, occupied_until, '[)')` for active calendar states. Database itself arbitrates concurrent inserts.
- calendar_events: immutable action-history rows; soft-deletion, cancellation and restoration are later API work.
- confirmation_tokens: only token SHA-256, never plaintext token; 15-minute lifetime enforced in future endpoint.
- For 45-minute holds, expires are processed by worker and before creating a new booking; until state transition, DB still blocks the range.

Only schema has been created in this stage. Booking/public flows, owner authentication, finance tables, scheduled cleanup, backup-and-restore verification are later work. No real personal data stored.
