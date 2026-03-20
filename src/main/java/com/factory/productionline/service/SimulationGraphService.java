package com.factory.productionline.service;

import com.factory.productionline.graph.ProductionLineMapper;
import com.factory.productionline.graph.ProductionLineRequest;
import com.factory.productionline.graph.ProductionLineResponse;
import com.factory.productionline.model.ProductionLine;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SimulationGraphService {

    private final ProductionLineMapper productionLineMapper;

    public SimulationGraphService(ProductionLineMapper productionLineMapper) {
        this.productionLineMapper = productionLineMapper;
    }

    public ProductionLineResponse buildGraph(ProductionLineRequest request) {
        ProductionLine productionLine = productionLineMapper.toModel(request);

        Map<String, ProductionLine.Operation> availableOperationsById = productionLine.availableOperations().stream()
                .collect(Collectors.toMap(
                        ProductionLine.Operation::id,
                        Function.identity(),
                        (left, right) -> {
                            throw new IllegalArgumentException("Duplicate available operation id: " + left.id());
                        }
                ));

        productionLine.routes().forEach(route -> route.operationGraph().forEach((sourceOperationId, targetOperations) -> {
            validateOperationReference(route.id(), sourceOperationId, availableOperationsById);
            targetOperations.keySet()
                    .forEach(targetOperationId -> validateOperationReference(route.id(), targetOperationId, availableOperationsById));
        }));

        return productionLineMapper.toResponse(productionLine);
    }

    private void validateOperationReference(
            String routeId,
            String operationId,
            Map<String, ProductionLine.Operation> availableOperationsById
    ) {
        if (!availableOperationsById.containsKey(operationId)) {
            throw new IllegalArgumentException("Route " + routeId + " references unknown operation id: " + operationId);
        }
    }
}
