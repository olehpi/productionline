package com.factory.productionline.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SimulationGraphControllerTest {

    @Autowired
    private MockMvc mockMvc;

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
    void runLinearSimulationBuildsSequentialPipelineWithKafkaTransfers() throws Exception {
        String payload = """
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
                """;

        mockMvc.perform(post("/api/simulation-graph/linear")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationTimelines.length()").value(7))
                .andExpect(jsonPath("$.operationTimelines[0].events.length()").value(3))
                .andExpect(jsonPath("$.operationTimelines[6].events.length()").value(3))
                .andExpect(jsonPath("$.kafkaMessages.length()").value(18))
                .andExpect(jsonPath("$.operationTimelines[0].events[0].startTau").value(0.0))
                .andExpect(jsonPath("$.operationTimelines[0].events[0].finishTau").value(10.0))
                .andExpect(jsonPath("$.operationTimelines[6].events[2].finishTau").value(90.0))
                .andExpect(jsonPath("$.finalTau").value(90.0));
    }
}
