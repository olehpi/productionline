package com.factory.productionline.service;

import com.factory.productionline.graph.DistributedSimulationStartResponse;
import com.factory.productionline.model.ProductionLine;

public interface DistributedSimulationLauncher {

    DistributedSimulationStartResponse start(ProductionLine.LinearSimulationInput input);
}
