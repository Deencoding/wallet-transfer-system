# ADR-010: Financial reconciliation and restricted repair

**Status:** Accepted

Persist stable reconciliation cases that compare wallet projections, reservations, transfers, and journals. Ledger discrepancies require investigation or compensating entries. Automation may rebuild only a mutable wallet projection from immutable ledger data and active reservations, with case locking, idempotency, snapshots, and audit.
