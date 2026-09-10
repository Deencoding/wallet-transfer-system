# Wallet and Money Transfer System

A production-oriented fintech portfolio project built with Java 21 and Spring Boot 3 as a modular monolith. It includes authentication, wallets, immutable double-entry accounting, atomic and concurrency-safe transfers, durable idempotency, transactional-outbox Kafka publication, external-provider uncertainty handling, reversals, reconciliation, observability, and security hardening.

## Prerequisites

- JDK 21 (or a compatible Gradle toolchain installation)
- Docker with the Compose plugin

## Run locally

### IntelliJ IDEA

Use the shared **Wallet Transfer - Local (Gradle)** run configuration from the
run-configuration selector. It runs the `bootRun` Gradle task with the `local`
profile, so IntelliJ uses the same resolved runtime classpath as the command
line.

Do not use a plain IntelliJ **Application** configuration for
`WalletTransferApplication` if its generated command does not contain a
`commons-lang3` JAR. That means IntelliJ's imported Gradle model is stale;
reload the Gradle project or use the shared Gradle configuration above.

Start infrastructure, then the application:

```bash
docker compose --env-file env.local up -d postgres redis kafka
./gradlew bootRun --args='--spring.profiles.active=local'
```

For an IDE run configuration, set the active profile to `local` using either
`SPRING_PROFILES_ACTIVE=local` or the program argument
`--spring.profiles.active=local`. The local profile keeps Flyway enabled, so
pending migrations run automatically before Hibernate validates the schema.
When no profile is selected, Spring defaults to `local`; deployments must always
set `SPRING_PROFILES_ACTIVE=prod` explicitly.

Before the first local run, open the ignored `env.local` file and replace every
placeholder password and secret. Spring imports `env.local` only for the `local`
profile. Docker Compose receives
the same file explicitly through `--env-file env.local`. Committed YAML contains
no database password, webhook secret, or audit pepper. Production should inject
these values from a managed secret store rather than deploy an environment file.

Or build and run the complete stack:

```bash
./gradlew clean bootJar
docker compose --env-file env.local up --build
```

Useful URLs:

- Readiness when running the app directly: <http://localhost:8081/actuator/health/readiness>
- Liveness when running the app directly: <http://localhost:8081/actuator/health/liveness>
- Swagger UI: <http://localhost:8080/swagger-ui.html>
- Prometheus: <http://localhost:9090>
- Grafana: <http://localhost:3000>

Replace every placeholder in the ignored `env.local` file before starting locally. Production requires database and infrastructure configuration, RSA `JWT_PUBLIC_KEY`/`JWT_PRIVATE_KEY`, `PROVIDER_WEBHOOK_SECRET`, and `SECURITY_AUDIT_PEPPER` from environment variables or a secret manager. Never commit `env.local`.

The Compose stack keeps management port `8081` internal; Prometheus scrapes it over the Compose network.

## Test

```bash
./gradlew test
./gradlew integrationTest
./gradlew check
```

Unit tests cover domain rules, security policies, metrics, and ArchUnit module boundaries. Integration tests use real PostgreSQL, Redis, and Kafka Testcontainers to prove migrations, transactions, locks, uniqueness, atomic rate limiting, publication, and consumption. Docker must be running.

Load-test smoke run:

```bash
./ops/load/run-smoke.sh
```

## Package policy

Business modules live under `com.wallettransfer`, including authentication, users, wallets, transfers, ledger, idempotency, outbox, messaging, notifications, audit, external transfers, providers, reversals, reconciliation, and reporting. Each feature uses `controller`, `service`, and `repository` layers plus feature-owned `model`, `dto`, `exception`, and supporting packages when needed.

The `shared` package is restricted to genuinely cross-cutting concepts. JPA entities are not API DTOs, controllers call services only, and one feature never accesses another feature's repository directly.

## Documentation

- [Architecture overview](docs/architecture.md)
- [Database and money conventions](docs/database.md)
- [Transfer state machine](docs/state-machines.md)
- [Authentication security](docs/authentication.md)
- [Wallet design and lifecycle](docs/wallets.md)
- [Double-entry ledger](docs/ledger.md)
- [Atomic internal transfers](docs/transfers.md)
- [Transfer concurrency strategy](docs/concurrency.md)
- [Transfer idempotency](docs/idempotency.md)
- [Transactional outbox](docs/transactional-outbox.md)
- [Idempotent event consumers](docs/event-consumers.md)
- [External provider simulator](docs/external-provider.md)
- [Provider resilience and reconciliation](docs/provider-resilience.md)
- [Financial reconciliation and safe projection repair](docs/financial-reconciliation.md)
- [Observability, metrics, health checks and dashboards](docs/observability.md)
- [Security hardening and residual risks](docs/security-hardening.md)
- [Internal transfer reversals](docs/reversals.md)
- [ER overview](docs/er-diagram.md)
- [Transfer sequence diagrams](docs/transfer-sequence.md)
- [Testing strategy](docs/testing-strategy.md)
- [Failure scenarios](docs/failure-scenarios.md)
- [Operational runbooks](ops/runbooks/)
- [Load testing](docs/load-testing.md)
- [Portfolio overview](docs/portfolio-overview.md)
- [HTTP request collection](requests/wallet-api.http)
- [ADR index](docs/adr/README.md)

## Delivery guarantees

The internal transfer and outbox insertion are atomic inside PostgreSQL. Kafka publication is at least once, so consumers must deduplicate durable event IDs. The project does not claim end-to-end exactly-once delivery.

## Authentication sample

```bash
curl -i http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"customer@example.com","password":"correct horse battery staple"}'

curl -i http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"customer@example.com","password":"correct horse battery staple"}'
```

Use the returned access token as `Authorization: Bearer <token>` with `GET /api/v1/users/me`. Raw passwords and tokens must never be placed in application logs.

The same access token retrieves the customer's initial NGN wallet:

```bash
curl -i http://localhost:8080/api/v1/wallets/me \
  -H 'Authorization: Bearer <access-token>'
```
