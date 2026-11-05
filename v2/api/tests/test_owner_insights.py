"""Client history and monthly owner reports are factual, private and paginated."""
from datetime import datetime, time, timedelta
from zoneinfo import ZoneInfo

TZ = ZoneInfo("Europe/Moscow")


def auth(t):
    return {"Authorization": f"Bearer {t}"}


def _booking(api, headers, client_id, service_id, at):
    day = at.date().isoformat()
    resp = api.put(f"/owner/schedule/{day}", headers=headers, json={
        "windows": [{"starts_at": "09:00", "ends_at": "20:00"}],
    })
    assert resp.status_code == 200, resp.text
    return api.post("/owner/calendar/appointments", headers=headers, json={
        "client_id": client_id, "service_id": service_id,
        "starts_at": at.isoformat(), "source": "phone",
    })


def test_private_client_history_is_paged_includes_cancelled_and_actual_money(api, owner_token):
    h = auth(owner_token)
    client = api.post("/owner/clients", headers=h, json={
        "name": "Client demo", "phone": "+70000000000",
    }).json()["id"]
    other = api.post("/owner/clients", headers=h, json={"name": "Other demo"}).json()["id"]
    service = api.post("/owner/services", headers=h, json={
        "code": "history-service", "name": "Demo keratin", "price_kopecks": 100000,
        "duration_minutes": 60, "active": True,
    }).json()["id"]

    day = datetime.now(TZ).date() + timedelta(days=4)
    first = _booking(api, h, client, service, datetime.combine(day, time(10), TZ))
    assert first.status_code == 201, first.text
    second = _booking(api, h, client, service, datetime.combine(day, time(14), TZ))
    assert second.status_code == 201, second.text
    third = _booking(api, h, other, service, datetime.combine(day, time(17), TZ))
    assert third.status_code == 201, third.text
    a, b = first.json()["id"], second.json()["id"]

    assert api.post(f"/owner/calendar/{a}/status", headers=h,
                    json={"status": "completed"}).status_code == 200
    assert api.post(f"/owner/calendar/{b}/cancel", headers=h).status_code == 200

    current = datetime.now(TZ).isoformat()
    paid = api.post("/owner/finances/transactions", headers=h, json={
        "kind": "income", "category": "procedure", "amount_kopecks": 90000,
        "occurred_at": current, "entry_id": a,
    })
    assert paid.status_code == 201, paid.text
    correction = api.post(f"/owner/finances/transactions/{paid.json()['id']}/reverse",
                          headers=h, json={"reason": "Client payment was returned"})
    assert correction.status_code == 201, correction.text
    page1 = api.get(f"/owner/clients/{client}/history?limit=1", headers=h)
    assert page1.status_code == 200, page1.text
    assert page1.json()["total"] == 2
    assert page1.json()["has_more"] is True
    assert page1.json()["visits"][0]["status"] == "cancelled"
    page2 = api.get(f"/owner/clients/{client}/history?limit=1&offset=1", headers=h)
    assert page2.status_code == 200
    visit = page2.json()["visits"][0]
    assert visit["status"] == "completed"
    assert visit["service_name"] == "Demo keratin"
    assert visit["quoted_price_kopecks"] == 100000
    assert visit["received_kopecks"] == 0
    assert page2.json()["has_more"] is False
    assert api.get(f"/owner/clients/{client}/history").status_code == 401
    assert api.get(f"/owner/clients/{client}/history?limit=101", headers=h).status_code == 422
    assert api.get(f"/owner/clients/{other}/history", headers=h).json()["total"] == 1

    update = api.patch(f"/owner/clients/{client}", headers=h, json={
        "name": "Client renamed", "phone": "",
    })
    assert update.status_code == 200
    assert update.json()["name"] == "Client renamed"
    assert update.json()["phone"] is None
    assert api.patch(f"/owner/clients/{client}", headers=h, json={
        "name": "   ",
    }).status_code == 422
    assert api.patch(f"/owner/clients/{client}", headers=h, json={}).status_code == 422


def test_monthly_report_counts_visits_without_recognizing_quotes_as_income(api, owner_token):
    h = auth(owner_token)
    client = api.post("/owner/clients", headers=h, json={"name": "Report client"}).json()["id"]
    service = api.post("/owner/services", headers=h, json={
        "code": "report-service", "name": "Report service", "price_kopecks": 230000,
        "duration_minutes": 90, "active": True,
    }).json()["id"]
    day = datetime.now(TZ).date() + timedelta(days=3)
    a = _booking(api, h, client, service, datetime.combine(day, time(11), TZ))
    b = _booking(api, h, client, service, datetime.combine(day, time(16), TZ))
    assert a.status_code == 201 and b.status_code == 201
    assert api.post(f"/owner/calendar/{a.json()['id']}/status", headers=h,
                    json={"status": "completed"}).status_code == 200
    assert api.post(f"/owner/calendar/{b.json()['id']}/cancel", headers=h).status_code == 200
    report = api.get("/owner/reports/month", headers=h, params={
        "year": day.year, "month": day.month,
    })
    assert report.status_code == 200, report.text
    data = report.json()
    assert data["visits_total"] == 2
    assert data["visits_by_status"] == {"completed": 1, "cancelled": 1}
    assert data["completed_minutes"] == 90
    assert data["service_breakdown"][0]["name"] == "Report service"
    assert data["service_breakdown"][0]["completed"] == 1
    assert data["income_kopecks"] == 0
    assert data["balance_kopecks"] == 0
    assert api.get("/owner/reports/month", params={"year": day.year, "month": day.month}).status_code == 401
    assert api.get("/owner/reports/month", headers=h,
                   params={"year": day.year, "month": 13}).status_code == 422
