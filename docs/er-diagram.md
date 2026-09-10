# Entity relationship overview

```mermaid
erDiagram
  USERS ||--o{ USER_ROLES : has
  ROLES ||--o{ USER_ROLES : grants
  USERS ||--o{ REFRESH_TOKENS : owns
  USERS ||--|| WALLETS : owns_NGN
  WALLETS ||--|| LEDGER_ACCOUNTS : projects
  WALLETS ||--o{ TRANSFERS : sends
  WALLETS ||--o{ TRANSFERS : receives
  JOURNAL_TRANSACTIONS ||--|{ JOURNAL_ENTRIES : contains
  LEDGER_ACCOUNTS ||--o{ JOURNAL_ENTRIES : receives
  TRANSFERS ||--o| IDEMPOTENCY_RECORDS : claimed_by
  TRANSFERS ||--o| TRANSFER_REVERSALS : reversed_by
  USERS ||--o{ EXTERNAL_TRANSFERS : requests
  EXTERNAL_TRANSFERS ||--|| EXTERNAL_TRANSFER_RESERVATIONS : reserves
  EXTERNAL_TRANSFERS ||--o{ PROVIDER_INTERACTIONS : records
  OUTBOX_EVENTS ||--o| CONSUMER_INBOX : deduplicated_by
  RECONCILIATION_RUNS ||--o{ RECONCILIATION_CASES : detects
  RECONCILIATION_CASES ||--o| RECONCILIATION_REPAIRS : repaired_by
  USERS ||--o{ SECURITY_EVENTS : actor
```

Ledger journal references connect financial entries to transfers and reversals without making mutable business state authoritative. Outbox aggregate IDs connect committed business changes to events; consumer inbox event IDs suppress duplicate effects.
