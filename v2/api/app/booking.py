from datetime import datetime, timezone
from uuid import UUID

from fastapi import HTTPException
from sqlalchemy import update
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.models import CalendarEntry, CalendarEvent


def expire_elapsed_holds(db: Session) -> None:
    """Call in the SAME transaction before booking a new occupied interval."""
    db.execute(
        update(CalendarEntry)
        .where(
            CalendarEntry.status == "pending_channel",
            CalendarEntry.deleted_at.is_(None),
            CalendarEntry.hold_until <= datetime.now(timezone.utc),
        )
        .values(status="expired")
    )


def persist_entry(db: Session, entry: CalendarEntry, actor: str) -> UUID:
    expire_elapsed_holds(db)
    db.add(entry)
    db.flush()
    db.add(CalendarEvent(entry_id=entry.id, actor=actor, action="created",
                         before=None, after={"kind": entry.kind, "status": entry.status}))
    try:
        db.commit()
    except IntegrityError as exc:
        db.rollback()
        # PostgreSQL exclusion violation: concurrent bookings can never overlap.
        if getattr(exc.orig, "sqlstate", None) == "23P01":
            raise HTTPException(status_code=409, detail="Time interval is already occupied") from exc
        raise
    return entry.id
