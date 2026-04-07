package com.factory.productionline.service;

import com.factory.productionline.model.DistributedBatchResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "simulation.kafka.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpDistributedTelemetryQueryService extends DistributedTelemetryQueryService {

    public NoOpDistributedTelemetryQueryService() {
        super(null, null, 0L, 0L);
    }

    @Override
    public DistributedBatchResult getBatchResult(String batchId) {
        throw new IllegalStateException("Kafka telemetry is disabled. Set simulation.kafka.enabled=true to query distributed telemetry");
    }
}
