package com.factory.productionline.service;

import com.factory.productionline.model.ProductionLine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "simulation.orchestration.from-api.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpDistributedWorkerOrchestrationService implements DistributedWorkerOrchestrationService {

    @Override
    public void applyWorkers(ProductionLine.DistributedRouteInput input) {
        throw new IllegalStateException("API-driven distributed orchestration is disabled");
    }
}
