# Architecture of a Monte-Carlo Production-Line Simulator

## 1) Goals and constraints

- Model a sequential-parallel production line as a directed graph of operations.
- Each operation has states `z=0..N`, where `z=0` is the nominal mode and `z>0` are risk states.
- Inter-operation communication is implemented via Kafka (consumer-producer pattern).
- Line parameters and distribution settings come from the Front-end as JSON.
- The engine runs Monte-Carlo simulations and returns aggregated metrics and traces.

## 2) Proposed high-level architecture

```text
React UI
   |
   v
API Gateway / Simulation API (Spring Boot)
   |  (validates JSON, creates SimulationRun)
   +--------------------+
                        |
                        v
               Orchestrator Service
      (creates topics/keys, run lifecycle, SLA, seed)
                        |
                        v
      +---------------- Kafka ----------------+
      |                                       |
      v                                       v
Operation Worker #1 ... Operation Worker #M   Metrics/Events Consumer
(one consumer group per operation)            (aggregation, persistence)
      |                                       |
      +------------- result events -----------+
                        |
                        v
                Analytics / Reporting API
                        |
                        v
                     React UI
```

### What is `API Gateway / Simulation API (Spring Boot)`

This is the **entry backend component** (it can be a single service at the start) through which React interacts with the simulator.

Main responsibilities:

- Accept Front-end requests (`POST /simulations`, `POST /simulations/{id}/start`, `GET /simulations/{id}/status`, `GET /simulations/{id}/results`).
- Perform syntax and business validation of the JSON config (distribution consistency, state probabilities, route connectivity).
- Transform input JSON into the internal domain model `SimulationConfig`.
- Create `runId` and initiate execution through a Kafka command to the orchestrator (`START_RUN`).
- Return current status and aggregated simulation results to the UI.
- Handle baseline cross-cutting concerns: authn/authz, rate limiting, audit, correlation id.

Why the diagram uses a double name:

- **Simulation API** — domain-specific REST API for simulation (MVP single-service option).
- **API Gateway** — external facade if the system evolves into separate microservices (orchestrator, metrics, storage) and needs a single entry point.

For MVP, one Spring Boot service can combine both roles.

### Python 3.12: why use it (and is it required)

In the proposed architecture, the **runtime simulator does not depend on Python**: the core stack is Spring Boot/Java, Kafka, and React.

`Python 3.12` can be used only as an **auxiliary tool** in cases such as:

- migration/conversion of legacy input data (for example, from existing Python dictionaries to the target JSON contract);
- offline analytics and exploratory calculations (notebooks/scripts) outside the production path;
- generation of synthetic test parameter sets for Monte-Carlo experiments.

If the goal is a minimal production stack, Python can be omitted entirely: all critical scenarios are covered by Java + Kafka + PostgreSQL.

## 3) Domain entities

- `SimulationConfig`
  - `lineId`, `runCount`, `randomSeed`, `timeUnit`.
  - `operations[]`.
  - `routes[]` (directed transition graph).
  - `routingPolicy` (probabilistic, deterministic, conditional).
- `OperationConfig`
  - `operationId`.
  - `bufferCapacity` (inter-operation WIP buffer).
  - `states[]`.
- `StateDistribution`
  - `stateId` (`0..N`).
  - `distributionType` (normal, lognormal, uniform, triangular).
  - `mean`, `std`, optional `min`, `max`, `normalizationFactor`.
  - `probability` of selecting the state for a given operation.
- `RouteEdge`
  - `fromOperationId`, `toOperationId`.
  - `condition`/`probability`.

## 4) Recommended JSON contract

```json
{
  "lineId": "e7_e1_01",
  "runCount": 10000,
  "randomSeed": 42,
  "timeUnit": "HOUR",
  "operations": [
    {
      "operationId": 1,
      "bufferCapacity": 200,
      "states": [
        {
          "stateId": 0,
          "distributionType": "NORMAL",
          "mean": 0.101,
          "std": 0.0202,
          "min": 0.0,
          "max": 0.2,
          "probability": 0.834
        },
        {
          "stateId": 1,
          "distributionType": "NORMAL",
          "mean": 0.361,
          "std": 0.0361,
          "normalizationFactor": 0.0,
          "probability": 0.036
        }
      ]
    }
  ],
  "routes": [
    { "fromOperationId": 1, "toOperationId": 2, "probability": 1.0 }
  ]
}
```

