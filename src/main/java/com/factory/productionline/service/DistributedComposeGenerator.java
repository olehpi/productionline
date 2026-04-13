package com.factory.productionline.service;

import com.factory.productionline.model.ProductionLine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DistributedComposeGenerator {

    private static final int CONTAINER_DEBUG_PORT_BASE = 5100;
    private static final int HOST_DEBUG_PORT_BASE = 20000;
    private static final int HOST_DEBUG_PORT_ROUTE_STRIDE = 100;
    private static final int HOST_DEBUG_PORT_ROUTE_SLOTS = 400;

    private final String workerImage;

    public DistributedComposeGenerator(
            @Value("${simulation.orchestration.compose.worker-image:productionline-productionline}") String workerImage
    ) {
        this.workerImage = workerImage;
    }

    public String generate(ProductionLine.LinearSimulationInput input) {
        return generate(new ProductionLine.DistributedRouteInput(
                input.routeId(),
                input.operationsCount(),
                input.operations()
        ));
    }

    public String generate(ProductionLine.DistributedRouteInput input) {
        validate(input);

        List<ProductionLine.LinearOperationInput> workerOperations = input.operations().stream()
                .filter(operation -> !isStore(operation.name()))
                .sorted(Comparator.comparingInt(ProductionLine.LinearOperationInput::id))
                .toList();

        StringBuilder compose = new StringBuilder("services:\n");
        for (ProductionLine.LinearOperationInput worker : workerOperations) {
            appendWorkerService(compose, worker, input.routeId());
        }

        appendFinishStoreService(compose, input.operationsCount(), input.routeId());
        return compose.toString();
    }

    private void validate(ProductionLine.DistributedRouteInput input) {
        if (input.operationsCount() <= 0) {
            throw new IllegalArgumentException("operationsCount must be greater than 0");
        }

        if (input.operations() == null || input.operations().isEmpty()) {
            throw new IllegalArgumentException("operations must not be empty");
        }

        List<ProductionLine.LinearOperationInput> startStores = input.operations().stream()
                .filter(operation -> normalize(operation.name()).equals("startstore"))
                .toList();
        List<ProductionLine.LinearOperationInput> finishStores = input.operations().stream()
                .filter(operation -> normalize(operation.name()).equals("finishstore"))
                .toList();

        if (startStores.size() != 1) {
            throw new IllegalArgumentException("Expected exactly one startStore operation");
        }
        if (finishStores.size() != 1) {
            throw new IllegalArgumentException("Expected exactly one finishStore operation");
        }

        if (startStores.get(0).id() != 0) {
            throw new IllegalArgumentException("startStore must have id=0");
        }

        int expectedFinishId = input.operationsCount() + 1;
        if (finishStores.get(0).id() != expectedFinishId) {
            throw new IllegalArgumentException(
                    "finishStore must have id=" + expectedFinishId + " when operationsCount=" + input.operationsCount()
            );
        }

        List<ProductionLine.LinearOperationInput> workers = input.operations().stream()
                .filter(operation -> !isStore(operation.name()))
                .toList();

        if (workers.size() != input.operationsCount()) {
            throw new IllegalArgumentException(
                    "operationsCount=" + input.operationsCount() + " but found " + workers.size() + " non-store operations"
            );
        }

        Set<Integer> ids = workers.stream().map(ProductionLine.LinearOperationInput::id).collect(Collectors.toSet());
        for (int expectedId = 1; expectedId <= input.operationsCount(); expectedId++) {
            if (!ids.contains(expectedId)) {
                throw new IllegalArgumentException(
                        "Worker operation IDs must be consecutive from 1 to " + input.operationsCount()
                );
            }
        }
    }

    private void appendWorkerService(StringBuilder compose, ProductionLine.LinearOperationInput operation, String routeId) {
        int operationId = operation.id();
        String inboundTopic = DistributedSimulationTopics.operationTopic(routeId, operationId - 1, operationId);
        int containerDebugPort = CONTAINER_DEBUG_PORT_BASE + operationId;
        int hostDebugPort = hostDebugPort(routeId, operationId);

        compose.append("  ").append(DistributedSimulationTopics.workerServiceName(routeId, operationId)).append(":\n")
                .append("    image: ").append(workerImage).append("\n")
                .append("    depends_on:\n")
                .append("      kafka:\n")
                .append("        condition: service_healthy\n")
                .append("    ports:\n")
                .append("      - \"").append(hostDebugPort).append(":").append(containerDebugPort).append("\"\n")
                .append("    environment:\n")
                .append("      - SIMULATION_KAFKA_ENABLED=true\n")
                .append("      - SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092\n")
                .append("      - SPRING_KAFKA_CONSUMER_AUTO_OFFSET_RESET=earliest\n")
                .append("      - SPRING_MAIN_WEB_APPLICATION_TYPE=none\n")
                .append("      - JAVA_TOOL_OPTIONS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:")
                .append(containerDebugPort).append("\n")
                .append("      - SIMULATION_DISTRIBUTED_WORKER_ENABLED=true\n")
                .append("      - SIMULATION_DISTRIBUTED_WORKER_GROUP_ID=")
                .append(DistributedSimulationTopics.workerGroupId(routeId, operationId)).append("\n")
                .append("      - SIMULATION_DISTRIBUTED_WORKER_INBOUND_TOPIC=").append(inboundTopic).append("\n")
                .append("      - SIMULATION_DISTRIBUTED_WORKER_OPERATION_ID=").append(operationId).append("\n")
                .append("      - SIMULATION_DISTRIBUTED_WORKER_NEXT_OPERATION_ID=").append(operationId + 1).append("\n")
                .append("      - SIMULATION_DISTRIBUTED_WORKER_TAU_MEAN=").append(operation.tauMean()).append("\n")
                .append("      - SIMULATION_DISTRIBUTED_WORKER_TAU_SIGMA=").append(operation.tauSigma()).append("\n")
                .append("      - SIMULATION_DISTRIBUTED_WORKER_RANDOM_SEED=").append(operation.randomSeed() == null ? 0 : operation.randomSeed()).append("\n");
    }

    private void appendFinishStoreService(StringBuilder compose, int operationsCount, String routeId) {
        String finishTopic = DistributedSimulationTopics.operationTopic(routeId, operationsCount, operationsCount + 1);
        int finishStoreId = operationsCount + 1;
        int containerDebugPort = CONTAINER_DEBUG_PORT_BASE + finishStoreId;
        int hostDebugPort = hostDebugPort(routeId, finishStoreId);
        compose.append("  ").append(DistributedSimulationTopics.finishStoreServiceName(routeId)).append(":\n")
                .append("    image: ").append(workerImage).append("\n")
                .append("    depends_on:\n")
                .append("      kafka:\n")
                .append("        condition: service_healthy\n")
                .append("    ports:\n")
                .append("      - \"").append(hostDebugPort).append(":").append(containerDebugPort).append("\"\n")
                .append("    environment:\n")
                .append("      - SIMULATION_KAFKA_ENABLED=true\n")
                .append("      - SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092\n")
                .append("      - SPRING_KAFKA_CONSUMER_AUTO_OFFSET_RESET=earliest\n")
                .append("      - SPRING_MAIN_WEB_APPLICATION_TYPE=none\n")
                .append("      - JAVA_TOOL_OPTIONS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:")
                .append(containerDebugPort).append("\n")
                .append("      - SIMULATION_DISTRIBUTED_FINISH_STORE_ENABLED=true\n")
                .append("      - SIMULATION_DISTRIBUTED_FINISH_STORE_TOPIC=").append(finishTopic).append("\n")
                .append("      - SIMULATION_DISTRIBUTED_FINISH_STORE_GROUP_ID=")
                .append(DistributedSimulationTopics.finishStoreGroupId(routeId)).append("\n");
    }

    int hostDebugPort(String routeId, int operationId) {
        int routeSlot = Math.floorMod(DistributedSimulationTopics.sanitize(routeId).hashCode(), HOST_DEBUG_PORT_ROUTE_SLOTS);
        return HOST_DEBUG_PORT_BASE + routeSlot * HOST_DEBUG_PORT_ROUTE_STRIDE + operationId;
    }

    private boolean isStore(String name) {
        String normalized = normalize(name);
        return normalized.equals("startstore") || normalized.equals("finishstore");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
