"""Owner-only, actual cash ledger and minimal inventory. No speculative finance models."""
from datetime import UTC, datetime
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field, model_validator
from sqlalchemy import func, select, text
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.auth import require_owner
from app.db import get_db
from app.models import (
    CalendarEntry, FinanceTransaction, MasterAccount, Material, MaterialMovement,
    MonthClosure,
)
from app.scheduling import STUDIO_TZ, local_day_limits

router = APIRouter(prefix="/owner", tags=["owner-finances"])
INCOME_CATEGORIES = {"procedure", "other"}
EXPENSE_CATEGORIES = {"materials", "rent", "taxes", "advertising", "other"}


class Month(BaseModel):
    year: int = Field(ge=2000, le=2100)
    month: int = Field(ge=1, le=12)


class Reopen(Month):
    reason: str = Field(min_length=10, max_length=500)


class NewTransaction(BaseModel):
    kind: str = Field(pattern="^(income|expense)$")
    category: str
    amount_kopecks: int = Field(gt=0)
    occurred_at: datetime
    entry_id: UUID | None = None
    note: str | None = Field(default=None, max_length=1000)

    @model_validator(mode="after")
    def validate(self):
        if self.occurred_at.tzinfo is None or self.occurred_at.utcoffset() is None:
            raise ValueError("Timezone-aware transaction timestamp required")
        allowed = INCOME_CATEGORIES if self.kind == "income" else EXPENSE_CATEGORIES
        if self.category not in allowed:
            raise ValueError("Invalid category for transaction kind")
        if self.category == "procedure" and self.entry_id is None:
            raise ValueError("Procedure income must reference completed visit")
        return self


class Correction(BaseModel):
    reason: str = Field(min_length=10, max_length=500)


class NewMaterial(BaseModel):
    name: str = Field(min_length=1, max_length=200)
    unit: str = Field(pattern="^(g|ml|pcs)$")


class NewMovement(BaseModel):
    change_milliunits: int
    reason: str = Field(min_length=3, max_length=200)

    @model_validator(mode="after")
    def nonzero(self):
        if self.change_milliunits == 0:
            raise ValueError("Movement must not be zero")
        return self


def finance_lock(db: Session) -> None:
    db.execute(text("SELECT pg_advisory_xact_lock(21941, 5)"))


def month_bounds(year: int, month: int) -> tuple[datetime, datetime]:
    from datetime import date
    first, _ = local_day_limits(date(year, month, 1))
    next_y, next_m = (year + 1, 1) if month == 12 else (year, month + 1)
    end, _ = local_day_limits(date(next_y, next_m, 1))
    return first, end


def active_closure(db: Session, year: int, month: int) -> MonthClosure | None:
    return db.scalar(select(MonthClosure).where(
        MonthClosure.year == year, MonthClosure.month == month,
        MonthClosure.reopened_at.is_(None),
    ))


def require_open_month(db: Session, when: datetime) -> tuple[int, int]:
    local = when.astimezone(STUDIO_TZ)
    if active_closure(db, local.year, local.month):
        raise HTTPException(status_code=409, detail="Month closed: reopen with reason before correction")
    return local.year, local.month


def monthly_transactions(db: Session, year: int, month: int) -> list[FinanceTransaction]:
    start, end = month_bounds(year, month)
    return db.scalars(select(FinanceTransaction).where(
        FinanceTransaction.occurred_at >= start,
        FinanceTransaction.occurred_at < end,
    ).order_by(FinanceTransaction.occurred_at, FinanceTransaction.created_at)).all()


def totals(entries: list[FinanceTransaction]) -> dict:
    income = sum(((-1 if x.correction_of_id else 1) * x.amount_kopecks)
                 for x in entries if x.kind == "income")
    expense = sum(((-1 if x.correction_of_id else 1) * x.amount_kopecks)
                  for x in entries if x.kind == "expense")
    return {"income_kopecks": income, "expense_kopecks": expense,
            "balance_kopecks": income - expense, "transactions": len(entries)}


def transaction_view(entry: FinanceTransaction) -> dict:
    return {
        "id": entry.id, "kind": entry.kind, "category": entry.category,
        "amount_kopecks": entry.amount_kopecks,
        "correction_of_id": entry.correction_of_id,
        "entry_id": entry.entry_id, "occurred_at": entry.occurred_at,
        "note": entry.note,
    }


@router.post("/finances/transactions", status_code=201)
def post_transaction(
    data: NewTransaction, db: Session = Depends(get_db),
    owner: MasterAccount = Depends(require_owner),
):
    finance_lock(db)
    require_open_month(db, data.occurred_at)
    if data.entry_id is not None:
        visit = db.get(CalendarEntry, data.entry_id)
        if (visit is None or visit.kind != "appointment" or visit.deleted_at
                or (data.category == "procedure" and visit.status != "completed")):
            raise HTTPException(status_code=422, detail="Invalid or unfinished linked visit")
    entry = FinanceTransaction(**data.model_dump(), created_by=str(owner.id))
    db.add(entry)
    db.commit()
    return {"id": entry.id}


@router.get("/finances/transactions")
def list_transactions(
    year: int, month: int, db: Session = Depends(get_db),
    owner: MasterAccount = Depends(require_owner),
):
    parsed = Month(year=year, month=month)
    return {"entries": [transaction_view(x) for x in monthly_transactions(
        db, parsed.year, parsed.month
    )]}


@router.get("/finances/summary")
def finance_summary(
    year: int, month: int, db: Session = Depends(get_db),
    owner: MasterAccount = Depends(require_owner),
):
    parsed = Month(year=year, month=month)
    closure = active_closure(db, parsed.year, parsed.month)
    return {"year": year, "month": month,
            "is_closed": closure is not None,
            "closed_version": closure.version if closure else None,
            **totals(monthly_transactions(db, year, month))}


