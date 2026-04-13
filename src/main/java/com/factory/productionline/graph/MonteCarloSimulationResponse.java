package com.factory.productionline.graph;

import java.util.List;

public record MonteCarloSimulationResponse(
        int repetitions,
        List<RouteResult> routes
) {
    public record RouteResult(
            String routeId,
            double expectedFinishTime,
            double finishTimeStandardDeviation,
            List<Double> finishTimes,
            List<BatchResult> batches
    ) {
    }

    public record BatchResult(
            String batchId,
            double expectedFinishBatchTime,
            double finishBatchTimeStandardDeviation,
            List<Double> finishBatchTimes
    ) {
    }
}
