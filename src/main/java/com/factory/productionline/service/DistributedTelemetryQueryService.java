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
import java.util.Properties;

@Service
@ConditionalOnProperty(name = "simulation.kafka.enabled", havingValue = "true")
public class DistributedTelemetryQueryService {

    private final KafkaAdmin kafkaAdmin;
    private final ObjectMapper objectMapper;
    private final long pollTimeoutMillis;
    private final long overallTimeoutMillis;

    public DistributedTelemetryQueryService(
            KafkaAdmin kafkaAdmin,
            ObjectMapper objectMapper,
            @Value("${simulation.kafka.telemetry-query-poll-timeout-ms:250}") long pollTimeoutMillis,
            @Value("${simulation.kafka.telemetry-query-timeout-ms:5000}") long overallTimeoutMillis
    ) {
        this.kafkaAdmin = kafkaAdmin;
        this.objectMapper = objectMapper;
        this.pollTimeoutMillis = pollTimeoutMillis;
        this.overallTimeoutMillis = overallTimeoutMillis;
    }

    public DistributedBatchResult getBatchResult(String batchId) {
        try (KafkaConsumer<String, String> consumer = createConsumer(batchId)) {
            List<PartitionInfo> partitionInfos = consumer.partitionsFor(DistributedSimulationTopics.OPERATION_EVENTS_TOPIC);
            if (partitionInfos.isEmpty()) {
                throw new IllegalStateException("Telemetry topic is not available: " + DistributedSimulationTopics.OPERATION_EVENTS_TOPIC);
            }

            List<TopicPartition> partitions = partitionInfos.stream()
                    .map(info -> new TopicPartition(info.topic(), info.partition()))
                    .toList();
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);

            Instant deadline = Instant.now().plus(Duration.ofMillis(overallTimeoutMillis));
            Map<Integer, Map<Integer, DistributedOperationEvent>> eventsByOperation = new HashMap<>();

            while (Instant.now().isBefore(deadline)) {
                var records = consumer.poll(Duration.ofMillis(pollTimeoutMillis));
                boolean sawAnyForBatch = false;
                for (ConsumerRecord<String, String> record : records) {
                    DistributedOperationEvent event = deserialize(record.value());
                    if (!batchId.equals(event.batchId())) {
                        continue;
                    }
                    sawAnyForBatch = true;
                    eventsByOperation
                            .computeIfAbsent(event.operationId(), ignored -> new HashMap<>())
                            .put(event.partNumber(), event);
                }

                if (isComplete(eventsByOperation)) {
                    return toResult(batchId, eventsByOperation);
                }

                if (!sawAnyForBatch && !eventsByOperation.isEmpty()) {
                    break;
                }
            }

            throw new IllegalStateException("Timed out waiting for distributed batch result for batchId=" + batchId);
        }
    }

    private boolean isComplete(Map<Integer, Map<Integer, DistributedOperationEvent>> eventsByOperation) {
        if (eventsByOperation.isEmpty()) {
            return false;
        }

        int maxOperationId = eventsByOperation.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (maxOperationId == 0) {
            return false;
        }

        int expectedParts = eventsByOperation.getOrDefault(1, Map.of()).size();
        if (expectedParts == 0) {
            return false;
        }

        for (int operationId = 1; operationId <= maxOperationId; operationId++) {
            if (eventsByOperation.getOrDefault(operationId, Map.of()).size() < expectedParts) {
                return false;
            }
        }
        return true;
    }

    private DistributedBatchResult toResult(
            String batchId,
            Map<Integer, Map<Integer, DistributedOperationEvent>> eventsByOperation
    ) {
        int maxOperationId = eventsByOperation.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<DistributedBatchResult.OperationTimeline> timelines = new ArrayList<>();
        List<DistributedBatchResult.KafkaTransferMessage> kafkaMessages = new ArrayList<>();
        double finalTau = 0d;

        eventsByOperation.getOrDefault(0, Map.of()).values().stream()
                .sorted(Comparator.comparingInt(DistributedOperationEvent::partNumber))
                .map(event -> new DistributedBatchResult.KafkaTransferMessage(
                        event.operationId(),
                        event.nextOperationId(),
                        event.partNumber(),
                        batchId,
                        event.startTau(),
                        event.processingTau(),
                        event.finishTau()
                ))
                .forEach(kafkaMessages::add);

        for (int operationId = 1; operationId <= maxOperationId; operationId++) {
            List<DistributedOperationEvent> orderedEvents = eventsByOperation.getOrDefault(operationId, Map.of()).values().stream()
                    .sorted(Comparator.comparingInt(DistributedOperationEvent::partNumber))
                    .toList();

            timelines.add(new DistributedBatchResult.OperationTimeline(
                    operationId,
                    orderedEvents.stream()
                            .map(event -> new DistributedBatchResult.PartProcessingEvent(
                                    event.partNumber(),
                                    batchId,
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
                            batchId,
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
