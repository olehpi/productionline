package com.factory.productionline.operations;

import java.util.Map;

public record TechnologicalOperation(
	String code,
	String name,
	int durationSeconds,
	Map<String, String> parameters
) {
}
