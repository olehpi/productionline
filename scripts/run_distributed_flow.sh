#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "Usage: $0 <linear-flow.json> [compose-override-output]"
  exit 1
fi

INPUT_JSON="$1"
OVERRIDE_FILE="${2:-docker-compose.operations.yml}"

if [[ ! -f "$INPUT_JSON" ]]; then
  echo "Input JSON not found: $INPUT_JSON"
  exit 1
fi

python3 scripts/generate_compose_for_linear_flow.py --input "$INPUT_JSON" --output "$OVERRIDE_FILE"

echo "Starting base + operation services with override: $OVERRIDE_FILE"
docker compose -f docker-compose.yml -f "$OVERRIDE_FILE" up --build
