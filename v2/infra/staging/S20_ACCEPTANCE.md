# Samsung S20 independent test — release checklist

The application currently runs with a local development API over ADB reverse.
For truly independent use (no USB, no Mac online), a private HTTPS endpoint is
required. This checklist deliberately prevents accidental deployment.

- [ ] Approved staging host and domain; verify data residency before any real PII.
- [ ] Docker Engine/Compose and TLS termination, firewall 80/443, no public PostgreSQL port.
- [ ] Encrypted private secrets file outside Git, preflight gate passes.
- [ ] PostgreSQL migration `0007_owner_sessions` applied and backup verified.
- [ ] Android APK uses `https://<approved-domain>/`, not `127.0.0.1`.
- [ ] S20 successful login over mobile data (Mac offline), restart retains session.
- [ ] S20 optional fingerprint, logout revocation, invalid refresh replay case.
- [ ] CRUD: client, visit, status, service, money, materials, monthly report.
- [ ] Reboot/restore checks, off-host encrypted backup and restore drill.
- [ ] Real XLSX files inspected **offline**; no import until mapping and dedup accepted.
- [ ] Telegram/VK credentials and verified callback setup only when supplied.
- [ ] Production and public site remain unchanged until separate go-live decision.
