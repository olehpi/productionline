package com.factory.productionline.model;

public record DistributedPartMessage(
        String routeId,
        int partNumber,
        String batchId,
        double startTau,
        double processingTau,
        double finishTau
) {
}
