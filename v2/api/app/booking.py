"""Database-serialized calendar writes and consistent mutation audit."""
from uuid import UUID

from fastapi import HTTPException
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.models import CalendarEntry, CalendarEvent
from app.scheduling import expire_holds, require_working_window, snapshot, timeline_lock


def expire_elapsed_holds(db: Session) -> None:
    timeline_lock(db)
    expire_holds(db)


def commit_entry(db: Session, entry: CalendarEntry, actor: str, action: str, before: dict | None) -> UUID:
    db.add(entry)
    try:
        db.flush()
        db.add(CalendarEvent(entry_id=entry.id, actor=actor, action=action,
                             before=before, after=snapshot(entry)))
        db.commit()
    except IntegrityError as exc:
        db.rollback()
        if getattr(exc.orig, "sqlstate", None) == "23P01":
            raise HTTPException(status_code=409, detail="Time interval is already occupied") from exc
        raise
    return entry.id


def persist_entry(db: Session, entry: CalendarEntry, actor: str) -> UUID:
    timeline_lock(db)
    expire_holds(db)
    if entry.kind == "appointment":
        require_working_window(db, entry.starts_at, entry.occupied_until)
    return commit_entry(db, entry, actor=actor, action="created", before=None)
