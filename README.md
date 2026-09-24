# Wallet and Money Transfer System

A production-oriented fintech portfolio project built with Java 21 and Spring Boot 3 as a modular monolith. It supports wallet-to-wallet internal transfers only. It includes authentication, wallets, immutable double-entry accounting, atomic and concurrency-safe transfers, durable idempotency, transactional-outbox Kafka publication, reversals, reconciliation, observability, and security hardening.

## Quick navigation

- [Run everything in Docker](#run-everything-in-docker)
- [API endpoint guide and validation rules](#api-endpoint-guide)
- [Where request values belong](#where-each-request-value-belongs)
- [Idempotency keys: generation and retries](#generate-and-reuse-an-idempotency-key)
- [Postman transfer walkthrough](#send-an-internal-transfer-in-postman)
- [Complete request examples for all 16 endpoints](#complete-request-examples-for-every-endpoint)
- [Management endpoints](#management-endpoints)
- [Error responses and retries](#error-responses-and-retries)

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
docker compose up -d postgres redis kafka
./gradlew bootRun --args='--spring.profiles.active=local'
```

For an IDE run configuration, set the active profile to `local` using either
`SPRING_PROFILES_ACTIVE=local` or the program argument
`--spring.profiles.active=local`. The local profile keeps Flyway enabled, so
pending migrations run automatically before Hibernate validates the schema.
The migration history has been consolidated into a clean V1. If you have an older
local database, follow the [database reset instructions](docs/database.md#clean-initial-migration-and-local-reset)
before starting this version.
When no profile is selected, Spring defaults to `local`; deployments must always
set `SPRING_PROFILES_ACTIVE=prod` explicitly.

Before the first local run, open the ignored `.env` file and replace every
placeholder password and secret. Spring imports `.env` only for the `local`
profile. Docker Compose automatically reads the same `.env` file. Committed YAML contains
no database password, or audit pepper. Production should inject
these values from a managed secret store rather than deploy an environment file.

If the IDE application uses Docker PostgreSQL, set `DB_URL=jdbc:postgresql://localhost:5433/wallet` in `.env`. Docker publishes PostgreSQL on host port `5433`; a separately installed PostgreSQL can continue using `localhost:5432`.

If the IDE application uses Docker Redis, set `REDIS_HOST=localhost` and `REDIS_PORT=6380` in `.env`. Docker publishes Redis as `6380:6379` so a locally installed Redis can continue using port `6379`. The application inside Docker connects directly to `redis:6379`.

### Run everything in Docker

Run the following commands from the project root, where `compose.yml`, `Dockerfile`, and `gradlew` are located. Start Docker first and stop the IDE application so port `8080` is available. Configure the credentials in `.env`, including both JWT keys and the Grafana login values referenced by `compose.yml`.

Build the application and start the stack:

```bash
./gradlew clean bootJar
docker compose up -d --build
```

| Term | Meaning and purpose |
| --- | --- |
| `./gradlew` | Runs the project's Gradle wrapper, using the Gradle version selected by the project. `./` means the executable in the current directory. |
| `clean` | Removes previous build outputs so the next package is built afresh. It does not clear database data or Docker volumes. |
| `bootJar` | Compiles and packages the Spring Boot application and its dependencies into an executable JAR under `build/libs`. It does not run the test suite. The Dockerfile copies this JAR, so build it before building the image. |
| `docker compose` | Manages the services defined in the project's `compose.yml`: the app, PostgreSQL, Redis, Kafka, Prometheus, and Grafana. |
| `.env` | Compose automatically reads this file for values used to substitute `${VARIABLE}` expressions in the Compose file. Only values explicitly passed through the service configuration enter each container; this does not copy the file into the image. |
| `up` | Creates or updates the containers and starts the services and their dependencies. |
| `-d` | Detached mode: services run in the background and the terminal becomes available again. |
| `--build` | Builds service images before starting containers. For this project it builds the app image from the Dockerfile; it does not run `bootJar` for you. |

Repeat both commands after changing application code to package the new code and rebuild the app image. The configured health checks and dependency conditions control startup readiness; a command returning successfully is not a substitute for checking service health.

Check the containers:

```bash
docker compose ps
```

`ps` lists this Compose project's containers, their state, published ports, and health status where a health check is configured.

Follow the application logs:

```bash
docker compose logs -f app
```

`logs` displays container output; `-f` follows new output as it arrives; `app` selects only the application service. Press `Ctrl+C` to stop following logs—the detached containers keep running. Omit `app` to view logs from all services.

The API is available at `http://localhost:8080`, Prometheus at `http://localhost:9090`, and Grafana at `http://localhost:3000`.

#### Database addresses and saved data

The PostgreSQL port mapping in `compose.yml` is `5433:5432`, in `HOST_PORT:CONTAINER_PORT` order:

```text
Database client on your computer -> localhost:5433 -> Docker PostgreSQL:5432
Application inside Docker       -> postgres:5432  -> Docker PostgreSQL:5432
Separately installed PostgreSQL -> localhost:5432 (a different database server)
```

The app service explicitly uses `jdbc:postgresql://postgres:5432/wallet`; it does not use the host-side database address from `.env`. `postgres` is the Compose service name. Docker PostgreSQL stores its data in the named `postgres_data` volume and does not automatically import data from a locally installed PostgreSQL server.

Stop and remove the stack's containers and network while keeping its stored data:

```bash
docker compose down
```

`down` removes the service containers and Compose network. Named volumes for PostgreSQL, Kafka, Redis, Prometheus, and Grafana remain, so a later `up` reuses their data. Do not add `-v` when you want to retain that data: `down -v` also removes the Compose-managed named volumes.

Useful URLs:

- Readiness when running the app directly: <http://localhost:8081/actuator/health/readiness>
- Liveness when running the app directly: <http://localhost:8081/actuator/health/liveness>
- Swagger UI: <http://localhost:8080/swagger-ui.html>
- Prometheus: <http://localhost:9090>
- Grafana: <http://localhost:3000>

Replace every placeholder in the ignored `.env` file before starting locally. Production requires database and infrastructure configuration, RSA `JWT_PUBLIC_KEY`/`JWT_PRIVATE_KEY`, and `SECURITY_AUDIT_PEPPER` from environment variables or a secret manager. Never commit `.env`.

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

Business modules live under `com.wallettransfer`, including authentication, users, wallets, transfers, ledger, idempotency, outbox, messaging, notifications, audit, reversals, reconciliation, and reporting. Each feature uses `controller`, `service`, and `repository` layers plus feature-owned `model`, `dto`, `exception`, and supporting packages when needed.

The `shared` package is restricted to genuinely cross-cutting concepts. JPA entities are not API DTOs, controllers call services only, and one feature never accesses another feature's repository directly.

Across application code, unit tests, and integration tests, assign constructed commands, entities, events, collections, and substantial computed arguments to descriptive local variables before passing them to another method. Keep simple getters, constants, direct return values, and fluent assertion or framework configuration chains inline. In exception tests, keep the operation expected to throw inside the assertion callback; prepare its arguments separately. For example, construct a named ledger posting command, then call the posting method with that variable.

## Documentation

- [Architecture overview](docs/architecture.md)
- [Database and money conventions](docs/database.md)
- [Transfer state machine](docs/state-machines.md)
- [Authentication security](docs/authentication.md)
- [Wallet design and lifecycle](docs/wallets.md)
- [Double-entry ledger](docs/ledger.md)
- [Atomic internal transfers](docs/transfers.md)
- [Wallet-to-wallet transfer walkthrough](docs/wallet-transfer-walkthrough.md)
- [Transfer concurrency strategy](docs/concurrency.md)
- [Transfer idempotency](docs/idempotency.md)
- [Transactional outbox](docs/transactional-outbox.md)
- [Idempotent event consumers](docs/event-consumers.md)
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

## API endpoint guide

This guide can be used with curl, Postman, or an IDE HTTP client; Swagger UI is not required. Application URLs use `http://localhost:8080`. Management URLs use `http://localhost:8081` when running directly from the IDE. The complete Compose stack does not publish the management port to the host.

### Headers and first requests

For every JSON payload below, send `Content-Type: application/json`. Endpoints marked **Customer** require `Authorization: Bearer <access-token>`. **Admin** requires an access token containing the `ADMIN` role. Registration creates a customer, not an administrator; there is no public role-promotion endpoint. **Public** needs no bearer token.

Endpoints marked **Key required** also need `Idempotency-Key: <unique-key>`. Use a UUID string for a new operation and preserve the same key and body when retrying it. Internal transfer keys must be 8–255 characters with no leading/trailing whitespace or control characters. Reusing an internal transfer key with different details returns `409 IDEMPOTENCY_KEY_CONFLICT`.

#### Where each request value belongs

A JSON payload is only the **body** of a request. Authentication and idempotency are separate **headers**; copying just the JSON does not produce a complete authenticated transfer request.

| Value | Who supplies it? | Where it goes / where to get it |
| --- | --- | --- |
| `email`, `password` | You / the client | JSON body for registration and login |
| Access token | Backend issues it; client sends it | Copy `accessToken` from login/refresh, then send `Authorization: Bearer <accessToken>` |
| Refresh token | Backend issues it; client sends it | Copy `refreshToken` from login/refresh into the refresh/logout JSON body |
| `Idempotency-Key` | **You / the client generate it** | Request header. It is **not** a JSON field and the backend does **not** generate a missing header for you |
| Idempotency record UUID | Backend | Internal database identifier generated when claiming a new key; do not send it |
| Sender user/wallet ID | Backend resolves it | Derived from the access token and currency; do not add sender IDs to the transfer body |
| `receiverWalletId` | Client selects an existing recipient | Get `id` from that recipient's `GET /api/v1/wallets/me` response; send it in the transfer JSON body. A user ID is not a wallet ID |
| Transfer ID and reference | Backend | Returned by creation; use the returned `reference` for transfer lookup/reversal paths, not the transfer UUID |
| Wallet ID in an admin status path | Client selects an existing wallet | Replace `{walletId}` with the wallet's UUID |
| Reconciliation case ID | Backend creates it; client selects it | Obtain from the case list and replace `{id}` in case/repair paths |

#### Generate and reuse an idempotency key

Generate a UUID once for a new operation, for example in a terminal:

```bash
python3 -c 'import uuid; print(uuid.uuid4())'
```

Copy the output into the `Idempotency-Key` header. A frontend can instead generate it with `crypto.randomUUID()` and retain it with that operation. UUID formatting is a convenient convention; internal-transfer validation requires a valid 8–255 character key, not specifically a UUID.

| Endpoint | Required idempotency header |
| --- | --- |
| `POST /api/v1/transfers` | `Idempotency-Key: <client-generated-key>` |
| `POST /api/v1/transfers/{reference}/reverse` | `Idempotency-Key: <client-generated-key>` |
| `POST /api/v1/admin/reconciliation/cases/{id}/repair-wallet-projection` | `Idempotency-Key: <client-generated-key>` |

Generate a separate key for each new transfer, reversal, or repair. To retry an operation after a timeout, keep **the same key, authenticated user, endpoint, and body**. Do not generate a fresh key on every retry. A second intentional payment needs a new key even if its amount and recipient are identical.

For internal transfers:

| Request | Result |
| --- | --- |
| Missing `Idempotency-Key` header | `400 INVALID_REQUEST` |
| Invalid key, such as one shorter than 8 characters | `400 INVALID_IDEMPOTENCY_KEY` |
| Existing key with matching transfer details | Original saved response; no second movement of funds |
| Existing key with changed transfer details | `409 IDEMPOTENCY_KEY_CONFLICT` |

The database record has an `expires_at` timestamp, but the current implementation does not enforce expiry or remove expired records. Do not assume the key becomes reusable after 24 hours.

#### Send an internal transfer in Postman

1. Choose **POST** and enter `http://localhost:8080/api/v1/transfers`.
2. In **Authorization**, choose **Bearer Token** and paste the sender's `accessToken` (without typing `Bearer` into the token field). Postman constructs the Authorization header. Alternatively, add that header manually as shown below; do not configure it twice.
3. In **Headers**, add `Content-Type` with value `application/json` and `Idempotency-Key` with the UUID you generated for this transfer.
4. In **Body**, choose **raw** and **JSON**, then enter the payload below. Replace the recipient placeholder with an existing wallet UUID.
5. Click **Send**. A funded, valid transfer returns `201`; save its `reference`. For a retry, leave the key and body unchanged.

Complete headers (the UUID shown is illustrative; generate your own):

```http
Authorization: Bearer <sender-access-token>
Content-Type: application/json
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
```

JSON body:

```json
{
  "receiverWalletId": "<receiver-wallet-uuid>",
  "amount": 100.00,
  "currency": "NGN",
  "description": "Sample transfer"
}
```

Do not add `idempotencyKey`, `idempotencyRecordId`, or `senderId` to this JSON. Use the full examples below for other endpoints: each shows its required headers and whether it has a body. For list endpoints, put `page` and `size` in the URL or Postman's **Params** tab; for path placeholders, replace the placeholder directly in the URL.

A typical local sequence is:

1. Start PostgreSQL, Redis, Kafka, and the application using the instructions above.
2. Register and log in. Copy `accessToken` and `refreshToken` from the login response.
3. Call `GET /api/v1/wallets/me` to obtain your wallet ID and balance.
4. Register and log in as a second customer to obtain their wallet ID. Use the first customer's access token to send to that second wallet.
5. Ensure the sender has funds. New wallets start at zero. There is **no public deposit/top-up endpoint**; test funding must maintain both wallet balances and balanced ledger entries, as the integration-test funding helpers do. Changing only the balance column creates an accounting inconsistency.

For example, after substituting actual values:

```bash
curl -i 'http://localhost:8080/api/v1/transfers' \
  -H 'Authorization: Bearer <sender-access-token>' \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000' \
  -d '{"receiverWalletId":"<receiver-wallet-uuid>","amount":100.00,"currency":"NGN","description":"Sample transfer"}'
```

Placeholders such as `<receiver-wallet-uuid>` must be replaced before sending. IDs are UUIDs; transfer references are the strings returned by the API. GET requests have no body. Paginated endpoints accept `?page=0&size=20` (zero-based page numbering; size is clamped to 1–100).

### Authentication

| Method and path | Access | Success | Body |
| --- | --- | --- | --- |
| `POST /api/v1/auth/register` | Public | `201`, user profile | Email/password below |
| `POST /api/v1/auth/login` | Public | `200`, token pair | Email/password below |
| `POST /api/v1/auth/refresh` | Public | `200`, rotated token pair | Refresh token below |
| `POST /api/v1/auth/logout` | Customer | `204`, no body | Refresh token below |

Register and login payload:

```json
{
  "email": "customer@example.com",
  "password": "correct horse battery staple"
}
```

Email is required, must be valid, and is limited to 320 characters. Password is required, must contain 12–72 characters, and must fit BCrypt's 72 UTF-8 byte limit. Registration also creates the initial NGN wallet and ledger account; it does not issue tokens.

Refresh and logout payload:

```json
{
  "refreshToken": "<refresh-token>"
}
```

The refresh token is required and limited to 8192 characters. Login and refresh return `accessToken`, `refreshToken`, `tokenType`, `accessTokenExpiresIn`, and `refreshTokenExpiresIn` (expiry durations in seconds). Save the new refresh token after each refresh. Logout requires the access-token header as well as the refresh-token body; it revokes the refresh-token family. Existing access tokens remain usable until expiry.

### Profile and wallets

| Method and path | Access | Success | Body |
| --- | --- | --- | --- |
| `GET /api/v1/users/me` | Customer | `200`, current user profile | None |
| `GET /api/v1/wallets/me` | Customer | `200`, wallet ID, currency, status, available and ledger balances | None |
| `PATCH /api/v1/admin/wallets/{walletId}/status` | Admin | `200`, updated wallet | Status below |

Wallet status payload:

```json
{
  "status": "FROZEN"
}
```

`status` is required: `ACTIVE`, `FROZEN`, or `CLOSED`. Active wallets can become frozen or closed; frozen wallets can become active or closed. Closed wallets cannot reopen. Closing requires both balances to be zero. Sending the current status is rejected.

### Internal wallet transfers

| Method and path | Access | Success | Body |
| --- | --- | --- | --- |
| `POST /api/v1/transfers` | Customer; Key required | `201`, transfer response and `Location` header | Transfer below |
| `GET /api/v1/transfers/{reference}` | Customer, sender or receiver | `200`, transfer response | None |
| `GET /api/v1/transfers?page=0&size=20` | Customer | `200`, own sent/received transfer page | None |
| `POST /api/v1/transfers/{reference}/reverse` | Admin; Key required | `201`, reversal response | Reason below |

Create transfer payload:

```json
{
  "receiverWalletId": "<receiver-wallet-uuid>",
  "amount": 100.00,
  "currency": "NGN",
  "description": "Sample transfer"
}
```

`receiverWalletId`, `amount`, and `currency` are required. Amount must be at least `0.01`, with at most 17 integer digits and 2 fractional digits. Currency is `NGN`. Description is optional, at most 255 characters. The sender is derived from the access token. Sender and receiver must differ, currencies must match, the sender must be active with sufficient available funds, and the receiver must not be closed. Frozen wallets may receive funds.

Reversal payload:

```json
{
  "reason": "Confirmed duplicate transfer"
}
```

Reason is required, nonblank, and limited to 500 characters. This creates a compensating transfer and ledger journal for an eligible original transfer; it does not delete its history. Reversal can be rejected if the original is ineligible or the funds cannot be moved back. The returned reversal `Location` is not a separately implemented GET endpoint.

### Financial reconciliation (admin)

Every endpoint in this section requires an admin bearer token.

| Method and path | Additional headers | Success | Body |
| --- | --- | --- | --- |
| `POST /api/v1/admin/reconciliation/runs` | None | `201`, reconciliation run | None |
| `GET /api/v1/admin/reconciliation/runs?page=0&size=20` | None | `200`, run page | None |
| `GET /api/v1/admin/reconciliation/cases?page=0&size=20` | None | `200`, case page | None |
| `GET /api/v1/admin/reconciliation/cases/{id}` | None | `200`, case details | None |
| `POST /api/v1/admin/reconciliation/cases/{id}/repair-wallet-projection` | Key required | `201`, repair response | Reason below |

Repair payload:

```json
{
  "reason": "Approved projection rebuild from immutable ledger"
}
```

`id` is the reconciliation case UUID. Reason is required, nonblank, and limited to 1000 characters. Repair is restricted to eligible wallet-projection discrepancies; unsafe repairs are rejected. Starting a run returns a `Location` containing its ID, but there is currently no `GET /runs/{id}` controller route; use the run list.

### Management endpoints

Use port `8081` for these GET requests; none takes a body.

| Path | Access | Purpose |
| --- | --- | --- |
| `/actuator` | Bearer token | Links to exposed management endpoints |
| `/actuator/health` | Public | Overall application health |
| `/actuator/health/liveness` | Public | Whether the application is alive |
| `/actuator/health/readiness` | Public | Readiness including database, Redis, and Kafka |
| `/actuator/health/{component}` | Public security rule; health visibility controls apply | Component health if available/exposed |
| `/actuator/info` | Bearer token | Configured application information |
| `/actuator/prometheus` | Public | Prometheus metrics text |

Health responses can return `503` when unhealthy. Detailed health information is configured as hidden. For example:

```bash
curl -i 'http://localhost:8081/actuator/health/readiness'
```

Generated API metadata is also available locally at `GET /v3/api-docs` and `GET /v3/api-docs.yaml` on port `8080`, with no body or bearer token required. These documentation endpoints can be disabled in production.

### Complete request examples for every endpoint

Each example includes the method, path, headers, and JSON payload where applicable. Use `http://localhost:8080` as the base URL in Postman, or paste the HTTP blocks into an IDE HTTP request file. Replace path placeholders before sending: `{walletId}` is a wallet UUID, `{id}` is a reconciliation case UUID, and `{reference}` is the corresponding transfer reference.

Replace token placeholders with actual tokens. Admin endpoints need an admin token. Give each new operation its own idempotency key; retain it for retries. The example key below is a placeholder, not a shared key to use for every operation. For requests with no payload, select **Body → none** in Postman.

#### 1. Register a customer

```http
POST /api/v1/auth/register HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "email": "customer@example.com",
  "password": "correct horse battery staple"
}
```

#### 2. Log in

```http
POST /api/v1/auth/login HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "email": "customer@example.com",
  "password": "correct horse battery staple"
}
```

#### 3. Refresh tokens

```http
POST /api/v1/auth/refresh HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "refreshToken": "<refresh-token>"
}
```

#### 4. Log out

```http
POST /api/v1/auth/logout HTTP/1.1
Host: localhost:8080
Authorization: Bearer <access-token>
Content-Type: application/json

{
  "refreshToken": "<refresh-token>"
}
```

#### 5. Get your profile

Request body: **none**.

```http
GET /api/v1/users/me HTTP/1.1
Host: localhost:8080
Authorization: Bearer <access-token>
```

#### 6. Get your wallet

Request body: **none**.

```http
GET /api/v1/wallets/me HTTP/1.1
Host: localhost:8080
Authorization: Bearer <access-token>
```

#### 7. Change wallet status

```http
PATCH /api/v1/admin/wallets/{walletId}/status HTTP/1.1
Host: localhost:8080
Authorization: Bearer <admin-access-token>
Content-Type: application/json

{
  "status": "FROZEN"
}
```

#### 8. Create an internal transfer

**Required headers:** sender bearer token, JSON content type, and a client-generated `Idempotency-Key`. The key belongs in Headers, not in the JSON body. Follow the [Postman walkthrough](#send-an-internal-transfer-in-postman) for setup.

```http
POST /api/v1/transfers HTTP/1.1
Host: localhost:8080
Authorization: Bearer <access-token>
Idempotency-Key: <unique-key-for-this-operation>
Content-Type: application/json

{
  "receiverWalletId": "<receiver-wallet-uuid>",
  "amount": 100.0,
  "currency": "NGN",
  "description": "Sample transfer"
}
```

#### 9. Get an internal transfer

Request body: **none**.

```http
GET /api/v1/transfers/{reference} HTTP/1.1
Host: localhost:8080
Authorization: Bearer <access-token>
```

#### 10. List internal transfers

Request body: **none**.

```http
GET /api/v1/transfers?page=0&size=20 HTTP/1.1
Host: localhost:8080
Authorization: Bearer <access-token>
```

#### 11. Reverse an internal transfer

**Client action:** generate a key for this new operation and send it in the `Idempotency-Key` header. Preserve that key and body if retrying.

```http
POST /api/v1/transfers/{reference}/reverse HTTP/1.1
Host: localhost:8080
Authorization: Bearer <admin-access-token>
Idempotency-Key: <unique-key-for-this-operation>
Content-Type: application/json

{
  "reason": "Confirmed duplicate transfer"
}
```

#### 12. Start a reconciliation run

Request body: **none**.

```http
POST /api/v1/admin/reconciliation/runs HTTP/1.1
Host: localhost:8080
Authorization: Bearer <admin-access-token>
```

#### 13. List reconciliation runs

Request body: **none**.

```http
GET /api/v1/admin/reconciliation/runs?page=0&size=20 HTTP/1.1
Host: localhost:8080
Authorization: Bearer <admin-access-token>
```

#### 14. List reconciliation cases

Request body: **none**.

```http
GET /api/v1/admin/reconciliation/cases?page=0&size=20 HTTP/1.1
Host: localhost:8080
Authorization: Bearer <admin-access-token>
```

#### 15. Get a reconciliation case

Request body: **none**.

```http
GET /api/v1/admin/reconciliation/cases/{id} HTTP/1.1
Host: localhost:8080
Authorization: Bearer <admin-access-token>
```

#### 16. Repair a wallet projection

**Client action:** generate a key for this new operation and send it in the `Idempotency-Key` header. Preserve that key and body if retrying.

```http
POST /api/v1/admin/reconciliation/cases/{id}/repair-wallet-projection HTTP/1.1
Host: localhost:8080
Authorization: Bearer <admin-access-token>
Idempotency-Key: <unique-key-for-this-operation>
Content-Type: application/json

{
  "reason": "Approved projection rebuild from immutable ledger"
}
```

### Error responses and retries

Application errors use a JSON body such as:

```json
{
  "code": "INSUFFICIENT_FUNDS",
  "message": "Insufficient available funds"
}
```

Common HTTP statuses are `400` for malformed/missing input, `401` for missing/invalid authentication, `403` for insufficient permissions, `404` for inaccessible or missing resources, `409` for state/idempotency conflicts, `422` for validation failures, and `429` for rate limiting. Unexpected failures return `500 INTERNAL_ERROR`; inspect server logs for the cause.

A timeout or commit-related error does not prove that a transfer failed. Retry with the **same idempotency key and identical body**: a committed internal transfer returns its stored response, while a rolled-back request can be attempted again. Avoid an unlimited retry loop, and do not create a new key merely because the first response was lost.
