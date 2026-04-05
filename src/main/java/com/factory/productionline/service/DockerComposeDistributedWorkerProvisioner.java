package com.factory.productionline.service;

import com.factory.productionline.model.ProductionLine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(name = "simulation.distributed.auto-provision.enabled", havingValue = "true")
public class DockerComposeDistributedWorkerProvisioner implements DistributedWorkerProvisioner {

    private final Path workDir;
    private final String composeBaseFile;
    private final String composeWorkersFile;
    private final String dockerCommand;

    public DockerComposeDistributedWorkerProvisioner(
            @Value("${simulation.distributed.auto-provision.workdir:.}") String workDir,
            @Value("${simulation.distributed.auto-provision.compose-base-file:docker-compose.yml}") String composeBaseFile,
            @Value("${simulation.distributed.auto-provision.compose-workers-file:docker-compose.operations.auto.yml}") String composeWorkersFile,
            @Value("${simulation.distributed.auto-provision.docker-command:docker}") String dockerCommand
    ) {
        this.workDir = Path.of(workDir).toAbsolutePath().normalize();
        this.composeBaseFile = composeBaseFile;
        this.composeWorkersFile = composeWorkersFile;
        this.dockerCommand = dockerCommand;
    }

    @Override
    public synchronized void ensureWorkers(ProductionLine.LinearSimulationInput input) {
        try {
            Path output = workDir.resolve(composeWorkersFile);
            Files.writeString(output, buildComposeOverride(input));

            runCommand(List.of(
                    dockerCommand,
                    "compose",
                    "-f",
                    composeBaseFile,
                    "-f",
                    composeWorkersFile,
                    "up",
                    "-d",
                    "--build"
            ));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to auto-provision distributed workers", exception);
        }
    }

    private String buildComposeOverride(ProductionLine.LinearSimulationInput input) {
        StringBuilder builder = new StringBuilder("services:\n");

        List<ProductionLine.LinearOperationInput> operations = input.operations().stream()
                .filter(operation -> !isStoreOperation(operation))
                .toList();

        if (operations.size() != input.operationsCount()) {
            throw new IllegalArgumentException(
                    "operationsCount must match count of non-store operations for distributed auto-provision"
            );
        }

        for (ProductionLine.LinearOperationInput operation : operations) {
            int operationId = operation.id();
            builder.append("  productionline-operation").append(operationId).append("-app:\n")
                    .append("    build:\n")
                    .append("      context: .\n")
                    .append("      dockerfile: Dockerfile\n")
                    .append("    depends_on:\n")
                    .append("      kafka:\n")
                    .append("        condition: service_healthy\n")
                    .append("    environment:\n")
                    .append("      - SIMULATION_KAFKA_ENABLED=true\n")
                    .append("      - SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092\n")
                    .append("      - SPRING_MAIN_WEB_APPLICATION_TYPE=none\n")
                    .append("      - SIMULATION_DISTRIBUTED_WORKER_ENABLED=true\n")
                    .append("      - SIMULATION_DISTRIBUTED_WORKER_GROUP_ID=productionline-operation-").append(operationId).append("\n")
                    .append("      - SIMULATION_DISTRIBUTED_WORKER_INBOUND_TOPIC=line-op-").append(operationId - 1).append("-to-").append(operationId).append("\n")
                    .append("      - SIMULATION_DISTRIBUTED_WORKER_OPERATION_ID=").append(operationId).append("\n")
                    .append("      - SIMULATION_DISTRIBUTED_WORKER_NEXT_OPERATION_ID=").append(operationId + 1).append("\n")
                    .append("      - SIMULATION_DISTRIBUTED_WORKER_TAU_MEAN=").append(operation.tauMean()).append("\n")
                    .append("      - SIMULATION_DISTRIBUTED_WORKER_TAU_SIGMA=").append(operation.tauSigma()).append("\n")
                    .append("      - SIMULATION_DISTRIBUTED_WORKER_RANDOM_SEED=").append(operation.randomSeed() == null ? 0 : operation.randomSeed()).append("\n");
        }

        builder.append("  productionline-finish-store-app:\n")
                .append("    build:\n")
                .append("      context: .\n")
                .append("      dockerfile: Dockerfile\n")
                .append("    depends_on:\n")
                .append("      kafka:\n")
                .append("        condition: service_healthy\n")
                .append("    environment:\n")
                .append("      - SIMULATION_KAFKA_ENABLED=true\n")
                .append("      - SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092\n")
                .append("      - SPRING_MAIN_WEB_APPLICATION_TYPE=none\n")
                .append("      - SIMULATION_DISTRIBUTED_FINISH_STORE_ENABLED=true\n")
                .append("      - SIMULATION_DISTRIBUTED_FINISH_STORE_TOPIC=line-op-").append(input.operationsCount()).append("-to-").append(input.operationsCount() + 1).append("\n")
                .append("      - SIMULATION_DISTRIBUTED_FINISH_STORE_GROUP_ID=productionline-finish-store\n");

        return builder.toString();
    }

    private boolean isStoreOperation(ProductionLine.LinearOperationInput operation) {
        String normalized = operation.name() == null ? "" : operation.name().trim().toLowerCase();
        return normalized.equals("startstore") || normalized.equals("finishstore");
    }

    private void runCommand(List<String> command) throws IOException {
        Process process = new ProcessBuilder(new ArrayList<>(command))
                .directory(workDir.toFile())
                .redirectErrorStream(true)
                .start();

        String output = new String(process.getInputStream().readAllBytes());
        try {
            int code = process.waitFor();
            if (code != 0) {
                throw new IllegalStateException("Command failed (" + String.join(" ", command) + "): " + output);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Command interrupted: " + String.join(" ", command), exception);
        }
    }
}
