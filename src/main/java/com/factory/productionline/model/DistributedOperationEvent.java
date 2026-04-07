package com.factory.productionline.model;

public record DistributedOperationEvent(
        int operationId,
        int nextOperationId,
        int partNumber,
        String batchId,
        double startTau,
        double processingTau,
        double finishTau
) {
}
