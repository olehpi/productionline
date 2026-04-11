package com.factory.productionline.service;

import com.factory.productionline.graph.DistributedSimulationStartResponse;
import com.factory.productionline.model.DistributedOperationEvent;
import com.factory.productionline.model.DistributedPartMessage;
import com.factory.productionline.model.ProductionLine;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;

@Component
@ConditionalOnProperty(name = "simulation.kafka.enabled", havingValue = "true")
public class KafkaDistributedSimulationLauncher implements DistributedSimulationLauncher {

    private final DistributedRouteRegistry distributedRouteRegistry;
    private final KafkaAdmin kafkaAdmin;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final long topicsReadyTimeoutMillis;
    private final long topicsReadyPollIntervalMillis;
    private final long batchCompletionTimeoutMillis;
    private final long batchCompletionPollIntervalMillis;

    public KafkaDistributedSimulationLauncher(
            DistributedRouteRegistry distributedRouteRegistry,
            KafkaAdmin kafkaAdmin,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${simulation.kafka.topics.ready-timeout-ms:30000}") long topicsReadyTimeoutMillis,
            @Value("${simulation.kafka.topics.ready-poll-interval-ms:250}") long topicsReadyPollIntervalMillis,
            @Value("${simulation.kafka.batch-completion-timeout-ms:120000}") long batchCompletionTimeoutMillis,
            @Value("${simulation.kafka.batch-completion-poll-interval-ms:250}") long batchCompletionPollIntervalMillis
    ) {
        this.distributedRouteRegistry = distributedRouteRegistry;
        this.kafkaAdmin = kafkaAdmin;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topicsReadyTimeoutMillis = topicsReadyTimeoutMillis;
        this.topicsReadyPollIntervalMillis = topicsReadyPollIntervalMillis;
        this.batchCompletionTimeoutMillis = batchCompletionTimeoutMillis;
        this.batchCompletionPollIntervalMillis = batchCompletionPollIntervalMillis;
    }

    @Override
    public DistributedSimulationStartResponse start(ProductionLine.LinearSimulationInput input) {
        validate(input);
        distributedRouteRegistry.ensureRouteMatches(input);
        distributedRouteRegistry.bindBatchToRoute(input.routeId(), input.batchId());

        ensurePipelineTopics(input.routeId(), input.operationsCount());

        String startTopic = DistributedSimulationTopics.operationTopic(input.routeId(), 0, 1);
        String finishTopic = DistributedSimulationTopics.operationTopic(
                input.routeId(),
                input.operationsCount(),
                input.operationsCount() + 1
        );
        double finishBatchTime = awaitBatchCompletion(input, startTopic, finishTopic);

        return new DistributedSimulationStartResponse(
                input.routeId(),
                input.batchId(),
                input.partsCount(),
                startTopic,
                finishTopic,
                finishBatchTime
        );
    }

    private double awaitBatchCompletion(
            ProductionLine.LinearSimulationInput input,
            String startTopic,
            String finishTopic
    ) {
        try (KafkaConsumer<String, String> consumer = createFinishTopicConsumer(input.batchId(), finishTopic)) {
            List<TopicPartition> partitions = consumer.partitionsFor(finishTopic).stream()
                    .map(partitionInfo -> new TopicPartition(partitionInfo.topic(), partitionInfo.partition()))
                    .toList();
            if (partitions.isEmpty()) {
                throw new IllegalStateException("No partitions available for finish topic: " + finishTopic);
            }

            consumer.assign(partitions);
            consumer.seekToEnd(partitions);

            for (int partNumber = 1; partNumber <= input.partsCount(); partNumber++) {
                DistributedPartMessage message = new DistributedPartMessage(
                        input.routeId(),
                        partNumber,
                        input.batchId(),
                        input.startTau(),
                        0d,
                        input.startTau()
                );
                publish(startTopic, message);
                publishOperationEvent(new DistributedOperationEvent(
                        input.routeId(),
                        0,
                        1,
                        partNumber,
                        input.batchId(),
                        input.startTau(),
                        0d,
                        input.startTau()
                ));
            }
            kafkaTemplate.flush();

            return waitForFinishedParts(consumer, input, finishTopic);
        }
    }

