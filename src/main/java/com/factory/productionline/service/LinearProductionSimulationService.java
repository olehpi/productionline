package com.factory.productionline.service;

import com.factory.productionline.model.ProductionLine;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Random;

@Service
public class LinearProductionSimulationService {

    private final TransferEventPublisher transferEventPublisher;
    private final DistributedWorkerProvisioner distributedWorkerProvisioner;

    public LinearProductionSimulationService(
            TransferEventPublisher transferEventPublisher,
            DistributedWorkerProvisioner distributedWorkerProvisioner
    ) {
        this.transferEventPublisher = transferEventPublisher;
        this.distributedWorkerProvisioner = distributedWorkerProvisioner;
    }

    public ProductionLine.LinearSimulationResult simulate(ProductionLine.LinearSimulationInput input) {
        distributedWorkerProvisioner.ensureWorkers(input);

        List<ProductionLine.LinearOperationInput> processingOperationInputs = input.operations().stream()
                .filter(operationInput -> !isStoreOperation(operationInput))
                .toList();
        int operationsCount = processingOperationInputs.size();
        int partsCount = input.partsCount();
        validateOperationsInput(input, processingOperationInputs);

        transferEventPublisher.ensureTopics(operationsCount);

        List<Queue<Integer>> bunkers = new ArrayList<>(operationsCount);
        for (int i = 0; i < operationsCount; i++) {
            bunkers.add(new ArrayDeque<>());
        }
        for (int part = 1; part <= partsCount; part++) {
            bunkers.getFirst().offer(part);
        }

        Map<String, Queue<ProductionLine.KafkaTransferMessage>> transferTopics = initializeTransferTopics(operationsCount);

        double[] machineBusyUntil = new double[operationsCount];
        List<List<ProductionLine.PartProcessingEvent>> eventsByOperation = new ArrayList<>(operationsCount);
        for (int i = 0; i < operationsCount; i++) {
            eventsByOperation.add(new ArrayList<>());
        }

        List<ProductionLine.KafkaTransferMessage> kafkaMessages = new ArrayList<>();
        PriorityQueue<ProcessingCompletionEvent> completionEvents = new PriorityQueue<>(
                Comparator.comparingDouble(ProcessingCompletionEvent::finishTau)
                        .thenComparingInt(ProcessingCompletionEvent::operationIndex)
                        .thenComparingLong(ProcessingCompletionEvent::sequence)
        );

        List<Random> randomsByOperation = processingOperationInputs.stream()
                .map(operationInput -> operationInput.randomSeed() == null
                        ? new Random()
                        : new Random(operationInput.randomSeed()))
                .toList();
        long sequence = 0L;
        sequence = tryStartOperation(
                0,
                input.startTau(),
                processingOperationInputs,
                bunkers,
                machineBusyUntil,
                completionEvents,
                randomsByOperation,
                sequence
        );

        int completedAtLastOperation = 0;
        double finalTau = input.startTau();
        while (!completionEvents.isEmpty() && completedAtLastOperation < partsCount) {
            ProcessingCompletionEvent completionEvent = completionEvents.poll();
            finalTau = completionEvent.finishTau();

            int operationIndex = completionEvent.operationIndex();
            int operationNumber = operationIndex + 1;
            eventsByOperation.get(operationIndex).add(new ProductionLine.PartProcessingEvent(
                    completionEvent.partNumber(),
                    input.batchId(),
                    completionEvent.startTau(),
                    completionEvent.processingTau(),
                    completionEvent.finishTau()
            ));

            if (operationIndex == operationsCount - 1) {
                completedAtLastOperation++;
            } else {
                int nextOperationIndex = operationIndex + 1;
                int nextOperationNumber = nextOperationIndex + 1;
                ProductionLine.KafkaTransferMessage transferMessage = new ProductionLine.KafkaTransferMessage(
                        operationNumber,
                        nextOperationNumber,
                        completionEvent.partNumber(),
                        input.batchId(),
                        completionEvent.startTau(),
                        completionEvent.processingTau(),
                        completionEvent.finishTau()
                );

                kafkaMessages.add(transferMessage);
                transferEventPublisher.publish(transferMessage);

                String topicKey = transferTopicKey(operationNumber, nextOperationNumber);
                transferTopics.get(topicKey).offer(transferMessage);
                drainTopicToBunker(transferTopics.get(topicKey), finalTau, bunkers.get(nextOperationIndex));

                sequence = tryStartOperation(
                        nextOperationIndex,
                        finalTau,
                        processingOperationInputs,
                        bunkers,
                        machineBusyUntil,
                        completionEvents,
                        randomsByOperation,
                        sequence
                );
            }

            sequence = tryStartOperation(
                    operationIndex,
                    finalTau,
                    processingOperationInputs,
                    bunkers,
                    machineBusyUntil,
                    completionEvents,
                    randomsByOperation,
                    sequence
            );
        }

        List<ProductionLine.LinearOperationTimeline> timelines = new ArrayList<>(operationsCount);
        for (int i = 0; i < operationsCount; i++) {
            timelines.add(new ProductionLine.LinearOperationTimeline(i + 1, eventsByOperation.get(i)));
        }

        return new ProductionLine.LinearSimulationResult(finalTau, timelines, kafkaMessages);
    }

