# Running production line with Kafka

## What starts with `docker compose up --build`

`docker-compose.yml` starts:
- `kafka`
- `productionline-app` API (`localhost:8080`)

The API container has auto-provision enabled:
- mounts project directory and Docker socket
- on `POST /api/simulation-graph/linear` it generates `docker-compose.operations.auto.yml`
- runs `docker compose -f docker-compose.yml -f docker-compose.operations.auto.yml up -d --build`
- so operation workers are created automatically if they are missing

## Run

```bash
docker compose up --build
```

## Start simulation (this now also auto-creates workers)

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

Check workers:

```bash
docker compose -f docker-compose.yml -f docker-compose.operations.auto.yml ps
```
