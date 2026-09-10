# Internal transfer reversals

Only administrators can call `POST /api/v1/transfers/{reference}/reverse`, and every request requires `Idempotency-Key`. The original transfer is locked before checking reversal uniqueness, so concurrent same-key or different-key requests serialize and at most one compensating transaction commits.

A reversal debits the original receiver, credits the original sender, and appends a new balanced `REVERSAL` journal. Original transfer journal entries remain immutable. The original transfer changes from `SUCCESSFUL` to `REVERSED`, while the reversal, wallet projections, journal, audit record, and `TransferReversed` outbox event commit in one PostgreSQL transaction.

If the original receiver no longer has sufficient available funds, the reversal is rejected and the entire transaction rolls back. The first version does not permit negative wallets or forced debt creation. Recovery requiring collections or a platform loss account needs a separately approved policy.

Reversal delivery events remain at least once. Consumers must deduplicate the outbox event UUID as with other transfer events.
