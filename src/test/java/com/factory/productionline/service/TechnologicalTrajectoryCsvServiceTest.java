package com.factory.productionline.service;

import com.factory.productionline.model.DistributedBatchResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TechnologicalTrajectoryCsvServiceTest {

    private final TechnologicalTrajectoryCsvService service = new TechnologicalTrajectoryCsvService();

    @Test
    void toCsvRowsBuildsOperationRowsWithPartColumns() {
        DistributedBatchResult result = new DistributedBatchResult(
                30.0,
                List.of(),
                List.of(
                        new DistributedBatchResult.KafkaTransferMessage(0, 1, 1, "batch-42", 0, 0.0, 0.0, 0.0),
                        new DistributedBatchResult.KafkaTransferMessage(0, 1, 2, "batch-42", 0, 0.0, 0.0, 0.0),
                        new DistributedBatchResult.KafkaTransferMessage(1, 2, 1, "batch-42", 0, 0.0, 10.0, 10.0),
                        new DistributedBatchResult.KafkaTransferMessage(1, 2, 2, "batch-42", 0, 10.0, 10.0, 20.0),
                        new DistributedBatchResult.KafkaTransferMessage(2, 3, 1, "batch-42", 0, 10.0, 10.0, 20.0),
                        new DistributedBatchResult.KafkaTransferMessage(2, 3, 2, "batch-42", 0, 20.0, 10.0, 30.0)
                )
        );

        String csv = service.header("batch-42", 2) + service.toCsvRows("route-42", "batch-42", 2, result);

        assertThat(csv).isEqualTo("""
                routeId;batchId;operationId;batch-42-1;batch-42-2
                route-42;batch-42;0;0.0;0.0
                route-42;batch-42;1;10.0;20.0
                route-42;batch-42;2;20.0;30.0
                """);
    }

    @Test
    void toRouteCsvBuildsRouteFileWithRouteBatchPartColumns() {
        DistributedBatchResult result = new DistributedBatchResult(
                30.0,
                List.of(),
                List.of(
                        new DistributedBatchResult.KafkaTransferMessage(0, 1, 1, "batch-42", 0, 0.0, 0.0, 0.0),
                        new DistributedBatchResult.KafkaTransferMessage(0, 1, 2, "batch-42", 0, 0.0, 0.0, 0.0),
                        new DistributedBatchResult.KafkaTransferMessage(1, 2, 1, "batch-42", 0, 0.0, 10.0, 10.0),
                        new DistributedBatchResult.KafkaTransferMessage(1, 2, 2, "batch-42", 0, 10.0, 10.0, 20.0)
                )
        );

        String csv = service.toRouteCsv(
                "route-42",
                List.of(new TechnologicalTrajectoryCsvService.BatchTrajectory("batch-42", 2, result))
        );

        assertThat(csv).doesNotContain("route-42-batch-42");
        assertThat(csv.lines().toList()).containsExactly(
                "operationId;batch-42-1;batch-42-2",
                "          0; 0,0000000; 0,0000000",
                "          1;10,0000000;20,0000000"
        );
    }

    @Test
    void toRouteFullCsvAddsNextOperationStartRowsBetweenTransferRows() {
        DistributedBatchResult result = new DistributedBatchResult(
                30.0,
                List.of(),
                List.of(
                        new DistributedBatchResult.KafkaTransferMessage(0, 1, 1, "batch-42", 0, 0.0, 0.0, 0.0),
                        new DistributedBatchResult.KafkaTransferMessage(0, 1, 2, "batch-42", 0, 0.0, 0.0, 0.0),
                        new DistributedBatchResult.KafkaTransferMessage(1, 2, 1, "batch-42", 0, 0.0, 10.0, 10.0),
                        new DistributedBatchResult.KafkaTransferMessage(1, 2, 2, "batch-42", 0, 10.0, 10.0, 20.0)
                )
        );

        String csv = service.toRouteFullCsv(
                "route-42",
                List.of(new TechnologicalTrajectoryCsvService.BatchTrajectory("batch-42", 2, result))
        );

        assertThat(csv.lines().toList()).containsExactly(
                "operationId;batch-42-1;batch-42-2",
                "          0; 0,0000000; 0,0000000",
                "          0; 0,0000000;10,0000000",
                "          1;10,0000000;20,0000000"
        );
    }

    @Test
    void toRouteCsvAppliesOutputBufferCapacityBlocking() {
        DistributedBatchResult result = new DistributedBatchResult(
                31.0,
                List.of(),
                List.of(
                        new DistributedBatchResult.KafkaTransferMessage(0, 1, 1, "batch-42", 0, 0.0, 0.0, 0.0),
                        new DistributedBatchResult.KafkaTransferMessage(0, 1, 2, "batch-42", 0, 0.0, 0.0, 0.0),
                        new DistributedBatchResult.KafkaTransferMessage(0, 1, 3, "batch-42", 0, 0.0, 0.0, 0.0),
                        new DistributedBatchResult.KafkaTransferMessage(1, 2, 1, "batch-42", 0, 0.0, 1.0, 1.0),
                        new DistributedBatchResult.KafkaTransferMessage(1, 2, 2, "batch-42", 0, 1.0, 1.0, 2.0),
                        new DistributedBatchResult.KafkaTransferMessage(1, 2, 3, "batch-42", 0, 2.0, 1.0, 3.0),
                        new DistributedBatchResult.KafkaTransferMessage(2, 3, 1, "batch-42", 0, 1.0, 10.0, 11.0),
                        new DistributedBatchResult.KafkaTransferMessage(2, 3, 2, "batch-42", 0, 11.0, 10.0, 21.0),
                        new DistributedBatchResult.KafkaTransferMessage(2, 3, 3, "batch-42", 0, 21.0, 10.0, 31.0)
                )
        );

        String csv = service.toRouteCsv(
                "route-42",
                List.of(new TechnologicalTrajectoryCsvService.BatchTrajectory("batch-42", 3, result)),
                Map.of(1, 1)
        );

        assertThat(csv.lines().toList()).containsExactly(
                "operationId;batch-42-1;batch-42-2;batch-42-3",
                "          0; 0,0000000; 0,0000000; 0,0000000",
                "          1; 1,0000000; 2,0000000;11,0000000",
                "          2;11,0000000;21,0000000;31,0000000"
        );
    }

    @Test
    void toRouteBunkersCsvBuildsChangingBunkerLoadThroughTime() {
        DistributedBatchResult result = new DistributedBatchResult(
                31.0,
                List.of(),
                List.of(
                        new DistributedBatchResult.KafkaTransferMessage(0, 1, 1, "batch-42", 0, 0.0, 0.0, 0.0),
                        new DistributedBatchResult.KafkaTransferMessage(0, 1, 2, "batch-42", 0, 0.0, 0.0, 0.0),
                        new DistributedBatchResult.KafkaTransferMessage(0, 1, 3, "batch-42", 0, 0.0, 0.0, 0.0),
                        new DistributedBatchResult.KafkaTransferMessage(1, 2, 1, "batch-42", 0, 0.0, 1.0, 1.0),
                        new DistributedBatchResult.KafkaTransferMessage(1, 2, 2, "batch-42", 0, 1.0, 1.0, 2.0),
                        new DistributedBatchResult.KafkaTransferMessage(1, 2, 3, "batch-42", 0, 2.0, 1.0, 3.0),
                        new DistributedBatchResult.KafkaTransferMessage(2, 3, 1, "batch-42", 0, 1.0, 10.0, 11.0),
                        new DistributedBatchResult.KafkaTransferMessage(2, 3, 2, "batch-42", 0, 11.0, 10.0, 21.0),
                        new DistributedBatchResult.KafkaTransferMessage(2, 3, 3, "batch-42", 0, 21.0, 10.0, 31.0)
                )
        );

        String csv = service.toRouteBunkersCsv(
                "route-42",
                List.of(new TechnologicalTrajectoryCsvService.BatchTrajectory("batch-42", 3, result)),
                Map.of(1, 1)
        );

        List<String> normalizedLines = csv.lines()
                .map(line -> java.util.Arrays.stream(line.split(";"))
                        .map(String::trim)
                        .collect(Collectors.joining(";")))
                .toList();

        assertThat(normalizedLines).containsExactly(
                "tau;bunker-0;bunker-1",
                "0,0000000;0;0",
                "1,0000000;1;0",
                "2,0000000;1;0",
                "11,0000000;0;1",
                "21,0000000;0;1"
        );
    }
}
