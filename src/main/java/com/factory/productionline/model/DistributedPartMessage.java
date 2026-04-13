package com.factory.productionline.model;

public record DistributedPartMessage(
        String routeId,
        int partNumber,
        String batchId,
        Integer repetition,
        double startTau,
        double processingTau,
        double finishTau
) {
    public DistributedPartMessage {
        repetition = repetition == null ? 0 : repetition;
    }
}
