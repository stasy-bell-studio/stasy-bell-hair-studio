"""Owner-only client history and monthly operational report.

All monetary figures are actual *manually entered* transactions. Completing an
appointment never creates revenue; historical quote is shown separately.
"""
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, Field
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.auth import require_owner
from app.db import get_db
from app.finance_api import Month, active_closure, month_bounds, monthly_transactions, totals
from app.models import (
    CalendarEntry, Client, FinanceTransaction, MasterAccount, Service,
)

router = APIRouter(prefix="/owner", tags=["owner-insights"])


class ClientUpdate(BaseModel):
    name: str | None = Field(default=None, min_length=1, max_length=200)
    phone: str | None = Field(default=None, max_length=40)


@router.patch("/clients/{client_id}")
def update_client(
    client_id: UUID, data: ClientUpdate, db: Session = Depends(get_db),
    owner: MasterAccount = Depends(require_owner),
):
    item = db.get(Client, client_id)
    if item is None:
        raise HTTPException(status_code=404, detail="Client not found")
    changes = data.model_dump(exclude_unset=True)
    if not changes or ("name" in changes and changes["name"] is None):
        raise HTTPException(status_code=422, detail="No valid changes")
    if "name" in changes:
        changes["name"] = changes["name"].strip()
        if not changes["name"]:
            raise HTTPException(status_code=422, detail="Client name cannot be empty")
    if "phone" in changes and changes["phone"] is not None:
        changes["phone"] = changes["phone"].strip() or None
    for key, value in changes.items():
        setattr(item, key, value)
    db.commit()
    return {"id": str(item.id), "name": item.name, "phone": item.phone}


@router.get("/clients/{client_id}/history")
def client_history(
    client_id: UUID,
    limit: int = Query(default=30, ge=1, le=100),
    offset: int = Query(default=0, ge=0, le=1000000),
    db: Session = Depends(get_db),
    owner: MasterAccount = Depends(require_owner),
):
    client = db.get(Client, client_id)
    if client is None:
        raise HTTPException(status_code=404, detail="Client not found")
    query = select(CalendarEntry).where(
        CalendarEntry.client_id == client.id,
        CalendarEntry.kind == "appointment",
        CalendarEntry.deleted_at.is_(None),
    )
    total = db.scalar(select(func.count()).select_from(query.subquery())) or 0
    items = db.scalars(query.order_by(
        CalendarEntry.starts_at.desc(), CalendarEntry.id.desc(),
    ).offset(offset).limit(limit)).all()

    services = {s.id: s for s in db.scalars(select(Service).where(
        Service.id.in_([r.service_id for r in items if r.service_id is not None])
    )).all()} if items else {}

    receipts: dict[UUID, int] = {}
    if items:
        transactions = db.scalars(select(FinanceTransaction).where(
            FinanceTransaction.entry_id.in_([r.id for r in items]),
            FinanceTransaction.kind == "income",
        )).all()
        for tx in transactions:
            receipts[tx.entry_id] = receipts.get(tx.entry_id, 0) + (
                (-1 if tx.correction_of_id else 1) * tx.amount_kopecks
            )

    return {"client": {"id": str(client.id), "name": client.name, "phone": client.phone},
            "total": total, "offset": offset, "limit": limit,
            "has_more": offset + len(items) < total,
            "visits": [{
                "id": str(item.id),
                "starts_at": item.starts_at.isoformat(),
                "ends_at": item.ends_at.isoformat(),
                "status": item.status,
                "source": item.source,
                "service_name": services[item.service_id].name if item.service_id in services else None,
                "service_variant": services[item.service_id].variant if item.service_id in services else None,
                "quoted_price_kopecks": item.quoted_price_kopecks,
                "received_kopecks": receipts.get(item.id, 0),
            } for item in items]}


@router.get("/reports/month")
def monthly_owner_report(
    year: int = Query(ge=2000, le=2100),
    month: int = Query(ge=1, le=12),
    db: Session = Depends(get_db),
    owner: MasterAccount = Depends(require_owner),
):
    period = Month(year=year, month=month)
    start, end = month_bounds(period.year, period.month)
    base = (
        CalendarEntry.kind == "appointment",
        CalendarEntry.deleted_at.is_(None),
        CalendarEntry.starts_at >= start,
        CalendarEntry.starts_at < end,
    )
    status_rows = db.execute(select(
        CalendarEntry.status, func.count(CalendarEntry.id),
    ).where(*base).group_by(CalendarEntry.status)).all()
    counts = {status: int(count) for status, count in status_rows}

    completed_rows = db.execute(select(
        Service.name, Service.variant,
        func.count(CalendarEntry.id), func.coalesce(func.sum(CalendarEntry.duration_minutes), 0),
    ).select_from(CalendarEntry).join(
        Service, Service.id == CalendarEntry.service_id,
    ).where(*base, CalendarEntry.status == "completed")
     .group_by(Service.id, Service.name, Service.variant)
     .order_by(func.count(CalendarEntry.id).desc(), Service.name)).all()

    money = totals(monthly_transactions(db, period.year, period.month))
    return {
        "year": period.year, "month": period.month,
        "visits_total": sum(counts.values()),
        "visits_by_status": counts,
        "completed_minutes": sum(int(row[3]) for row in completed_rows),
        "service_breakdown": [
            {"name": row[0], "variant": row[1],
             "completed": int(row[2]), "minutes": int(row[3])}
            for row in completed_rows
        ],
        "income_kopecks": money["income_kopecks"],
        "expense_kopecks": money["expense_kopecks"],
        "balance_kopecks": money["balance_kopecks"],
        "is_closed": active_closure(db, period.year, period.month) is not None,
    }
