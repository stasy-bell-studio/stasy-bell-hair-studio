# Website holds and messenger confirmations (stage 3 implementation)

This code is **disabled by default**, not connected to any real bot or VK community and not deployed.

## API
- `STASY_PUBLIC_BOOKING_ENABLED` must be explicitly set to `1` to expose public services, availability and holds. No defaults or test credentials are accepted in production.
- `GET /public/services` and `GET /public/availability?day=YYYY-MM-DD&service_id=UUID` return only configured active services and slots within 30 days.
- `POST /public/holds` requires name, optional phone, valid service/start, channel telegram/vk and explicit personal-data consent. It atomically creates a 45-minute hold in PostgreSQL using the same calendar intersection rule as the master's Android/API.
- An opaque 15-minute confirmation code is created and stored as SHA-256 only. Client receives a Telegram deep link (if bot username is set), or VK community message instructions (if community name is configured). Browser also gets an independent secret for token reissue.
- `POST /public/holds/{id}/reissue` requires the independent hold secret, accepts at most two reissues within original 45-minute window. New code revokes older unused codes.
- `POST /callbacks/telegram` accepts only configured Telegram `X-Telegram-Bot-Api-Secret-Token`, shows an inline confirmation button after incoming `/start CODE`, and records confirmation only on explicit callback click.
- `POST /callbacks/vk` accepts only a matching group ID and configured VK Callback API secret. It returns the confirmation code for VK handshake and processes inbound `CONFIRM CODE` messages. VK messages are sent on behalf of the community, not a personal account.
- Duplicate callbacks are idempotent: appointment and audit confirmation are not duplicated. A code can only be used once by the same messenger identity. Expired tokens cannot confirm; old holds leave the calendar when expired.
- `v2/infra/compose.yaml` includes a worker under the *optional* `worker` profile that periodically expires holds. It is not started by the default development stack.

## Security before any deployment
- No real bot credentials or client records in GitHub. Secrets are loaded only from process environment.
- HMAC hashed remote address, rate-limit requests kept in PostgreSQL. Only explicitly trusted reverse proxy should supply the client address; no blind trust in forwarded headers.
- Add production TLS, abuse monitoring, webhook replay protections, refresh CSRF/origin controls and consent retention review before enabling public traffic.
- To connect real channels later: `TELEGRAM_BOT_TOKEN`, `TELEGRAM_WEBHOOK_SECRET`, `TELEGRAM_BOT_USERNAME`, `VK_GROUP_ID`, `VK_CALLBACK_SECRET`, `VK_CONFIRMATION_CODE`, `VK_GROUP_ACCESS_TOKEN`, `VK_COMMUNITY_SCREEN_NAME` in secure deployment environment. **Never commit actual values.**
- VK Callback API handshake and message event format follow published VK schemas; Telegram secret header follows official Bot API.
