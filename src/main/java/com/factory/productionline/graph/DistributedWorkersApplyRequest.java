package com.factory.productionline.graph;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record DistributedWorkersApplyRequest(
        @NotEmpty List<@Valid RouteInput> routes
) {
    public record RouteInput(
            @NotBlank String routeId,
            @NotEmpty List<@Valid OperationInput> operations
    ) {
    }

    public record OperationInput(
            @Min(0) int id,
            @NotBlank String name,
            @DecimalMin(value = "0.0", inclusive = true) double tauMean,
            @DecimalMin(value = "0.0", inclusive = true) double tauSigma,
            Long randomSeed
    ) {
    }
}
