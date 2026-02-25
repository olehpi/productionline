package com.factory.productionline.operation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Service;

@Service
public class TechnologicalOperationsJsonLoader {

	private final ObjectMapper objectMapper;

	public TechnologicalOperationsJsonLoader(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public TechnologicalOperationsConfig loadFromPath(String filePath) {
		if (filePath == null || filePath.isBlank()) {
			throw new IllegalArgumentException("Path to JSON file must not be blank");
		}

		Path path = Path.of(filePath);
		if (!Files.exists(path)) {
			throw new IllegalArgumentException("JSON file not found: " + filePath);
		}

		try {
			return objectMapper.readValue(path.toFile(), TechnologicalOperationsConfig.class);
		} catch (IOException exception) {
			throw new UncheckedIOException("Failed to read operations config from: " + filePath, exception);
		}
	}
}
