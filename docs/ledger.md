# Double-entry ledger

Each wallet has one `LIABILITY` ledger account. For an internal transfer the sender account is debited and the receiver account credited, reducing one customer liability and increasing the other. Account balance is derived as credits minus debits; no mutable balance is stored on the ledger account.

`LedgerService` accepts internal posting commands only. It validates positive scale-2 amounts, active accounts, currency consistency, at least two entries, and equal debit/credit totals. PostgreSQL repeats the critical multi-row validation with a deferred constraint trigger at commit. Unique `(source_type, source_reference)` prevents duplicate business postings.

Posted journal transactions and entries are immutable: database triggers reject updates and deletes. Corrections must append a reversing journal; the schema has a unique `reversal_of_journal_id` for that later workflow.

The wallet `ledger_balance` remains a fast projection. The authoritative value is `SUM(CREDIT)-SUM(DEBIT)` for its liability account. Reconciliation reports differences and never rewrites either record silently. Phase 5 will update the wallet projection and post the ledger journal within one outer PostgreSQL transaction.
