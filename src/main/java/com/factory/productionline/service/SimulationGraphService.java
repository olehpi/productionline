package com.factory.productionline.service;

import com.factory.productionline.graph.ProductionLineRequest;
import com.factory.productionline.graph.ProductionLineResponse;
import org.springframework.stereotype.Service;

@Service
public class SimulationGraphService {

    public ProductionLineResponse buildGraph(ProductionLineRequest request) {
        return new ProductionLineResponse(
                request.routes().stream()
                        .map(route -> new ProductionLineResponse.Route(
                                route.id(),
                                route.name(),
                                route.operations().stream()
                                        .map(operation -> new ProductionLineResponse.Operation(
                                                operation.id(),
                                                operation.name(),
                                                operation.men().stream().map(ignored -> new ProductionLineResponse.Man()).toList(),
                                                operation.materials().stream().map(ignored -> new ProductionLineResponse.Material()).toList(),
                                                operation.machines().stream().map(ignored -> new ProductionLineResponse.Machine()).toList(),
                                                operation.methods().stream().map(ignored -> new ProductionLineResponse.Method()).toList()
                                        ))
                                        .toList()
                        ))
                        .toList()
        );
    }
}
