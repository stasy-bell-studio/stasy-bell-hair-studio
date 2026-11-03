"""Verify that UI actions persisted their synthetic results in the isolated backend."""
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

import httpx


def main():
    today = datetime.now(ZoneInfo("Europe/Moscow")).date()
    day = today + timedelta(days=1)
    with httpx.Client(base_url="http://127.0.0.1:8000", timeout=10) as client:
        response = client.post("/auth/login", json={
            "username": "acceptance_owner",
            "password": "synthetic_password_only_123",
        })
        response.raise_for_status()
        client.headers["Authorization"] = "Bearer " + response.json()["access_token"]

        def get(path, **params):
            result = client.get(path, params=params)
            result.raise_for_status()
            return result.json()

        schedule = get(f"/owner/schedule/{day}")["windows"]
        assert len(schedule) == 1
        assert schedule[0]["starts_at"].startswith("09:00")
        assert schedule[0]["ends_at"].startswith("19:00")
        services = get("/owner/services")["services"]
        assert len(services) == 1
        assert services[0]["name"] == "Synthetic service"
        assert services[0]["variant"] is None
        assert services[0]["price_kopecks"] == 120050
        assert services[0]["duration_minutes"] == 60
        assert services[0]["buffer_minutes"] == 15
        clients = {item["id"]: item["name"] for item in get("/owner/clients")["clients"]}
        visits = get("/owner/calendar", from_day=str(day), to_day=str(day))["entries"]
        by_client = {clients[item["client_id"]]: item for item in visits}
        moved = by_client["Synthetic Move"]
        assert moved["status"] == "cancelled"
        assert datetime.fromisoformat(moved["starts_at"]).astimezone(
            ZoneInfo("Europe/Moscow")
        ).strftime("%H:%M") == "12:00"
        events = get(f"/owner/calendar/{moved['id']}/events")["events"]
        assert {"created_manual", "moved", "cancelled"} <= {e["action"] for e in events}
        assert by_client["Synthetic Completed"]["status"] == "completed"
        summary = get("/owner/finances/summary", year=today.year, month=today.month)
        assert summary["income_kopecks"] == 240000
        assert summary["expense_kopecks"] == 0
        assert summary["balance_kopecks"] == 240000
        assert summary["is_closed"] is True
        closures = get("/owner/finances/closures", year=today.year, month=today.month)["versions"]
        assert len(closures) == 2
        assert closures[-1]["version"] == 2
        materials = get("/owner/materials")["materials"]
        assert len(materials) == 1
        assert materials[0]["name"] == "Synthetic material"
        assert materials[0]["stock_milliunits"] == 20000
    print("PASS: schedule, service, manual visits, move, cancellation, completion, cash and stock persisted")


if __name__ == "__main__":
    main()
