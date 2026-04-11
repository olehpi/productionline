# Remote Debug for `productionline-app`
This project supports remote JVM debug for the main API container running in Docker.
The Docker Compose configuration already exposes debug port `5005` and enables JDWP in the `productionline-app` container.

## Start the stack
Run:
```bash
docker compose up --build
```

Verify that the container is running and port `5005` is exposed:
```bash
docker ps
```
Expected mapping:
```text
0.0.0.0:5005->5005/tcp
```

## Create IntelliJ remote debug configuration
In IntelliJ IDEA:

1. Open `Run` -> `Edit Configurations...`
2. Click `+`
3. Choose `Remote JVM Debug`
4. Set:
   - Name: `productionline-remote`
   - Debugger mode: `Attach to remote JVM`
   - Host: `localhost`
   - Port: `5005`
5. If IntelliJ asks for module/classpath, choose `productionline.main`
Start the configuration with `Debug`.

## Recommended first breakpoints
Set breakpoints in:
- `SimulationGraphController.applyDistributedWorkers(...)`
- `DockerComposeDistributedWorkerOrchestrationService.applyWorkers(...)`
- `KafkaDistributedSimulationLauncher.start(...)`
After the debugger is attached, call the API:
```bash
curl -X POST http://localhost:8080/api/simulation-graph/linear/distributed/apply \
  -H 'Content-Type: application/json' \
  -d @linear-flow.json
```
If the request reaches a breakpoint, IntelliJ will stop execution on that line and open the Debug tool window.

## Notes
- The current setup uses `suspend=n`, so the container starts immediately and does not wait for debugger connection.
- To make the JVM wait for debugger connection before startup, switch JDWP to `suspend=y`.
- Distributed worker containers have their own remote debug ports generated dynamically per operation route.

## Remote Debug for Distributed Workers
Each generated worker service exposes its own JDWP port. Ports are derived from the operation id:

- `operation1` -> `5101`
- `operation2` -> `5102`
- `operation3` -> `5103`
- `operation4` -> `5104`
- `operation5` -> `5105`
- `operation6` -> `5106`
- `operation7` -> `5107`
- `finishStore` -> `5108`

These ports are written into generated route-specific override files such as:

- `docker-compose.operations-route101.yml`

Example worker services:

- `productionline-route101-operation1-app`
- `productionline-route101-operation2-app`
- `productionline-route101-finish-store-app`

### Prepare workers for debugging
1. Rebuild the main image if needed:

```bash
docker compose build productionline
```

2. Start base services:

```bash
docker compose up -d kafka productionline
```

3. Register the route so the API generates the worker compose file:

```bash
curl -X POST http://localhost:8080/api/simulation-graph/linear/distributed/apply \
  -H 'Content-Type: application/json' \
  -d @linear-flow.json
```

4. Start route workers:

```bash
docker compose -f docker-compose.yml -f docker-compose.operations-route101.yml up -d
```

5. Verify that the worker is running and the debug port is exposed:

```bash
docker compose -f docker-compose.yml -f docker-compose.operations-route101.yml ps
```

### Create IntelliJ remote debug configuration for a worker
In IntelliJ IDEA:

1. Open `Run` -> `Edit Configurations...`
2. Click `+`
3. Choose `Remote JVM Debug`
4. Set:
   - Name: `worker-op01`
   - Debugger mode: `Attach to remote JVM`
   - Host: `localhost`
   - Port: `5101`
5. If IntelliJ asks for module/classpath, choose `productionline.main`
6. Start the configuration with `Debug`

Repeat with other ports for other workers.

### Trigger worker execution
To hit a breakpoint inside a worker, start a batch for the same route:

```bash
curl -X POST http://localhost:8080/api/simulation-graph/linear/distributed/start \
  -H 'Content-Type: application/json' \
  -d @linear-flow.json
```

This publishes the first messages into the route-specific Kafka pipeline, for example:

- `route101-line-op-0-to-1` -> consumed by `operation1`
- `route101-line-op-1-to-2` -> consumed by `operation2`
- ...
- `route101-line-op-7-to-8` -> consumed by `finishStore`

### Recommended first breakpoints for worker flow
Set breakpoints in:

- `DistributedOperationWorker.process(String payload)`
- `DistributedOperationWorker.processMessage(DistributedPartMessage incoming)`
- `DistributedFinishStoreConsumer.consume(String payload)`
- `KafkaDistributedSimulationLauncher.awaitBatchCompletion(...)`
- `KafkaDistributedSimulationLauncher.waitForFinishedParts(...)`

### Troubleshooting
- If IntelliJ cannot attach, make sure the route workers were recreated after debug ports were added to the generated compose file.
- If the worker does not hit the breakpoint, verify that the worker container for the route is running.
- If the batch times out, inspect worker logs:

```bash
docker compose -f docker-compose.yml -f docker-compose.operations-route101.yml logs -f productionline-route101-operation1-app
```
