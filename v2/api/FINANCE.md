# Stage 5 — simple cash and materials (owner-only API)

Actual income and expenses, no accrual accounting, projections, tax models or speculative profit. All money is stored as integer kopecks. All timestamps are timezone-aware; monthly boundaries use Europe/Moscow.

- `POST /owner/finances/transactions`: append actual income or expense. Categories: procedure/other income; materials/rent/taxes/advertising/other expenses.
- `GET /owner/finances/summary?year=YYYY&month=M`: actual income, expense and **cash difference** (not accounting profit). Booking alone does not create income. A procedure-linked receipt requires a completed visit.
- `POST /owner/finances/close`: fixed versioned month snapshot. While closed, no new month transactions are allowed.
- `POST /owner/finances/reopen`: requires a recorded reason and actor. Original snapshot is preserved; closing again creates version 2/3/etc.
- `POST /owner/finances/transactions/{id}/reverse`: append an equal opposite-effect reversal (never silent edit/delete), preserving original operation and reason. Cannot reverse the same entry twice.
- `GET /owner/finances/transactions`, `GET /owner/finances/closures`: full audit and version history by month.
- `POST /owner/materials`, `GET /owner/materials`, `POST /owner/materials/{id}/movement`, `GET /owner/materials/{id}/movements`: simple inventory in thousandths of gram/ml/piece, with append-only stock movement log and no negative stock.

Materials purchases are recorded as cash expenses independently of stock movements. No fictional service costs or values are seeded; do not interpret cash difference as net profit. Historical Excel migration is a **separate private dry-run** and has not begun. 
