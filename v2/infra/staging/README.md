# Private staging for Samsung S20 — deployment prepared, NOT deployed

**Goal:** S20 works over mobile data or Wi-Fi, without USB/Mac. The HTTPS endpoint
must be hosted on an authorised server with public DNS/TLS. This branch only
prepares the software — no host, domain, costs, secrets or deployments have
been created.

## Separate from existing services

- `v2/infra/compose.yaml` and the Mac's `v2/.env` remain untouched.
- Private PostgreSQL volume is never published as a port.
- API is reachable only through Caddy HTTPS on 443 (HTTP 80 solely for ACME/redirect).
- The `migrate` service runs Alembic to head before API startup.
- `STASY_PUBLIC_BOOKING_ENABLED=0` is explicitly forced for API and worker.
- `/ready` is not exposed externally; `/health` remains intentionally lightweight.
- Real customer Excel and personal data must **not** be copied to this staging host
  during initial acceptance.

## Before first launch

1. Provision an approved host with a public IP, private DNS entry and open TCP 80/443,
   optional UDP 443. Do not use the user's Mac as an internet server.
2. Set an A/AAAA record for `STASY_API_DOMAIN` pointing to the host. Obtain an email
   for ACME certificate notifications.
3. Install Docker Engine / Compose v2 and Python 3.
4. Copy `.env.example` to a **private path outside the repo**, generate independent
   high-entropy database password and JWT secret, populate `DATABASE_URL`, then
   `chmod 600 /private/path/staging.env`.
5. Run `python3 v2/infra/staging/preflight.py /private/path/staging.env`.
6. Run `bash v2/infra/staging/deploy-staging.sh /private/path/staging.env`.
7. From a different network verify `https://<api-domain>/health` and HTTP to HTTPS
   redirect; inspect valid TLS certificate and logs.
8. Create one test owner through `python -m app.bootstrap_owner` inside the isolated
   API container. Credentials must not enter GitHub, CI logs or chat.
9. Populate synthetic clients/services. Build Android APK with
   `-PstasyApiBaseUrl=https://<api-domain>/` and verify login from S20 over mobile data.
10. Confirm restarting the app preserves owner session; logout revokes it;
    confirm a fresh login is required on a different device.

**No Google Play upload.** Local APK and server must use matching backend schema and
owner-session protocol. Never send the old emulator APK to an independent remote API.

## Backups

Before any real personal data: configure `age` recipient and `rclone` off-host
storage, run `backup-offsite.sh`, verify the remote artifact and perform a
restore drill into an isolated empty DB. Schedule backups externally with
monitoring. Do not rely on database container volume as backup.

Backups are encrypted **before leaving the host**. Secrets remain outside Git.
Automatic deletion/retention must be defined by the approved storage provider.

## Security limitations / release gates

- This is **private staging**, not a production-ready service for personal data.
- Hosting in the required jurisdiction, data-processing notices, privacy policy,
  encryption/key custody, log retention, network/firewall rules and incident response
  require an explicit business decision before real client records.
- The API access token is 30 minutes; refresh token rotates for the one registered
  S20 session. Never expose secrets in APK.
- Caddy handles TLS automatically only when DNS and ACME inbound traffic are working.
- No manual approval is needed to modify code; deploying to a paid third-party host,
  uploading actual customer data and changing production remain external gates.
