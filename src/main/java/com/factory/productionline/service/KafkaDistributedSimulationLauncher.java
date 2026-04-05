package com.factory.productionline.service;

import com.factory.productionline.graph.DistributedSimulationStartResponse;
import com.factory.productionline.model.DistributedPartMessage;
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
public class KafkaDistributedSimulationLauncher implements DistributedSimulationLauncher {

    private final KafkaAdmin kafkaAdmin;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaDistributedSimulationLauncher(
            KafkaAdmin kafkaAdmin,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper
    ) {
        this.kafkaAdmin = kafkaAdmin;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public DistributedSimulationStartResponse start(ProductionLine.LinearSimulationInput input) {
        validate(input);

        ensurePipelineTopics(input.operationsCount());

        String startTopic = topicName(0, 1);
        String finishTopic = topicName(input.operationsCount(), input.operationsCount() + 1);

        for (int partNumber = 1; partNumber <= input.partsCount(); partNumber++) {
            DistributedPartMessage message = new DistributedPartMessage(
                    partNumber,
                    input.batchId(),
                    input.startTau(),
                    0d,
                    input.startTau()
            );
            publish(startTopic, message);
        }

        return new DistributedSimulationStartResponse(input.batchId(), input.partsCount(), startTopic, finishTopic);
    }

    private void validate(ProductionLine.LinearSimulationInput input) {
        if (input.operationsCount() <= 0) {
            throw new IllegalArgumentException("operationsCount must be greater than 0");
        }
        if (input.partsCount() <= 0) {
            throw new IllegalArgumentException("partsCount must be greater than 0");
        }
    }

    private void ensurePipelineTopics(int operationsCount) {
        List<NewTopic> topics = new ArrayList<>();
        for (int operation = 0; operation <= operationsCount; operation++) {
            topics.add(new NewTopic(topicName(operation, operation + 1), 1, (short) 1));
        }

        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            adminClient.createTopics(topics).all().get();
        } catch (Exception exception) {
            if (isTopicExistsException(exception)) {
                return;
            }
            throw new IllegalStateException("Failed to create Kafka topics for distributed flow", exception);
        }
    }

    private void publish(String topic, DistributedPartMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            kafkaTemplate.send(topic, message.batchId() + "-" + message.partNumber(), payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize distributed part message", exception);
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
