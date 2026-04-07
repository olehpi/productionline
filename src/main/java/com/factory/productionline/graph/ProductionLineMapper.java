package com.factory.productionline.graph;

import com.factory.productionline.model.DistributedBatchResult;
import com.factory.productionline.model.ProductionLine;
import org.springframework.stereotype.Component;

@Component
public class ProductionLineMapper {

    public ProductionLine toModel(ProductionLineRequest request) {
        return new ProductionLine(
                request.routes().stream()
                        .map(route -> new ProductionLine.Route(
                                route.id(),
                                route.name(),
                                route.operationGraph()
                        ))
                        .toList(),
                request.availableBunkers().stream()
                        .map(bunker -> new ProductionLine.Bunker(
                                bunker.id(),
                                bunker.name(),
                                bunker.size(),
                                bunker.maxSize()
                        ))
                        .toList(),
                request.availableOperations().stream()
                        .map(operation -> new ProductionLine.Operation(
                                operation.id(),
                                operation.name(),
                                operation.bunkerIds(),
                                operation.inputIds(),
                                operation.outputIds(),
                                operation.men().stream().map(ignored -> new ProductionLine.Man()).toList(),
                                operation.materials().stream().map(ignored -> new ProductionLine.Material()).toList(),
                                operation.machines().stream().map(ignored -> new ProductionLine.Machine()).toList(),
                                operation.methods().stream().map(ignored -> new ProductionLine.Method()).toList()
                        ))
                        .toList()
        );
    }

    public ProductionLineResponse toResponse(ProductionLine productionLine) {
        return new ProductionLineResponse(
                productionLine.routes().stream()
                        .map(route -> new ProductionLineResponse.Route(
                                route.id(),
                                route.name(),
                                route.operationGraph()
                        ))
                        .toList(),
                productionLine.availableBunkers().stream()
                        .map(bunker -> new ProductionLineResponse.Bunker(
                                bunker.id(),
                                bunker.name(),
                                bunker.size(),
                                bunker.maxSize()
                        ))
                        .toList(),
                productionLine.availableOperations().stream()
                        .map(operation -> new ProductionLineResponse.Operation(
                                operation.id(),
                                operation.name(),
                                operation.bunkerIds(),
                                operation.inputIds(),
                                operation.outputIds(),
                                operation.men().stream().map(ignored -> new ProductionLineResponse.Man()).toList(),
                                operation.materials().stream().map(ignored -> new ProductionLineResponse.Material()).toList(),
                                operation.machines().stream().map(ignored -> new ProductionLineResponse.Machine()).toList(),
                                operation.methods().stream().map(ignored -> new ProductionLineResponse.Method()).toList()
                        ))
                        .toList()
        );
    }

    public ProductionLine.LinearSimulationInput toModel(LinearSimulationRequest request) {
        int operationsCount = (int) request.operations().stream()
                .filter(operationInput -> !isStore(operationInput.name()))
                .count();

        return new ProductionLine.LinearSimulationInput(
                request.partsCount(),
                operationsCount,
                request.batchId(),
                request.startTau(),
                request.finishTau(),
                request.operations().stream()
                        .map(operationInput -> new ProductionLine.LinearOperationInput(
                                operationInput.id(),
                                operationInput.name(),
                                operationInput.tauMean(),
                                operationInput.tauSigma(),
                                operationInput.randomSeed()
                        ))
                        .toList()
        );
    }

    private boolean isStore(String name) {
        String normalized = name == null ? "" : name.trim().toLowerCase();
        return normalized.equals("startstore") || normalized.equals("finishstore");
    }

    public LinearSimulationResponse toResponse(DistributedBatchResult result) {
        return new LinearSimulationResponse(
                result.finalTau(),
                result.operationTimelines().stream()
                        .map(timeline -> new LinearSimulationResponse.OperationTimeline(
                                timeline.operationNumber(),
                                timeline.events().stream()
                                        .map(event -> new LinearSimulationResponse.PartProcessingEvent(
                                                event.partNumber(),
                                                event.batchId(),
                                                event.startTau(),
                                                event.processingTau(),
                                                event.finishTau()
                                        ))
                                        .toList()
                        ))
                        .toList(),
                result.kafkaMessages().stream()
                        .map(message -> new LinearSimulationResponse.KafkaTransferMessage(
                                message.fromOperation(),
                                message.toOperation(),
                                message.partNumber(),
                                message.batchId(),
                                message.processingStartTau(),
                                message.processingTau(),
                                message.availableAtTau()
                        ))
                        .toList()
        );
    }
}
