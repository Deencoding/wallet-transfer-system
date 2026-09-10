#!/usr/bin/env sh
set -eu
WALLET_ENV_FILE="${WALLET_ENV_FILE:-env.local}"

docker compose --env-file "$WALLET_ENV_FILE" -p wallet-load -f compose.yml -f ops/load/compose.load.yml up -d --build app prometheus grafana
docker compose --env-file "$WALLET_ENV_FILE" -p wallet-load -f compose.yml -f ops/load/compose.load.yml exec -T postgres \
  psql -v ON_ERROR_STOP=1 -U wallet -d wallet < load-tests/seed/seed-load-users.sql
docker compose --env-file "$WALLET_ENV_FILE" -p wallet-load -f compose.yml -f ops/load/compose.load.yml --profile load run --rm k6 \
  run /work/scenarios/smoke.js
docker compose --env-file "$WALLET_ENV_FILE" -p wallet-load -f compose.yml -f ops/load/compose.load.yml exec -T postgres \
  psql -v ON_ERROR_STOP=1 -U wallet -d wallet < load-tests/seed/assert-financial-invariants.sql
