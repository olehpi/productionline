package com.factory.productionline.graph;

public record DistributedWorkersApplyResponse(
        String batchId,
        int operationsCount,
        long servicesPrepared
) {
}
