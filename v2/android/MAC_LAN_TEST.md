# Samsung S20 — MacBook local API without USB, no VPS

This is **LAN-only debug testing** with synthetic data. It uses the existing
PostgreSQL container and owner `s20tester`. It never creates a replacement
database, publishes a production site, or uses real customer records.

## Preparation

1. Ensure MacBook and Samsung S20 use the **same trusted Wi-Fi**.
2. MacBook has Docker Desktop with the existing local `v2/.env` and existing
   `s20tester` account. Do not invoke `run-local-emulator.sh`: it can
   bootstrap a different test database.
3. Obtain the new **debug APK from this integration branch**. The previous
   cable-based APK has no field to set a Mac LAN IP and cannot connect over Wi-Fi.
4. Run from the repository root on Mac:

```bash
bash v2/android/scripts/run-mac-lan.sh
```

The script refuses non-test DBs, checks that the existing `s20tester` is present,
takes a private `pg_dump` backup, builds the API, migrates it through Alembic,
and recreates **only API** with a LAN IP:8000 port. Database volume and contents
remain untouched. Backups are local development backups, not a production
disaster-recovery solution.

If the Mac's Wi-Fi is not `en0`, explicitly set the adapter IP:

```bash
STASY_LAN_IP=192.168.1.20 bash v2/android/scripts/run-mac-lan.sh
```

## Phone

Open `http://<printed Mac IP>:8000/health` in the Samsung browser. Expect
`{"status":"ok","version":"v2"}`. If it does not load, check both devices are
in the same subnet, router client isolation and Mac firewall. No USB needed.

Install the freshly built **debug** APK, launch Stasy Bell OS. At login:
- Enter only the Mac IP (e.g. `192.168.1.20`) in `IP Mac`.
- Tap `Подключить сервер на Mac` (clears previous local session token).
- Log in with the **existing** `s20tester` password; credentials never belong
  in GitHub or build arguments.
- Restart app, verify automatic login and owner report/client history.

Debug allows plain HTTP **only** for explicitly entered RFC1918 private IPv4
on port 8000. Release still requires HTTPS. Do not forward port 8000 in the
router, use public Wi-Fi, or import real client records into this HTTP setup.

To revert LAN exposure without deleting PostgreSQL:

```bash
bash v2/android/scripts/stop-mac-lan.sh
```

## Limitations

The first server startup needs executing commands **on the MacBook** using
Terminal or local Codex; GitHub Actions cannot control that Mac. An updated APK
also needs installation on S20; the existing signed APK cannot be remotely
replaced from ChatGPT. If Android rejects in-place install due to a signature
mismatch, do not automatically uninstall; first confirm local data is disposable.

For real customer data and persistent usage, move to approved HTTPS hosting,
separate secrets, encrypted off-host backups and a reviewed import procedure.
