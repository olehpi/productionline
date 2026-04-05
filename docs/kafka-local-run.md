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

The API service has Kafka publishing enabled in compose:
- `SIMULATION_KAFKA_ENABLED=true`
- `SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092`

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
