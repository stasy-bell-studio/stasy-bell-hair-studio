# Offline backup and restoration

All backup work is **local development or isolated CI only**. Production hosting, data retention, encryption and geographic storage are not yet accepted.

## Create a local backup
From the repository root, use `bash v2/infra/backup-local.sh /absolute/private/location/backup.dump` after setting up `v2/.env` and local Compose. The destination must be outside the repository. The script never overwrites existing files and uses permission mask 077.

## Restore an isolated test database
Use `createdb` and `pg_restore --exit-on-error --no-owner --no-acl -d <new_empty_database> backup.dump` with PostgreSQL 16 client tools. **Never** restore into an existing production database. Validate Alembic version, row counts, and application functionality after restore.

GitHub Actions exercises `pg_dump` → new isolated database → `pg_restore` → revision/row check on synthetic tests only. This is a successful technical restore drill, not proof of off-site resilience.

Before deploying in Russia: private encrypted backups outside the host, retention policy, monitored automatic schedule, restore drill using the selected infrastructure, and separation of production credentials. No real client Excel or records are included in CI.
