package com.factory.productionline.service;

import com.factory.productionline.model.ProductionLine;

public interface DistributedWorkerProvisioner {

    void ensureWorkers(ProductionLine.LinearSimulationInput input);
}
