# Financial reconciliation

Phase 13 adds a durable control process that compares business state with the authoritative ledger. It does not edit journal transactions or journal entries.

## Checks

Each full run checks:

- wallet `ledger_balance` against the net immutable journal entries for its ledger account;
- wallet `available_balance` against ledger balance less active external-transfer reservations;
- successful and reversed internal transfers against their required transfer and reversal journals;
- external-transfer status against reservation state and settlement-journal count.

A discrepancy has a stable `case_key`. Seeing the same problem in later runs updates the existing case rather than creating alert noise. A later clean scan resolves cases no longer observed as `AUTO_VERIFIED`. PostgreSQL transaction-scoped advisory locking prevents overlapping full runs.

## Safe repair boundary

The only automatic financial repair is rebuilding a wallet's mutable balance projection from immutable ledger entries and active reservations. It requires an ADMIN, an open `WALLET_PROJECTION` case, a reason, and an `Idempotency-Key`. The operation locks the case, records before/after JSON snapshots, increments the wallet version, resolves the case, and writes an audit record in one database transaction.

Transfer, reservation, and ledger discrepancies are deliberately investigation-only. Ledger entries remain immutable; financial corrections must use the reversal workflow or another explicit compensating journal.

## API

- `POST /api/v1/admin/reconciliation/runs`
- `GET /api/v1/admin/reconciliation/runs?page=0&size=20`
- `GET /api/v1/admin/reconciliation/cases?page=0&size=20`
- `GET /api/v1/admin/reconciliation/cases/{id}`
- `POST /api/v1/admin/reconciliation/cases/{id}/repair-wallet-projection`

The repair endpoint requires `Idempotency-Key`. Reusing the key with the same actor and request returns the original repair; using it for a different request returns HTTP 409.

## Scheduling and recovery

The full scan runs at 02:00 by default. Configure it with `FINANCIAL_RECONCILIATION_CRON`, or disable it with `FINANCIAL_RECONCILIATION_ENABLED=false`. Operators can start an on-demand run through the admin API.

An interrupted transaction creates neither a completed run nor partial cases. This implementation performs a database-local snapshot-style control; it does not claim exactly-once processing or replace provider reconciliation.
