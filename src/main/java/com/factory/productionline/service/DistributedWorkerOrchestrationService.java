package com.factory.productionline.service;

import com.factory.productionline.model.ProductionLine;

public interface DistributedWorkerOrchestrationService {
    void applyWorkers(ProductionLine.LinearSimulationInput input);
}
