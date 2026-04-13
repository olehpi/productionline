package com.factory.productionline.service;

import com.factory.productionline.graph.DistributedSimulationStartResponse;
import com.factory.productionline.model.ProductionLine;

import java.util.ArrayList;
import java.util.List;

public interface DistributedSimulationLauncher {

    default DistributedSimulationStartResponse start(ProductionLine.LinearSimulationInput input) {
        return start(input, 0);
    }

    DistributedSimulationStartResponse start(ProductionLine.LinearSimulationInput input, int repetition);

    default List<DistributedSimulationStartResponse> startRepeated(
            ProductionLine.LinearSimulationInput input,
            int repetitions
    ) {
        List<DistributedSimulationStartResponse> responses = new ArrayList<>(repetitions);
        for (int repetition = 1; repetition <= repetitions; repetition++) {
            responses.add(start(input, repetition));
        }
        return List.copyOf(responses);
    }

    default List<List<DistributedSimulationStartResponse>> startRouteRepeated(
            List<ProductionLine.LinearSimulationInput> inputs,
            int repetitions
    ) {
        List<List<DistributedSimulationStartResponse>> responsesByBatch = new ArrayList<>(inputs.size());
        for (int batchIndex = 0; batchIndex < inputs.size(); batchIndex++) {
            responsesByBatch.add(new ArrayList<>(repetitions));
        }

        for (int repetition = 1; repetition <= repetitions; repetition++) {
            for (int batchIndex = 0; batchIndex < inputs.size(); batchIndex++) {
                responsesByBatch.get(batchIndex).add(start(inputs.get(batchIndex), repetition));
            }
        }

        return responsesByBatch.stream()
                .map(List::copyOf)
                .toList();
    }
}
