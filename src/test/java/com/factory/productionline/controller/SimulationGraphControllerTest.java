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
    void buildGraphReturnsTopologyForValidPayload() throws Exception {
        String payload = """
                {
                  "operations": [
                    {
                      "id": "drill-fast",
                      "name": "Drilling",
                      "meanProcessingTimeSeconds": 10.0,
                      "standardDeviationSeconds": 1.5,
                      "distributionType": "NORMAL",
                      "eligibleEquipmentIds": ["drill-a", "drill-b"]
                    },
                    {
                      "id": "drill-slow",
                      "name": "Drilling",
                      "meanProcessingTimeSeconds": 20.0,
                      "standardDeviationSeconds": 2.0,
                      "distributionType": "LOGNORMAL",
                      "eligibleEquipmentIds": ["drill-c"]
                    },
                    {
                      "id": "pack",
                      "name": "Packaging",
                      "meanProcessingTimeSeconds": 12.0,
                      "standardDeviationSeconds": 1.0,
                      "distributionType": "UNIFORM",
                      "eligibleEquipmentIds": ["pack-1"]
                    }
                  ],
                  "equipmentResources": [
                    {"id": "drill-a", "name": "Drill machine A", "type": "DRILL", "quantity": 1},
                    {"id": "drill-b", "name": "Drill machine B", "type": "DRILL", "quantity": 1},
                    {"id": "drill-c", "name": "Legacy drill", "type": "DRILL", "quantity": 1},
                    {"id": "pack-1", "name": "Pack station", "type": "PACKAGING", "quantity": 1}
                  ],
                  "transitions": [
                    {"fromOperationId": "drill-fast", "toOperationId": "pack"},
                    {"fromOperationId": "drill-slow", "toOperationId": "pack"}
                  ]
                }
                """;

        mockMvc.perform(post("/api/simulation-graph")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasCycle").value(false))
                .andExpect(jsonPath("$.topologicalOrder[0]").value("drill-fast"))
                .andExpect(jsonPath("$.topologicalOrder[1]").value("drill-slow"))
                .andExpect(jsonPath("$.topologicalOrder[2]").value("pack"))
                .andExpect(jsonPath("$.adjacency.drill-fast[0]").value("pack"))
                .andExpect(jsonPath("$.nodes[0].distributionType").value("NORMAL"))
                .andExpect(jsonPath("$.nodes[0].eligibleEquipmentIds[0]").value("drill-a"))
                .andExpect(jsonPath("$.equipmentResources[2].id").value("drill-c"));
    }

    @Test
    void buildGraphReturnsBadRequestForUnknownEquipmentReference() throws Exception {
        String payload = """
                {
                  "operations": [
                    {
                      "id": "drill-fast",
                      "name": "Drilling",
                      "meanProcessingTimeSeconds": 10.0,
                      "standardDeviationSeconds": 1.5,
                      "distributionType": "NORMAL",
                      "eligibleEquipmentIds": ["drill-a", "drill-z"]
                    }
                  ],
                  "equipmentResources": [
                    {"id": "drill-a", "name": "Drill machine A", "type": "DRILL", "quantity": 1}
                  ],
                  "transitions": [
                    {"fromOperationId": "drill-fast", "toOperationId": "drill-fast"}
                  ]
                }
                """;

        mockMvc.perform(post("/api/simulation-graph")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid production line configuration"))
                .andExpect(jsonPath("$.detail").value("Operation drill-fast references unknown equipment: drill-z"));
    }
}
