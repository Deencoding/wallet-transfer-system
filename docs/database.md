# Database and money conventions

Flyway owns forward-only schema changes. PostgreSQL stores UUID business identifiers, UTC `TIMESTAMPTZ` timestamps, and NGN amounts as `NUMERIC(19,2)`. Java uses `BigDecimal`; excess fractional scale is rejected rather than rounded.

| Capability | Tables |
| --- | --- |
| Identity | `users`, `roles`, `user_roles`, `refresh_tokens` |
| Wallets | `wallets` |
| Internal movement | `transfers`, `transfer_reversals`, `idempotency_records` |
| Ledger | `ledger_accounts`, `journal_transactions`, `journal_entries` |
| Messaging | `outbox_events`, `consumer_inbox` |
| Projections | `notifications`, `audit_records` |
| Providers | `external_transfers`, `external_transfer_reservations`, `provider_interactions`, `provider_webhook_events`, `simulated_provider_transfers` |
| Provider recovery | `provider_reconciliation_attempts` |
| Financial controls | `reconciliation_runs`, `reconciliation_cases`, `reconciliation_repairs` |
| Security | `security_events` |

Critical invariants are enforced with primary/foreign keys, unique indexes, positive-amount checks, state checks, deferred journal-balancing triggers, and immutable ledger/security-event triggers. Application validation improves errors but is not the sole defense.

See [ER documentation](er-diagram.md), [ledger design](ledger.md), and [financial reconciliation](financial-reconciliation.md).
