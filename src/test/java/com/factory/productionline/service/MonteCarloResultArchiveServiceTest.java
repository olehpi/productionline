package com.factory.productionline.service;

import com.factory.productionline.graph.MonteCarloSimulationResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MonteCarloResultArchiveServiceTest {

    private final MonteCarloResultArchiveService service = new MonteCarloResultArchiveService();

    @Test
    void toFinishTimesCsvMovesRouteFinishTimesIntoRouteColumns() {
        MonteCarloSimulationResponse response = new MonteCarloSimulationResponse(
                2,
                List.of(
                        route("route1", List.of(0.4736556138, 0.4268251745)),
                        route("route2", List.of(0.5736556138, 0.5268251745))
                )
        );

        assertThat(service.toFinishTimesCsv(response)).isEqualTo("""
                id;route1;route2
                1;0,4736556;0,5736556
                2;0,4268252;0,5268252
                """);
    }

    @Test
    void toSummaryOmitsRouteFinishTimes() {
        MonteCarloSimulationResponse response = new MonteCarloSimulationResponse(
                2,
                List.of(route("route1", List.of(0.4736556138, 0.4268251745)))
        );

        var summary = service.toSummary(response);

        assertThat(summary.repetitions()).isEqualTo(2);
        assertThat(summary.routes()).hasSize(1);
        assertThat(summary.routes().get(0).routeId()).isEqualTo("route1");
    }

    private MonteCarloSimulationResponse.RouteResult route(String routeId, List<Double> finishTimes) {
        return new MonteCarloSimulationResponse.RouteResult(
                routeId,
                0.5,
                0.1,
                finishTimes,
                List.of(new MonteCarloSimulationResponse.BatchResult(
                        "batch1",
                        10.0,
                        1.0,
                        List.of(10.0, 12.0)
                ))
        );
    }
}
