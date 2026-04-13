package com.factory.productionline.graph;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record MonteCarloSimulationRequest(
        @NotEmpty List<@Valid RouteInput> routes,
        @Min(1) int repetitions
) {
    public record RouteInput(
            @NotBlank String routeId,
            @NotEmpty List<@Valid OperationInput> operations,
            @NotEmpty List<@Valid BatchInput> batches
    ) {
    }

    public record OperationInput(
            @Min(0) int id,
            @NotBlank String name,
            @DecimalMin(value = "0.0", inclusive = true) double tauMean,
            @DecimalMin(value = "0.0", inclusive = true) double tauSigma,
            Long randomSeed
    ) implements ProductionLineMapper.OperationInputView {
    }

    public record BatchInput(
            @NotBlank String batchId,
            @Min(1) int partsCount,
            @DecimalMin(value = "0.0", inclusive = true) double startTau,
            @DecimalMin(value = "0.0", inclusive = true) double finishTau
    ) {
    }
}
