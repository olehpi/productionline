package com.factory.productionline.graph;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record LinearSimulationRequest(
        @Min(1) int partsCount,
        @Min(1) int operationsCount,
        @NotBlank String batchId,
        @DecimalMin(value = "0.000001", inclusive = true) double tauMean,
        @DecimalMin(value = "0.0", inclusive = true) double tauSigma,
        Long randomSeed
) {
}