    private double waitForFinishedParts(
            KafkaConsumer<String, String> consumer,
            ProductionLine.LinearSimulationInput input,
            String finishTopic
    ) {
        Instant deadline = Instant.now().plus(Duration.ofMillis(batchCompletionTimeoutMillis));
        Set<Integer> completedPartNumbers = new HashSet<>();
        double finishBatchTime = input.startTau();

        while (Instant.now().isBefore(deadline) && completedPartNumbers.size() < input.partsCount()) {
            var records = consumer.poll(Duration.ofMillis(batchCompletionPollIntervalMillis));
            for (ConsumerRecord<String, String> record : records) {
                DistributedPartMessage message = deserialize(record.value());
                if (!input.batchId().equals(message.batchId()) || !input.routeId().equals(message.routeId())) {
                    continue;
                }
                if (completedPartNumbers.add(message.partNumber())) {
                    finishBatchTime = Math.max(finishBatchTime, message.finishTau());
                }
            }
        }

        if (completedPartNumbers.size() < input.partsCount()) {
            throw new IllegalStateException(
                    "Timed out waiting for batch completion on topic "
                            + finishTopic
                            + ": expected "
                            + input.partsCount()
                            + " unique parts, got "
                            + completedPartNumbers.size()
            );
        }

        return finishBatchTime;
    }

    private KafkaConsumer<String, String> createFinishTopicConsumer(String batchId, String finishTopic) {
        Properties properties = new Properties();
        properties.putAll(kafkaAdmin.getConfigurationProperties());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "productionline-batch-await-" + sanitize(batchId) + "-" + System.nanoTime());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties);

        Instant deadline = Instant.now().plus(Duration.ofMillis(topicsReadyTimeoutMillis));
        while (Instant.now().isBefore(deadline)) {
            if (!consumer.partitionsFor(finishTopic).isEmpty()) {
                return consumer;
            }
            sleep(topicsReadyPollIntervalMillis);
        }

        consumer.close();
        throw new IllegalStateException("Timed out waiting for finish topic readiness: " + finishTopic);
    }

    private void validate(ProductionLine.LinearSimulationInput input) {
        if (input.operationsCount() <= 0) {
            throw new IllegalArgumentException("operationsCount must be greater than 0");
        }
        if (input.partsCount() <= 0) {
            throw new IllegalArgumentException("partsCount must be greater than 0");
        }
    }

    private void ensurePipelineTopics(String routeId, int operationsCount) {
        List<NewTopic> topics = new ArrayList<>();
        List<String> topicNames = new ArrayList<>();
        for (int operation = 0; operation <= operationsCount; operation++) {
            String topicName = DistributedSimulationTopics.operationTopic(routeId, operation, operation + 1);
            topics.add(new NewTopic(topicName, 1, (short) 1));
            topicNames.add(topicName);
        }
        String operationEventsTopic = DistributedSimulationTopics.operationEventsTopic(routeId);
        topics.add(new NewTopic(operationEventsTopic, 1, (short) 1));
        topicNames.add(operationEventsTopic);

        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            try {
                adminClient.createTopics(topics).all().get();
            } catch (Exception exception) {
                if (!isTopicExistsException(exception)) {
                    throw exception;
                }
            }
            waitForTopicsReady(adminClient, topicNames);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create Kafka topics for distributed flow", exception);
        }
    }

    private void publish(String topic, DistributedPartMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            kafkaTemplate.send(
                    topic,
                    message.routeId() + "-" + message.batchId() + "-" + message.partNumber(),
                    payload
            ).get();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize distributed part message", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing distributed part message", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Failed to publish distributed part message", exception);
        }
    }

    private void publishOperationEvent(DistributedOperationEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(
                    DistributedSimulationTopics.operationEventsTopic(event.routeId()),
                    event.routeId() + "-" + event.batchId() + "-" + event.operationId() + "-" + event.partNumber(),
                    payload
            ).get();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize distributed operation event", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing distributed operation event", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Failed to publish distributed operation event", exception);
        }
    }

    private DistributedPartMessage deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, DistributedPartMessage.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize distributed part message", exception);
        }
    }

    private boolean isTopicExistsException(Exception exception) {
        if (exception instanceof TopicExistsException) {
            return true;
        }
        if (exception instanceof ExecutionException executionException) {
            return executionException.getCause() instanceof TopicExistsException;
        }
        return false;
    }

    private void waitForTopicsReady(AdminClient adminClient, Collection<String> topicNames) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofMillis(topicsReadyTimeoutMillis));
        Set<String> expectedTopics = new HashSet<>(topicNames);

        while (Instant.now().isBefore(deadline)) {
            Map<String, TopicDescription> descriptions = adminClient.describeTopics(expectedTopics).allTopicNames().get();
            if (descriptions.keySet().containsAll(expectedTopics)
                    && descriptions.values().stream().allMatch(description -> !description.partitions().isEmpty())) {
                return;
            }
            sleep(topicsReadyPollIntervalMillis);
        }

        throw new IllegalStateException("Timed out waiting for distributed flow topics readiness: " + expectedTopics);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for distributed flow topics readiness", exception);
        }
    }

    private String sanitize(String value) {
        return value == null ? "batch" : value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
