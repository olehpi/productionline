package com.factory.productionline.service;

import com.factory.productionline.model.DistributedPartMessage;
import com.factory.productionline.model.ProductionLine;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

@Component
@ConditionalOnProperty(name = "simulation.orchestration.mode", havingValue = "inprocess", matchIfMissing = true)
@ConditionalOnProperty(name = "simulation.kafka.enabled", havingValue = "true")
public class InProcessDistributedWorkerOrchestrationService implements DistributedWorkerOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(InProcessDistributedWorkerOrchestrationService.class);

    private final ConcurrentKafkaListenerContainerFactory<String, String> listenerContainerFactory;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final DistributedSimulationLauncher distributedSimulationLauncher;

    private final Map<Integer, WorkerRuntime> workers = new HashMap<>();
    private ConcurrentMessageListenerContainer<String, String> finishStoreContainer;
    private String finishStoreTopic;

    public InProcessDistributedWorkerOrchestrationService(
            ConcurrentKafkaListenerContainerFactory<String, String> listenerContainerFactory,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            DistributedSimulationLauncher distributedSimulationLauncher
    ) {
        this.listenerContainerFactory = listenerContainerFactory;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.distributedSimulationLauncher = distributedSimulationLauncher;
    }

    @Override
    public synchronized void ensureWorkersAndStartBatch(ProductionLine.LinearSimulationInput input) {
        List<ProductionLine.LinearOperationInput> operations = input.operations().stream()
                .filter(operation -> !isStore(operation.name()))
                .sorted(java.util.Comparator.comparingInt(ProductionLine.LinearOperationInput::id))
                .toList();

        for (ProductionLine.LinearOperationInput operation : operations) {
            ensureWorker(operation);
        }
        ensureFinishStoreConsumer(input.operationsCount());

        distributedSimulationLauncher.start(input);
    }

    private void ensureWorker(ProductionLine.LinearOperationInput operation) {
        WorkerRuntime current = workers.get(operation.id());
        WorkerSpec expected = new WorkerSpec(operation.id(), operation.tauMean(), operation.tauSigma(), operation.randomSeed());
        if (current != null && current.spec().equals(expected) && current.container().isRunning()) {
            return;
        }

        if (current != null) {
            current.container().stop();
            workers.remove(operation.id());
        }

        String inboundTopic = "line-op-" + (operation.id() - 1) + "-to-" + operation.id();
        String groupId = "productionline-operation-" + operation.id();
        ConcurrentMessageListenerContainer<String, String> container = listenerContainerFactory.createContainer(inboundTopic);
        container.setMissingTopicsFatal(false);
        container.getContainerProperties().setGroupId(groupId);
        WorkerRuntime runtime = new WorkerRuntime(expected, container, expected.random());
        container.getContainerProperties().setMessageListener((MessageListener<String, String>) record ->
                process(operation.id(), operation.id() + 1, runtime, record.value()));

        container.start();
        workers.put(operation.id(), runtime);
        log.info("Started in-process worker opId={} inboundTopic={}", operation.id(), inboundTopic);
    }

    private void ensureFinishStoreConsumer(int operationsCount) {
        String expectedTopic = "line-op-" + operationsCount + "-to-" + (operationsCount + 1);
        if (finishStoreContainer != null && Objects.equals(finishStoreTopic, expectedTopic) && finishStoreContainer.isRunning()) {
            return;
        }

        if (finishStoreContainer != null) {
            finishStoreContainer.stop();
        }

        ConcurrentMessageListenerContainer<String, String> container = listenerContainerFactory.createContainer(expectedTopic);
        container.setMissingTopicsFatal(false);
        container.getContainerProperties().setGroupId("productionline-finish-store");
        container.getContainerProperties().setMessageListener((MessageListener<String, String>) record -> {
            DistributedPartMessage message = deserialize(record.value());
            log.info("FinishStore received batch={} part={} finishTau={}",
                    message.batchId(), message.partNumber(), message.finishTau());
        });

        container.start();
        finishStoreContainer = container;
        finishStoreTopic = expectedTopic;
        log.info("Started in-process finishStore consumer topic={}", expectedTopic);
    }

    private void process(int operationId, int nextOperationId, WorkerRuntime runtime, String payload) {
        DistributedPartMessage incoming = deserialize(payload);

        double startTau = incoming.finishTau();
        double processingTau = runtime.spec().tauSigma() == 0d
                ? runtime.spec().tauMean()
                : Math.max(0.000001d, runtime.spec().tauMean() + runtime.random().nextGaussian() * runtime.spec().tauSigma());
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

    private boolean isStore(String name) {
        String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("startstore") || normalized.equals("finishstore");
    }

    private record WorkerSpec(int operationId, double tauMean, double tauSigma, Long randomSeed) {
        Random random() {
            return randomSeed == null ? new Random() : new Random(randomSeed);
        }
    }

    private record WorkerRuntime(
            WorkerSpec spec,
            ConcurrentMessageListenerContainer<String, String> container,
            Random random
    ) {
    }
}
