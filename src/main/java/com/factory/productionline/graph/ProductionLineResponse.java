package com.factory.productionline.graph;

import java.util.List;

public record ProductionLineResponse(
        List<Route> routes,
        List<Operation> availableOperations
) {
    public record Route(
            String id,
            String name,
            List<String> operationIds
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
