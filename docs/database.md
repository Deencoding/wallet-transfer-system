# Database and money conventions

Flyway owns forward-only schema changes. PostgreSQL stores UUID business identifiers, UTC `TIMESTAMPTZ` timestamps, and NGN amounts as `NUMERIC(19,2)`. Java uses `BigDecimal`; excess fractional scale is rejected rather than rounded.

| Capability | Tables |
| --- | --- |
| Identity | `users`, `roles`, `user_roles`, `refresh_tokens` |
| Wallets | `wallets` |
| Internal movement | `transfers`, `transfer_reversals`, `idempotency_records` |
| Ledger | `ledger_accounts`, `journal_transactions`, `journal_entries` |
| Messaging | `outbox_events`, `consumed_events` |
| Projections | `notifications`, `audit_records` |
| Financial controls | `reconciliation_runs`, `reconciliation_cases`, `reconciliation_repairs` |
| Security | `security_events` |

Critical invariants are enforced with primary/foreign keys, unique indexes, positive-amount checks, state checks, deferred journal-balancing triggers, and immutable ledger/security-event triggers. Application validation improves errors but is not the sole defense.

See [ER documentation](er-diagram.md), [ledger design](ledger.md), and [financial reconciliation](financial-reconciliation.md).

## Clean initial migration and local reset

`V1__initial_schema.sql` creates the current internal-transfer schema, including
roles, the platform settlement account, constraints, indexes, and triggers.
External transfers, providers, simulators, and reservations have no tables or seed
records. Both wallet balances are reconciled directly against ledger entries.
Future schema changes should use new migrations starting with V2.

This replaces the previous migration history and requires an empty schema. It is
not an upgrade migration for an existing database. Stop the application before
resetting your disposable local database. Recreate the database, or run the
following SQL in the project's database as its schema owner:

```sql
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
```

This deletes all data and schema objects, including `flyway_schema_history`, old
functions, and triggers. Deleting only application tables is insufficient.
Restart the application (rebuild its image when using Docker Compose); Flyway
will apply V1 and Hibernate will validate the resulting schema. Register users
again and reseed development balances as needed.
