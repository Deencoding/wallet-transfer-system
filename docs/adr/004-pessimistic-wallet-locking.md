# ADR-004: Pessimistic sender-wallet locking

**Status:** Accepted for the first transfer implementation

Use PostgreSQL `SELECT ... FOR UPDATE` on the sender wallet during a short transfer transaction. It serializes competing debit decisions and prevents a stale read-check-write from double-spending funds. Locks must be acquired deterministically, held briefly, and never span external calls. Very hot wallets may experience contention; an atomic conditional update can be evaluated later with load evidence.
