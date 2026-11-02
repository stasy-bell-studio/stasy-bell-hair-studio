"""Verified Telegram/VK callbacks; off unless provider credentials are configured."""
import hmac
import os
import secrets

import httpx
from fastapi import APIRouter, Header, HTTPException, Request
from fastapi.responses import PlainTextResponse

from app.db import get_session_factory
from app.public_booking import confirm_token

router = APIRouter(tags=["messenger-callbacks"])


def secret_matches(received: str | None, expected: str) -> bool:
    return bool(expected and received and hmac.compare_digest(received, expected))


async def telegram_api(method: str, payload: dict) -> None:
    token = os.environ.get("TELEGRAM_BOT_TOKEN", "")
    if not token:
        raise HTTPException(status_code=503, detail="Telegram integration disabled")
    async with httpx.AsyncClient(timeout=7) as client:
        response = await client.post(
            f"https://api.telegram.org/bot{token}/{method}", json=payload
        )
        response.raise_for_status()
        if not response.json().get("ok"):
            raise HTTPException(status_code=502, detail="Telegram delivery failed")


@router.post("/callbacks/telegram")
async def telegram_webhook(
    request: Request, x_telegram_bot_api_secret_token: str | None = Header(default=None),
):
    secret = os.environ.get("TELEGRAM_WEBHOOK_SECRET", "")
    if not secret_matches(x_telegram_bot_api_secret_token, secret):
        raise HTTPException(status_code=403, detail="Webhook rejected")
    update = await request.json()
    message = update.get("message") or {}
    callback = update.get("callback_query") or {}
    if message and message.get("chat", {}).get("type") == "private":
        raw = message.get("text", "")
        if raw.startswith("/start "):
            token = raw.split(maxsplit=1)[1].strip()
            if 25 <= len(token) <= 64 and token.replace("-", "").replace("_", "").isalnum():
                await telegram_api("sendMessage", {
                    "chat_id": message["chat"]["id"],
                    "text": "Для подтверждения записи нажмите кнопку ниже.",
                    "reply_markup": {"inline_keyboard": [[{
                        "text": "Подтвердить запись", "callback_data": "confirm:" + token,
                    }]]},
                })
    if callback and callback.get("message", {}).get("chat", {}).get("type") == "private":
        data = callback.get("data", "")
        if data.startswith("confirm:") and callback.get("from", {}).get("id"):
            raw_token = data[len("confirm:"):]
            with get_session_factory()() as db:
                try:
                    outcome = confirm_token(db, raw_token, "telegram",
                                            str(callback["from"]["id"]))
                    message = ("Запись подтверждена." if not outcome["repeated"]
                               else "Запись уже была подтверждена.")
                except HTTPException:
                    message = "Подтверждение недействительно. Повторите запись или запросите новый код."
            await telegram_api("answerCallbackQuery", {
                "callback_query_id": callback["id"], "text": message,
                "show_alert": True,
            })
    return {"ok": True}


async def vk_send_message(peer_id: int, message: str):
    token = os.environ.get("VK_GROUP_ACCESS_TOKEN", "")
    if not token:
        raise HTTPException(status_code=503, detail="VK integration disabled")
    async with httpx.AsyncClient(timeout=7) as client:
        response = await client.post("https://api.vk.com/method/messages.send", data={
            "peer_id": peer_id, "message": message,
            "random_id": secrets.randbelow(2_147_483_647) + 1,
            "access_token": token, "v": "5.199",
        })
        response.raise_for_status()
        if "error" in response.json():
            raise HTTPException(status_code=502, detail="VK delivery failed")


@router.post("/callbacks/vk", response_class=PlainTextResponse)
async def vk_webhook(request: Request):
    expected = os.environ.get("VK_CALLBACK_SECRET", "")
    group = os.environ.get("VK_GROUP_ID", "")
    if not expected or not group:
        raise HTTPException(status_code=503, detail="VK integration disabled")
    event = await request.json()
    if not secret_matches(event.get("secret"), expected) or str(event.get("group_id")) != group:
        raise HTTPException(status_code=403, detail="Webhook rejected")
    if event.get("type") == "confirmation":
        code = os.environ.get("VK_CONFIRMATION_CODE", "")
        if not code:
            raise HTTPException(status_code=503, detail="VK confirmation disabled")
        return code
    if event.get("type") == "message_new":
        msg = (event.get("object") or {}).get("message") or {}
        text = msg.get("text", "")
        user_id = msg.get("from_id")
        if isinstance(text, str) and text.startswith("CONFIRM ") and user_id:
            raw_token = text.partition(" ")[2].strip()
            with get_session_factory()() as db:
                try:
                    outcome = confirm_token(db, raw_token, "vk", str(user_id))
                    answer = ("Запись подтверждена." if not outcome["repeated"]
                              else "Запись уже подтверждена.")
                except HTTPException:
                    answer = "Код не действует. Запросите новый код на сайте."
            await vk_send_message(int(user_id), answer)
    return "ok"
