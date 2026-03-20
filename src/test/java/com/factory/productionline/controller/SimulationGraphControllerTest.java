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
                  "availableOperations": [
                    {
                      "id": "op-1",
                      "name": "Cutting",
                      "men": [{}],
                      "materials": [{}],
                      "machines": [{}],
                      "methods": [{}]
                    },
                    {
                      "id": "op-2",
                      "name": "Packaging",
                      "men": [{}],
                      "materials": [{}],
                      "machines": [{}],
                      "methods": [{}]
                    },
                    {
                      "id": "op-3",
                      "name": "Painting",
                      "men": [{}],
                      "materials": [{}],
                      "machines": [{}],
                      "methods": [{}]
                    },
                    {
                      "id": "op-4",
                      "name": "Inspection",
                      "men": [{}],
                      "materials": [{}],
                      "machines": [{}],
                      "methods": [{}]
                    },
                    {
                      "id": "op-5",
                      "name": "Assembly",
                      "men": [{}],
                      "materials": [{}],
                      "machines": [{}],
                      "methods": [{}]
                    },
                    {
                      "id": "op-6",
                      "name": "Dispatch",
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
                .andExpect(jsonPath("$.availableOperations[0].id").value("op-1"))
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
                  "availableOperations": [
                    {
                      "id": "op-1",
                      "name": "Cutting",
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
}
