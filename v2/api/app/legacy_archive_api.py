"""Authenticated readonly archive, never mixed into current live cash or booking calendar."""
from datetime import date
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.auth import require_owner
from app.db import get_db
from app.models import (
    Client, LegacyClientRef, LegacyCatalogItem, LegacyImportBatch, LegacyMonthlySummary,
    LegacyVisit, MasterAccount,
)

router = APIRouter(prefix="/owner/archive", tags=["owner-legacy-archive"])


@router.get("/clients/{client_id}/visits")
def client_legacy_visits(
    client_id: UUID,
    offset: int = Query(default=0, ge=0, le=1000000),
    limit: int = Query(default=50, ge=1, le=100),
    db: Session = Depends(get_db),
    owner: MasterAccount = Depends(require_owner),
):
    if db.get(Client, client_id) is None:
        raise HTTPException(status_code=404, detail="Client not found")
    query = select(LegacyVisit).where(LegacyVisit.client_id == client_id)
    total = db.scalar(select(func.count()).select_from(query.subquery())) or 0
    rows = db.scalars(query.order_by(
        LegacyVisit.visited_on.desc(), LegacyVisit.ref,
    ).limit(limit).offset(offset)).all()
    return {
        "total": total, "offset": offset, "has_more": (offset + len(rows) < total),
        "visits": [{
            "ref": item.ref, "date": item.visited_on.isoformat(),
            "service": item.service_label,
            "historical_amount_kopecks": item.amount_kopecks,
            "amount_origin": item.amount_origin,
            "consumables_kopecks": item.consumables_kopecks,
            "notes": item.notes,
            "source_sheet": item.source_sheet,
        } for item in rows],
    }


@router.get("/month")
def legacy_month(
    year: int = Query(ge=2000, le=2100), month: int = Query(ge=1, le=12),
    db: Session = Depends(get_db), owner: MasterAccount = Depends(require_owner),
):
    first = date(year, month, 1)
    end = date(year + (month == 12), (month % 12) + 1, 1)
    period = f"{year:04d}-{month:02d}"
    summary = db.get(LegacyMonthlySummary, period)
    archive_count = db.scalar(select(func.count()).select_from(LegacyVisit).where(
        LegacyVisit.visited_on >= first, LegacyVisit.visited_on < end,
    )) or 0
    archive_amount = db.scalar(select(func.sum(LegacyVisit.amount_kopecks)).where(
        LegacyVisit.visited_on >= first, LegacyVisit.visited_on < end,
    ))
    return {
        "year": year, "month": month, "archived_visits": archive_count,
        "historical_amount_sum_kopecks": archive_amount,
        "legacy_sheet_summary": None if summary is None else {
            "revenue_kopecks": summary.revenue_kopecks,
            "consumables_kopecks": summary.consumables_kopecks,
            "rent_kopecks": summary.rent_kopecks,
            "utilities_kopecks": summary.utilities_kopecks,
            "other_expenses_kopecks": summary.other_expenses_kopecks,
            "net_profit_kopecks": summary.net_profit_kopecks,
            "unique_clients": summary.unique_clients,
            "total_visits": summary.total_visits,
        },
        "money_verified_as_cash": False,
    }


@router.get("/status")
def archive_status(
    db: Session = Depends(get_db), owner: MasterAccount = Depends(require_owner),
):
    counts = {
        "clients_from_archive": db.scalar(select(func.count()).select_from(
            LegacyClientRef
        )) or 0,
        "legacy_visits": db.scalar(select(func.count()).select_from(LegacyVisit)) or 0,
        "unlinked_visits": db.scalar(select(func.count()).select_from(LegacyVisit).where(
            LegacyVisit.client_id.is_(None)
        )) or 0,
        "catalog_items": db.scalar(select(func.count()).select_from(LegacyCatalogItem)) or 0,
        "monthly_summaries": db.scalar(select(func.count()).select_from(LegacyMonthlySummary)) or 0,
    }
    return {"counts": counts, "batches": db.scalar(
        select(func.count()).select_from(LegacyImportBatch)
    ) or 0, "is_live_cash": False}
