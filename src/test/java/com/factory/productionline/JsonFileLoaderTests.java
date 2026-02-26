package com.factory.productionline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonFileLoaderTests {

	private final JsonFileLoader jsonFileLoader = new JsonFileLoader(new ObjectMapper());

	@Test
	void loadFromPathReadsJsonFile(@TempDir Path tempDir) throws IOException {
		Path file = tempDir.resolve("config.json");
		Files.writeString(file, """
				{
				  \"line\": \"A\",
				  \"capacity\": 120
				}
				""");

		JsonNode result = jsonFileLoader.loadFromPath(file.toString());

		assertEquals("A", result.get("line").asText());
		assertEquals(120, result.get("capacity").asInt());
	}

	@Test
	void loadFromPathThrowsForBlankPath() {
		assertThrows(IllegalArgumentException.class, () -> jsonFileLoader.loadFromPath("  "));
	}

	@Test
	void loadFromPathThrowsForMissingFile(@TempDir Path tempDir) {
		Path missingFile = tempDir.resolve("missing.json");

		assertThrows(IllegalArgumentException.class, () -> jsonFileLoader.loadFromPath(missingFile.toString()));
	}
}
