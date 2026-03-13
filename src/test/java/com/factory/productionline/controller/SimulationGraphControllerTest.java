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
                      "operationIds": ["op-1", "op-2"]
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
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/simulation-graph")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routes[0].id").value("route-1"))
                .andExpect(jsonPath("$.routes[0].operationIds[1]").value("op-2"))
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
                      "operationIds": ["op-unknown"]
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
