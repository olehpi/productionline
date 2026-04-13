package com.factory.productionline.service;

import com.factory.productionline.model.DistributedBatchResult;
import com.factory.productionline.model.DistributedOperationEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Properties;
import java.util.function.Predicate;

@Service
@ConditionalOnProperty(name = "simulation.kafka.enabled", havingValue = "true")
public class DistributedTelemetryQueryService {

    private final DistributedRouteRegistry distributedRouteRegistry;
    private final KafkaAdmin kafkaAdmin;
    private final ObjectMapper objectMapper;
    private final long pollTimeoutMillis;
    private final long overallTimeoutMillis;

    public DistributedTelemetryQueryService(
            DistributedRouteRegistry distributedRouteRegistry,
            KafkaAdmin kafkaAdmin,
            ObjectMapper objectMapper,
            @Value("${simulation.kafka.telemetry-query-poll-timeout-ms:250}") long pollTimeoutMillis,
            @Value("${simulation.kafka.telemetry-query-timeout-ms:5000}") long overallTimeoutMillis
    ) {
        this.distributedRouteRegistry = distributedRouteRegistry;
        this.kafkaAdmin = kafkaAdmin;
        this.objectMapper = objectMapper;
        this.pollTimeoutMillis = pollTimeoutMillis;
        this.overallTimeoutMillis = overallTimeoutMillis;
    }

    public DistributedBatchResult getRouteResult(String routeId) {
        distributedRouteRegistry.ensureRouteRegistered(routeId);
        return readTelemetry(
                routeId,
                routeId,
                event -> routeId.equals(event.routeId()),
                "No telemetry found for routeId=" + routeId
        );
    }

    public DistributedBatchResult getBatchResult(String routeId, String batchId) {
        distributedRouteRegistry.ensureBatchBoundToRoute(routeId, batchId);
        return readTelemetry(
                routeId,
                batchId,
                event -> routeId.equals(event.routeId()) && batchId.equals(event.batchId()),
                "No telemetry found for routeId=" + routeId + ", batchId=" + batchId
        );
    }

    public DistributedBatchResult getPartResult(String routeId, String batchId, int partNumber) {
        distributedRouteRegistry.ensureBatchBoundToRoute(routeId, batchId);
        return readTelemetry(
                routeId,
                batchId,
                event -> routeId.equals(event.routeId())
                        && batchId.equals(event.batchId())
                        && partNumber == event.partNumber(),
                "No telemetry found for routeId=" + routeId + ", batchId=" + batchId + ", partNumber=" + partNumber
        );
    }

    private DistributedBatchResult readTelemetry(
            String routeId,
            String consumerToken,
            Predicate<DistributedOperationEvent> filter,
            String emptyResultMessage
    ) {
        try (KafkaConsumer<String, String> consumer = createConsumer(consumerToken)) {
            String operationEventsTopic = DistributedSimulationTopics.operationEventsTopic(routeId);
            List<PartitionInfo> partitionInfos = consumer.partitionsFor(operationEventsTopic);
            if (partitionInfos.isEmpty()) {
                throw new IllegalStateException("Telemetry topic is not available: " + operationEventsTopic);
            }

            List<TopicPartition> partitions = partitionInfos.stream()
                    .map(info -> new TopicPartition(info.topic(), info.partition()))
                    .toList();
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions);
            Map<Integer, Map<String, DistributedOperationEvent>> eventsByOperation = new HashMap<>();

            while (!reachedEnd(consumer, partitions, endOffsets)) {
                var records = consumer.poll(Duration.ofMillis(pollTimeoutMillis));
                for (ConsumerRecord<String, String> record : records) {
                    DistributedOperationEvent event = deserialize(record.value());
                    if (!filter.test(event)) {
                        continue;
                    }
                    eventsByOperation
                            .computeIfAbsent(event.operationId(), ignored -> new HashMap<>())
                            .put(event.batchId() + "-" + event.repetition() + "-" + event.partNumber(), event);
                }
            }

            if (eventsByOperation.isEmpty()) {
                throw new IllegalStateException(emptyResultMessage);
            }

            return toResult(eventsByOperation);
        }
    }

    private boolean reachedEnd(
            KafkaConsumer<String, String> consumer,
            List<TopicPartition> partitions,
            Map<TopicPartition, Long> endOffsets
    ) {
        Set<TopicPartition> assignment = consumer.assignment();
        return partitions.stream()
                .filter(assignment::contains)
                .allMatch(partition -> consumer.position(partition) >= endOffsets.getOrDefault(partition, 0L));
    }

    private DistributedBatchResult toResult(Map<Integer, Map<String, DistributedOperationEvent>> eventsByOperation) {
        int maxOperationId = eventsByOperation.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<DistributedBatchResult.OperationTimeline> timelines = new ArrayList<>();
        List<DistributedBatchResult.KafkaTransferMessage> kafkaMessages = new ArrayList<>();
        double finalTau = 0d;

        eventsByOperation.getOrDefault(0, Map.of()).values().stream()
                .sorted(Comparator.comparing(DistributedOperationEvent::batchId)
                        .thenComparingInt(DistributedOperationEvent::repetition)
                        .thenComparingInt(DistributedOperationEvent::partNumber))
                .map(event -> new DistributedBatchResult.KafkaTransferMessage(
                        event.operationId(),
                        event.nextOperationId(),
                        event.partNumber(),
                        event.batchId(),
                        event.repetition(),
                        event.startTau(),
                        event.processingTau(),
                        event.finishTau()
                ))
                .forEach(kafkaMessages::add);

        for (int operationId = 1; operationId <= maxOperationId; operationId++) {
            List<DistributedOperationEvent> orderedEvents = eventsByOperation.getOrDefault(operationId, Map.of()).values().stream()
                    .sorted(Comparator.comparing(DistributedOperationEvent::batchId)
                            .thenComparingInt(DistributedOperationEvent::repetition)
                            .thenComparingInt(DistributedOperationEvent::partNumber))
                    .toList();

            timelines.add(new DistributedBatchResult.OperationTimeline(
                    operationId,
                    orderedEvents.stream()
                            .map(event -> new DistributedBatchResult.PartProcessingEvent(
                                    event.partNumber(),
                                    event.batchId(),
                                    event.repetition(),
                                    event.startTau(),
                                    event.processingTau(),
                                    event.finishTau()
                            ))
                            .toList()
            ));

            orderedEvents.stream()
                    .map(event -> new DistributedBatchResult.KafkaTransferMessage(
                            event.operationId(),
                            event.nextOperationId(),
                            event.partNumber(),
                            event.batchId(),
                            event.repetition(),
                            event.startTau(),
                            event.processingTau(),
                            event.finishTau()
                    ))
                    .forEach(kafkaMessages::add);

            if (operationId == maxOperationId) {
                finalTau = orderedEvents.stream().mapToDouble(DistributedOperationEvent::finishTau).max().orElse(finalTau);
            }
        }

        return new DistributedBatchResult(finalTau, timelines, kafkaMessages);
    }

    private KafkaConsumer<String, String> createConsumer(String batchId) {
        Properties properties = new Properties();
        properties.putAll(kafkaAdmin.getConfigurationProperties());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "productionline-telemetry-query-" + sanitize(batchId) + "-" + System.nanoTime());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new KafkaConsumer<>(properties);
    }

    private DistributedOperationEvent deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, DistributedOperationEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize distributed operation event", exception);
        }
    }

    private String sanitize(String value) {
        return value == null ? "batch" : value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
