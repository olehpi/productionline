package com.factory.productionline.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TechnologicalOperationsLoaderTest {

	private final TechnologicalOperationsLoader loader = new TechnologicalOperationsLoader(new ObjectMapper());

	@TempDir
	Path tempDir;

	@Test
	void shouldLoadTechnologicalOperationsFromJsonFile() throws Exception {
		Path jsonFile = tempDir.resolve("operations.json");
		Files.writeString(
			jsonFile,
			"""
			{
			  "operations": [
			    {
			      "code": "CUT-001",
			      "name": "Laser cutting",
			      "durationSeconds": 120,
			      "parameters": {
			        "material": "steel",
			        "thickness": "2mm"
			      }
			    }
			  ]
			}
			"""
		);

		TechnologicalOperationsConfig result = loader.loadFromPath(jsonFile.toString());

		assertEquals(1, result.operations().size());
		assertEquals("CUT-001", result.operations().getFirst().code());
	}

	@Test
	void shouldThrowWhenJsonFileDoesNotExist() {
		Path missingFile = tempDir.resolve("missing.json");

		assertThrows(IllegalArgumentException.class, () -> loader.loadFromPath(missingFile.toString()));
	}
}
