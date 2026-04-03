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

    public LinearProductionSimulationService(TransferEventPublisher transferEventPublisher) {
        this.transferEventPublisher = transferEventPublisher;
    }

    public ProductionLine.LinearSimulationResult simulate(ProductionLine.LinearSimulationInput input) {
        int operationsCount = input.operationsCount();
        int partsCount = input.partsCount();

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

        Random random = input.randomSeed() == null ? new Random() : new Random(input.randomSeed());
        long sequence = 0L;
        sequence = tryStartOperation(0, 0d, input, bunkers, machineBusyUntil, completionEvents, random, sequence);

        int completedAtLastOperation = 0;
        double finalTau = 0d;
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

                sequence = tryStartOperation(nextOperationIndex, finalTau, input, bunkers, machineBusyUntil, completionEvents, random, sequence);
            }

            sequence = tryStartOperation(operationIndex, finalTau, input, bunkers, machineBusyUntil, completionEvents, random, sequence);
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
            ProductionLine.LinearSimulationInput input,
            List<Queue<Integer>> bunkers,
            double[] machineBusyUntil,
            PriorityQueue<ProcessingCompletionEvent> completionEvents,
            Random random,
            long sequence
    ) {
        if (machineBusyUntil[operationIndex] > currentTau || bunkers.get(operationIndex).isEmpty()) {
            return sequence;
        }

        Integer partNumber = bunkers.get(operationIndex).poll();
        if (partNumber == null) {
            return sequence;
        }

        double processingTau = sampleNormalBounded(input.tauMean(), input.tauSigma(), random);
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
