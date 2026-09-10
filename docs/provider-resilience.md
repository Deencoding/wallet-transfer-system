# Provider resilience and uncertain outcomes

All provider creates reuse the immutable `provider_request_reference`. Resilience4j applies a three-attempt exponential retry with jitter to retryable failures and a count-based circuit breaker. A read timeout is classified as uncertain and is deliberately excluded from create retries; the reservation remains active and the transfer enters `PENDING_PROVIDER_CONFIRMATION`.

The reconciliation worker claims due transfers with PostgreSQL `FOR UPDATE SKIP LOCKED`, commits the short claim, and performs a read-only provider status query outside the transaction. Confirmed success settles the reservation and balanced ledger exactly once. Confirmed failure releases it. Pending or unknown results are rescheduled with increasing delay, while every attempt is recorded without secrets or customer identifiers.

The simulator adapter implements the provider interface and stores request references uniquely, including the `SIM-TIMEOUT-SUCCESS` case where the provider commits before the caller observes a timeout. This proves query-first recovery without repeating the payout request. A real HTTP implementation can replace the adapter without changing transfer or reconciliation services; transport-specific connection/read timeout configuration will be introduced with that adapter.

This system does not infer failure from silence and does not claim exactly-once communication. Database state transitions, unique provider references, idempotent settlement, and status queries make at-least-once execution safe.
