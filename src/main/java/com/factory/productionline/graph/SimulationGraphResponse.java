package com.factory.productionline.graph;

import java.util.List;
import java.util.Map;

public record SimulationGraphResponse(
        List<Operation> nodes,
        List<OperationEdge> edges,
        List<EquipmentResource> equipmentResources,
        Map<String, List<String>> adjacency,
        boolean hasCycle,
        List<String> topologicalOrder
) {
    public record Operation(
            String id,
            String name,
            List<ProcessingState> manStates,
            List<ProcessingState> materialStates,
            List<ProcessingState> machineStates,
            List<ProcessingState> methodStates,
            List<String> eligibleManIds,
            List<String> eligibleMaterialIds,
            List<Machine> eligibleMachines,
            List<String> eligibleMethodIds,
            List<RiskCategory> riskCategories
    ) {
    }

    public record ProcessingState(
            int z,
            double meanProcessingTimeSeconds,
            double standardDeviationSeconds,
            DistributionType distributionType
    ) {
    }

    public record RiskCategory(
            String category,
            List<ProcessingState> states,
            List<String> eligibleResourceIds
    ) {
    }

    public record Machine(
            String id
    ) {
    }

    public record EquipmentResource(
            String id,
            String name,
            String type,
            int quantity
    ) {
    }

    public record OperationEdge(
            String fromOperationId,
            String toOperationId
    ) {
    }
}
