package com.factory.productionline.service;

import com.factory.productionline.model.ProductionLine;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Component
@ConditionalOnProperty(name = "simulation.kafka.enabled", havingValue = "true")
public class KafkaTransferEventPublisher implements TransferEventPublisher {

    private final KafkaAdmin kafkaAdmin;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaTransferEventPublisher(
            KafkaAdmin kafkaAdmin,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper
    ) {
        this.kafkaAdmin = kafkaAdmin;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void ensureTopics(int operationsCount) {
        List<NewTopic> topics = new ArrayList<>();
        for (int operation = 1; operation < operationsCount; operation++) {
            topics.add(new NewTopic(topicName(operation, operation + 1), 1, (short) 1));
        }

        if (topics.isEmpty()) {
            return;
        }

        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            adminClient.createTopics(topics).all().get();
        } catch (Exception exception) {
            if (isTopicExistsException(exception)) {
                return;
            }
            throw new IllegalStateException("Failed to create Kafka topics for linear simulation", exception);
        }
    }

    @Override
    public void publish(ProductionLine.KafkaTransferMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            kafkaTemplate.send(topicName(message.fromOperation(), message.toOperation()), message.batchId(), payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize Kafka transfer message", exception);
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

    private String topicName(int fromOperation, int toOperation) {
        return "line-op-" + fromOperation + "-to-" + toOperation;
    }
}
