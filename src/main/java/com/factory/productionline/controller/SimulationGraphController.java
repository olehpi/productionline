package com.factory.productionline.controller;

import com.factory.productionline.graph.DistributedComposeResponse;
import com.factory.productionline.graph.DistributedStartRequest;
import com.factory.productionline.graph.DistributedStartResponse;
import com.factory.productionline.graph.DistributedWorkersApplyRequest;
import com.factory.productionline.graph.DistributedWorkersApplyResponse;
import com.factory.productionline.graph.DistributedSimulationStartResponse;
import com.factory.productionline.graph.LinearSimulationRequest;
import com.factory.productionline.graph.LinearSimulationResponse;
import com.factory.productionline.graph.MonteCarloSimulationRequest;
import com.factory.productionline.graph.MonteCarloSimulationResponse;
import com.factory.productionline.graph.ProductionLineMapper;
import com.factory.productionline.graph.ProductionLineRequest;
import com.factory.productionline.graph.ProductionLineResponse;
import com.factory.productionline.service.DistributedComposeGenerator;
import com.factory.productionline.service.DistributedMonteCarloSimulationService;
import com.factory.productionline.service.DistributedRouteRegistry;
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
    private final DistributedMonteCarloSimulationService distributedMonteCarloSimulationService;
    private final DistributedRouteRegistry distributedRouteRegistry;
    private final DistributedTelemetryQueryService distributedTelemetryQueryService;
    private final DistributedComposeGenerator distributedComposeGenerator;
    private final DistributedWorkerOrchestrationService distributedWorkerOrchestrationService;

    public SimulationGraphController(
            SimulationGraphService simulationGraphService,
            ProductionLineMapper productionLineMapper,
            DistributedSimulationLauncher distributedSimulationLauncher,
            DistributedMonteCarloSimulationService distributedMonteCarloSimulationService,
            DistributedRouteRegistry distributedRouteRegistry,
            DistributedTelemetryQueryService distributedTelemetryQueryService,
            DistributedComposeGenerator distributedComposeGenerator,
            DistributedWorkerOrchestrationService distributedWorkerOrchestrationService
    ) {
        this.simulationGraphService = simulationGraphService;
        this.productionLineMapper = productionLineMapper;
        this.distributedSimulationLauncher = distributedSimulationLauncher;
        this.distributedMonteCarloSimulationService = distributedMonteCarloSimulationService;
        this.distributedRouteRegistry = distributedRouteRegistry;
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
    public DistributedWorkersApplyResponse applyDistributedWorkers(@Valid @RequestBody DistributedWorkersApplyRequest request) {
        var routes = request.routes().stream()
                .map(productionLineMapper::toModel)
                .toList();

        routes.forEach(distributedWorkerOrchestrationService::applyWorkers);

        var routeResults = routes.stream()
                .map(route -> new DistributedWorkersApplyResponse.RouteWorkersApplyResult(
                        route.routeId(),
                        route.operationsCount(),
                        route.operationsCount() + 1L
                ))
                .toList();

        long totalServicesPrepared = routeResults.stream()
                .mapToLong(DistributedWorkersApplyResponse.RouteWorkersApplyResult::servicesPrepared)
                .sum();

        return new DistributedWorkersApplyResponse(routeResults, totalServicesPrepared);
    }


    @PostMapping("/linear/distributed/compose")
    @ResponseStatus(HttpStatus.OK)
    public DistributedComposeResponse generateDistributedCompose(@Valid @RequestBody LinearSimulationRequest request) {
        var model = productionLineMapper.toModel(request);
        String composeYaml = distributedComposeGenerator.generate(model);
        return new DistributedComposeResponse(routeComposeFileName(model.routeId()), composeYaml);
    }

    @PostMapping("/linear/distributed/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DistributedStartResponse startDistributedLinearSimulation(@Valid @RequestBody DistributedStartRequest request) {
        return new DistributedStartResponse(
                request.routes().stream()
                        .map(route -> new DistributedStartResponse.RouteStartResult(
                                route.routeId(),
                                route.batches().stream()
                                        .map(batch -> distributedRouteRegistry.createLinearSimulationInput(
                                                route.routeId(),
                                                batch.partsCount(),
                                                batch.batchId(),
                                                batch.startTau(),
                                                batch.finishTau()
                                        ))
                                        .map(distributedSimulationLauncher::start)
                                        .toList()
                        ))
                        .toList()
        );
    }

    @PostMapping("/linear/distributed/monte-carlo")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MonteCarloSimulationResponse startDistributedMonteCarloSimulation(@Valid @RequestBody MonteCarloSimulationRequest request) {
        return new MonteCarloSimulationResponse(
                request.repetitions(),
                request.routes().stream()
                        .map(route -> distributedMonteCarloSimulationService.runRoute(
                                route.routeId(),
                                route.batches().stream()
                                        .map(batch -> productionLineMapper.toModel(route, batch))
                                        .toList(),
                                request.repetitions()
                        ))
                        .toList()
        );
    }

    @GetMapping("/linear/distributed/telemetry/{routeId}")
    @ResponseStatus(HttpStatus.OK)
    public LinearSimulationResponse getDistributedRouteTelemetry(@PathVariable String routeId) {
        return productionLineMapper.toResponse(distributedTelemetryQueryService.getRouteResult(routeId));
    }

    @GetMapping("/linear/distributed/telemetry/{routeId}/{batchId}")
    @ResponseStatus(HttpStatus.OK)
    public LinearSimulationResponse getDistributedBatchTelemetry(
            @PathVariable String routeId,
            @PathVariable String batchId
    ) {
        return productionLineMapper.toResponse(distributedTelemetryQueryService.getBatchResult(routeId, batchId));
    }

    @GetMapping("/linear/distributed/telemetry/{routeId}/{batchId}/{partNumber}")
    @ResponseStatus(HttpStatus.OK)
    public LinearSimulationResponse getDistributedPartTelemetry(
            @PathVariable String routeId,
            @PathVariable String batchId,
            @PathVariable int partNumber
    ) {
        return productionLineMapper.toResponse(distributedTelemetryQueryService.getPartResult(routeId, batchId, partNumber));
    }

    private String routeComposeFileName(String routeId) {
        return "docker-compose.operations-" + routeId.replaceAll("[^A-Za-z0-9._-]", "_") + ".yml";
    }
}

