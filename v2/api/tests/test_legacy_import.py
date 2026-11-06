"""No personal data in GitHub: entirely synthetic test of private archive import."""
import hashlib
import json
from app.import_legacy import validate_package, apply_archive
from app.models import LegacyVisit, Client, FinanceTransaction, CalendarEntry
from app.db import get_session_factory
from sqlalchemy import select, func


def sample():
    data = {
        "clients": [{
            "ref":"prepared:1","name":"Synthetic Owner","phone":"+79999999998",
            "name_variants":"[]","needs_review":False,"original_id":1,"source":"synthetic"
        }],
        "visits": [{
            "ref":"prepared:visit:1","client_ref":"prepared:1","legacy_client_id":1,
            "date":"2024-02-29","service_label":"Synthetic",
            "amount_kopecks":200000,"amount_origin":"paid_column_not_verified_as_cash",
            "consumables_kopecks":5000,"notes":None,
            "source_sheet":"synthetic","source_row":2,"source":"prepared:visits",
        }],
        "catalog": [{
            "ref":"prepared:service:1","name":"Synthetic composition","category":"Состав",
            "price_kopecks":200000,"cost_estimate_kopecks":None,"active_in_source":True,
        }],
        "monthly_summaries": [{
            "month":"2024-02","revenue_kopecks":200000,
            "consumables_kopecks":5000, "rent_kopecks":None,
            "utilities_kopecks":None,"other_expenses_kopecks":None,
            "net_profit_kopecks":None,"unique_clients":1,"total_visits":1,
        }],
    }
    digest = hashlib.sha256(json.dumps(
        data, ensure_ascii=False, sort_keys=True, separators=(",", ":")
    ).encode()).hexdigest()
    return {"manifest":{
        "schema":"stasy-bell-legacy-archive-v1","dataset_sha256":digest,
        "record_counts":{k:len(v) for k,v in data.items()},
        "money_control":{
            "prepared_paid_field_kopecks":200000,"extra_price_field_kopecks":0,
            "prepared_monthly_summary_kopecks":200000,
            "prepared_consumables_kopecks":5000,
        }
    },"data":data}


def test_validate_rejects_changed_money():
    document = sample()
    digest, data = validate_package(document)
    assert len(digest) == 64 and len(data["clients"]) == 1
    document["data"]["visits"][0]["amount_kopecks"] += 1
    import pytest
    with pytest.raises(ValueError, match="sha256 mismatch"):
        validate_package(document)


def test_dry_run_then_apply_archive_only(api, owner_token, monkeypatch):
    document = sample()
    digest, data = validate_package(document)
    from app.auth import passwords
    from app.models import MasterAccount
    with get_session_factory()() as db:
        db.add(MasterAccount(username="s20tester",
                             password_hash=passwords.hash("synthetic-s20-only-password")))
        db.commit()
    monkeypatch.setenv("STASY_EMULATOR_DB", "1")
    monkeypatch.setenv("STASY_PUBLIC_BOOKING_ENABLED", "0")
    h = {"Authorization": f"Bearer {owner_token}"}
    with get_session_factory()() as db:
        with db.begin():
            before_fin = db.scalar(select(func.count()).select_from(
                FinanceTransaction
            ))
            before_book = db.scalar(select(__import__("sqlalchemy").func.count()).select_from(
                CalendarEntry
            ))
            preview = apply_archive(db,digest,data,dry_run=True)
            assert preview["state"] == "dry_run"
    with get_session_factory()() as db:
        with db.begin():
            applied=apply_archive(db,digest,data,dry_run=False)
            assert applied["state"] == "imported"
    with get_session_factory()() as db:
        with db.begin():
            assert apply_archive(db,digest,data,dry_run=False)["state"] == "already_imported"
            assert db.scalar(select(__import__("sqlalchemy").func.count()).select_from(
                __import__("app.models",fromlist=["FinanceTransaction"]).FinanceTransaction
            )) == before_fin
            assert db.scalar(select(__import__("sqlalchemy").func.count()).select_from(
                __import__("app.models",fromlist=["CalendarEntry"]).CalendarEntry
            )) == before_book
            archived = db.scalar(select(LegacyVisit).where(LegacyVisit.ref == "prepared:visit:1"))
            assert archived.amount_kopecks == 200000
            assert archived.client_id is not None
            linked_client = db.get(Client, archived.client_id)
            assert linked_client.name == "Synthetic Owner"
    assert api.get("/owner/archive/status", headers=h).json()["counts"]["legacy_visits"] == 1
    history = api.get(f"/owner/archive/clients/{archived.client_id}/visits",headers=h)
    assert history.status_code == 200
    assert history.json()["total"] == 1
    assert history.json()["visits"][0]["date"] == "2024-02-29"
    assert api.get("/owner/archive/clients/%s/visits" % archived.client_id).status_code == 401
    report = api.get("/owner/archive/month?year=2024&month=2",headers=h)
    assert report.json()["historical_amount_sum_kopecks"] == 200000
    assert report.json()["money_verified_as_cash"] is False
