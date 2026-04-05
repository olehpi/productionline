package com.factory.productionline.service;

import com.factory.productionline.model.ProductionLine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "simulation.distributed.auto-provision.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpDistributedWorkerProvisioner implements DistributedWorkerProvisioner {

    @Override
    public void ensureWorkers(ProductionLine.LinearSimulationInput input) {
        // no-op
    }
}
