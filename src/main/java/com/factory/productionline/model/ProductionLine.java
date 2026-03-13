package com.factory.productionline.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ProductionLine(
        @NotEmpty List<@Valid Route> routes
) {
    public record Route(
            @NotBlank String id,
            @NotBlank String name,
            @NotEmpty List<@Valid Operation> operations
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
