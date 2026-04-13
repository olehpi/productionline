package com.factory.productionline.graph;

import java.util.List;

public record DistributedStartResponse(
        List<RouteStartResult> routes
) {
    public record RouteStartResult(
            String routeId,
            List<DistributedSimulationStartResponse> batches
    ) {
    }
}