@router.post("/finances/close", status_code=201)
def close_month(
    period: Month, db: Session = Depends(get_db),
    owner: MasterAccount = Depends(require_owner),
):
    finance_lock(db)
    if active_closure(db, period.year, period.month):
        raise HTTPException(status_code=409, detail="Month already closed")
    last_version = db.scalar(select(func.max(MonthClosure.version)).where(
        MonthClosure.year == period.year, MonthClosure.month == period.month
    )) or 0
    values = totals(monthly_transactions(db, period.year, period.month))
    closure = MonthClosure(
        year=period.year, month=period.month, version=last_version + 1,
        income_kopecks=values["income_kopecks"],
        expense_kopecks=values["expense_kopecks"],
        balance_kopecks=values["balance_kopecks"],
        closed_by=str(owner.id),
    )
    db.add(closure)
    db.commit()
    return {"id": closure.id, "version": closure.version, **values}


@router.post("/finances/reopen")
def reopen_month(
    data: Reopen, db: Session = Depends(get_db),
    owner: MasterAccount = Depends(require_owner),
):
    finance_lock(db)
    closure = active_closure(db, data.year, data.month)
    if closure is None:
        raise HTTPException(status_code=409, detail="Month is not closed")
    closure.reopened_at = datetime.now(UTC)
    closure.reopened_by = str(owner.id)
    closure.reopen_reason = data.reason
    db.commit()
    return {"year": data.year, "month": data.month, "reopened_version": closure.version}


@router.get("/finances/closures")
def closure_history(
    year: int, month: int, db: Session = Depends(get_db),
    owner: MasterAccount = Depends(require_owner),
):
    parsed = Month(year=year, month=month)
    entries = db.scalars(select(MonthClosure).where(
        MonthClosure.year == parsed.year, MonthClosure.month == parsed.month,
    ).order_by(MonthClosure.version)).all()
    return {"versions": [{
        "version": x.version, "income_kopecks": x.income_kopecks,
        "expense_kopecks": x.expense_kopecks,
        "balance_kopecks": x.balance_kopecks,
        "reopened_at": x.reopened_at,
        "reopen_reason": x.reopen_reason, "closed_at": x.closed_at,
    } for x in entries]}


@router.post("/finances/transactions/{transaction_id}/reverse", status_code=201)
def reverse_transaction(
    transaction_id: UUID, data: Correction, db: Session = Depends(get_db),
    owner: MasterAccount = Depends(require_owner),
):
    finance_lock(db)
    original = db.scalar(select(FinanceTransaction).where(
        FinanceTransaction.id == transaction_id
    ).with_for_update())
    if original is None:
        raise HTTPException(status_code=404, detail="Transaction not found")
    if original.correction_of_id is not None:
        raise HTTPException(status_code=409, detail="Cannot reverse a reversal")
    require_open_month(db, original.occurred_at)
    if db.scalar(select(FinanceTransaction.id).where(
        FinanceTransaction.correction_of_id == original.id
    )):
        raise HTTPException(status_code=409, detail="Already reversed")
    correction = FinanceTransaction(
        kind=original.kind, category=original.category,
        amount_kopecks=original.amount_kopecks, occurred_at=original.occurred_at,
        entry_id=original.entry_id, correction_of_id=original.id,
        note="Reversal: " + data.reason, created_by=str(owner.id),
    )
    db.add(correction)
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        raise HTTPException(status_code=409, detail="Already reversed")
    return {"id": correction.id, "correction_of_id": original.id}


@router.post("/materials", status_code=201)
def create_material(
    data: NewMaterial, db: Session = Depends(get_db),
    owner: MasterAccount = Depends(require_owner),
):
    if db.scalar(select(Material.id).where(Material.name == data.name)):
        raise HTTPException(status_code=409, detail="Material already exists")
    item = Material(name=data.name, unit=data.unit, stock_milliunits=0)
    db.add(item)
    db.commit()
    return {"id": item.id}


@router.get("/materials")
def materials(
    db: Session = Depends(get_db), owner: MasterAccount = Depends(require_owner),
):
    rows = db.scalars(select(Material).order_by(Material.name)).all()
    return {"materials": [{"id": x.id, "name": x.name, "unit": x.unit,
                           "stock_milliunits": x.stock_milliunits} for x in rows]}


@router.post("/materials/{material_id}/movement", status_code=201)
def material_movement(
    material_id: UUID, data: NewMovement, db: Session = Depends(get_db),
    owner: MasterAccount = Depends(require_owner),
):
    item = db.scalar(select(Material).where(Material.id == material_id).with_for_update())
    if item is None:
        raise HTTPException(status_code=404, detail="Material not found")
    if item.stock_milliunits + data.change_milliunits < 0:
        raise HTTPException(status_code=409, detail="Insufficient material stock")
    item.stock_milliunits += data.change_milliunits
    movement = MaterialMovement(
        material_id=item.id, change_milliunits=data.change_milliunits,
        reason=data.reason, created_by=str(owner.id),
    )
    db.add(movement)
    db.commit()
    return {"id": movement.id, "stock_milliunits": item.stock_milliunits}


@router.get("/materials/{material_id}/movements")
def material_history(
    material_id: UUID, db: Session = Depends(get_db),
    owner: MasterAccount = Depends(require_owner),
):
    if db.get(Material, material_id) is None:
        raise HTTPException(status_code=404, detail="Material not found")
    rows = db.scalars(select(MaterialMovement).where(
        MaterialMovement.material_id == material_id
    ).order_by(MaterialMovement.created_at)).all()
    return {"movements": [{
        "id": x.id, "change_milliunits": x.change_milliunits,
        "reason": x.reason, "created_at": x.created_at,
    } for x in rows]}
