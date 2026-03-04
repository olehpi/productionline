package com.factory.productionline.graph;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public record ProductionLineRequest(
        @NotEmpty List<@Valid Operation> operations,
        @NotEmpty List<@Valid EquipmentResource> equipmentResources,
        @NotEmpty List<@Valid OperationTransition> transitions
) {
    public record Operation(
            @NotBlank String id,
            @NotBlank String name,
            @DecimalMin(value = "0.0", inclusive = false) double meanProcessingTimeSeconds,
            @PositiveOrZero double standardDeviationSeconds,
            @NotNull DistributionType distributionType,
            @NotEmpty List<@NotBlank String> eligibleEquipmentIds
    ) {
    }

    public record EquipmentResource(
            @NotBlank String id,
            @NotBlank String name,
            @NotBlank String type,
            @Positive int quantity
    ) {
    }

    public record OperationTransition(
            @NotBlank String fromOperationId,
            @NotBlank String toOperationId
    ) {
    }
}
