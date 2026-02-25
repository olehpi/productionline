package com.factory.productionline.operation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TechnologicalOperationsJsonLoaderTest {

	private final TechnologicalOperationsJsonLoader loader = new TechnologicalOperationsJsonLoader(new ObjectMapper());

	@Test
	void shouldLoadOperationsFromJsonFile(@TempDir Path tempDir) throws IOException {
		Path jsonFile = tempDir.resolve("operations.json");
		Files.writeString(jsonFile, """
			{
			  "operations": [
			    {
			      "operationId": "cut-01",
			      "operationName": "Cutting",
			      "durationSeconds": 45,
			      "parameters": {
			        "speed": 1200,
			        "tool": "laser"
			      }
			    }
			  ]
			}
			""");

		TechnologicalOperationsConfig config = loader.loadFromPath(jsonFile.toString());

		assertEquals(1, config.operations().size());
		assertEquals("cut-01", config.operations().getFirst().operationId());
		assertEquals("laser", config.operations().getFirst().parameters().get("tool"));
	}

	@Test
	void shouldThrowWhenPathIsBlank() {
		assertThrows(IllegalArgumentException.class, () -> loader.loadFromPath(" "));
	}
}
