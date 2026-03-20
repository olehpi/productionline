package com.factory.productionline.graph;

import com.factory.productionline.model.ProductionLine;
import org.springframework.stereotype.Component;

@Component
public class ProductionLineMapper {

    public ProductionLine toModel(ProductionLineRequest request) {
        return new ProductionLine(
                request.routes().stream()
                        .map(route -> new ProductionLine.Route(
                                route.id(),
                                route.name(),
                                route.operationGraph()
                        ))
                        .toList(),
                request.availableOperations().stream()
                        .map(operation -> new ProductionLine.Operation(
                                operation.id(),
                                operation.name(),
                                operation.men().stream().map(ignored -> new ProductionLine.Man()).toList(),
                                operation.materials().stream().map(ignored -> new ProductionLine.Material()).toList(),
                                operation.machines().stream().map(ignored -> new ProductionLine.Machine()).toList(),
                                operation.methods().stream().map(ignored -> new ProductionLine.Method()).toList()
                        ))
                        .toList()
        );
    }

    public ProductionLineResponse toResponse(ProductionLine productionLine) {
        return new ProductionLineResponse(
                productionLine.routes().stream()
                        .map(route -> new ProductionLineResponse.Route(
                                route.id(),
                                route.name(),
                                route.operationGraph()
                        ))
                        .toList(),
                productionLine.availableOperations().stream()
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
