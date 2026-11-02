# Excel migration — offline preflight

This tool **does not connect to the database, import, export, upload, or rewrite clients**. User's real XLSX workbooks are not in GitHub, not in CI, and not in this repository.

After receiving files, run offline on a trusted workstation with restricted folder permissions:

```sh
python -m pip install 'openpyxl>=3.1,<4'
python v2/migration/preflight.py /private/source/clients.xlsx --kind clients
python v2/migration/preflight.py /private/source/money.xlsx --kind finances
```

Optional private `--map /private/mapping.json`, with contents such as `{"name":"Клиент","phone":"Номер телефона"}`, and `--sheet "Лист1"`. Multiple matching sheets or duplicate headers are rejected, not guessed.

The report includes **counts and error categories only**, never names, telephone numbers or financial row details. It discovers headers in first 30 rows and streams worksheet rows (max 200,000 or 60 MiB input). It detects possible duplicate clients via normalized name+phone and duplicate finance records using normalized date+amount+kind+category. It validates amounts exactly in kopecks and rejects values outside integer range; it never evaluates Excel formulas. A flagged duplicate needs manual review rather than automatic deletion.

**The real import is NOT done yet.** After seeing actual files and resolving ambiguous columns, implement a separately reviewed staging import under a restricted, backed-up PostgreSQL instance, compare totals and row counts to originals, then approve any production transfer. Treat spreadsheet macros, formulas and external links as untrusted; use `read_only`/`data_only` mode. Keep original files private and encrypted at rest.
