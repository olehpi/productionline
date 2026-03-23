package com.factory.productionline.graph;

import java.util.List;
import java.util.Map;

public record ProductionLineResponse(
        List<Route> routes,
        List<Bunker> availableBunkers,
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
            List<String> bunkerIds,
            List<Integer> inputIds,
            List<Integer> outputIds,
            List<Man> men,
            List<Material> materials,
            List<Machine> machines,
            List<Method> methods
    ) {
    }

    public record Bunker(
            String id,
            String name,
            double size,
            double maxSize
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
