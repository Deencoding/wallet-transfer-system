# ADR-005: Authoritative double-entry ledger

**Status:** Accepted

Immutable double-entry journal records are the financial source of truth. Mutable wallet balances are transactionally maintained projections used for low-latency authorization. Every journal balances debits and credits per currency. Corrections append compensating entries. Reconciliation reports projection differences and never rewrites history silently.
