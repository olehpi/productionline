# Running linear simulation with Kafka via Docker Compose

This project includes `docker-compose.yml` with:
- `kafka` (KRaft, single node)
- `productionline` app

## Start

```bash
docker compose up --build
```

## What is enabled in compose

`productionline` service sets:
- `SIMULATION_KAFKA_ENABLED=true`
- `SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092`

So linear simulation transfer events are published to Kafka topics:
- `line-op-1-to-2`
- `line-op-2-to-3`
- ...

Topics are created automatically by the app when simulation starts.

## Example request

```bash
curl -X POST http://localhost:8080/api/simulation-graph/linear \
  -H 'Content-Type: application/json' \
  -d '{
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
  }'
```
