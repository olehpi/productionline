package com.factory.productionline.model;

public record DistributedPartMessage(
        int partNumber,
        String batchId,
        double startTau,
        double processingTau,
        double finishTau
) {
}
