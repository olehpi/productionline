package com.factory.productionline.service;

import com.factory.productionline.model.ProductionLine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "simulation.kafka.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpTransferEventPublisher implements TransferEventPublisher {

    @Override
    public void ensureTopics(int operationsCount) {
        // Kafka publishing disabled.
    }

    @Override
    public void publish(ProductionLine.KafkaTransferMessage message) {
        // Kafka publishing disabled.
    }
}
