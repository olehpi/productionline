package com.factory.productionline.service;

import com.factory.productionline.model.DistributedPartMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "simulation.distributed.finish-store.enabled", havingValue = "true")
public class DistributedFinishStoreConsumer {

    private static final Logger log = LoggerFactory.getLogger(DistributedFinishStoreConsumer.class);

    private final ObjectMapper objectMapper;

    public DistributedFinishStoreConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${simulation.distributed.finish-store.topic}",
            groupId = "${simulation.distributed.finish-store.group-id}"
    )
    public void consume(String payload) {
        try {
            DistributedPartMessage message = objectMapper.readValue(payload, DistributedPartMessage.class);
            log.info("finishStore received part {} for batch {} at finishTau={}",
                    message.partNumber(),
                    message.batchId(),
                    message.finishTau());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize finishStore message", exception);
        }
    }
}
