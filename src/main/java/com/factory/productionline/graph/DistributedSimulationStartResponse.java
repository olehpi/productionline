package com.factory.productionline.graph;

public record DistributedSimulationStartResponse(
        String batchId,
        int partsSent,
        String startTopic,
        String finishTopic
) {
}
