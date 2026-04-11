package com.factory.productionline.graph;

public record DistributedSimulationStartResponse(
        String routeId,
        String batchId,
        int partsSent,
        String startTopic,
        String finishTopic,
        double finishBatchTime
) {
}