    private Map<String, Queue<ProductionLine.KafkaTransferMessage>> initializeTransferTopics(int operationsCount) {
        Map<String, Queue<ProductionLine.KafkaTransferMessage>> topics = new HashMap<>();
        for (int operationNumber = 1; operationNumber < operationsCount; operationNumber++) {
            topics.put(transferTopicKey(operationNumber, operationNumber + 1), new ArrayDeque<>());
        }
        return topics;
    }

    private void drainTopicToBunker(
            Queue<ProductionLine.KafkaTransferMessage> topic,
            double currentTau,
            Queue<Integer> bunker
    ) {
        while (!topic.isEmpty() && topic.peek().availableAtTau() <= currentTau) {
            ProductionLine.KafkaTransferMessage delivered = topic.poll();
            if (delivered != null) {
                bunker.offer(delivered.partNumber());
            }
        }
    }

    private String transferTopicKey(int fromOperation, int toOperation) {
        return fromOperation + "->" + toOperation;
    }

    private long tryStartOperation(
            int operationIndex,
            double currentTau,
            List<ProductionLine.LinearOperationInput> operationInputs,
            List<Queue<Integer>> bunkers,
            double[] machineBusyUntil,
            PriorityQueue<ProcessingCompletionEvent> completionEvents,
            List<Random> randomsByOperation,
            long sequence
    ) {
        if (machineBusyUntil[operationIndex] > currentTau || bunkers.get(operationIndex).isEmpty()) {
            return sequence;
        }

        Integer partNumber = bunkers.get(operationIndex).poll();
        if (partNumber == null) {
            return sequence;
        }

        ProductionLine.LinearOperationInput operationInput = operationInputs.get(operationIndex);
        double processingTau = sampleNormalBounded(
                operationInput.tauMean(),
                operationInput.tauSigma(),
                randomsByOperation.get(operationIndex)
        );
        double startTau = Math.max(currentTau, machineBusyUntil[operationIndex]);
        double finishTau = startTau + processingTau;
        machineBusyUntil[operationIndex] = finishTau;

        completionEvents.offer(new ProcessingCompletionEvent(
                operationIndex,
                partNumber,
                startTau,
                processingTau,
                finishTau,
                sequence
        ));

        return sequence + 1;
    }

    private double sampleNormalBounded(double mean, double sigma, Random random) {
        if (sigma == 0d) {
            return mean;
        }

        double sampled = mean + random.nextGaussian() * sigma;
        return Math.max(0.000001d, sampled);
    }

    private void validateOperationsInput(
            ProductionLine.LinearSimulationInput input,
            List<ProductionLine.LinearOperationInput> processingOperationInputs
    ) {
        if (processingOperationInputs.size() != input.operationsCount()) {
            throw new IllegalArgumentException(
                    "operationsCount must match count of non-store operations: expected "
                            + input.operationsCount()
                            + ", got "
                            + processingOperationInputs.size()
            );
        }
        if (input.finishTau() < input.startTau()) {
            throw new IllegalArgumentException(
                    "finishTau must be greater than or equal to startTau: startTau="
                            + input.startTau()
                            + ", finishTau="
                            + input.finishTau()
            );
        }
        boolean hasStartStore = input.operations().stream().anyMatch(this::isStartStoreOperation);
        boolean hasFinishStore = input.operations().stream().anyMatch(this::isFinishStoreOperation);
        if (!hasStartStore || !hasFinishStore) {
            throw new IllegalArgumentException("operations must include startStore and finishStore");
        }

        ProductionLine.LinearOperationInput startStore = input.operations().stream()
                .filter(this::isStartStoreOperation)
                .findFirst()
                .orElseThrow();
        if (startStore.id() != 0) {
            throw new IllegalArgumentException("startStore id must be 0");
        }

        ProductionLine.LinearOperationInput finishStore = input.operations().stream()
                .filter(this::isFinishStoreOperation)
                .findFirst()
                .orElseThrow();
        int expectedFinishStoreId = input.operationsCount() + 1;
        if (finishStore.id() != expectedFinishStoreId) {
            throw new IllegalArgumentException(
                    "finishStore id must be operationsCount + 1: expected "
                            + expectedFinishStoreId
                            + ", got "
                            + finishStore.id()
            );
        }
    }

    private boolean isStoreOperation(ProductionLine.LinearOperationInput operationInput) {
        return isStartStoreOperation(operationInput) || isFinishStoreOperation(operationInput);
    }

    private boolean isStartStoreOperation(ProductionLine.LinearOperationInput operationInput) {
        return "startStore".equalsIgnoreCase(operationInput.name());
    }

    private boolean isFinishStoreOperation(ProductionLine.LinearOperationInput operationInput) {
        return "finishStore".equalsIgnoreCase(operationInput.name())
                || "finishtStore".equalsIgnoreCase(operationInput.name());
    }

    private record ProcessingCompletionEvent(
            int operationIndex,
            int partNumber,
            double startTau,
            double processingTau,
            double finishTau,
            long sequence
    ) {
    }
}
