package com.factory.productionline.service;

import com.factory.productionline.model.ProductionLine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistributedComposeGeneratorTest {

    private final DistributedComposeGenerator generator = new DistributedComposeGenerator();

    @Test
    void generateReturnsComposeForValidLinearInput() {
        ProductionLine.LinearSimulationInput input = new ProductionLine.LinearSimulationInput(
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

        assertTrue(compose.contains("productionline-operation1-app"));
        assertTrue(compose.contains("SIMULATION_DISTRIBUTED_WORKER_INBOUND_TOPIC=line-op-1-to-2"));
        assertTrue(compose.contains("productionline-finish-store-app"));
    }

    @Test
    void generateFailsWhenFinishStoreIdIsInvalid() {
        ProductionLine.LinearSimulationInput input = new ProductionLine.LinearSimulationInput(
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
