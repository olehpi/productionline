package com.factory.productionline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class JsonFileLoader {

	private final ObjectMapper objectMapper;

	public JsonFileLoader(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public JsonNode loadFromPath(String filePath) throws IOException {
		if (filePath == null || filePath.isBlank()) {
			throw new IllegalArgumentException("File path must not be blank");
		}

		Path path = Path.of(filePath);
		if (!Files.exists(path)) {
			throw new IllegalArgumentException("JSON file does not exist: " + filePath);
		}

		if (!Files.isRegularFile(path)) {
			throw new IllegalArgumentException("Path is not a regular file: " + filePath);
		}

		return objectMapper.readTree(path.toFile());
	}
}
