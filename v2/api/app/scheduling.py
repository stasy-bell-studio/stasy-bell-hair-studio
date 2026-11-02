"""Single-master schedule rules shared by owner API and booking transactions."""
from datetime import UTC, datetime, time, timedelta
from zoneinfo import ZoneInfo

from fastapi import HTTPException
from sqlalchemy import select, text
from sqlalchemy.orm import Session

from app.models import CalendarEntry, WorkingWindow

STUDIO_TZ = ZoneInfo("Europe/Moscow")
ACTIVE = ("pending_channel", "confirmed", "completed", "no_show", "blocked")


def require_tz(value: datetime) -> None:
    if value.tzinfo is None or value.utcoffset() is None:
        raise HTTPException(status_code=422, detail="Timezone-aware datetime required")


def timeline_lock(db: Session) -> None:
    """Serialize working-window edits against bookings/moves for this single master."""
    db.execute(text("SELECT pg_advisory_xact_lock(21941, 2)"))


def expire_holds(db: Session) -> None:
    # Atomic expiry is inside the same transaction and advisory lock as new booking.
    from sqlalchemy import update
    db.execute(
        update(CalendarEntry)
        .where(CalendarEntry.status == "pending_channel",
               CalendarEntry.deleted_at.is_(None),
               CalendarEntry.hold_until <= datetime.now(UTC))
        .values(status="expired")
    )


def local_day_limits(day) -> tuple[datetime, datetime]:
    start = datetime.combine(day, time(0, 0), STUDIO_TZ)
    return start, start + timedelta(days=1)


def covers_window(db: Session, starts_at: datetime, occupied_until: datetime) -> bool:
    require_tz(starts_at)
    require_tz(occupied_until)
    start = starts_at.astimezone(STUDIO_TZ)
    end = occupied_until.astimezone(STUDIO_TZ)
    if end <= start or end.date() != start.date():
        return False
    windows = db.scalars(
        select(WorkingWindow).where(WorkingWindow.day == start.date())
    ).all()
    return any(w.starts_at <= start.time().replace(tzinfo=None)
               and end.time().replace(tzinfo=None) <= w.ends_at for w in windows)


def require_working_window(db: Session, start: datetime, occupied_until: datetime) -> None:
    if not covers_window(db, start, occupied_until):
        raise HTTPException(status_code=422, detail="Time outside working schedule or break")


def snapshot(entry: CalendarEntry) -> dict:
    return {
        "id": str(entry.id), "kind": entry.kind, "status": entry.status,
        "source": entry.source,
        "client_id": str(entry.client_id) if entry.client_id else None,
        "service_id": str(entry.service_id) if entry.service_id else None,
        "starts_at": entry.starts_at.isoformat(),
        "ends_at": entry.ends_at.isoformat(),
        "occupied_until": entry.occupied_until.isoformat(),
        "duration_minutes": entry.duration_minutes,
        "quoted_price_kopecks": entry.quoted_price_kopecks,
        "deleted_at": entry.deleted_at.isoformat() if entry.deleted_at else None,
    }
