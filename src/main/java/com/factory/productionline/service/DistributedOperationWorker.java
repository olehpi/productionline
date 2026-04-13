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
    private final Set<String> seenBatchKeys;
    private final Map<String, Double> batchStartTauByBatchKey;
    private final Map<String, Double> machineBusyUntilByRouteRepetitionKey;
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
        this.seenBatchKeys = new HashSet<>();
        this.batchStartTauByBatchKey = new HashMap<>();
        this.machineBusyUntilByRouteRepetitionKey = new HashMap<>();
        this.machineBusyUntil = 0d;
    }

    @KafkaListener(
            topics = "${simulation.distributed.worker.inbound-topic}",
            groupId = "${simulation.distributed.worker.group-id}"
    )
    public void process(String payload) {
        DistributedPartMessage incoming = deserialize(payload);
        DistributedPartMessage outgoing = processMessage(incoming);
        String outputTopic = DistributedSimulationTopics.operationTopic(outgoing.routeId(), operationId, nextOperationId);
        kafkaTemplate.send(
                outputTopic,
                outgoing.routeId() + "-" + outgoing.batchId() + "-" + outgoing.repetition() + "-" + outgoing.partNumber(),
                serialize(outgoing)
        );
        kafkaTemplate.send(
                DistributedSimulationTopics.operationEventsTopic(outgoing.routeId()),
                outgoing.routeId() + "-" + outgoing.batchId() + "-" + outgoing.repetition() + "-" + operationId + "-" + outgoing.partNumber(),
                serialize(new DistributedOperationEvent(
                        outgoing.routeId(),
                        operationId,
                        nextOperationId,
                        outgoing.partNumber(),
                        outgoing.batchId(),
                        outgoing.repetition(),
                        outgoing.startTau(),
                        outgoing.processingTau(),
                        outgoing.finishTau()
                ))
        );

        log.info("Operation {} processed part {} for route {} batch {} repetition {}. startTau={}, finishTau={}, outputTopic={}",
                operationId,
                outgoing.partNumber(),
                outgoing.routeId(),
                outgoing.batchId(),
                outgoing.repetition(),
                outgoing.startTau(),
                outgoing.finishTau(),
                outputTopic);
    }

    synchronized DistributedPartMessage processMessage(DistributedPartMessage incoming) {
        if (incoming.repetition() > 0) {
            return processMonteCarloMessage(incoming);
        }

        String batchKey = batchKey(incoming);
        if (incoming.partNumber() == 1 && seenBatchKeys.contains(batchKey)) {
            machineBusyUntil = batchStartTauByBatchKey.getOrDefault(batchKey, incoming.finishTau());
        }
        if (incoming.partNumber() == 1) {
            batchStartTauByBatchKey.putIfAbsent(batchKey, machineBusyUntil);
            seenBatchKeys.add(batchKey);
        }

        double startTau = Math.max(incoming.finishTau(), machineBusyUntil);
        double processingTau = sampleNormalBounded();
        double finishTau = startTau + processingTau;
        machineBusyUntil = finishTau;

        return new DistributedPartMessage(
                incoming.routeId(),
                incoming.partNumber(),
                incoming.batchId(),
                incoming.repetition(),
                startTau,
                processingTau,
                finishTau
        );
    }

    private DistributedPartMessage processMonteCarloMessage(DistributedPartMessage incoming) {
        String routeRepetitionKey = routeRepetitionKey(incoming);
        double routeMachineBusyUntil = machineBusyUntilByRouteRepetitionKey.getOrDefault(routeRepetitionKey, 0d);
        double startTau = Math.max(incoming.finishTau(), routeMachineBusyUntil);
        double processingTau = sampleNormalBounded();
        double finishTau = startTau + processingTau;
        machineBusyUntilByRouteRepetitionKey.put(routeRepetitionKey, finishTau);

        return new DistributedPartMessage(
                incoming.routeId(),
                incoming.partNumber(),
                incoming.batchId(),
                incoming.repetition(),
                startTau,
                processingTau,
                finishTau
        );
    }

    private String batchKey(DistributedPartMessage incoming) {
        return incoming.routeId() + "-" + incoming.batchId() + "-" + incoming.repetition();
    }

    private String routeRepetitionKey(DistributedPartMessage incoming) {
        return incoming.routeId() + "-" + incoming.repetition();
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
