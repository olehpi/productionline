package com.factory.productionline.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

public record ProductionLine(
        @NotEmpty List<@Valid Route> routes,
        @NotEmpty List<@Valid Bunker> availableBunkers,
        @NotEmpty List<@Valid Operation> availableOperations
) {
    public record Route(
            @NotBlank String id,
            @NotBlank String name,
            @NotEmpty Map<String, Map<String, Integer>> operationGraph
    ) {
    }

    public record Operation(
            @NotBlank String id,
            @NotBlank String name,
            Integer outputBufferCapacity,
            @NotEmpty List<@NotBlank String> bunkerIds,
            List<Integer> inputIds,
            List<Integer> outputIds,
            @NotEmpty List<@Valid Man> men,
            @NotEmpty List<@Valid Material> materials,
            @NotEmpty List<@Valid Machine> machines,
            @NotEmpty List<@Valid Method> methods
    ) {
    }

    public record Bunker(
            @NotBlank String id,
            @NotBlank String name,
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


    public record LinearSimulationInput(
            String routeId,
            int partsCount,
            int operationsCount,
            String batchId,
            double startTau,
            double finishTau,
            List<LinearOperationInput> operations
    ) {
    }

    public record LinearOperationInput(
            int id,
            String name,
            double tauMean,
            double tauSigma,
            Long randomSeed,
            Integer outputBufferCapacity
    ) {
        public LinearOperationInput(int id, String name, double tauMean, double tauSigma, Long randomSeed) {
            this(id, name, tauMean, tauSigma, randomSeed, null);
        }
    }

    public record DistributedRouteInput(
            String routeId,
            int operationsCount,
            List<LinearOperationInput> operations
    ) {
    }
}
