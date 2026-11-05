from datetime import datetime, time, timedelta
from zoneinfo import ZoneInfo

TZ = ZoneInfo("Europe/Moscow")


def test_cash_ledger_close_reopen_corrections_are_audited(api, owner_token):
    headers = {"Authorization": f"Bearer {owner_token}"}
    now = datetime.now(TZ)
    period = {"year": now.year, "month": now.month}
    occurred = now.isoformat()
    def post(kind, category, amount):
        return api.post("/owner/finances/transactions", headers=headers, json={
            "kind": kind, "category": category,
            "amount_kopecks": amount, "occurred_at": occurred,
        })
    income = post("income", "other", 200000)
    expense = post("expense", "materials", 60000)
    assert income.status_code == 201, income.text
    assert expense.status_code == 201, expense.text
    summary = api.get("/owner/finances/summary", headers=headers,
                      params=period).json()
    assert summary["income_kopecks"] == 200000
    assert summary["expense_kopecks"] == 60000
    assert summary["balance_kopecks"] == 140000
    assert not summary["is_closed"]
    closed = api.post("/owner/finances/close", headers=headers, json=period)
    assert closed.status_code == 201, closed.text
    assert closed.json()["version"] == 1
    assert post("expense", "rent", 100000).status_code == 409
    assert api.post("/owner/finances/close", headers=headers,
                    json=period).status_code == 409
    reason = "Проверка исправления тестового расхода"
    reopened = api.post("/owner/finances/reopen", headers=headers,
                        json={**period, "reason": reason})
    assert reopened.status_code == 200, reopened.text
    correction = api.post(
        f"/owner/finances/transactions/{expense.json()['id']}/reverse",
        headers=headers, json={"reason": reason},
    )
    assert correction.status_code == 201, correction.text
    assert api.post(
        f"/owner/finances/transactions/{expense.json()['id']}/reverse",
        headers=headers, json={"reason": reason},
    ).status_code == 409
    new_summary = api.get("/owner/finances/summary", headers=headers,
                          params=period).json()
    assert new_summary["income_kopecks"] == 200000
    assert new_summary["expense_kopecks"] == 0
    assert new_summary["balance_kopecks"] == 200000
    closed2 = api.post("/owner/finances/close", headers=headers, json=period)
    assert closed2.status_code == 201 and closed2.json()["version"] == 2
    history = api.get("/owner/finances/closures", headers=headers,
                      params=period).json()["versions"]
    assert len(history) == 2
    assert history[0]["expense_kopecks"] == 60000
    assert history[0]["reopen_reason"] == reason
    assert history[1]["expense_kopecks"] == 0
    ledger = api.get("/owner/finances/transactions", headers=headers, params=period)
    assert len(ledger.json()["entries"]) == 3


def test_income_only_after_completed_visit(api, owner_token):
    headers = {"Authorization": f"Bearer {owner_token}"}
    day = datetime.now(TZ).date() + timedelta(days=4)
    at = datetime.combine(day, time(10), TZ).isoformat()
    assert api.put(f"/owner/schedule/{day}", headers=headers, json={
        "windows": [{"starts_at": "09:00", "ends_at": "18:00"}],
    }).status_code == 200
    service = api.post("/owner/services", headers=headers, json={
        "code": "finance-synthetic-service", "name": "Synthetic",
        "duration_minutes": 90, "active": True,
    }).json()["id"]
    booking = api.post("/owner/calendar/manual", headers=headers, json={
        "client_name": "Finance test client", "service_id": service,
        "starts_at": at,
    })
    assert booking.status_code == 201, booking.text
    visit = booking.json()["id"]
    tx = {"kind": "income", "category": "procedure",
          "amount_kopecks": 500000, "occurred_at": at, "entry_id": visit}
    assert api.post("/owner/finances/transactions", headers=headers,
                    json=tx).status_code == 422
    assert api.post(f"/owner/calendar/{visit}/status", headers=headers,
                    json={"status": "completed"}).status_code == 200
    assert api.post("/owner/finances/transactions", headers=headers,
                    json=tx).status_code == 201
    # An appointment reservation alone is never counted as cash income.


def test_minimal_material_stock_and_history(api, owner_token):
    headers = {"Authorization": f"Bearer {owner_token}"}
    item = api.post("/owner/materials", headers=headers,
                    json={"name": "Synthetic keratin", "unit": "g"})
    assert item.status_code == 201, item.text
    ident = item.json()["id"]
    def move(q, reason):
        return api.post(f"/owner/materials/{ident}/movement", headers=headers,
                        json={"change_milliunits": q, "reason": reason})
    assert move(125000, "Test purchase").json()["stock_milliunits"] == 125000
    assert move(-25000, "Test usage").json()["stock_milliunits"] == 100000
    assert move(-100001, "Too much").status_code == 409
    info = api.get("/owner/materials", headers=headers).json()["materials"]
    assert info[0]["stock_milliunits"] == 100000
    history = api.get(f"/owner/materials/{ident}/movements", headers=headers)
    assert len(history.json()["movements"]) == 2
    assert [(row["change_milliunits"], row["reason"])
            for row in history.json()["movements"]] == [
                (125000, "Test purchase"), (-25000, "Test usage")]
    assert api.get("/owner/materials").status_code == 401
