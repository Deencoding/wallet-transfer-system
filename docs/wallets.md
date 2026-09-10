# Wallets

Every customer owns exactly one NGN wallet. Registration hashes the password before starting the database transaction, then `CustomerRegistrationService` creates the user and wallet atomically through `UserService` and `WalletService`. PostgreSQL uniqueness on `(owner_id, currency)` prevents duplicate wallets, including under concurrent requests. Migration V3 backfills zero-balance wallets for customers created before Phase 3.

Wallets start `ACTIVE` with `available_balance = ledger_balance = 0.00`. PostgreSQL rejects negative balances and requires available balance not to exceed ledger balance. There is intentionally no funding or balance-adjustment endpoint; Phase 4 introduces the authoritative ledger.

Status transitions are `ACTIVE -> FROZEN`, `FROZEN -> ACTIVE`, and either non-closed status to `CLOSED`. Closing requires zero balances and is irreversible. Only `ADMIN` may call the status endpoint. Optimistic locking detects concurrent administrative updates; such commands are not retried automatically because the intended transition may have become invalid.

```text
GET   /api/v1/wallets/me
PATCH /api/v1/admin/wallets/{walletId}/status
```
