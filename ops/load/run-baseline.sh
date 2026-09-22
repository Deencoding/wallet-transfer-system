#!/usr/bin/env sh
set -eu
WALLET_ENV_FILE="${WALLET_ENV_FILE:-.env}"

docker compose --env-file "$WALLET_ENV_FILE" -p wallet-load -f compose.yml -f ops/load/compose.load.yml up -d --build app prometheus grafana
docker compose --env-file "$WALLET_ENV_FILE" -p wallet-load -f compose.yml -f ops/load/compose.load.yml exec -T postgres \
  psql -v ON_ERROR_STOP=1 -U wallet -d wallet < load-tests/seed/seed-load-users.sql
docker compose --env-file "$WALLET_ENV_FILE" -p wallet-load -f compose.yml -f ops/load/compose.load.yml --profile load run --rm \
  -e VUS="${VUS:-25}" -e WARMUP="${WARMUP:-30s}" -e DURATION="${DURATION:-5m}" k6 \
  run --summary-export=/results/steady-summary.json /work/scenarios/steady-transfers.js
docker compose --env-file "$WALLET_ENV_FILE" -p wallet-load -f compose.yml -f ops/load/compose.load.yml exec -T postgres \
  psql -v ON_ERROR_STOP=1 -U wallet -d wallet < load-tests/seed/assert-financial-invariants.sql
