# Real historical import: existing MacBook test database only

Customer data is not stored in GitHub. Download the PRIVATE archive ZIP from
this chat, not from a public share. Only the MacBook should receive the archive.

- 159 source client references (133 prepared clients + 26 new references)
- 224 historical date-only visits (193 prepared, 21 Feb 2023,
  one 29 Feb 2024, nine Apr 2026)
- 12 historical visits with no reliable client reference remain unlinked
- 31 source service/formulation items, NOT activated as bookable services
- 14 old monthly summary records stored separately
- Prepared 'paid' column total: 846456 RUB; original source monthly
  summary total matches exactly. Additional raw price values: 120166 RUB.
- Historical source figures are NOT verified cash receipts. No real entries
  are added to finance_transactions or calendar_entries.

## On the Mac

Use branch feature/private-legacy-archive-import-2026-11-06 in a separate
local worktree with private v2/.env (mode 600), attached to the existing
test PostgreSQL containing user s20tester.

Run from repository root:

bash v2/migration/import-mac-archive.sh /absolute/private/StasyBell-Private-Migration-2026-11-06.zip

The script pins private ZIP SHA256, verifies test settings, creates a full DB
backup off-repository, builds only API, migrates Alembic to 0008, restricts
API binding to 127.0.0.1, runs dry-run and transactional import, then verifies
record counts. It does not destroy or replace the PostgreSQL volume.

With actual customer data on the Mac, keep port 8000 closed to office Wi-Fi.
Connect Samsung by cable: adb reverse tcp:8000 tcp:8000. On the new debug
APK use "Тестировать через USB (adb reverse)" to clear the previously stored
Mac LAN IP and revert to the loopback API.

Do not push client data, XLSX, ZIP or database dumps to GitHub. After import
remove the archive ZIP from Downloads or retain it only in encrypted private
storage. Real cash reconciliation and orphan visits require subsequent review.