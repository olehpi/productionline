package com.factory.productionline.service;

import com.factory.productionline.graph.ProductionLineRequest;
import com.factory.productionline.graph.ProductionLineResponse;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SimulationGraphService {

    public ProductionLineResponse buildGraph(ProductionLineRequest request) {
        Map<String, ProductionLineRequest.Operation> availableOperationsById = request.availableOperations().stream()
                .collect(Collectors.toMap(
                        ProductionLineRequest.Operation::id,
                        Function.identity(),
                        (left, right) -> {
                            throw new IllegalArgumentException("Duplicate available operation id: " + left.id());
                        }
                ));

        request.routes().forEach(route -> route.operationIds().forEach(operationId -> {
            if (!availableOperationsById.containsKey(operationId)) {
                throw new IllegalArgumentException("Route " + route.id() + " references unknown operation id: " + operationId);
            }
        }));

        return new ProductionLineResponse(
                request.routes().stream()
                        .map(route -> new ProductionLineResponse.Route(
                                route.id(),
                                route.name(),
                                route.operationIds()
                        ))
                        .toList(),
                request.availableOperations().stream()
                        .map(operation -> new ProductionLineResponse.Operation(
                                operation.id(),
                                operation.name(),
                                operation.men().stream().map(ignored -> new ProductionLineResponse.Man()).toList(),
                                operation.materials().stream().map(ignored -> new ProductionLineResponse.Material()).toList(),
                                operation.machines().stream().map(ignored -> new ProductionLineResponse.Machine()).toList(),
                                operation.methods().stream().map(ignored -> new ProductionLineResponse.Method()).toList()
                        ))
                        .toList()
        );
    }
}
