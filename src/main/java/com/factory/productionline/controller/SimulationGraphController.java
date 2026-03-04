package com.factory.productionline.controller;

import com.factory.productionline.graph.ProductionLineRequest;
import com.factory.productionline.graph.SimulationGraphResponse;
import com.factory.productionline.service.SimulationGraphService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulation-graph")
public class SimulationGraphController {

    private final SimulationGraphService simulationGraphService;

    public SimulationGraphController(SimulationGraphService simulationGraphService) {
        this.simulationGraphService = simulationGraphService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public SimulationGraphResponse buildSimulationGraph(@Valid @RequestBody ProductionLineRequest request) {
        return simulationGraphService.buildGraph(request);
    }
}
