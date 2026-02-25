package com.factory.productionline.operation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operations")
public class TechnologicalOperationsController {

	private final TechnologicalOperationsJsonLoader operationsJsonLoader;

	public TechnologicalOperationsController(TechnologicalOperationsJsonLoader operationsJsonLoader) {
		this.operationsJsonLoader = operationsJsonLoader;
	}

	@GetMapping("/load")
	public TechnologicalOperationsConfig loadOperationsFromFile(@RequestParam("path") String path) {
		return operationsJsonLoader.loadFromPath(path);
	}
}
