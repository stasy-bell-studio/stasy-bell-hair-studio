"""Private owner calendar API. Public customer booking is intentionally absent."""
from datetime import UTC, date, datetime, time, timedelta
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, Field, model_validator
from sqlalchemy import delete, select
from sqlalchemy.orm import Session

from app.auth import require_owner
from app.booking import commit_entry, persist_entry
from app.db import get_db
from app.models import CalendarEntry, CalendarEvent, Client, MasterAccount, Service, WorkingWindow
from app.scheduling import (
    ACTIVE, STUDIO_TZ, expire_holds, local_day_limits, require_tz,
    require_working_window, snapshot, timeline_lock,
)

router = APIRouter(prefix="/owner", tags=["owner-calendar"])


class Window(BaseModel):
    starts_at: time
    ends_at: time

    @model_validator(mode="after")
    def valid(self):
        if (self.starts_at.tzinfo or self.ends_at.tzinfo
                or self.ends_at <= self.starts_at):
            raise ValueError("Working window requires increasing local clock times")
        return self


class ReplaceSchedule(BaseModel):
    windows: list[Window] = Field(max_length=20)


class NewAppointment(BaseModel):
    client_id: UUID
    service_id: UUID
    starts_at: datetime
    source: str = Field(pattern="^(phone|telegram_manual|vk_manual|other_manual)$")
    duration_minutes: int | None = Field(default=None, gt=0, le=1440)
    quoted_price_kopecks: int | None = Field(default=None, ge=0)

    @model_validator(mode="after")
    def timezone(self):
        if self.starts_at.tzinfo is None or self.starts_at.utcoffset() is None:
            raise ValueError("Timezone-aware start required")
        return self


class NewBlock(BaseModel):
    starts_at: datetime
    ends_at: datetime

    @model_validator(mode="after")
    def interval(self):
        if (self.starts_at.tzinfo is None or self.ends_at.tzinfo is None
                or self.starts_at.utcoffset() is None or self.ends_at.utcoffset() is None
                or self.ends_at <= self.starts_at):
            raise ValueError("Valid timezone-aware interval required")
        return self


class MoveAppointment(BaseModel):
    starts_at: datetime
    service_id: UUID | None = None
    duration_minutes: int | None = Field(default=None, gt=0, le=1440)
    quoted_price_kopecks: int | None = Field(default=None, ge=0)

    @model_validator(mode="after")
    def timezone(self):
        if self.starts_at.tzinfo is None or self.starts_at.utcoffset() is None:
            raise ValueError("Timezone-aware start required")
        return self


class ConfirmDelete(BaseModel):
    confirm: bool
    expected_start: datetime
    expected_client_name: str
    completed_acknowledged: bool = False


class ChangeStatus(BaseModel):
    status: str = Field(pattern="^(completed|no_show)$")


def get_entry(db: Session, entry_id: UUID) -> CalendarEntry:
    entry = db.scalar(
        select(CalendarEntry).where(CalendarEntry.id == entry_id).with_for_update()
    )
    if entry is None:
        raise HTTPException(status_code=404, detail="Calendar entry not found")
    return entry


def get_active_service(db: Session, service_id: UUID) -> Service:
    service = db.get(Service, service_id)
    if service is None or not service.active or not service.duration_minutes:
        raise HTTPException(status_code=422, detail="Unconfigured or inactive service")
    return service


def calculate_times(start: datetime, minutes: int, buffer: int):
    end = start + timedelta(minutes=minutes)
    return end, end + timedelta(minutes=buffer)


@router.put("/schedule/{day}")
def replace_schedule(
    day: date, data: ReplaceSchedule, db: Session = Depends(get_db),
    owner: MasterAccount = Depends(require_owner),
):
    windows = sorted(data.windows, key=lambda w: w.starts_at)
    if any(a.ends_at > b.starts_at for a, b in zip(windows, windows[1:])):
        raise HTTPException(status_code=422, detail="Working windows may not overlap")
    timeline_lock(db)
    expire_holds(db)
    day_start, day_end = local_day_limits(day)
    active_visits = db.scalars(
        select(CalendarEntry).where(
            CalendarEntry.kind == "appointment", CalendarEntry.deleted_at.is_(None),
            CalendarEntry.status.in_(ACTIVE),
            CalendarEntry.starts_at < day_end, CalendarEntry.occupied_until > day_start,
        )
    ).all()
    for entry in active_visits:
        start_local = entry.starts_at.astimezone(STUDIO_TZ)
        end_local = entry.occupied_until.astimezone(STUDIO_TZ)
        fits = (start_local.date() == day and end_local.date() == day
                and any(w.starts_at <= start_local.time().replace(tzinfo=None)
                        and end_local.time().replace(tzinfo=None) <= w.ends_at
                        for w in windows))
        if not fits:
            raise HTTPException(status_code=409, detail="Schedule would exclude an existing visit")
    db.execute(delete(WorkingWindow).where(WorkingWindow.day == day))
    db.add_all(WorkingWindow(day=day, starts_at=w.starts_at, ends_at=w.ends_at)
               for w in windows)
    db.commit()
    return {"day": day.isoformat(), "windows": [w.model_dump(mode="json") for w in windows]}


