package com.factory.productionline.model;

import java.util.List;

public record DistributedBatchResult(
        double finalTau,
        List<OperationTimeline> operationTimelines,
        List<KafkaTransferMessage> kafkaMessages
) {
    public record OperationTimeline(
            int operationNumber,
            List<PartProcessingEvent> events
    ) {
    }

    public record PartProcessingEvent(
            int partNumber,
            String batchId,
            double startTau,
            double processingTau,
            double finishTau
    ) {
    }

    public record KafkaTransferMessage(
            int fromOperation,
            int toOperation,
            int partNumber,
            String batchId,
            double processingStartTau,
            double processingTau,
            double availableAtTau
    ) {
    }
}
