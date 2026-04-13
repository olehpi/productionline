package com.factory.productionline.controller;

import com.factory.productionline.model.ProductionLine;
import com.factory.productionline.service.DistributedRouteRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SimulationGraphControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DistributedRouteRegistry distributedRouteRegistry;

    @Test
    void buildGraphReturnsProductionLineForValidPayload() throws Exception {
        String payload = """
                {
                  "routes": [
                    {
                      "id": "route-1",
                      "name": "Main route",
                      "operationGraph": {
                        "op-1": {
                          "op-2": 1
                        },
                        "op-2": {
                          "op-3": 1,
                          "op-4": 1
                        },
                        "op-3": {
                          "op-5": 1
                        },
                        "op-4": {
                          "op-5": 1
                        },
                        "op-5": {
                          "op-6": 1
                        },
                        "op-6": {
                        }
                      }
                    }
                  ],
                  "availableBunkers": [
                    {
                      "id": "bunker-cutting",
                      "name": "Bunker before Cutting",
                      "size": 10,
                      "maxSize": 100
                    },
                    {
                      "id": "bunker-shared-pack-paint-inspect",
                      "name": "Shared bunker after op-2",
                      "size": 5,
                      "maxSize": 50
                    },
                    {
                      "id": "bunker-assembly",
                      "name": "Bunker before Assembly",
                      "size": 7,
                      "maxSize": 80
                    },
                    {
                      "id": "bunker-dispatch",
                      "name": "Bunker before Dispatch",
                      "size": 1,
                      "maxSize": 20
                    }
                  ],
                  "availableOperations": [
                    {
                      "id": "op-1",
                      "name": "Cutting",
                      "bunkerIds": ["bunker-cutting"],
                      "men": [{}],
                      "materials": [{}],
                      "machines": [{}],
                      "methods": [{}]
                    },
                    {
                      "id": "op-2",
                      "name": "Packaging",
                      "bunkerIds": ["bunker-shared-pack-paint-inspect"],
                      "men": [{}],
                      "materials": [{}],
                      "machines": [{}],
                      "methods": [{}]
                    },
                    {
                      "id": "op-3",
                      "name": "Painting",
                      "bunkerIds": ["bunker-shared-pack-paint-inspect", "bunker-assembly"],
                      "men": [{}],
                      "materials": [{}],
                      "machines": [{}],
                      "methods": [{}]
                    },
                    {
                      "id": "op-4",
                      "name": "Inspection",
                      "bunkerIds": ["bunker-shared-pack-paint-inspect"],
                      "men": [{}],
                      "materials": [{}],
                      "machines": [{}],
                      "methods": [{}]
                    },
                    {
                      "id": "op-5",
                      "name": "Assembly",
                      "bunkerIds": ["bunker-assembly"],
                      "men": [{}],
                      "materials": [{}],
                      "machines": [{}],
                      "methods": [{}]
                    },
                    {
                      "id": "op-6",
                      "name": "Dispatch",
                      "bunkerIds": ["bunker-dispatch"],
                      "men": [{}],
                      "materials": [{}],
                      "machines": [{}],
                      "methods": [{}]
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/simulation-graph")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routes[0].id").value("route-1"))
                .andExpect(jsonPath("$.routes[0].operationGraph.op-2.op-4").value(1))
                .andExpect(jsonPath("$.availableBunkers.length()").value(4))
                .andExpect(jsonPath("$.availableOperations[0].id").value("op-1"))
                .andExpect(jsonPath("$.availableOperations[2].bunkerIds[0]").value("bunker-shared-pack-paint-inspect"))
                .andExpect(jsonPath("$.availableOperations[2].bunkerIds[1]").value("bunker-assembly"))
                .andExpect(jsonPath("$.availableOperations[3].bunkerIds[0]").value("bunker-shared-pack-paint-inspect"))
                .andExpect(jsonPath("$.availableOperations[0].men.length()").value(1));
    }

    @Test
    void buildGraphReturnsBadRequestForUnknownOperationReference() throws Exception {
        String payload = """
                {
                  "routes": [
                    {
                      "id": "route-1",
                      "name": "Main route",
                      "operationGraph": {
                        "op-unknown": {
                          "op-1": 1
                        }
                      }
                    }
                  ],
                  "availableBunkers": [
                    {
                      "id": "bunker-1",
                      "name": "Bunker 1",
                      "size": 0,
                      "maxSize": 10
                    }
                  ],
                  "availableOperations": [
                    {
                      "id": "op-1",
                      "name": "Cutting",
                      "bunkerIds": ["bunker-1"],
                      "men": [{}],
                      "materials": [{}],
                      "machines": [{}],
                      "methods": [{}]
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/simulation-graph")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid production line configuration"))
                .andExpect(jsonPath("$.detail").value("Route route-1 references unknown operation id: op-unknown"));
    }


    @Test
    void generateDistributedComposeFromLinearPayload() throws Exception {
        String payload = """
                {
                  "routeId": "route-42",
                  "partsCount": 3,
                  "batchId": "batch-42",
                  "startTau": 0.0,
                  "finishTau": 30.0,
                  "operations": [
                    { "id": 0, "name": "startStore", "tauMean": 0.0, "tauSigma": 0.0, "randomSeed": 0 },
                    { "id": 1, "name": "Op01", "tauMean": 10.0, "tauSigma": 0.0, "randomSeed": 1 },
                    { "id": 2, "name": "Op02", "tauMean": 10.0, "tauSigma": 0.0, "randomSeed": 2 },
                    { "id": 3, "name": "finishStore", "tauMean": 10.0, "tauSigma": 0.0, "randomSeed": 0 }
                  ]
                }
                """;

        mockMvc.perform(post("/api/simulation-graph/linear/distributed/compose")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.composeFileName").value("docker-compose.operations-route-42.yml"))
                .andExpect(jsonPath("$.composeYaml").value(org.hamcrest.Matchers.containsString("productionline-route-42-operation1-app")))
                .andExpect(jsonPath("$.composeYaml").value(org.hamcrest.Matchers.containsString("productionline-route-42-finish-store-app")));
    }
    @Test
    void applyDistributedWorkersReturnsConflictWhenApiOrchestrationDisabled() throws Exception {
        String payload = """
                {
                  "routes": [
                    {
                      "routeId": "route-42",
                      "operations": [
                        { "id": 0, "name": "startStore", "tauMean": 0.0, "tauSigma": 0.0, "randomSeed": 0 },
                        { "id": 1, "name": "Op01", "tauMean": 10.0, "tauSigma": 0.0, "randomSeed": 1 },
                        { "id": 2, "name": "Op02", "tauMean": 10.0, "tauSigma": 0.0, "randomSeed": 2 },
                        { "id": 3, "name": "finishStore", "tauMean": 10.0, "tauSigma": 0.0, "randomSeed": 0 }
                      ]
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/simulation-graph/linear/distributed/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Distributed orchestration unavailable"))
                .andExpect(jsonPath("$.detail").value("API-driven distributed orchestration is disabled"));
    }

    @Test
    void startDistributedSimulationReturnsAcceptedWithKafkaTopics() throws Exception {
        distributedRouteRegistry.registerRoute(new ProductionLine.DistributedRouteInput(
                "route-42",
                2,
                List.of(
                        new ProductionLine.LinearOperationInput(0, "startStore", 0.0, 0.0, 0L),
                        new ProductionLine.LinearOperationInput(1, "Op01", 10.0, 0.0, 1L),
                        new ProductionLine.LinearOperationInput(2, "Op02", 10.0, 0.0, 2L),
                        new ProductionLine.LinearOperationInput(3, "finishStore", 10.0, 0.0, 0L)
                )
        ));

        String payload = """
                {
                  "routes": [
                    {
                      "routeId": "route-42",
                      "batches": [
                        {
                          "batchId": "batch-42",
                          "partsCount": 3,
                          "startTau": 0.0,
                          "finishTau": 30.0
                        }
                      ]
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/simulation-graph/linear/distributed/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid production line configuration"))
                .andExpect(jsonPath("$.detail").value("Kafka is disabled. Set simulation.kafka.enabled=true to start distributed flow"));
    }

    @Test
    void monteCarloSimulationReturnsBadRequestWhenKafkaDisabled() throws Exception {
        String payload = """
                {
                  "routes": [
                    {
                      "routeId": "route-42",
                      "operations": [
                        { "id": 0, "name": "startStore", "tauMean": 0.0, "tauSigma": 0.0, "randomSeed": 0 },
                        { "id": 1, "name": "Op01", "tauMean": 10.0, "tauSigma": 0.0, "randomSeed": 1 },
                        { "id": 2, "name": "Op02", "tauMean": 10.0, "tauSigma": 0.0, "randomSeed": 2 },
                        { "id": 3, "name": "finishStore", "tauMean": 10.0, "tauSigma": 0.0, "randomSeed": 0 }
                      ],
                      "batches": [
                        {
                          "batchId": "batch-42",
                          "partsCount": 3,
                          "startTau": 0.0,
                          "finishTau": 30.0
                        }
                      ]
                    }
                  ],
                  "repetitions": 5
                }
                """;

        mockMvc.perform(post("/api/simulation-graph/linear/distributed/monte-carlo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid production line configuration"))
                .andExpect(jsonPath("$.detail").value("Kafka is disabled. Set simulation.kafka.enabled=true to start distributed flow"));
    }

    @Test
    void routeTelemetryReturnsConflictWhenKafkaDisabled() throws Exception {
        mockMvc.perform(get("/api/simulation-graph/linear/distributed/telemetry/route-unknown"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Distributed orchestration unavailable"))
                .andExpect(jsonPath("$.detail").value("Kafka telemetry is disabled. Set simulation.kafka.enabled=true to query distributed telemetry"));
    }

    @Test
    void batchTelemetryReturnsConflictWhenKafkaDisabled() throws Exception {
        mockMvc.perform(get("/api/simulation-graph/linear/distributed/telemetry/route-unknown/batch-unknown"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Distributed orchestration unavailable"))
                .andExpect(jsonPath("$.detail").value("Kafka telemetry is disabled. Set simulation.kafka.enabled=true to query distributed telemetry"));
    }

    @Test
    void partTelemetryReturnsConflictWhenKafkaDisabled() throws Exception {
        mockMvc.perform(get("/api/simulation-graph/linear/distributed/telemetry/route-unknown/batch-unknown/3"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Distributed orchestration unavailable"))
                .andExpect(jsonPath("$.detail").value("Kafka telemetry is disabled. Set simulation.kafka.enabled=true to query distributed telemetry"));
    }
}
