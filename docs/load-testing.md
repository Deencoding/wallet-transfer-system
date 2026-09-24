# Load testing

Grafana k6 runs against the isolated `wallet-load` Compose project. Fixtures contain 20 users funded with balanced opening journals. Core throughput runs disable rate limiting to measure database/ledger contention; `authentication-pressure.js` tests throttling separately.

The isolated project publishes app/PostgreSQL/Redis/Kafka/Prometheus/Grafana on
`58080`/`55432`/`56379`/`59092`/`59090`/`53000`, avoiding the normal development ports.

Scenarios:

- `smoke.js`: five end-to-end internal transfers;
- `steady-transfers.js`: warm-up, sustained ring transfers, and ramp-down;
- `mixed-api.js`: 60% transfers, 20% wallet reads, and 20% paginated transfer-list reads;
- `hot-wallet.js`: 200 attempts against one finite wallet;
- `idempotency-storm.js`: 50 concurrent identical requests;
- `authentication-pressure.js`: invalid login and `429` behavior.

Run:

```bash
./ops/load/run-smoke.sh
VUS=25 DURATION=5m ./ops/load/run-baseline.sh
```

Every run ends with PostgreSQL checks for non-negative balances, balanced journals, projection agreement, completed-transfer journals, reversal uniqueness, and idempotent effects.

## Reporting policy

Record CPU, memory, Docker allocation, test duration, virtual users, application configuration, throughput, p95/p99 latency, failure classification, lock contention, HikariCP pressure, and outbox backlog. A local result is a development baseline, not a production SLA or capacity promise.

## Implementation-environment baseline

On 2026-08-18, a deliberately short local validation used 10 virtual users,
a 5-second ramp, 30-second steady stage, and 15-second ramp-down. It completed
4,765 transfer iterations (4,805 HTTP requests including setup), sustained about
84 requests/second overall, and reported zero failed requests. Internal-transfer
latency was 59.05 ms at p95 and 89.57 ms at p99.

The post-run database gate passed with 4,770 transfers, 4,790 balanced journals,
no negative or divergent wallet projection, and zero dead outbox events. These
figures are a smoke-sized development baseline on an unconstrained local Docker
host—not an SLA, production capacity claim, or substitute for a longer soak test.

The first export attempt exposed a bind-mount UID mismatch after the performance
thresholds and database gate had passed. The Compose override now runs k6 with
configurable `LOAD_UID`/`LOAD_GID` (default `1000:1000`) so future baseline JSON
summaries can be written to `load-tests/results`.
