# Личная авторизация Stasy Bell OS V2 (development)

Branch: `feature/android-owner-sessions-2026-11-05`, derived from Android PR #5.
**Do not deploy or merge until owner approval.**

## Protocol
- `POST /auth/login`: access JWT (30 minutes) and opaque device refresh token (30 days).
- `POST /auth/refresh`: rotates the refresh credential and issues a new access JWT.
- `POST /auth/logout`: revokes the device session (204).
- `GET /owner/sessions`, `POST /owner/sessions/{uuid}/revoke`: remote review/revocation.
- Refresh SHA-256 hash only is stored on server; the previous hash is retained for replay detection.
- Access JWT includes session ID and stops working immediately when the session is revoked.
- The Android app encrypts only the refresh token with an app-local Android Keystore AES-GCM key.
  Owner password and access JWT are never persisted to disk.
- Automatic restoration and 401 retry work only while the API is reachable; network failure is **not** treated as revocation.
- Optional `BiometricPrompt` controls **cold-start app unlock**. It is a local UX gate, not a cryptographic
  biometric requirement on the AES key. It does not yet re-lock a still-running app after inactivity.

## Local update (NOT performed on the user's Mac)
The existing `v2/.env` and PostgreSQL volume must be kept; do not recreate the DB.
From the repository root and in a restricted development workspace:

```sh
docker compose --env-file v2/.env -f v2/infra/compose.yaml up -d --build api
docker compose --env-file v2/.env -f v2/infra/compose.yaml exec -T api alembic upgrade head
```

After the API migration, install a **new** debug APK built from this branch, configured for
`http://127.0.0.1:8000/` only for ADB-reversed debug testing. The old APK cannot persist
a session; the new APK expects `refresh_token` from the upgraded backend.

## Gates
- Synthetic FastAPI/PostgreSQL tests for login, rotation, replay, logout, remote revoke and expiry.
- Android debug build, unit tests and emulator activity recreation restoring a session.
- No prod site, no real customer records, no credentials in commits.

## Known gaps outside this change
- HTTPS staging endpoint accessible without Mac/USB still requires host and DNS/TLS decisions.
- Owner's client card only shows nearby bookings, not complete historical archive.
- Reports screen and integration/backup status remain placeholders.
- Secure remote logout if the device is offline requires a second authenticated device/server admin.
- Real Excel import remains preflight-only until verified source files and approved mapping are provided.
