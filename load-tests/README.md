# Load tests

Load tests run only against the isolated `wallet-load` Compose project. Never change these scripts to target production.

```bash
./ops/load/run-smoke.sh
./ops/load/run-baseline.sh
```

The fixture password is `load-test-password`. Funding is inserted as balanced opening-balance journals; no load-only application endpoint exists. The seed refuses to run after fixture transfers exist. Reset the environment explicitly with:

```bash
docker compose -p wallet-load -f compose.yml -f ops/load/compose.load.yml down -v
```

Additional scenarios can be invoked with the k6 service:

```bash
docker compose -p wallet-load -f compose.yml -f ops/load/compose.load.yml --profile load run --rm k6 \
  run /work/scenarios/idempotency-storm.js
```

Run `verify-invariants.sh` after every scenario. A performance run is failed if its financial invariant check fails, regardless of latency.

Available scenarios are smoke, steady transfers, a mixed 60/20/20 API workload,
hot-wallet contention, an idempotency storm, and authentication pressure.
