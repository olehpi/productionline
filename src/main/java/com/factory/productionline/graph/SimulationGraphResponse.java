package com.factory.productionline.graph;

import java.util.List;
import java.util.Map;

public record SimulationGraphResponse(
        List<OperationNode> nodes,
        List<OperationEdge> edges,
        List<EquipmentResourceNode> equipmentResources,
        Map<String, List<String>> adjacency,
        boolean hasCycle,
        List<String> topologicalOrder
) {
    public record OperationNode(
            String id,
            String name,
            List<ProcessingStateNode> manStates,
            List<ProcessingStateNode> materialStates,
            List<ProcessingStateNode> machineStates,
            List<ProcessingStateNode> methodStates,
            List<String> eligibleManIds,
            List<String> eligibleMaterialIds,
            List<String> eligibleMachineIds,
            List<String> eligibleMethodIds
    ) {
    }

    public record ProcessingStateNode(
            int z,
            double meanProcessingTimeSeconds,
            double standardDeviationSeconds,
            DistributionType distributionType
    ) {
    }

    public record EquipmentResourceNode(
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
