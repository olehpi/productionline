package com.factory.productionline.graph;

public record DistributedWorkersApplyResponse(
        String routeId,
        String batchId,
        int operationsCount,
        long servicesPrepared
) {
}
