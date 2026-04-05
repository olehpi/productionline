package com.factory.productionline.service;

import com.factory.productionline.model.ProductionLine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(DistributedWorkerOrchestrationService.class)
public class NoOpDistributedWorkerOrchestrationService implements DistributedWorkerOrchestrationService {

    @Override
    public void ensureWorkersAndStartBatch(ProductionLine.LinearSimulationInput input) {
        // no-op fallback: preserve historical /linear behavior when orchestration bean is not active
    }
}
