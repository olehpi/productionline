package com.factory.productionline.service;

import com.factory.productionline.model.DistributedBatchResult;
import com.factory.productionline.model.DistributedOperationEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistributedTelemetryQueryServiceTest {

    @Test
    void toResultIncludesStartAndFinishTransfers() {
        DistributedTelemetryQueryService service = new DistributedTelemetryQueryService(new DistributedRouteRegistry(), null, new ObjectMapper(), 250, 5000);

        Map<Integer, Map<String, DistributedOperationEvent>> eventsByOperation = Map.of(
                0, Map.of(
                        "batch-60-1", new DistributedOperationEvent("route-60", 0, 1, 1, "batch-60", 0d, 0d, 0d),
                        "batch-60-2", new DistributedOperationEvent("route-60", 0, 1, 2, "batch-60", 0d, 0d, 0d)
                ),
                1, Map.of(
                        "batch-60-1", new DistributedOperationEvent("route-60", 1, 2, 1, "batch-60", 2d, 3d, 5d),
                        "batch-60-2", new DistributedOperationEvent("route-60", 1, 2, 2, "batch-60", 5d, 3d, 8d)
                ),
                2, Map.of(
                        "batch-60-1", new DistributedOperationEvent("route-60", 2, 3, 1, "batch-60", 5d, 4d, 9d),
                        "batch-60-2", new DistributedOperationEvent("route-60", 2, 3, 2, "batch-60", 9d, 4d, 13d)
                ),
                7, Map.of(
                        "batch-60-1", new DistributedOperationEvent("route-60", 7, 8, 1, "batch-60", 21d, 2d, 23d),
                        "batch-60-2", new DistributedOperationEvent("route-60", 7, 8, 2, "batch-60", 23d, 2d, 25d)
                )
        );

        DistributedBatchResult result = ReflectionTestUtils.invokeMethod(service, "toResult", eventsByOperation);

        assertEquals(25d, result.finalTau());
        assertTrue(result.kafkaMessages().stream().anyMatch(message ->
                message.fromOperation() == 0
                        && message.toOperation() == 1
                        && message.partNumber() == 1
                        && message.availableAtTau() == 0d
                        && message.processingTau() == 0d
        ));
        assertTrue(result.kafkaMessages().stream().anyMatch(message ->
                message.fromOperation() == 7
                        && message.toOperation() == 8
                        && message.partNumber() == 2
                        && message.availableAtTau() == 25d
        ));
    }
}
