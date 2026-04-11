package com.factory.productionline.service;

import com.factory.productionline.model.ProductionLine;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DistributedRouteRegistry {

    private final Map<String, RegisteredRoute> routesByRouteId = new ConcurrentHashMap<>();
    private final Map<String, String> routeIdsByBatchId = new ConcurrentHashMap<>();

    public void registerRoute(ProductionLine.LinearSimulationInput input) {
        RegisteredRoute route = RegisteredRoute.from(input);
        RegisteredRoute existing = routesByRouteId.putIfAbsent(input.routeId(), route);
        if (existing != null) {
            throw new IllegalStateException("Route with routeId=" + input.routeId() + " already exists");
        }
    }

    public void ensureRouteMatches(ProductionLine.LinearSimulationInput input) {
        RegisteredRoute route = getRegisteredRoute(input.routeId());
        if (!route.matches(input)) {
            throw new IllegalStateException("Route with routeId=" + input.routeId() + " exists but payload operations do not match registered route");
        }
    }

    public void bindBatchToRoute(String routeId, String batchId) {
        getRegisteredRoute(routeId);
        String existingRouteId = routeIdsByBatchId.putIfAbsent(batchId, routeId);
        if (existingRouteId != null && !existingRouteId.equals(routeId)) {
            throw new IllegalStateException(
                    "Batch with batchId=" + batchId + " is already bound to routeId=" + existingRouteId
            );
        }
    }

    public String getRouteIdByBatchId(String batchId) {
        String routeId = routeIdsByBatchId.get(batchId);
        if (routeId == null) {
            throw new IllegalStateException("Batch with batchId=" + batchId + " is not bound to any route");
        }
        return routeId;
    }

    public void ensureRouteRegistered(String routeId) {
        getRegisteredRoute(routeId);
    }

    public void ensureBatchBoundToRoute(String routeId, String batchId) {
        String registeredRouteId = getRouteIdByBatchId(batchId);
        if (!registeredRouteId.equals(routeId)) {
            throw new IllegalStateException(
                    "Batch with batchId=" + batchId + " is already bound to routeId=" + registeredRouteId
            );
        }
    }

    private RegisteredRoute getRegisteredRoute(String routeId) {
        RegisteredRoute route = routesByRouteId.get(routeId);
        if (route == null) {
            throw new IllegalStateException("Route with routeId=" + routeId + " is not registered");
        }
        return route;
    }

    private record RegisteredRoute(
            int operationsCount,
            List<RegisteredOperation> operations
    ) {
        private static RegisteredRoute from(ProductionLine.LinearSimulationInput input) {
            return new RegisteredRoute(
                    input.operationsCount(),
                    input.operations().stream()
                            .map(operation -> new RegisteredOperation(
                                    operation.id(),
                                    operation.name(),
                                    operation.tauMean(),
                                    operation.tauSigma(),
                                    operation.randomSeed()
                            ))
                            .toList()
            );
        }

        private boolean matches(ProductionLine.LinearSimulationInput input) {
            return operationsCount == input.operationsCount()
                    && operations.equals(input.operations().stream()
                    .map(operation -> new RegisteredOperation(
                            operation.id(),
                            operation.name(),
                            operation.tauMean(),
                            operation.tauSigma(),
                            operation.randomSeed()
                    ))
                    .toList());
        }
    }

    private record RegisteredOperation(
            int id,
            String name,
            double tauMean,
            double tauSigma,
            Long randomSeed
    ) {
    }
}
