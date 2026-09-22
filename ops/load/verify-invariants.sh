#!/usr/bin/env sh
set -eu
WALLET_ENV_FILE="${WALLET_ENV_FILE:-.env}"
docker compose --env-file "$WALLET_ENV_FILE" -p wallet-load -f compose.yml -f ops/load/compose.load.yml exec -T postgres \
  psql -v ON_ERROR_STOP=1 -U wallet -d wallet < load-tests/seed/assert-financial-invariants.sql
