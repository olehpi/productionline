package com.factory.productionline.service;

import com.factory.productionline.graph.MonteCarloSimulationResponse;
import com.factory.productionline.graph.MonteCarloSimulationSummaryResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class MonteCarloResultArchiveService {

    public String toFinishTimesCsv(MonteCarloSimulationResponse response) {
        int rowsCount = response.routes().stream()
                .map(MonteCarloSimulationResponse.RouteResult::finishTimes)
                .mapToInt(List::size)
                .max()
                .orElse(0);

        StringBuilder csv = new StringBuilder("id");
        for (MonteCarloSimulationResponse.RouteResult route : response.routes()) {
            csv.append(";").append(route.routeId());
        }
        csv.append("\n");

        for (int rowIndex = 0; rowIndex < rowsCount; rowIndex++) {
            csv.append(rowIndex + 1);
            for (MonteCarloSimulationResponse.RouteResult route : response.routes()) {
                csv.append(";");
                if (rowIndex < route.finishTimes().size()) {
                    csv.append(format(route.finishTimes().get(rowIndex)));
                }
            }
            csv.append("\n");
        }

        return csv.toString();
    }

    public MonteCarloSimulationSummaryResponse toSummary(MonteCarloSimulationResponse response) {
        return new MonteCarloSimulationSummaryResponse(
                response.repetitions(),
                response.routes().stream()
                        .map(route -> new MonteCarloSimulationSummaryResponse.RouteResult(
                                route.routeId(),
                                route.expectedFinishTime(),
                                route.finishTimeStandardDeviation(),
                                route.batches()
                        ))
                        .toList()
        );
    }

    private String format(double value) {
        return String.format(Locale.US, "%.7f", value).replace(".", ",");
    }
}
