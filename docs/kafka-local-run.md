# Running production line with Kafka (single service and distributed workers)

This project supports two Kafka-based modes:

1. **Single-service linear simulation** (`productionline-app`) that computes all operations in one process.
2. **Distributed operation workers** where every technological operation is a separate app container communicating via Kafka topics.

## 1) Base stack

`docker-compose.yml` contains:
- `kafka` (KRaft, single node)
- `productionline` (`productionline-app`) — API service on port `8080`

Start base services:

```bash
docker compose up --build
```


> ⚠️ `docker compose up --build` (without override) starts only `kafka` and `productionline-app`.
> Operation workers are **not** part of the base compose file and will not appear in `docker compose ps` until you add the generated override file.

The API service has Kafka publishing enabled in compose:
- `SIMULATION_KAFKA_ENABLED=true`
- `SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092`
- `SIMULATION_ORCHESTRATION_FROM_API_ENABLED=true` (for local compose run)

## 2) Single-service linear simulation (existing behavior)

Call:

```bash
curl -X POST http://localhost:8080/api/simulation-graph/linear \
  -H 'Content-Type: application/json' \
  -d @linear-flow.json
```

The API returns full timeline and Kafka transfer messages in response.

## 3) Distributed mode: one container per operation

### Step A. Prepare input JSON

Create `linear-flow.json`:

```json
{
  "partsCount": 3,
  "operationsCount": 7,
  "batchId": "batch-42",
  "startTau": 0.0,
  "finishTau": 90.0,
  "operations": [
    { "id": 0, "name": "startStore", "tauMean": 0.0, "tauSigma": 0.0, "randomSeed": 0 },
    { "id": 1, "name": "Op01", "tauMean": 10.0, "tauSigma": 0.0, "randomSeed": 1 },
    { "id": 2, "name": "Op02", "tauMean": 10.0, "tauSigma": 0.0, "randomSeed": 2 },
    { "id": 3, "name": "Op03", "tauMean": 10.0, "tauSigma": 0.0, "randomSeed": 3 },
    { "id": 4, "name": "Op04", "tauMean": 10.0, "tauSigma": 0.0, "randomSeed": 4 },
    { "id": 5, "name": "Op05", "tauMean": 10.0, "tauSigma": 0.0, "randomSeed": 5 },
    { "id": 6, "name": "Op06", "tauMean": 10.0, "tauSigma": 0.0, "randomSeed": 6 },
    { "id": 7, "name": "Op07", "tauMean": 10.0, "tauSigma": 0.0, "randomSeed": 7 },
    { "id": 8, "name": "finishStore", "tauMean": 10.0, "tauSigma": 0.0, "randomSeed": 0 }
  ]
}
```

### Step B. Generate dynamic compose override

If your `linear-flow` is already sent to API, use the same payload for compose generation endpoint:

```bash
curl -X POST http://localhost:8080/api/simulation-graph/linear/distributed/compose \
  -H 'Content-Type: application/json' \
  -d @linear-flow.json
```

Response contains `composeYaml` (content of `docker-compose.operations.yml`).
You can save it directly:

```bash
curl -s -X POST http://localhost:8080/api/simulation-graph/linear/distributed/compose \
  -H 'Content-Type: application/json' \
  -d @linear-flow.json | jq -r ' .composeYaml ' > docker-compose.operations.yml
```

Alternatively, file-based generator still works:

```bash
python3 scripts/generate_compose_for_linear_flow.py --input linear-flow.json
```

This generates `docker-compose.operations.yml` with services like:
- `productionline-operation1-app`
- `productionline-operation2-app`
- ...
- `productionline-operation7-app`
- `productionline-finish-store-app`

Each operation service gets its own `operation-id`, `next-operation-id`, `tauMean`, `tauSigma`, and `randomSeed` from JSON.

### Step C. One-command startup (recommended)

Instead of manually running generation + compose, you can use wrapper script:

```bash
scripts/run_distributed_flow.sh linear-flow.json
```

What it does:
1. validates the JSON flow (startStore/finishStore and sequential operation IDs),
2. generates `docker-compose.operations.yml`,
3. starts `docker compose -f docker-compose.yml -f docker-compose.operations.yml up --build`.

### Step D. Start all services manually (alternative)

```bash
docker compose -f docker-compose.yml -f docker-compose.operations.yml up --build
```

### Step E. Start batch flow through Kafka

Use API endpoint that initializes topics and sends first parts from `startStore` to operation 1:

```bash
curl -X POST http://localhost:8080/api/simulation-graph/linear/distributed/start \
  -H 'Content-Type: application/json' \
  -d @linear-flow.json
```

Expected response:

```json
{
  "batchId": "batch-42",
  "partsSent": 3,
  "startTopic": "line-op-0-to-1",
  "finishTopic": "line-op-7-to-8"
}
```

Operation workers consume/produce messages hop-by-hop:
- `line-op-0-to-1` -> `Op01`
- `line-op-1-to-2` -> `Op02`
- ...
- `line-op-7-to-8` -> `finishStore`


## 4) API orchestration mode (auto-start workers from `/linear`)

If you want `POST /api/simulation-graph/linear` to automatically:
1. generate/update worker compose override,
2. start missing worker services,
3. wait until they are running,
4. publish start batch messages to Kafka,

enable property:

```properties
simulation.orchestration.from-api.enabled=true
```

Optional tuning:

```properties
simulation.orchestration.compose.project-dir=.
simulation.orchestration.compose.base-file=docker-compose.yml
simulation.orchestration.compose.override-file=docker-compose.operations.yml
simulation.orchestration.workers.ready-timeout-ms=120000
simulation.orchestration.workers.ready-poll-interval-ms=3000
```

When disabled (default), `/linear` keeps legacy single-service behavior.

> In the provided `docker-compose.yml` for local run, orchestration is explicitly enabled via env var, so `/linear` auto-starts missing worker services.


### Why `/api/simulation-graph/linear` can return 500 in orchestration mode

If you enabled:

```properties
simulation.orchestration.from-api.enabled=true
```

then `/linear` tries to run `docker compose ...` from inside the `productionline-app` container.
In a default local setup, that container does not have Docker CLI/socket access, so orchestration can fail with HTTP 500.

Use one of these approaches:

1. Keep orchestration disabled (default) and run distributed workers manually via override compose (`-f docker-compose.operations.yml`).
2. Run `scripts/run_distributed_flow.sh linear-flow.json` from host.
3. If you need API-driven orchestration from container, provide Docker access to the app container explicitly (Docker socket/CLI).
