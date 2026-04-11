package com.factory.productionline.graph;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record LinearSimulationRequest(
        @NotBlank String routeId,
        @Min(1) int partsCount,
        @NotBlank String batchId,
        @DecimalMin(value = "0.0", inclusive = true) double startTau,
        @DecimalMin(value = "0.0", inclusive = true) double finishTau,
        @NotEmpty List<@Valid OperationInput> operations
) {
    public record OperationInput(
            @Min(0) int id,
            @NotBlank String name,
            @DecimalMin(value = "0.0", inclusive = true) double tauMean,
            @DecimalMin(value = "0.0", inclusive = true) double tauSigma,
            Long randomSeed
    ) {
    }
}
