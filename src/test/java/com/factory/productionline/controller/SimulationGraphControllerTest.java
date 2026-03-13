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
                      "manStates": [{"z": 0, "meanProcessingTimeSeconds": 10.0, "standardDeviationSeconds": 1.5, "distributionType": "NORMAL"}, {"z": 1, "meanProcessingTimeSeconds": 14.0, "standardDeviationSeconds": 2.5, "distributionType": "NORMAL"}],
                      "materialStates": [{"z": 0, "meanProcessingTimeSeconds": 10.0, "standardDeviationSeconds": 1.5, "distributionType": "NORMAL"}, {"z": 1, "meanProcessingTimeSeconds": 13.0, "standardDeviationSeconds": 2.0, "distributionType": "LOGNORMAL"}],
                      "machineStates": [{"z": 0, "meanProcessingTimeSeconds": 10.0, "standardDeviationSeconds": 1.5, "distributionType": "NORMAL"}, {"z": 1, "meanProcessingTimeSeconds": 18.0, "standardDeviationSeconds": 3.0, "distributionType": "LOGNORMAL"}],
                      "methodStates": [{"z": 0, "meanProcessingTimeSeconds": 10.0, "standardDeviationSeconds": 1.5, "distributionType": "NORMAL"}, {"z": 1, "meanProcessingTimeSeconds": 12.0, "standardDeviationSeconds": 1.8, "distributionType": "UNIFORM"}],
                      "eligibleManIds": ["operator-a"],
                      "eligibleMaterialIds": ["material-a"],
                      "eligibleMachineIds": ["drill-a", "drill-b"],
                      "eligibleMethodIds": ["method-drill"]
                    },
                    {
                      "id": "drill-slow",
                      "name": "Drilling",
                      "manStates": [{"z": 0, "meanProcessingTimeSeconds": 20.0, "standardDeviationSeconds": 2.0, "distributionType": "LOGNORMAL"}, {"z": 1, "meanProcessingTimeSeconds": 22.0, "standardDeviationSeconds": 2.5, "distributionType": "LOGNORMAL"}],
                      "materialStates": [{"z": 0, "meanProcessingTimeSeconds": 20.0, "standardDeviationSeconds": 2.0, "distributionType": "LOGNORMAL"}, {"z": 2, "meanProcessingTimeSeconds": 26.0, "standardDeviationSeconds": 3.0, "distributionType": "NORMAL"}],
                      "machineStates": [{"z": 0, "meanProcessingTimeSeconds": 20.0, "standardDeviationSeconds": 2.0, "distributionType": "LOGNORMAL"}, {"z": 2, "meanProcessingTimeSeconds": 28.0, "standardDeviationSeconds": 4.0, "distributionType": "UNIFORM"}],
                      "methodStates": [{"z": 0, "meanProcessingTimeSeconds": 20.0, "standardDeviationSeconds": 2.0, "distributionType": "LOGNORMAL"}, {"z": 1, "meanProcessingTimeSeconds": 24.0, "standardDeviationSeconds": 2.2, "distributionType": "NORMAL"}],
                      "eligibleManIds": ["operator-b"],
                      "eligibleMaterialIds": ["material-b"],
                      "eligibleMachineIds": ["drill-c"],
                      "eligibleMethodIds": ["method-drill"]
                    },
                    {
                      "id": "pack",
                      "name": "Packaging",
                      "manStates": [{"z": 0, "meanProcessingTimeSeconds": 12.0, "standardDeviationSeconds": 1.0, "distributionType": "UNIFORM"}, {"z": 1, "meanProcessingTimeSeconds": 15.0, "standardDeviationSeconds": 2.0, "distributionType": "NORMAL"}],
                      "materialStates": [{"z": 0, "meanProcessingTimeSeconds": 12.0, "standardDeviationSeconds": 1.0, "distributionType": "UNIFORM"}, {"z": 1, "meanProcessingTimeSeconds": 14.0, "standardDeviationSeconds": 1.5, "distributionType": "LOGNORMAL"}],
                      "machineStates": [{"z": 0, "meanProcessingTimeSeconds": 12.0, "standardDeviationSeconds": 1.0, "distributionType": "UNIFORM"}, {"z": 1, "meanProcessingTimeSeconds": 16.0, "standardDeviationSeconds": 2.5, "distributionType": "NORMAL"}],
                      "methodStates": [{"z": 0, "meanProcessingTimeSeconds": 12.0, "standardDeviationSeconds": 1.0, "distributionType": "UNIFORM"}, {"z": 1, "meanProcessingTimeSeconds": 13.0, "standardDeviationSeconds": 1.2, "distributionType": "UNIFORM"}],
                      "eligibleManIds": ["operator-c"],
                      "eligibleMaterialIds": ["material-pack"],
                      "eligibleMachineIds": ["pack-1"],
                      "eligibleMethodIds": ["method-pack"]
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
                .andExpect(jsonPath("$.nodes[0].machineStates[0].distributionType").value("NORMAL"))
                .andExpect(jsonPath("$.nodes[0].eligibleMachineIds[0]").value("drill-a"))
                .andExpect(jsonPath("$.nodes[0].riskCategories[2].category").value("machine"))
                .andExpect(jsonPath("$.nodes[0].riskCategories[2].eligibleResourceIds[0]").value("drill-a"))
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
                      "manStates": [{"z": 0, "meanProcessingTimeSeconds": 10.0, "standardDeviationSeconds": 1.5, "distributionType": "NORMAL"}, {"z": 1, "meanProcessingTimeSeconds": 14.0, "standardDeviationSeconds": 2.5, "distributionType": "NORMAL"}],
                      "materialStates": [{"z": 0, "meanProcessingTimeSeconds": 10.0, "standardDeviationSeconds": 1.5, "distributionType": "NORMAL"}, {"z": 1, "meanProcessingTimeSeconds": 13.0, "standardDeviationSeconds": 2.0, "distributionType": "LOGNORMAL"}],
                      "machineStates": [{"z": 0, "meanProcessingTimeSeconds": 10.0, "standardDeviationSeconds": 1.5, "distributionType": "NORMAL"}, {"z": 1, "meanProcessingTimeSeconds": 18.0, "standardDeviationSeconds": 3.0, "distributionType": "LOGNORMAL"}],
                      "methodStates": [{"z": 0, "meanProcessingTimeSeconds": 10.0, "standardDeviationSeconds": 1.5, "distributionType": "NORMAL"}, {"z": 1, "meanProcessingTimeSeconds": 12.0, "standardDeviationSeconds": 1.8, "distributionType": "UNIFORM"}],
                      "eligibleManIds": ["operator-a"],
                      "eligibleMaterialIds": ["material-a"],
                      "eligibleMachineIds": ["drill-a", "drill-z"],
                      "eligibleMethodIds": ["method-drill"]
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
                .andExpect(jsonPath("$.detail").value("Operation drill-fast references unknown machine: drill-z"));
    }
}
