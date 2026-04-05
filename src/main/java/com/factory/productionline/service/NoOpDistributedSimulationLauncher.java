package com.factory.productionline.service;

import com.factory.productionline.graph.DistributedSimulationStartResponse;
import com.factory.productionline.model.ProductionLine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "simulation.kafka.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpDistributedSimulationLauncher implements DistributedSimulationLauncher {

    @Override
    public DistributedSimulationStartResponse start(ProductionLine.LinearSimulationInput input) {
        throw new IllegalArgumentException("Kafka is disabled. Set simulation.kafka.enabled=true to start distributed flow");
    }
}
