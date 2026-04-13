package com.factory.productionline.graph;

import java.util.List;

public record DistributedWorkersApplyResponse(
        List<RouteWorkersApplyResult> routes,
        long totalServicesPrepared
) {
    public record RouteWorkersApplyResult(
            String routeId,
            int operationsCount,
            long servicesPrepared
    ) {
    }
}
