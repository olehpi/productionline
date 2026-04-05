package com.factory.productionline.service;

import com.factory.productionline.model.DistributedPartMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Random;

@Component
@ConditionalOnProperty(name = "simulation.distributed.worker.enabled", havingValue = "true")
public class DistributedOperationWorker {

    private static final Logger log = LoggerFactory.getLogger(DistributedOperationWorker.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final int operationId;
    private final int nextOperationId;
    private final double tauMean;
    private final double tauSigma;
    private final Random random;

    public DistributedOperationWorker(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${simulation.distributed.worker.operation-id}") int operationId,
            @Value("${simulation.distributed.worker.next-operation-id}") int nextOperationId,
            @Value("${simulation.distributed.worker.tau-mean}") double tauMean,
            @Value("${simulation.distributed.worker.tau-sigma:0}") double tauSigma,
            @Value("${simulation.distributed.worker.random-seed:0}") long randomSeed
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.operationId = operationId;
        this.nextOperationId = nextOperationId;
        this.tauMean = tauMean;
        this.tauSigma = tauSigma;
        this.random = new Random(randomSeed);
    }

    @KafkaListener(
            topics = "${simulation.distributed.worker.inbound-topic}",
            groupId = "${simulation.distributed.worker.group-id}"
    )
    public void process(String payload) {
        DistributedPartMessage incoming = deserialize(payload);

        double startTau = incoming.finishTau();
        double processingTau = sampleNormalBounded();
        double finishTau = startTau + processingTau;

        DistributedPartMessage outgoing = new DistributedPartMessage(
                incoming.partNumber(),
                incoming.batchId(),
                startTau,
                processingTau,
                finishTau
        );

        String outputTopic = "line-op-" + operationId + "-to-" + nextOperationId;
        kafkaTemplate.send(outputTopic, outgoing.batchId() + "-" + outgoing.partNumber(), serialize(outgoing));

        log.info("Operation {} processed part {} for batch {}. startTau={}, finishTau={}, outputTopic={}",
                operationId,
                outgoing.partNumber(),
                outgoing.batchId(),
                outgoing.startTau(),
                outgoing.finishTau(),
                outputTopic);
    }

    private double sampleNormalBounded() {
        if (tauSigma == 0d) {
            return tauMean;
        }
        return Math.max(0.000001d, tauMean + random.nextGaussian() * tauSigma);
    }

    private DistributedPartMessage deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, DistributedPartMessage.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize distributed part message", exception);
        }
    }

    private String serialize(DistributedPartMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize distributed part message", exception);
        }
    }
}
