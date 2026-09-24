# ADR-010: Financial reconciliation and restricted repair

**Status:** Accepted

Persist stable reconciliation cases that compare wallet projections, transfers, and journals. Ledger discrepancies require investigation or compensating entries. Automation may rebuild only a mutable wallet projection from immutable ledger data, with case locking, idempotency, snapshots, and audit.
