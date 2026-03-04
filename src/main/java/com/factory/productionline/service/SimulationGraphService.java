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

        validateEquipmentAssignments(request.operations(), equipmentById);

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
                        .map(operation -> new SimulationGraphResponse.OperationNode(
                                operation.id(),
                                operation.name(),
                                operation.meanProcessingTimeSeconds(),
                                operation.standardDeviationSeconds(),
                                operation.distributionType(),
                                operation.eligibleEquipmentIds()
                        ))
                        .toList(),
                request.transitions().stream()
                        .map(transition -> new SimulationGraphResponse.OperationEdge(
                                transition.fromOperationId(),
                                transition.toOperationId()
                        ))
                        .toList(),
                request.equipmentResources().stream()
                        .map(equipment -> new SimulationGraphResponse.EquipmentResourceNode(
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

    private void validateEquipmentAssignments(List<ProductionLineRequest.Operation> operations,
                                              Map<String, ProductionLineRequest.EquipmentResource> equipmentById) {
        for (ProductionLineRequest.Operation operation : operations) {
            Set<String> uniqueEquipmentIds = new HashSet<>();
            for (String equipmentId : operation.eligibleEquipmentIds()) {
                if (!equipmentById.containsKey(equipmentId)) {
                    throw new IllegalArgumentException("Operation " + operation.id()
                            + " references unknown equipment: " + equipmentId);
                }
                if (!uniqueEquipmentIds.add(equipmentId)) {
                    throw new IllegalArgumentException("Operation " + operation.id()
                            + " has duplicate equipment assignment: " + equipmentId);
                }
            }
        }
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
