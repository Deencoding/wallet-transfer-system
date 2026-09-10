# Transfer concurrency strategy

A transaction alone does not make a read-check-write debit safe: two requests can read the same balance and both approve spending it. `WalletService` therefore discovers the stable sender ID, rejects self-transfer, sorts the sender and receiver UUIDs, and loads both rows with PostgreSQL `SELECT ... FOR UPDATE`. Status, currency, and funds are checked again using the locked rows.

Both rows are locked so simultaneous credits to one receiver cannot be lost. UUID ordering prevents the ordinary opposite-direction deadlock cycle. Locks live only inside the short outer `TransferService` transaction; no network, Redis, Kafka, or provider call is performed while held.

PostgreSQL `READ COMMITTED` plus explicit row locks is the selected strategy. Redis locks are not used because they do not share the authoritative database transaction. Optimistic versions remain defense in depth. Lock/deadlock failures roll back and return `WALLET_BUSY`; there is no automatic retry before Phase 7 idempotency.

The Testcontainers stress test releases twenty NGN 10.00 debits simultaneously against NGN 100.00. Exactly ten succeed, ten are rejected, neither balance becomes negative, combined money remains NGN 100.00, and exactly ten balanced transfer journals exist.
