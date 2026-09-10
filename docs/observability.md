# Observability

The production profile emits structured JSON logs. The local profile uses human-readable console logs and suppresses routine Kafka client noise. The application also exposes Prometheus metrics and Spring Boot health probes. Operational telemetry is best-effort and is not an accounting record; authoritative financial reporting must use PostgreSQL and the immutable ledger.

## Endpoints

Application APIs listen on port `8080`. Actuator uses the separate management port `8081`:

- `/actuator/health/liveness` checks only application process state;
- `/actuator/health/readiness` checks PostgreSQL, Redis, Kafka, and readiness state;
- `/actuator/prometheus` exposes metrics for Prometheus.

The management port is reachable inside the Compose network but is not published to the host. Production deployments must enforce equivalent network isolation.

## Correlation and logs

Clients may supply `X-Correlation-ID` using letters, digits, `.`, `_`, `:`, or `-`, up to 128 characters. Unsafe or missing values are replaced. The ID is returned in the response, propagated through the outbox/Kafka header, restored for consumer processing, and cleared after each operation.

Request logs contain method, route template, status, duration, and correlation ID. They deliberately exclude request bodies, response bodies, JWTs, passwords, refresh tokens, webhook signatures, idempotency keys, email addresses, wallet IDs, and transfer references.

## Metrics

Important custom metrics include:

- `wallet_transfers_total` and `wallet_transfer_duration_seconds`;
- `wallet_outbox_events_total`, `wallet_outbox_publish_duration_seconds`, `wallet_outbox_backlog`, and `wallet_outbox_oldest_pending_age_seconds`;
- `wallet_provider_requests_total` and `wallet_provider_request_duration_seconds`;
- `wallet_reconciliation_runs_total`, `wallet_reconciliation_discrepancies_total`, and `wallet_reconciliation_open_cases`.

Custom tags are bounded values such as operation, type, status, severity, and outcome. User IDs, wallet IDs, transfer references, and correlation IDs are never metric labels.

Backlog gauges are refreshed from PostgreSQL every 15 seconds by default. Change this with `OBSERVABILITY_SNAPSHOT_INTERVAL`.

## Local monitoring

Start the environment:

```bash
docker compose --env-file env.local up --build
```

Prometheus is available at `http://localhost:9090` and Grafana at `http://localhost:3000`. Grafana provisions the Prometheus datasource and the **Wallet Transfer System** dashboard automatically. Local credentials default to `admin` / `admin_local_only`; override them with `GRAFANA_ADMIN_USER` and `GRAFANA_ADMIN_PASSWORD`.

## Limitations

An application crash immediately after a database commit but before an in-memory metric update can undercount a business counter. Prometheus restarts and retention can also remove history. Alerts and dashboards assist operations but do not replace reconciliation, audit records, or ledger-derived reports.
