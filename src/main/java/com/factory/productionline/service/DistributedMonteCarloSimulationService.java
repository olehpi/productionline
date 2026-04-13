package com.factory.productionline.service;

import com.factory.productionline.graph.DistributedSimulationStartResponse;
import com.factory.productionline.graph.MonteCarloSimulationResponse;
import com.factory.productionline.model.ProductionLine;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DistributedMonteCarloSimulationService {

    private final DistributedSimulationLauncher distributedSimulationLauncher;

    public DistributedMonteCarloSimulationService(DistributedSimulationLauncher distributedSimulationLauncher) {
        this.distributedSimulationLauncher = distributedSimulationLauncher;
    }

    public MonteCarloSimulationResponse.RouteResult runRoute(
            String routeId,
            List<ProductionLine.LinearSimulationInput> batches,
            int repetitions
    ) {
        List<List<DistributedSimulationStartResponse>> routeResponses = distributedSimulationLauncher.startRouteRepeated(
                batches,
                repetitions
        );

        List<MonteCarloSimulationResponse.BatchResult> batchResults = java.util.stream.IntStream.range(0, batches.size())
                .mapToObj(batchIndex -> toBatchResult(
                        batches.get(batchIndex),
                        routeResponses.get(batchIndex).stream()
                                .map(DistributedSimulationStartResponse::finishBatchTime)
                                .toList()
                ))
                .toList();

        double totalPartsCount = batches.stream()
                .mapToInt(ProductionLine.LinearSimulationInput::partsCount)
                .sum();
        MonteCarloSimulationResponse.BatchResult lastBatchResult = batchResults.get(batchResults.size() - 1);
        List<Double> finishTimesPerPart = lastBatchResult.finishBatchTimes().stream()
                .map(finishTime -> finishTime / totalPartsCount)
                .toList();
        double expectedFinishTimePerPart = lastBatchResult.expectedFinishBatchTime() / totalPartsCount;
        double finishTimeStandardDeviationPerPart = lastBatchResult.finishBatchTimeStandardDeviation() / totalPartsCount;

        return new MonteCarloSimulationResponse.RouteResult(
                routeId,
                expectedFinishTimePerPart,
                finishTimeStandardDeviationPerPart,
                finishTimesPerPart,
                List.copyOf(batchResults)
        );
    }

    public MonteCarloSimulationResponse.BatchResult runBatch(ProductionLine.LinearSimulationInput input, int repetitions) {
        List<Double> finishBatchTimes = distributedSimulationLauncher.startRepeated(input, repetitions).stream()
                .map(DistributedSimulationStartResponse::finishBatchTime)
                .toList();

        return toBatchResult(input, finishBatchTimes);
    }

    private MonteCarloSimulationResponse.BatchResult toBatchResult(
            ProductionLine.LinearSimulationInput input,
            List<Double> finishBatchTimes
    ) {
        double expectedFinishBatchTime = finishBatchTimes.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElseThrow(() -> new IllegalStateException("Monte Carlo simulation did not produce any finishBatchTime values"));

        double variance = finishBatchTimes.stream()
                .mapToDouble(finishBatchTime -> {
                    double deviation = finishBatchTime - expectedFinishBatchTime;
                    return deviation * deviation;
                })
                .average()
                .orElse(0d);

        return new MonteCarloSimulationResponse.BatchResult(
                input.batchId(),
                expectedFinishBatchTime,
                Math.sqrt(variance),
                List.copyOf(finishBatchTimes)
        );
    }
}
