package com.factory.productionline.graph;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ProductionLineRequest(
        @NotEmpty List<@Valid Route> routes,
        @NotEmpty List<@Valid Operation> availableOperations
) {
    public record Route(
            @NotBlank String id,
            @NotBlank String name,
            @NotEmpty List<@NotBlank String> operationIds
    ) {
    }

    public record Operation(
            @NotBlank String id,
            @NotBlank String name,
            @NotEmpty List<@Valid Man> men,
            @NotEmpty List<@Valid Material> materials,
            @NotEmpty List<@Valid Machine> machines,
            @NotEmpty List<@Valid Method> methods
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
