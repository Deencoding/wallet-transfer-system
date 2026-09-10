# Architecture overview

The system is a Java 21/Spring Boot 3 modular monolith backed by PostgreSQL. Modules are organized by business capability and each module uses controller, service, repository, model, DTO, and exception packages as needed. ArchUnit tests enforce controller-to-service-to-repository direction and prevent cross-module repository access.

```text
Clients -> Spring Security/JWT -> feature controllers -> feature services
                                                    -> PostgreSQL transaction
                                                    -> Redis rate limits
PostgreSQL outbox -> background publisher -> Kafka -> idempotent consumers
External provider -> signed webhook/status query -> reconciliation
Actuator -> Prometheus -> Grafana
```

## Modules

| Module | Responsibility |
| --- | --- |
| Authentication/users | Registration, BCrypt credentials, JWT pairs, refresh rotation and roles |
| Wallets | NGN wallet lifecycle and spendable balance projection |
| Transfers | Internal transfer state and API |
| Ledger | Immutable accounts, journals and double-entry posting |
| Idempotency | Durable request claim, fingerprint and response replay |
| Outbox/messaging | Atomic event creation, Kafka publication and inbox deduplication |
| Notifications/audit | Idempotent event-derived projections and sensitive-action records |
| External transfers/providers | Reservations, provider calls, webhooks and uncertain outcomes |
| Reversals | Compensating business and ledger transactions |
| Reconciliation | Provider polling and financial control cases/repairs |
| Shared security/observability | Rate limiting, security events, correlation, health and metrics |

## Consistency boundaries

PostgreSQL is the consistency boundary for internal money movement. One transaction locks wallets, changes projections, appends a balanced journal, completes the transfer, stores the idempotent response, and inserts the outbox event. Kafka, Redis, Prometheus, and external providers are outside that transaction.

The ledger is authoritative. Wallet balances are low-latency projections checked by financial reconciliation. Corrections append reversal entries; ledger history is never edited.

Kafka delivery is at least once. Consumers use durable event IDs to make their effects idempotent. The system does not claim end-to-end exactly-once delivery.

External-provider calls do not occur while wallet locks are held. Funds are reserved first. A timeout after possible provider success creates an uncertain outcome that is resolved by status query, webhook, and reconciliation rather than blind retry.

## Extraction path

Module boundaries permit later service extraction, but the current single database is intentional: internal transfers benefit from one local ACID transaction. Extraction would require explicit ownership of ledger and wallet data, versioned events, and saga/compensation design; it must not be presented as a mechanical packaging change.