@router.get("/schedule/{day}")
def get_schedule(
    day: date, db: Session = Depends(get_db), owner: MasterAccount = Depends(require_owner),
):
    rows = db.scalars(
        select(WorkingWindow).where(WorkingWindow.day == day)
        .order_by(WorkingWindow.starts_at)
    ).all()
    return {"day": day.isoformat(),
            "windows": [{"starts_at": r.starts_at.isoformat(),
                         "ends_at": r.ends_at.isoformat()} for r in rows]}


@router.get("/availability")
def availability(
    day: date, service_id: UUID, db: Session = Depends(get_db),
    owner: MasterAccount = Depends(require_owner),
):
    now = datetime.now(UTC)
    today = now.astimezone(STUDIO_TZ).date()
    if day < today or day >= today + timedelta(days=30):
        raise HTTPException(status_code=422, detail="Availability horizon is 30 days")
    service = get_active_service(db, service_id)
    windows = db.scalars(
        select(WorkingWindow).where(WorkingWindow.day == day)
        .order_by(WorkingWindow.starts_at)
    ).all()
    day_start, day_end = local_day_limits(day)
    busy = db.scalars(
        select(CalendarEntry).where(
            CalendarEntry.deleted_at.is_(None),
            CalendarEntry.status.in_(ACTIVE),
            CalendarEntry.starts_at < day_end, CalendarEntry.occupied_until > day_start,
        )
    ).all()
    starts = []
    for window in windows:
        cursor = datetime.combine(day, window.starts_at, STUDIO_TZ)
        limit = datetime.combine(day, window.ends_at, STUDIO_TZ)
        while cursor + timedelta(minutes=service.duration_minutes + service.buffer_minutes) <= limit:
            end, occupied = calculate_times(cursor, service.duration_minutes, service.buffer_minutes)
            if (cursor > now and not any(
                e.starts_at < occupied and e.occupied_until > cursor and (
                    e.status != "pending_channel" or (e.hold_until and e.hold_until > now)
                )
                for e in busy
            )):
                starts.append(cursor.isoformat())
            cursor += timedelta(minutes=15)
    return {"day": day.isoformat(), "service_id": service_id, "starts_at": starts}


@router.post("/calendar/appointments", status_code=201)
def create_appointment(
    data: NewAppointment, db: Session = Depends(get_db),
    owner: MasterAccount = Depends(require_owner),
):
    if db.get(Client, data.client_id) is None:
        raise HTTPException(status_code=422, detail="Unknown client")
    service = get_active_service(db, data.service_id)
    minutes = data.duration_minutes or service.duration_minutes
    end, occupied = calculate_times(data.starts_at, minutes, service.buffer_minutes)
    price = service.price_kopecks if "quoted_price_kopecks" not in data.model_fields_set else data.quoted_price_kopecks
    entry = CalendarEntry(
        kind="appointment", client_id=data.client_id, service_id=service.id,
        source=data.source, status="confirmed", starts_at=data.starts_at,
        ends_at=end, occupied_until=occupied, duration_minutes=minutes,
        quoted_price_kopecks=price,
    )
    return {"id": persist_entry(db, entry, actor=str(owner.id)), "status": "confirmed"}


@router.post("/calendar/blocks", status_code=201)
def create_block(
    data: NewBlock, db: Session = Depends(get_db),
    owner: MasterAccount = Depends(require_owner),
):
    entry = CalendarEntry(
        kind="block", source="master", status="blocked",
        starts_at=data.starts_at, ends_at=data.ends_at, occupied_until=data.ends_at,
    )
    return {"id": persist_entry(db, entry, actor=str(owner.id)), "status": "blocked"}


@router.get("/calendar")
def list_calendar(
    from_day: date, to_day: date, include_deleted: bool = False,
    db: Session = Depends(get_db), owner: MasterAccount = Depends(require_owner),
):
    if to_day < from_day or (to_day - from_day).days > 30:
        raise HTTPException(status_code=422, detail="Invalid calendar range (maximum 31 days)")
    begin, _ = local_day_limits(from_day)
    _, end = local_day_limits(to_day)
    stmt = select(CalendarEntry).where(
        CalendarEntry.starts_at < end, CalendarEntry.occupied_until > begin,
    ).order_by(CalendarEntry.starts_at)
    if not include_deleted:
        stmt = stmt.where(CalendarEntry.deleted_at.is_(None))
    return {"entries": [snapshot(e) for e in db.scalars(stmt).all()]}


