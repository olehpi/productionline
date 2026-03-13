package com.factory.productionline.service;

import com.factory.productionline.graph.ProductionLineRequest;
import com.factory.productionline.graph.SimulationGraphResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SimulationGraphService {

    public SimulationGraphResponse buildGraph(ProductionLineRequest request) {
        Map<String, ProductionLineRequest.Operation> operationsById = request.operations().stream()
                .collect(Collectors.toMap(
                        ProductionLineRequest.Operation::id,
                        operation -> operation,
                        (left, right) -> {
                            throw new IllegalArgumentException("Duplicate operation id: " + left.id());
                        },
                        LinkedHashMap::new
                ));

        Map<String, ProductionLineRequest.EquipmentResource> equipmentById = request.equipmentResources().stream()
                .collect(Collectors.toMap(
                        ProductionLineRequest.EquipmentResource::id,
                        equipment -> equipment,
                        (left, right) -> {
                            throw new IllegalArgumentException("Duplicate equipment id: " + left.id());
                        },
                        LinkedHashMap::new
                ));

        validateResourceAssignments(request.operations(), equipmentById);
        validateStates(request.operations());

        Map<String, List<String>> adjacency = new LinkedHashMap<>();
        request.operations().forEach(operation -> adjacency.put(operation.id(), new ArrayList<>()));

        Set<String> seenEdges = new HashSet<>();
        for (ProductionLineRequest.OperationTransition transition : request.transitions()) {
            if (!operationsById.containsKey(transition.fromOperationId())) {
                throw new IllegalArgumentException("Unknown source operation: " + transition.fromOperationId());
            }
            if (!operationsById.containsKey(transition.toOperationId())) {
                throw new IllegalArgumentException("Unknown target operation: " + transition.toOperationId());
            }
            if (transition.fromOperationId().equals(transition.toOperationId())) {
                throw new IllegalArgumentException("Self-loop is not supported for operation: " + transition.fromOperationId());
            }
            String edgeKey = transition.fromOperationId() + "->" + transition.toOperationId();
            if (!seenEdges.add(edgeKey)) {
                throw new IllegalArgumentException("Duplicate transition: " + edgeKey);
            }
            adjacency.get(transition.fromOperationId()).add(transition.toOperationId());
        }

        adjacency.values().forEach(neighbours -> neighbours.sort(Comparator.naturalOrder()));

        List<String> topologicalOrder = topologicalSort(adjacency);
        boolean hasCycle = topologicalOrder.size() != operationsById.size();

        return new SimulationGraphResponse(
                request.operations().stream()
                        .map(operation -> new SimulationGraphResponse.Operation(
                                operation.id(),
                                operation.name(),
                                mapStates(operation.manStates()),
                                mapStates(operation.materialStates()),
                                mapStates(operation.machineStates()),
                                mapStates(operation.methodStates()),
                                operation.eligibleManIds(),
                                operation.eligibleMaterialIds(),
                                operation.eligibleMachineIds(),
                                operation.eligibleMethodIds(),
                                buildRiskCategories(operation)
                        ))
                        .toList(),
                request.transitions().stream()
                        .map(transition -> new SimulationGraphResponse.OperationEdge(
                                transition.fromOperationId(),
                                transition.toOperationId()
                        ))
                        .toList(),
                request.equipmentResources().stream()
                        .map(equipment -> new SimulationGraphResponse.EquipmentResource(
                                equipment.id(),
                                equipment.name(),
                                equipment.type(),
                                equipment.quantity()
                        ))
                        .toList(),
                adjacency,
                hasCycle,
                hasCycle ? List.of() : topologicalOrder
        );
    }

    private void validateResourceAssignments(List<ProductionLineRequest.Operation> operations,
                                             Map<String, ProductionLineRequest.EquipmentResource> equipmentById) {
        for (ProductionLineRequest.Operation operation : operations) {
            validateUniqueAssignments(operation.id(), operation.eligibleMachineIds(), "machine");
            validateUniqueAssignments(operation.id(), operation.eligibleManIds(), "man");
            validateUniqueAssignments(operation.id(), operation.eligibleMaterialIds(), "material");
            validateUniqueAssignments(operation.id(), operation.eligibleMethodIds(), "method");

            for (String machineId : operation.eligibleMachineIds()) {
                if (!equipmentById.containsKey(machineId)) {
                    throw new IllegalArgumentException("Operation " + operation.id()
                            + " references unknown machine: " + machineId);
                }
            }
        }
    }

    private void validateStates(List<ProductionLineRequest.Operation> operations) {
        for (ProductionLineRequest.Operation operation : operations) {
            validateCategoryStates(operation.id(), "man", operation.manStates());
            validateCategoryStates(operation.id(), "material", operation.materialStates());
            validateCategoryStates(operation.id(), "machine", operation.machineStates());
            validateCategoryStates(operation.id(), "method", operation.methodStates());
        }
    }

    private void validateCategoryStates(String operationId,
                                        String category,
                                        List<ProductionLineRequest.ProcessingState> states) {
        Set<Integer> uniqueZ = new HashSet<>();
        boolean hasNormalState = false;

        for (ProductionLineRequest.ProcessingState state : states) {
            if (!uniqueZ.add(state.z())) {
                throw new IllegalArgumentException("Operation " + operationId
                        + " has duplicate z state in " + category + " category: " + state.z());
            }
            if (state.z() == 0) {
                hasNormalState = true;
            }
        }

        if (!hasNormalState) {
            throw new IllegalArgumentException("Operation " + operationId
                    + " must contain normal state z=0 for " + category + " category");
        }
    }

    private void validateUniqueAssignments(String operationId, List<String> ids, String category) {
        Set<String> uniqueIds = new HashSet<>();
        for (String id : ids) {
            if (!uniqueIds.add(id)) {
                throw new IllegalArgumentException("Operation " + operationId
                        + " has duplicate " + category + " assignment: " + id);
            }
        }
    }

    private List<SimulationGraphResponse.ProcessingState> mapStates(List<ProductionLineRequest.ProcessingState> states) {
        return states.stream()
                .map(state -> new SimulationGraphResponse.ProcessingState(
                        state.z(),
                        state.meanProcessingTimeSeconds(),
                        state.standardDeviationSeconds(),
                        state.distributionType()
                ))
                .toList();
    }


    private List<SimulationGraphResponse.RiskCategory> buildRiskCategories(ProductionLineRequest.Operation operation) {
        return List.of(
                new SimulationGraphResponse.RiskCategory("man", mapStates(operation.manStates()), operation.eligibleManIds()),
                new SimulationGraphResponse.RiskCategory("material", mapStates(operation.materialStates()), operation.eligibleMaterialIds()),
                new SimulationGraphResponse.RiskCategory("machine", mapStates(operation.machineStates()), operation.eligibleMachineIds()),
                new SimulationGraphResponse.RiskCategory("method", mapStates(operation.methodStates()), operation.eligibleMethodIds())
        );
    }

    private List<String> topologicalSort(Map<String, List<String>> adjacency) {
        Map<String, Integer> inDegree = new HashMap<>();
        adjacency.forEach((node, ignored) -> inDegree.put(node, 0));

        adjacency.forEach((ignored, neighbours) ->
                neighbours.forEach(neighbour -> inDegree.computeIfPresent(neighbour, (ignoredKey, degree) -> degree + 1))
        );

        ArrayDeque<String> queue = inDegree.entrySet().stream()
                .filter(entry -> entry.getValue() == 0)
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toCollection(ArrayDeque::new));

        List<String> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            order.add(current);

            for (String neighbour : adjacency.getOrDefault(current, List.of())) {
                int updated = inDegree.computeIfPresent(neighbour, (ignoredKey, degree) -> degree - 1);
                if (updated == 0) {
                    queue.addLast(neighbour);
                }
            }
        }
        return order;
    }
}
