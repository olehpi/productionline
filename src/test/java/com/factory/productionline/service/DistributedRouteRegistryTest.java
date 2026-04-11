package com.factory.productionline.service;

import com.factory.productionline.model.ProductionLine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DistributedRouteRegistryTest {

    @Test
    void registerRouteRejectsDuplicateRouteId() {
        DistributedRouteRegistry registry = new DistributedRouteRegistry();
        ProductionLine.LinearSimulationInput route = input("route-42", "batch-42");

        registry.registerRoute(route);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> registry.registerRoute(input("route-42", "batch-43"))
        );
        assertEquals("Route with routeId=route-42 already exists", exception.getMessage());
    }

    @Test
    void bindBatchToRouteRejectsDifferentRouteForExistingBatch() {
        DistributedRouteRegistry registry = new DistributedRouteRegistry();
        registry.registerRoute(input("route-42", "batch-42"));
        registry.registerRoute(input("route-50", "batch-50"));
        registry.bindBatchToRoute("route-42", "batch-42");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> registry.bindBatchToRoute("route-50", "batch-42")
        );
        assertEquals("Batch with batchId=batch-42 is already bound to routeId=route-42", exception.getMessage());
    }

    @Test
    void bindBatchToRouteAllowsRestartInsideSameRoute() {
        DistributedRouteRegistry registry = new DistributedRouteRegistry();
        registry.registerRoute(input("route-42", "batch-42"));
        registry.bindBatchToRoute("route-42", "batch-42");

        assertDoesNotThrow(() -> registry.bindBatchToRoute("route-42", "batch-42"));
        assertEquals("route-42", registry.getRouteIdByBatchId("batch-42"));
    }

    private ProductionLine.LinearSimulationInput input(String routeId, String batchId) {
        return new ProductionLine.LinearSimulationInput(
                routeId,
                3,
                2,
                batchId,
                0.0,
                30.0,
                List.of(
                        new ProductionLine.LinearOperationInput(0, "startStore", 0.0, 0.0, 0L),
                        new ProductionLine.LinearOperationInput(1, "Op01", 10.0, 0.0, 1L),
                        new ProductionLine.LinearOperationInput(2, "Op02", 10.0, 0.0, 2L),
                        new ProductionLine.LinearOperationInput(3, "finishStore", 0.0, 0.0, 0L)
                )
        );
    }
}
