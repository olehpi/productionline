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
- This setup applies only to `productionline-app`. Distributed worker containers require separate debug configuration if it needs to stop inside worker code.
