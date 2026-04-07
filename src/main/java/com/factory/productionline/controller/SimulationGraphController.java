package com.factory.productionline.controller;

import com.factory.productionline.graph.DistributedComposeResponse;
import com.factory.productionline.graph.DistributedWorkersApplyResponse;
import com.factory.productionline.graph.DistributedSimulationStartResponse;
import com.factory.productionline.graph.LinearSimulationRequest;
import com.factory.productionline.graph.LinearSimulationResponse;
import com.factory.productionline.graph.ProductionLineMapper;
import com.factory.productionline.graph.ProductionLineRequest;
import com.factory.productionline.graph.ProductionLineResponse;
import com.factory.productionline.service.DistributedComposeGenerator;
import com.factory.productionline.service.DistributedSimulationLauncher;
import com.factory.productionline.service.DistributedTelemetryQueryService;
import com.factory.productionline.service.DistributedWorkerOrchestrationService;
import com.factory.productionline.service.SimulationGraphService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulation-graph")
public class SimulationGraphController {

    private final SimulationGraphService simulationGraphService;
    private final ProductionLineMapper productionLineMapper;
    private final DistributedSimulationLauncher distributedSimulationLauncher;
    private final DistributedTelemetryQueryService distributedTelemetryQueryService;
    private final DistributedComposeGenerator distributedComposeGenerator;
    private final DistributedWorkerOrchestrationService distributedWorkerOrchestrationService;

    public SimulationGraphController(
            SimulationGraphService simulationGraphService,
            ProductionLineMapper productionLineMapper,
            DistributedSimulationLauncher distributedSimulationLauncher,
            DistributedTelemetryQueryService distributedTelemetryQueryService,
            DistributedComposeGenerator distributedComposeGenerator,
            DistributedWorkerOrchestrationService distributedWorkerOrchestrationService
    ) {
        this.simulationGraphService = simulationGraphService;
        this.productionLineMapper = productionLineMapper;
        this.distributedSimulationLauncher = distributedSimulationLauncher;
        this.distributedTelemetryQueryService = distributedTelemetryQueryService;
        this.distributedComposeGenerator = distributedComposeGenerator;
        this.distributedWorkerOrchestrationService = distributedWorkerOrchestrationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public ProductionLineResponse buildSimulationGraph(@Valid @RequestBody ProductionLineRequest request) {
        return simulationGraphService.buildGraph(request);
    }

    @PostMapping("/linear/distributed/apply")
    @ResponseStatus(HttpStatus.OK)
    public DistributedWorkersApplyResponse applyDistributedWorkers(@Valid @RequestBody LinearSimulationRequest request) {
        var model = productionLineMapper.toModel(request);
        distributedWorkerOrchestrationService.applyWorkers(model);
        return new DistributedWorkersApplyResponse(
                model.batchId(),
                model.operationsCount(),
                model.operationsCount() + 1L
        );
    }


    @PostMapping("/linear/distributed/compose")
    @ResponseStatus(HttpStatus.OK)
    public DistributedComposeResponse generateDistributedCompose(@Valid @RequestBody LinearSimulationRequest request) {
        String composeYaml = distributedComposeGenerator.generate(productionLineMapper.toModel(request));
        return new DistributedComposeResponse("docker-compose.operations.yml", composeYaml);
    }

    @PostMapping("/linear/distributed/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DistributedSimulationStartResponse startDistributedLinearSimulation(@Valid @RequestBody LinearSimulationRequest request) {
        return distributedSimulationLauncher.start(productionLineMapper.toModel(request));
    }

    @GetMapping("/linear/distributed/telemetry/{batchId}")
    @ResponseStatus(HttpStatus.OK)
    public LinearSimulationResponse getDistributedTelemetry(@PathVariable String batchId) {
        return productionLineMapper.toResponse(distributedTelemetryQueryService.getBatchResult(batchId));
    }
}

