package com.factory.productionline.operation;

import java.util.Map;

public record TechnologicalOperationParameters(
	String operationId,
	String operationName,
	Integer durationSeconds,
	Map<String, Object> parameters
) {
}
