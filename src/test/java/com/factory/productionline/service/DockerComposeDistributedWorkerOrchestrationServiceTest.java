package com.factory.productionline.service;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerComposeDistributedWorkerOrchestrationServiceTest {

    private final DockerComposeDistributedWorkerOrchestrationService service =
            new DockerComposeDistributedWorkerOrchestrationService(
                    new DistributedComposeGenerator("productionline-productionline"),
                    new DistributedRouteRegistry(),
                    new KafkaAdmin(Map.of("bootstrap.servers", "localhost:9092")),
                    ".",
                    "docker-compose.yml",
                    "docker-compose.operations.yml",
                    120000,
                    3000,
                    3,
                    1500
            );

    @Test
    void recognizesTransientDockerNetworkingFailures() {
        assertTrue(service.isTransientDockerStartupFailure(
                "Error response from daemon: failed to set up container networking: failed to add interface veth79a6249 "
                        + "to sandbox: check bridge port state: bridge port not forwarding after 200ms"
        ));
    }

    @Test
    void ignoresNonTransientDockerFailures() {
        assertFalse(service.isTransientDockerStartupFailure(
                "Failed to start operation workers with docker compose. Output: Bind for 0.0.0.0:5105 failed: port is already allocated"
        ));
    }
}
