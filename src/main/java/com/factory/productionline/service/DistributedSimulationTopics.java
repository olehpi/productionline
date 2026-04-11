package com.factory.productionline.service;

public final class DistributedSimulationTopics {

    private DistributedSimulationTopics() {
    }

    public static String operationTopic(String routeId, int fromOperation, int toOperation) {
        return sanitize(routeId) + "-line-op-" + fromOperation + "-to-" + toOperation;
    }

    public static String operationEventsTopic(String routeId) {
        return sanitize(routeId) + "-line-operation-events";
    }

    public static String workerGroupId(String routeId, int operationId) {
        return "productionline-" + sanitize(routeId) + "-operation-" + operationId;
    }

    public static String finishStoreGroupId(String routeId) {
        return "productionline-" + sanitize(routeId) + "-finish-store";
    }

    public static String workerServiceName(String routeId, int operationId) {
        return "productionline-" + sanitize(routeId) + "-operation" + operationId + "-app";
    }

    public static String finishStoreServiceName(String routeId) {
        return "productionline-" + sanitize(routeId) + "-finish-store-app";
    }

    public static String sanitize(String value) {
        return value == null ? "route" : value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
