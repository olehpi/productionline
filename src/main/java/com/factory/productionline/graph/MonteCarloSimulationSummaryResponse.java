package com.factory.productionline.graph;

import java.util.List;

public record MonteCarloSimulationSummaryResponse(
        int repetitions,
        List<RouteResult> routes
) {
    public record RouteResult(
            String routeId,
            double expectedFinishTime,
            double finishTimeStandardDeviation,
            List<MonteCarloSimulationResponse.BatchResult> batches
    ) {
    }
}
