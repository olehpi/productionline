package com.factory.productionline.service;

import com.factory.productionline.model.DistributedOperationEvent;
import com.factory.productionline.model.DistributedPartMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;

@Component
@ConditionalOnProperty(name = "simulation.distributed.worker.enabled", havingValue = "true")
public class DistributedOperationWorker {

    private static final Logger log = LoggerFactory.getLogger(DistributedOperationWorker.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaAdmin kafkaAdmin;
    private final ObjectMapper objectMapper;
    private final int operationId;
    private final int nextOperationId;
    private final int outputBufferCapacity;
    private final String downstreamGroupId;
    private final long outputBufferPollIntervalMillis;
    private final double tauMean;
    private final double tauSigma;
    private final Random random;
    private final Set<String> seenBatchKeys;
    private final Map<String, Double> batchStartTauByBatchKey;
    private final Map<String, Double> machineBusyUntilByRouteRepetitionKey;
    private double machineBusyUntil;

    @Autowired
    public DistributedOperationWorker(
            KafkaTemplate<String, String> kafkaTemplate,
            KafkaAdmin kafkaAdmin,
            ObjectMapper objectMapper,
            @Value("${simulation.distributed.worker.operation-id}") int operationId,
            @Value("${simulation.distributed.worker.next-operation-id}") int nextOperationId,
            @Value("${simulation.distributed.worker.output-buffer-capacity:-1}") int outputBufferCapacity,
            @Value("${simulation.distributed.worker.downstream-group-id:}") String downstreamGroupId,
            @Value("${simulation.distributed.worker.output-buffer-poll-interval-ms:100}") long outputBufferPollIntervalMillis,
            @Value("${simulation.distributed.worker.tau-mean}") double tauMean,
            @Value("${simulation.distributed.worker.tau-sigma:0}") double tauSigma,
            @Value("${simulation.distributed.worker.random-seed:0}") long randomSeed
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaAdmin = kafkaAdmin;
        this.objectMapper = objectMapper;
        this.operationId = operationId;
        this.nextOperationId = nextOperationId;
        this.outputBufferCapacity = outputBufferCapacity;
        this.downstreamGroupId = downstreamGroupId;
        this.outputBufferPollIntervalMillis = outputBufferPollIntervalMillis;
        this.tauMean = tauMean;
        this.tauSigma = tauSigma;
        this.random = new Random(randomSeed);
        this.seenBatchKeys = new HashSet<>();
        this.batchStartTauByBatchKey = new HashMap<>();
        this.machineBusyUntilByRouteRepetitionKey = new HashMap<>();
        this.machineBusyUntil = 0d;
    }

    DistributedOperationWorker(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            int operationId,
            int nextOperationId,
            double tauMean,
            double tauSigma,
            long randomSeed
    ) {
        this(kafkaTemplate, null, objectMapper, operationId, nextOperationId, -1, "", 100L, tauMean, tauSigma, randomSeed);
    }

    int outputBufferCapacity() {
        return outputBufferCapacity;
    }

    @KafkaListener(
            topics = "${simulation.distributed.worker.inbound-topic}",
            groupId = "${simulation.distributed.worker.group-id}"
    )
    public void process(String payload) {
        DistributedPartMessage incoming = deserialize(payload);
        DistributedPartMessage outgoing = processMessage(incoming);
        String outputTopic = DistributedSimulationTopics.operationTopic(outgoing.routeId(), operationId, nextOperationId);
        awaitOutputBufferSpace(outputTopic);
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

    private void awaitOutputBufferSpace(String outputTopic) {
        if (outputBufferCapacity <= 0) {
            return;
        }
        if (downstreamGroupId == null || downstreamGroupId.isBlank()) {
            throw new IllegalStateException("downstreamGroupId is required when outputBufferCapacity is limited");
        }
        if (kafkaAdmin == null) {
            throw new IllegalStateException("KafkaAdmin is required when outputBufferCapacity is limited");
        }

        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            while (outputBufferLag(adminClient, outputTopic) >= outputBufferCapacity) {
                sleep(outputBufferPollIntervalMillis);
            }
        }
    }

    private long outputBufferLag(AdminClient adminClient, String outputTopic) {
        try {
            Collection<TopicPartition> partitions = adminClient.describeTopics(Set.of(outputTopic))
                    .allTopicNames()
                    .get()
                    .get(outputTopic)
                    .partitions()
                    .stream()
                    .map(partition -> new TopicPartition(outputTopic, partition.partition()))
                    .toList();

            Map<TopicPartition, OffsetAndMetadata> committedOffsets = adminClient
                    .listConsumerGroupOffsets(downstreamGroupId)
                    .partitionsToOffsetAndMetadata()
                    .get();
            Map<TopicPartition, OffsetSpec> latestOffsetSpecs = new HashMap<>();
            for (TopicPartition partition : partitions) {
                latestOffsetSpecs.put(partition, OffsetSpec.latest());
            }
            Map<TopicPartition, ListOffsetsResultInfo> latestOffsets = adminClient
                    .listOffsets(latestOffsetSpecs)
                    .all()
                    .get();

            long lag = 0L;
            for (TopicPartition partition : partitions) {
                long endOffset = latestOffsets.get(partition).offset();
                OffsetAndMetadata committedOffset = committedOffsets.get(partition);
                long consumedOffset = committedOffset == null ? 0L : committedOffset.offset();
                lag += Math.max(0L, endOffset - consumedOffset);
            }
            return lag;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while checking output buffer lag", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Failed to check output buffer lag for topic " + outputTopic, exception);
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for output buffer space", exception);
        }
    }
}
