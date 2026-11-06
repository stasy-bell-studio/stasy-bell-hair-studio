"""Offline private archive importer: source bytes via stdin, isolated test DB only.

Source data must NEVER go to git, CI, chat logs, app analytics or public endpoints.
This code writes only archive tables and new clients; never inserts live
calendar appointments, historical cash or source's price into finance_transactions.
"""
import argparse
import hashlib
import json
import os
import re
import sys
from datetime import date
from uuid import uuid5, NAMESPACE_URL

from sqlalchemy import func, select, text

from app.db import get_session_factory
from app.models import (
    Client, LegacyCatalogItem, LegacyClientRef,
    LegacyImportBatch, LegacyMonthlySummary, LegacyVisit, MasterAccount,
)

PHONE_PATTERN = re.compile(r"^\+7\d{10}$")
REF_PATTERN = re.compile(r"^[a-zA-Z0-9:._-]{1,200}$")
PERIOD_PATTERN = re.compile(r"^20\d{2}-(0[1-9]|1[0-2])$")
DATASET_FORMAT = "stasy-bell-legacy-archive-v1"


def _die(msg):
    raise ValueError(msg)


def _safe_money(value):
    if value is None:
        return None
    if isinstance(value, bool) or not isinstance(value, int) or not (0 <= value <= 2_147_483_647):
        _die("Source amount must be non-negative integer kopecks, or null")
    return value


