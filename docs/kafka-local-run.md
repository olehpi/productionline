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
    "tauMean": 10.0,
    "tauSigma": 0.0,
    "randomSeed": 1
  }'
```
