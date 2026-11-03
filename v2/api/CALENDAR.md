# Calendar and manual appointments — stage 2 (owner-only API)

The authoritative calendar is `calendar_entries` in PostgreSQL. Active appointments, holds, and blocks share one GiST range exclusion constraint; an advisory transaction lock serializes booking and working-schedule modifications for one master. All client details and endpoints below require a valid owner JWT.

## Schedule and slots
- `PUT /owner/schedule/{YYYY-MM-DD}` atomically replaces one day's non-overlapping working windows (empty = holiday). Cannot exclude existing active appointments. Breaks are represented by gaps between windows.
- `GET /owner/schedule/{YYYY-MM-DD}` reads windows.
- `GET /owner/availability?day=...&service_id=...` offers starts at 15-minute increments for the next 30 local days, checking duration and buffer. This stage exposes the endpoint only to the master; public confirmation needs separate abuse protection.
- Dates are interpreted in `Europe/Moscow`; event timestamps require a timezone offset. Procedures may not extend past the working window or midnight. Manual editing of service duration and buffer is applied to new bookings.

## Visits and blocks
- `POST /owner/clients`: a client may have no phone or messenger connection.
- `POST /owner/services`: master explicitly configures active services, prices, durations and buffer. The API never invents prices.
- `POST /owner/calendar/appointments`: creates confirmed visits without verification codes; optional duration and quoted-price override become immutable historical snapshots until deliberately changed. Choose source from phone, telegram_manual, vk_manual or other_manual.
- `POST /owner/calendar/blocks`: blocks time for breaks, vacations or unavailability.
- `GET /owner/calendar?from_day=...&to_day=...`: calendar for at most 31 days; optional include_deleted.
- `PATCH /owner/calendar/{id}/move`: reschedules confirmed visit, re-checking working windows and conflicts.
- `POST /owner/calendar/{id}/cancel`: releases time while keeping visit/history.
- `POST /owner/calendar/{id}/status`: marks confirmed visit completed or no_show.
- `POST /owner/calendar/{id}/delete`: soft delete; requires confirmed action, current client name and start snapshot; completed visits require separate acknowledgement.
- `POST /owner/calendar/{id}/restore`: restore when no new conflict exists.
- `GET /owner/calendar/{id}/events`: action history (before/after) with actor and timestamp.

**Limitations:** no production client-facing booking, Android UI, webhook verification, recurring work patterns, multi-service bundles, reminder delivery or financial postings yet. The master-only API is intentionally private and undeployed. More tests (especially schedule race behavior, lifecycle and restore) remain part of the stage 2 gate.
