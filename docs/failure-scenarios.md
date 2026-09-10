# Failure scenarios and recovery

| Failure | Expected behavior | Recovery evidence |
| --- | --- | --- |
| PostgreSQL unavailable | Financial request fails; no success is acknowledged | Readiness down; retry only as a new/idempotent client request after DB recovery |
| Kafka unavailable | Transfer and outbox commit; notifications lag | Outbox backlog grows then drains after Kafka recovery |
| Redis unavailable | Sensitive endpoints fail closed in production | Readiness down; no wallet mutation from rejected calls |
| Provider timeout | Transfer becomes uncertain; no blind retry | Status query/webhook/reconciliation reaches one terminal effect |
| App crashes after commit | Committed outbox remains durable | Lease expiry permits republishing; inbox suppresses duplicate effects |
| Projection mismatch | Reconciliation opens a case | Only safe wallet projection rebuild is automated and audited |
| Reversal lacks receiver funds | Entire reversal rolls back | Original transfer and journals remain unchanged |

Detailed operator procedures live under [`ops/runbooks`](../ops/runbooks/).