@router.patch("/calendar/{entry_id}/move")
def move_appointment(
    entry_id: UUID, data: MoveAppointment, db: Session = Depends(get_db),
    owner: MasterAccount = Depends(require_owner),
):
    timeline_lock(db)
    expire_holds(db)
    entry = get_entry(db, entry_id)
    if entry.deleted_at or entry.kind != "appointment" or entry.status != "confirmed":
        raise HTTPException(status_code=409, detail="Only active confirmed visits can be moved")
    before = snapshot(entry)
    service = get_active_service(db, data.service_id or entry.service_id)
    minutes = data.duration_minutes or (
        service.duration_minutes if data.service_id and data.service_id != entry.service_id
        else entry.duration_minutes or service.duration_minutes
    )
    end, occupied = calculate_times(data.starts_at, minutes, service.buffer_minutes)
    require_working_window(db, data.starts_at, occupied)
    entry.service_id = service.id
    entry.starts_at, entry.ends_at, entry.occupied_until = data.starts_at, end, occupied
    entry.duration_minutes = minutes
    if "quoted_price_kopecks" in data.model_fields_set:
        entry.quoted_price_kopecks = data.quoted_price_kopecks
    elif data.service_id and data.service_id != before["service_id"]:
        entry.quoted_price_kopecks = service.price_kopecks
    commit_entry(db, entry, str(owner.id), "moved", before)
    return {"id": entry.id, "status": entry.status}


@router.post("/calendar/{entry_id}/cancel")
def cancel_appointment(
    entry_id: UUID, db: Session = Depends(get_db),
    owner: MasterAccount = Depends(require_owner),
):
    timeline_lock(db)
    entry = get_entry(db, entry_id)
    if entry.deleted_at or entry.kind != "appointment" or entry.status not in ("confirmed", "pending_channel"):
        raise HTTPException(status_code=409, detail="Only active appointments may be cancelled")
    before = snapshot(entry)
    entry.status = "cancelled"
    commit_entry(db, entry, str(owner.id), "cancelled", before)
    return {"id": entry.id, "status": "cancelled"}


@router.post("/calendar/{entry_id}/status")
def change_visit_status(
    entry_id: UUID, data: ChangeStatus, db: Session = Depends(get_db),
    owner: MasterAccount = Depends(require_owner),
):
    timeline_lock(db)
    entry = get_entry(db, entry_id)
    if entry.deleted_at or entry.kind != "appointment" or entry.status != "confirmed":
        raise HTTPException(status_code=409, detail="Only confirmed visits may be completed")
    before = snapshot(entry)
    entry.status = data.status
    commit_entry(db, entry, str(owner.id), data.status, before)
    return {"id": entry.id, "status": entry.status}


@router.post("/calendar/{entry_id}/delete")
def soft_delete(
    entry_id: UUID, data: ConfirmDelete, db: Session = Depends(get_db),
    owner: MasterAccount = Depends(require_owner),
):
    require_tz(data.expected_start)
    timeline_lock(db)
    entry = get_entry(db, entry_id)
    if entry.deleted_at:
        raise HTTPException(status_code=409, detail="Already deleted")
    client = db.get(Client, entry.client_id) if entry.client_id else None
    expected_name = client.name if client else ""
    if (not data.confirm or data.expected_start != entry.starts_at
            or data.expected_client_name != expected_name):
        raise HTTPException(status_code=409, detail="Deletion confirmation does not match current visit")
    if entry.status == "completed" and not data.completed_acknowledged:
        raise HTTPException(status_code=409, detail="Completed visit requires additional acknowledgement")
    before = snapshot(entry)
    entry.deleted_at = datetime.now(UTC)
    commit_entry(db, entry, str(owner.id), "soft_deleted", before)
    return {"id": entry.id, "deleted": True}


@router.post("/calendar/{entry_id}/restore")
def restore(
    entry_id: UUID, db: Session = Depends(get_db),
    owner: MasterAccount = Depends(require_owner),
):
    timeline_lock(db)
    expire_holds(db)
    entry = get_entry(db, entry_id)
    if not entry.deleted_at:
        raise HTTPException(status_code=409, detail="Entry is not deleted")
    before = snapshot(entry)
    if entry.kind == "appointment" and entry.status in ACTIVE:
        require_working_window(db, entry.starts_at, entry.occupied_until)
    entry.deleted_at = None
    commit_entry(db, entry, str(owner.id), "restored", before)
    return {"id": entry.id, "deleted": False}


@router.get("/calendar/{entry_id}/events")
def entry_events(
    entry_id: UUID, db: Session = Depends(get_db),
    owner: MasterAccount = Depends(require_owner),
):
    if db.get(CalendarEntry, entry_id) is None:
        raise HTTPException(status_code=404, detail="Entry not found")
    rows = db.scalars(
        select(CalendarEvent).where(CalendarEvent.entry_id == entry_id)
        .order_by(CalendarEvent.occurred_at, CalendarEvent.id)
    ).all()
    return {"events": [{"actor": r.actor, "action": r.action, "before": r.before,
                         "after": r.after, "occurred_at": r.occurred_at.isoformat()}
                        for r in rows]}
