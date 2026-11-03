"""Add explicitly synthetic review data through the isolated acceptance API."""
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

import httpx


def main():
    now = datetime.now(ZoneInfo("Europe/Moscow"))
    today = now.date()
    tomorrow = today + timedelta(days=1)
    with httpx.Client(base_url="http://127.0.0.1:8000", timeout=10) as client:
        def send(method, path, **kwargs):
            response = client.request(method, path, **kwargs)
            response.raise_for_status()
            return response.json()

        auth = send("POST", "/auth/login", json={
            "username": "acceptance_owner", "password": "synthetic_password_only_123",
        })
        client.headers["Authorization"] = "Bearer " + auth["access_token"]
        summary = send("GET", "/owner/finances/summary",
                       params={"year": today.year, "month": today.month})
        if summary["is_closed"]:
            send("POST", "/owner/finances/reopen", json={
                "year": today.year, "month": today.month,
                "reason": "Synthetic visual review fixtures",
            })
        for day in (today, tomorrow):
            send("PUT", f"/owner/schedule/{day}", json={
                "windows": [{"starts_at": "09:00", "ends_at": "19:00"}],
            })
        for index, (name, price, client_name) in enumerate([
            ("Кератин / Ботокс", 620000, "Анна · демо"),
            ("Здоровье для волос", 370000, "Мария · демо"),
            ("Комплексный уход", 800000, "Елена · демо"),
        ]):
            service = send("POST", "/owner/services", json={
                "code": f"visual-demo-{index}", "name": name, "variant": None,
                "price_kopecks": price, "duration_minutes": 60, "buffer_minutes": 15,
                "active": True,
            })
            for day, clock in ((today, ["09:00", "12:00", "15:00"][index]),
                               (tomorrow, ["09:00", "11:00", "16:00"][index])):
                send("POST", "/owner/calendar/manual", json={
                    "client_name": client_name, "service_id": service["id"],
                    "starts_at": f"{day}T{clock}:00+03:00", "source": "phone",
                })
        for kind, amount in (("income", 960000), ("expense", 180000)):
            send("POST", "/owner/finances/transactions", json={
                "kind": kind, "category": "other", "amount_kopecks": amount,
                "occurred_at": now.isoformat(), "note": "Synthetic visual review",
            })
    print("Synthetic review fixtures created through owner APIs; no production data used.")


if __name__ == "__main__":
    main()