def validate_package(document):
    if not isinstance(document, dict) or not isinstance(document.get("manifest"), dict):
        _die("Missing manifest")
    manifest = document["manifest"]
    if manifest.get("schema") != DATASET_FORMAT:
        _die("Unsupported archive schema")
    data = document.get("data")
    if not isinstance(data, dict) or set(data) != {
        "clients", "visits", "catalog", "monthly_summaries",
    }:
        _die("Invalid archive sections")
    if any(not isinstance(section, list) or len(section) > 100_000 for section in data.values()):
        _die("Invalid or excessive archive rows")
    encoded = json.dumps(data, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()
    digest = hashlib.sha256(encoded).hexdigest()
    if digest != manifest.get("dataset_sha256"):
        _die("Archive sha256 mismatch; refusing import")
    counts = manifest.get("record_counts")
    if not isinstance(counts, dict) or any(
        counts.get(key) != len(data[key]) for key in data
    ):
        _die("Archive record-count mismatch")
    controls = manifest.get("money_control")
    if not isinstance(controls, dict):
        _die("Missing monetary controls")
    for group, ref in (("clients", "ref"), ("visits", "ref"), ("catalog", "ref")):
        values = [item.get(ref) for item in data[group] if isinstance(item, dict)]
        if len(values) != len(data[group]) or any(
            not isinstance(value, str) or not REF_PATTERN.fullmatch(value)
            for value in values
        ) or len(values) != len(set(values)):
            _die(f"Invalid or duplicate {group} references")
    existing_refs = {item["ref"] for item in data["clients"]}
    for item in data["clients"]:
        if (not isinstance(item.get("name"), str) or
                not 1 <= len(item["name"]) <= 200):
            _die("Invalid archive client name")
        if item.get("phone") is not None and not PHONE_PATTERN.fullmatch(item["phone"]):
            _die("Invalid archive phone format")
    for item in data["visits"]:
        if item.get("client_ref") is not None and item["client_ref"] not in existing_refs:
            _die("Unresolved source client ref")
        try:
            date.fromisoformat(item["date"])
        except (KeyError, ValueError, TypeError) as exc:
            raise ValueError("Invalid archive visit date") from exc
        for field in ("amount_kopecks", "consumables_kopecks"):
            _safe_money(item.get(field))
        if not isinstance(item.get("service_label"), str):
            _die("Invalid archive service label")
        if len(item["service_label"]) > 300:
            _die("Archive service label too long")
        if item.get("notes") is not None and len(str(item["notes"])) > 10000:
            _die("Archive note too long")
        if item.get("source_sheet") is None or len(str(item["source_sheet"])) > 120:
            _die("Invalid archive source sheet")
    for item in data["catalog"]:
        for field in ("price_kopecks", "cost_estimate_kopecks"):
            _safe_money(item.get(field))
        if not isinstance(item.get("name"), str) or len(item["name"]) > 200:
            _die("Invalid catalog name")
    periods = set()
    for item in data["monthly_summaries"]:
        period = item.get("month")
        if not isinstance(period, str) or not PERIOD_PATTERN.fullmatch(period) or period in periods:
            _die("Invalid or duplicate archive month")
        periods.add(period)
        for field in ("revenue_kopecks", "consumables_kopecks",
                      "rent_kopecks", "utilities_kopecks", "other_expenses_kopecks",
                      "net_profit_kopecks"):
            # Some source profit figures can be negative, but must stay exact kopecks.
            value = item.get(field)
            if value is not None and (
                isinstance(value, bool) or not isinstance(value, int) or
                abs(value) > 2_147_483_647
            ):
                _die("Invalid historical month amount")
    prepared_total = sum(x["amount_kopecks"] or 0 for x in data["visits"]
                         if x["source"] == "prepared:visits")
    raw_total = sum(x["amount_kopecks"] or 0 for x in data["visits"]
                    if x["source"] != "prepared:visits")
    month_total = sum(x["revenue_kopecks"] or 0 for x in data["monthly_summaries"])
    costs = sum(x["consumables_kopecks"] or 0 for x in data["monthly_summaries"])
    if (prepared_total != controls.get("prepared_paid_field_kopecks") or
            raw_total != controls.get("extra_price_field_kopecks") or
            month_total != controls.get("prepared_monthly_summary_kopecks") or
            costs != controls.get("prepared_consumables_kopecks") or
            month_total != prepared_total):
        _die("Historical money-control totals mismatch")
    return digest, data


def _test_db_required(db):
    name = str(db.scalar(text("SELECT current_database()")) or "")
    if not (
        os.environ.get("STASY_EMULATOR_DB") == "1" or
        any(marker in name.lower() for marker in ("test", "demo", "emulator", "s20"))
    ):
        _die("Production or non-test database is prohibited")
    if os.environ.get("STASY_PUBLIC_BOOKING_ENABLED", "0") != "0":
        _die("Archive import requires public booking disabled")
    if not db.scalar(select(MasterAccount.id).where(
        MasterAccount.username == "s20tester"
    )):
        _die("Expected existing test owner s20tester not found")


def _norm_name(textvalue):
    return " ".join(textvalue.casefold().split())


def _client_uuid(ref):
    return uuid5(NAMESPACE_URL, "stasy-bell:v2:legacy-client:" + ref)


def apply_archive(db, digest, data, *, dry_run):
    _test_db_required(db)
    db.execute(text("SELECT pg_advisory_xact_lock(21941, 11)"))
    existed = db.get(LegacyImportBatch, digest) is not None
    if existed:
        return {"state": "already_imported", "sha256": digest[:12],
                "record_counts": {k: len(v) for k, v in data.items()}}
    if db.scalar(select(func.count()).select_from(LegacyImportBatch)):
        _die("A different archive snapshot was previously imported: review before replacing")
    for model in (LegacyClientRef, LegacyVisit, LegacyCatalogItem, LegacyMonthlySummary):
        if db.scalar(select(func.count()).select_from(model)):
            _die("Archive tables are nonempty without the expected batch: refuse possible double import")

    phone_list = [item["phone"] for item in data["clients"] if item.get("phone")]
    present = db.scalars(select(Client).where(Client.phone.in_(phone_list))).all()
    by_phone = {}
    for item in present:
        if item.phone in by_phone:
            _die("Existing database has duplicate phone; review before linking")
        by_phone[item.phone] = item

    reuse_by_ref = {}
    for item in data["clients"]:
        current = by_phone.get(item["phone"]) if item.get("phone") else None
        if current is not None and _norm_name(current.name) != _norm_name(item["name"]):
            _die("Existing client phone conflicts with archive name; review on Mac")
        if current is not None:
            reuse_by_ref[item["ref"]] = current.id
        else:
            reuse_by_ref[item["ref"]] = _client_uuid(item["ref"])
            if db.get(Client, reuse_by_ref[item["ref"]]) is not None:
                _die("Archive deterministic client ID already exists")
    result = {
        "state": "dry_run" if dry_run else "imported",
        "sha256": digest[:12],
        "record_counts": {key: len(rows) for key, rows in data.items()},
        "new_client_count": sum(1 for item in data["clients"] if not (
            item.get("phone") and item["phone"] in by_phone
        )),
        "reused_existing_clients": sum(1 for item in data["clients"] if (
            item.get("phone") and item["phone"] in by_phone
        )),
        "unlinked_visits": sum(1 for v in data["visits"] if v.get("client_ref") is None),
        "live_calendar_added": 0, "live_cash_added": 0,
    }
    if dry_run:
        return result

    for item in data["clients"]:
        identifier = reuse_by_ref[item["ref"]]
        if db.get(Client, identifier) is None:
            db.add(Client(id=identifier, name=item["name"], phone=item["phone"],
                          notes="Перенесено из архивной таблицы Stasy Bell; без подтверждения согласия на рассылку."))
        db.add(LegacyClientRef(ref=item["ref"], client_id=identifier))
    db.flush()
    db.add_all([
        LegacyVisit(
            ref=item["ref"],
            client_id=reuse_by_ref.get(item.get("client_ref")),
            visited_on=date.fromisoformat(item["date"]),
            service_label=item["service_label"],
            amount_kopecks=item["amount_kopecks"],
            amount_origin=item["amount_origin"],
            consumables_kopecks=item.get("consumables_kopecks"),
            notes=item.get("notes"), source_sheet=item["source_sheet"],
            source_row=item.get("source_row"),
        ) for item in data["visits"]
    ])
    db.add_all([
        LegacyCatalogItem(
            ref=item["ref"], name=item["name"],
            category=item.get("category"),
            price_kopecks=item.get("price_kopecks"),
            cost_estimate_kopecks=item.get("cost_estimate_kopecks"),
            active_in_source=bool(item["active_in_source"]),
        ) for item in data["catalog"]
    ])
    db.add_all([
        LegacyMonthlySummary(
            period=item["month"],
            **{field: item.get(field) for field in (
                "revenue_kopecks", "consumables_kopecks", "rent_kopecks",
                "utilities_kopecks", "other_expenses_kopecks", "net_profit_kopecks",
                "unique_clients", "total_visits"
            )}
        ) for item in data["monthly_summaries"]
    ])
    db.add(LegacyImportBatch(dataset_sha256=digest, counts=result["record_counts"]))
    db.flush()
    for model, expected in (
        (LegacyClientRef, len(data["clients"])),
        (LegacyVisit, len(data["visits"])),
        (LegacyCatalogItem, len(data["catalog"])),
        (LegacyMonthlySummary, len(data["monthly_summaries"])),
    ):
        if db.scalar(select(func.count()).select_from(model)) != expected:
            _die("Post-insert archive row counts diverged")
    return result


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--input", required=True,
                   help="'-' for private stdin; filenames inside container also accepted")
    modes = p.add_mutually_exclusive_group(required=True)
    modes.add_argument("--dry-run", action="store_true")
    modes.add_argument("--apply", action="store_true")
    args = p.parse_args()

    try:
        source = sys.stdin.buffer.read() if args.input == "-" else open(args.input, "rb").read()
        if len(source) > 40 * 1024 * 1024:
            _die("Source archive JSON too large")
        digest, data = validate_package(json.loads(source))
        del source
        with get_session_factory()() as db:
            with db.begin():
                result = apply_archive(db, digest, data, dry_run=not args.apply)
        print(json.dumps(result, ensure_ascii=False))
    except (ValueError, OSError, json.JSONDecodeError) as exc:
        # Never include file data or customer details in logs.
        print("ARCHIVE_IMPORT_BLOCKED: " + str(exc), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
