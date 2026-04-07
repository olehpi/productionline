package com.factory.productionline.service;

import com.factory.productionline.model.DistributedOperationEvent;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

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
    private final Set<String> seenBatchIds;
    private final Map<String, Double> batchStartTauByBatchId;
    private double machineBusyUntil;

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
        this.seenBatchIds = new HashSet<>();
        this.batchStartTauByBatchId = new HashMap<>();
        this.machineBusyUntil = 0d;
    }

    @KafkaListener(
            topics = "${simulation.distributed.worker.inbound-topic}",
            groupId = "${simulation.distributed.worker.group-id}"
    )
    public void process(String payload) {
        DistributedPartMessage incoming = deserialize(payload);
        DistributedPartMessage outgoing = processMessage(incoming);
        String outputTopic = "line-op-" + operationId + "-to-" + nextOperationId;
        kafkaTemplate.send(outputTopic, outgoing.batchId() + "-" + outgoing.partNumber(), serialize(outgoing));
        kafkaTemplate.send(
                DistributedSimulationTopics.OPERATION_EVENTS_TOPIC,
                outgoing.batchId() + "-" + operationId + "-" + outgoing.partNumber(),
                serialize(new DistributedOperationEvent(
                        operationId,
                        nextOperationId,
                        outgoing.partNumber(),
                        outgoing.batchId(),
                        outgoing.startTau(),
                        outgoing.processingTau(),
                        outgoing.finishTau()
                ))
        );

        log.info("Operation {} processed part {} for batch {}. startTau={}, finishTau={}, outputTopic={}",
                operationId,
                outgoing.partNumber(),
                outgoing.batchId(),
                outgoing.startTau(),
                outgoing.finishTau(),
                outputTopic);
    }

    synchronized DistributedPartMessage processMessage(DistributedPartMessage incoming) {
        String batchId = incoming.batchId();
        if (incoming.partNumber() == 1 && seenBatchIds.contains(batchId)) {
            machineBusyUntil = batchStartTauByBatchId.getOrDefault(batchId, incoming.finishTau());
        }
        if (incoming.partNumber() == 1) {
            batchStartTauByBatchId.putIfAbsent(batchId, machineBusyUntil);
            seenBatchIds.add(batchId);
        }

        double startTau = Math.max(incoming.finishTau(), machineBusyUntil);
        double processingTau = sampleNormalBounded();
        double finishTau = startTau + processingTau;
        machineBusyUntil = finishTau;

        return new DistributedPartMessage(
                incoming.partNumber(),
                incoming.batchId(),
                startTau,
                processingTau,
                finishTau
        );
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

    private String serialize(Object message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize distributed part message", exception);
        }
    }
}
