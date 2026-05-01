package com.factory.productionline.service;

import com.factory.productionline.model.ProductionLine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistributedComposeGeneratorTest {

    private final DistributedComposeGenerator generator = new DistributedComposeGenerator("productionline-productionline");

    @Test
    void generateReturnsComposeForValidLinearInput() {
        int operation1HostPort = generator.hostDebugPort("route-42", 1);
        int finishStoreHostPort = generator.hostDebugPort("route-42", 3);
        ProductionLine.LinearSimulationInput input = new ProductionLine.LinearSimulationInput(
                "route-42",
                3,
                2,
                "batch-42",
                0.0,
                30.0,
                List.of(
                        new ProductionLine.LinearOperationInput(0, "startStore", 0.0, 0.0, 0L),
                        new ProductionLine.LinearOperationInput(1, "Op01", 10.0, 0.0, 1L, 2),
                        new ProductionLine.LinearOperationInput(2, "Op02", 10.0, 0.0, 2L),
                        new ProductionLine.LinearOperationInput(3, "finishStore", 10.0, 0.0, 0L)
                )
        );

        String compose = generator.generate(input);

        assertTrue(compose.contains("productionline-route-42-operation1-app"));
        assertTrue(compose.contains("image: productionline-productionline"));
        assertTrue(compose.contains("SIMULATION_DISTRIBUTED_WORKER_INBOUND_TOPIC=route-42-line-op-1-to-2"));
        assertTrue(compose.contains("- \"" + operation1HostPort + ":5101\""));
        assertTrue(compose.contains("SPRING_KAFKA_CONSUMER_AUTO_OFFSET_RESET=earliest"));
        assertTrue(compose.contains("SIMULATION_DISTRIBUTED_WORKER_DOWNSTREAM_GROUP_ID=productionline-route-42-operation-2"));
        assertTrue(compose.contains("SIMULATION_DISTRIBUTED_WORKER_OUTPUT_BUFFER_CAPACITY=2"));
        assertTrue(compose.contains("JAVA_TOOL_OPTIONS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5101"));
        assertTrue(compose.contains("productionline-route-42-finish-store-app"));
        assertTrue(compose.contains("- \"" + finishStoreHostPort + ":5103\""));
    }

    @Test
    void generateUsesDifferentHostDebugPortsForDifferentRoutes() {
        assertTrue(generator.hostDebugPort("route100", 5) != generator.hostDebugPort("route105", 5));
    }

    @Test
    void generatePassesOutputBufferCapacityForLastTechnologicalOperation() {
        ProductionLine.LinearSimulationInput input = new ProductionLine.LinearSimulationInput(
                "route-42",
                3,
                2,
                "batch-42",
                0.0,
                30.0,
                List.of(
                        new ProductionLine.LinearOperationInput(0, "startStore", 0.0, 0.0, 0L),
                        new ProductionLine.LinearOperationInput(1, "Op01", 10.0, 0.0, 1L),
                        new ProductionLine.LinearOperationInput(2, "Op02", 10.0, 0.0, 2L, 100),
                        new ProductionLine.LinearOperationInput(3, "finishStore", 10.0, 0.0, 0L)
                )
        );

        String compose = generator.generate(input);

        assertTrue(compose.contains("productionline-route-42-operation2-app"));
        assertTrue(compose.contains("SIMULATION_DISTRIBUTED_WORKER_DOWNSTREAM_GROUP_ID=productionline-route-42-finish-store"));
        assertTrue(compose.contains("SIMULATION_DISTRIBUTED_WORKER_OUTPUT_BUFFER_CAPACITY=100"));
    }

    @Test
    void generateOmitsOutputBufferCapacityWhenOperationHasUnlimitedOutputBuffer() {
        ProductionLine.LinearSimulationInput input = new ProductionLine.LinearSimulationInput(
                "route-42",
                3,
                2,
                "batch-42",
                0.0,
                30.0,
                List.of(
                        new ProductionLine.LinearOperationInput(0, "startStore", 0.0, 0.0, 0L),
                        new ProductionLine.LinearOperationInput(1, "Op01", 10.0, 0.0, 1L),
                        new ProductionLine.LinearOperationInput(2, "Op02", 10.0, 0.0, 2L),
                        new ProductionLine.LinearOperationInput(3, "finishStore", 10.0, 0.0, 0L)
                )
        );

        String compose = generator.generate(input);

        assertFalse(compose.contains("SIMULATION_DISTRIBUTED_WORKER_OUTPUT_BUFFER_CAPACITY"));
    }

    @Test
    void generateFailsWhenFinishStoreIdIsInvalid() {
        ProductionLine.LinearSimulationInput input = new ProductionLine.LinearSimulationInput(
                "route-42",
                3,
                2,
                "batch-42",
                0.0,
                30.0,
                List.of(
                        new ProductionLine.LinearOperationInput(0, "startStore", 0.0, 0.0, 0L),
                        new ProductionLine.LinearOperationInput(1, "Op01", 10.0, 0.0, 1L),
                        new ProductionLine.LinearOperationInput(2, "Op02", 10.0, 0.0, 2L),
                        new ProductionLine.LinearOperationInput(8, "finishStore", 10.0, 0.0, 0L)
                )
        );

        assertThrows(IllegalArgumentException.class, () -> generator.generate(input));
    }
}
