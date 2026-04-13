package com.factory.productionline.model;

public record DistributedOperationEvent(
        String routeId,
        int operationId,
        int nextOperationId,
        int partNumber,
        String batchId,
        Integer repetition,
        double startTau,
        double processingTau,
        double finishTau
) {
    public DistributedOperationEvent {
        repetition = repetition == null ? 0 : repetition;
    }
}
