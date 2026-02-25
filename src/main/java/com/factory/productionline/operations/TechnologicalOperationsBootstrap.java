package com.factory.productionline.operations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class TechnologicalOperationsBootstrap implements ApplicationRunner {

	private static final Logger logger = LoggerFactory.getLogger(TechnologicalOperationsBootstrap.class);

	private final TechnologicalOperationsLoader operationsLoader;
	private final String operationsFilePath;

	public TechnologicalOperationsBootstrap(
		TechnologicalOperationsLoader operationsLoader,
		@Value("${productionline.operations.file-path:}") String operationsFilePath
	) {
		this.operationsLoader = operationsLoader;
		this.operationsFilePath = operationsFilePath;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (operationsFilePath.isBlank()) {
			logger.info("Path to technological operations file is not configured. Skip loading.");
			return;
		}

		TechnologicalOperationsConfig config = operationsLoader.loadFromPath(operationsFilePath);
		int operationsCount = config.operations() == null ? 0 : config.operations().size();
		logger.info("Loaded {} technological operation(s) from {}.", operationsCount, operationsFilePath);
	}
}
