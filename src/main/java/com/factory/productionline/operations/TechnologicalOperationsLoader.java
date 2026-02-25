package com.factory.productionline.operations;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Service;

@Service
public class TechnologicalOperationsLoader {

	private final ObjectMapper objectMapper;

	public TechnologicalOperationsLoader(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public TechnologicalOperationsConfig loadFromPath(String jsonFilePath) {
		if (jsonFilePath == null || jsonFilePath.isBlank()) {
			throw new IllegalArgumentException("Path to technological operations JSON file is not configured.");
		}

		Path path = Path.of(jsonFilePath);
		if (!Files.exists(path)) {
			throw new IllegalArgumentException("JSON file with technological operations was not found: " + path);
		}

		try {
			return objectMapper.readValue(path.toFile(), TechnologicalOperationsConfig.class);
		} catch (IOException exception) {
			throw new IllegalStateException(
				"Failed to read technological operations from JSON file: " + path,
				exception
			);
		}
	}
}
