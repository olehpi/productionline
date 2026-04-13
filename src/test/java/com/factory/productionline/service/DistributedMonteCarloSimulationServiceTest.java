package com.factory.productionline.service;

import com.factory.productionline.graph.DistributedSimulationStartResponse;
import com.factory.productionline.graph.MonteCarloSimulationResponse;
import com.factory.productionline.model.ProductionLine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DistributedMonteCarloSimulationServiceTest {

    @Test
    void runCalculatesExpectedValueAndStandardDeviationFromRepeatedRuns() {
        DistributedSimulationLauncher launcher = mock(DistributedSimulationLauncher.class);
        when(launcher.startRepeated(any(), any(Integer.class))).thenReturn(List.of(
                new DistributedSimulationStartResponse("route-42", "batch-42", 1, 3, "start", "finish", 10.0),
                new DistributedSimulationStartResponse("route-42", "batch-42", 2, 3, "start", "finish", 14.0),
                new DistributedSimulationStartResponse("route-42", "batch-42", 3, 3, "start", "finish", 18.0)
        ));

        DistributedMonteCarloSimulationService service = new DistributedMonteCarloSimulationService(launcher);

        MonteCarloSimulationResponse.BatchResult response = service.runBatch(input(), 3);

        assertThat(response.batchId()).isEqualTo("batch-42");
        assertThat(response.expectedFinishBatchTime()).isEqualTo(14.0);
        assertThat(response.finishBatchTimeStandardDeviation()).isEqualTo(Math.sqrt(32.0 / 3.0));
        assertThat(response.finishBatchTimes()).containsExactly(10.0, 14.0, 18.0);
    }

    @Test
    void runRouteUsesLastBatchAsRouteFinishAndKeepsPerBatchResults() {
        DistributedSimulationLauncher launcher = mock(DistributedSimulationLauncher.class);
        when(launcher.startRouteRepeated(List.of(input("batch-1"), input("batch-2")), 2)).thenReturn(List.of(
                List.of(
                        new DistributedSimulationStartResponse("route-42", "batch-1", 1, 3, "start", "finish", 10.0),
                        new DistributedSimulationStartResponse("route-42", "batch-1", 2, 3, "start", "finish", 12.0)
                ),
                List.of(
                        new DistributedSimulationStartResponse("route-42", "batch-2", 1, 3, "start", "finish", 20.0),
                        new DistributedSimulationStartResponse("route-42", "batch-2", 2, 3, "start", "finish", 24.0)
                )
        ));

        DistributedMonteCarloSimulationService service = new DistributedMonteCarloSimulationService(launcher);

        MonteCarloSimulationResponse.RouteResult response = service.runRoute(
                "route-42",
                List.of(input("batch-1"), input("batch-2")),
                2
        );

        assertThat(response.routeId()).isEqualTo("route-42");
        assertThat(response.expectedFinishTime()).isEqualTo(22.0 / 6.0);
        assertThat(response.finishTimeStandardDeviation()).isEqualTo(2.0 / 6.0);
        assertThat(response.finishTimes()).containsExactly(20.0 / 6.0, 24.0 / 6.0);
        assertThat(response.batches()).hasSize(2);
        assertThat(response.batches().get(0).batchId()).isEqualTo("batch-1");
        assertThat(response.batches().get(0).finishBatchTimes()).containsExactly(10.0, 12.0);
        assertThat(response.batches().get(1).batchId()).isEqualTo("batch-2");
        assertThat(response.batches().get(1).finishBatchTimes()).containsExactly(20.0, 24.0);
    }

    private ProductionLine.LinearSimulationInput input() {
        return input("batch-42");
    }

    private ProductionLine.LinearSimulationInput input(String batchId) {
        return new ProductionLine.LinearSimulationInput(
                "route-42",
                3,
                2,
                batchId,
                0.0,
                30.0,
                List.of(
                        new ProductionLine.LinearOperationInput(0, "startStore", 0.0, 0.0, 0L),
                        new ProductionLine.LinearOperationInput(1, "Op01", 10.0, 1.0, 1L),
                        new ProductionLine.LinearOperationInput(2, "Op02", 10.0, 1.0, 2L),
                        new ProductionLine.LinearOperationInput(3, "finishStore", 0.0, 0.0, 0L)
                )
        );
    }
}
