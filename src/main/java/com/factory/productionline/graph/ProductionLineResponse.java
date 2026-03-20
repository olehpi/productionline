package com.factory.productionline.graph;

import java.util.List;
import java.util.Map;

public record ProductionLineResponse(
        List<Route> routes,
        List<Operation> availableOperations
) {
    public record Route(
            String id,
            String name,
            Map<String, Map<String, Integer>> operationGraph
    ) {
    }

    public record Operation(
            String id,
            String name,
            List<Man> men,
            List<Material> materials,
            List<Machine> machines,
            List<Method> methods
    ) {
    }

    public record Man() {
    }

    public record Material() {
    }

    public record Machine() {
    }

    public record Method() {
    }
}