## 5) Kafka design

### Topics

- `sim.run.commands`
  - commands: `START_RUN`, `STOP_RUN`, `PAUSE_RUN`.
- `sim.part.arrived`
  - part arrived to an operation/buffer.
- `sim.part.processed`
  - operation completed, part is ready for routing.
- `sim.metrics`
  - technical and business metrics over time windows.
- `sim.dlq`
  - messages that failed processing after retries.

### Message keys

- For part events: `key = runId + partId` (preserve order per part).
- For command events: `key = runId`.

### Guarantees

- Producer idempotence + `acks=all`.
- Exactly-once semantics for critical chains (where required).
- Retry + DLQ + observability (`traceId/runId/operationId`).

## 6) Operation Worker logic

1. Consume `PartArrivedEvent`.
2. Check operation buffer availability (`bufferCapacity`).
3. Select state `z` according to state probabilities.
4. Sample processing time from the configured distribution.
5. Optionally apply `min/max` clipping and normalization.
6. Emit `PartProcessedEvent` with actual processing time.
7. Router forwards the part to the next operation according to `routes` graph.

## 7) Monte-Carlo engine

- Recommended support for 2 modes:
  - **Fast simulation mode**: virtual time (no `sleep`), maximum throughput.
  - **Realtime demo mode**: scaled real-time for UI demonstration.
- Initialize `seed` with `runId` + `operationId` for reproducibility.
- Core metrics:
  - Throughput (parts/hour), WIP, lead time, queue time.
  - Utilization per operation.
  - Frequency and impact of risk states `z>0`.

## 8) Backend modules (Spring Boot)

- `simulation-api` — REST API for config ingestion, run lifecycle, and status.
- `simulation-core` — domain model, sampling, graph validation, routing.
- `simulation-kafka` — producers/consumers and event serialization.
- `simulation-metrics` — aggregator, Prometheus export, reporting.
- `simulation-storage` — PostgreSQL (runs, configs, aggregated results).

## 9) Observability

- OpenTelemetry: distributed traces for each part path across operations.
- Prometheus/Grafana:
  - consumer group lag,
  - buffer depth,
  - errors/retries,
  - p50/p95/p99 operation duration.
- Correlation IDs: `runId`, `partId`, `operationId`, `eventId`.

## 10) Step-by-step implementation plan (MVP → production)

1. **MVP-1**: JSON schema + validation + route graph builder.
2. **MVP-2**: One run, one part flow, processing via Kafka across 2–3 operations.
3. **MVP-3**: Monte-Carlo batch runs, aggregated metrics, REST reporting.
4. **MVP-4**: React UI (JSON upload, run control, buffer/throughput charts).
5. **MVP-5**: scaling, resilience, event replay, DLQ operations.

## 11) Topics to align before implementation

- Time model: fully virtual or hybrid.
- State-probability format (explicit in JSON or computed elsewhere).
- Required determinism level across reruns.
- Buffer business semantics: upstream blocking vs part rejection/scrap.
- First-iteration target KPIs (e.g., throughput + lead time + risk contribution).

## 12) Updated graph semantics for technological routes

The route graph models **technological operations**, not physical machines.

- A graph **node** is a technological operation.
- A graph **edge** is a transition between operations.
- Equipment is modeled as a **resource pool** and can be shared across operations.

### Equipment and operation behavior

1. **Identical equipment case**
   - If several machines provide the same processing characteristics for an operation, they belong to the same operation node.
   - Example: three equivalent drilling machines.
   - The route does not change; only the selected free resource changes.

2. **Non-identical equipment case**
   - If alternative machines produce different operation parameters (for example different mean/std processing time), they must be represented as different operation nodes.
   - Operation names may be equal, but parameter sets differ, so they are different graph nodes.
   - This supports both fallback routing (fast machine busy -> slower machine) and parallel execution on heterogeneous equipment.

### API contract alignment

The request model should contain:

- `operations[]`: operation nodes with processing parameters (`mean`, `std`, and `distributionType`) and a list of eligible equipment IDs.
- `equipmentResources[]`: reusable resources (equipment catalog) with quantity.
- `transitions[]`: directed links between operations.

This model allows one equipment resource to participate in multiple operations and prepares the simulator for future scenarios where the same equipment processes different batches or different part types.
