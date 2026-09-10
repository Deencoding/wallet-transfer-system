# Testing strategy

- Unit tests cover money rules, state transitions, JWT/password behavior, rate-limit failure policy, outbox state, and architecture boundaries.
- PostgreSQL Testcontainers tests prove migrations, constraints, row locking, transaction rollback, ledger balance, idempotency, reversals, reconciliation, and immutable records.
- Redis Testcontainers tests prove that the Lua limiter enforces one allowance under concurrent callers.
- Kafka Testcontainers tests prove outbox publication and idempotent consumption.
- k6 tests characterize sustained API behavior; SQL correctness gates run afterward.

```bash
./gradlew test
DOCKER_API_VERSION=1.44 ./gradlew integrationTest
DOCKER_API_VERSION=1.44 ./gradlew clean check bootJar
./ops/load/run-smoke.sh
```

Mocks are not used to claim PostgreSQL locking, isolation, uniqueness, Kafka delivery, or Redis atomicity.
