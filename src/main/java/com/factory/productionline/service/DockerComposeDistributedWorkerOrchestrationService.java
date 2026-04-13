package com.factory.productionline.service;

import com.factory.productionline.model.ProductionLine;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "simulation.orchestration.from-api.enabled", havingValue = "true")
public class DockerComposeDistributedWorkerOrchestrationService implements DistributedWorkerOrchestrationService {

    private final DistributedComposeGenerator distributedComposeGenerator;
    private final DistributedRouteRegistry distributedRouteRegistry;
    private final KafkaAdmin kafkaAdmin;
    private final String composeProjectDir;
    private final String composeBaseFile;
    private final String composeOverrideFile;
    private final long readyTimeoutMillis;
    private final long readyPollIntervalMillis;
    private final int startRetries;
    private final long retryDelayMillis;

    public DockerComposeDistributedWorkerOrchestrationService(
            DistributedComposeGenerator distributedComposeGenerator,
            DistributedRouteRegistry distributedRouteRegistry,
            KafkaAdmin kafkaAdmin,
            @Value("${simulation.orchestration.compose.project-dir:.}") String composeProjectDir,
            @Value("${simulation.orchestration.compose.base-file:docker-compose.yml}") String composeBaseFile,
            @Value("${simulation.orchestration.compose.override-file:docker-compose.operations.yml}") String composeOverrideFile,
            @Value("${simulation.orchestration.workers.ready-timeout-ms:120000}") long readyTimeoutMillis,
            @Value("${simulation.orchestration.workers.ready-poll-interval-ms:3000}") long readyPollIntervalMillis,
            @Value("${simulation.orchestration.workers.start-retries:3}") int startRetries,
            @Value("${simulation.orchestration.workers.retry-delay-ms:1500}") long retryDelayMillis
    ) {
        this.distributedComposeGenerator = distributedComposeGenerator;
        this.distributedRouteRegistry = distributedRouteRegistry;
        this.kafkaAdmin = kafkaAdmin;
        this.composeProjectDir = composeProjectDir;
        this.composeBaseFile = composeBaseFile;
        this.composeOverrideFile = composeOverrideFile;
        this.readyTimeoutMillis = readyTimeoutMillis;
        this.readyPollIntervalMillis = readyPollIntervalMillis;
        this.startRetries = Math.max(1, startRetries);
        this.retryDelayMillis = retryDelayMillis;
    }

    @Override
    public void applyWorkers(ProductionLine.DistributedRouteInput input) {
        distributedRouteRegistry.registerRoute(input);
        String overrideYaml = distributedComposeGenerator.generate(input);
        Path projectDir = Path.of(composeProjectDir).toAbsolutePath().normalize();
        Path overridePath = routeOverridePath(projectDir, input.routeId());
        String overrideFileName = overridePath.getFileName().toString();

        try {
            Files.createDirectories(overridePath.getParent());
            Files.writeString(overridePath, overrideYaml, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write compose override file: " + overridePath, exception);
        }

        List<String> expectedServices = expectedWorkerServices(input);

        List<String> upCommand = new ArrayList<>(List.of(
                "docker", "compose", "-f", composeBaseFile, "-f", overrideFileName, "up", "-d"
        ));
        upCommand.addAll(expectedServices);

        runCommandWithRetries(projectDir,
                upCommand,
                "Failed to start operation workers with docker compose"
        );

        waitForWorkers(projectDir, expectedServices, overrideFileName);
        waitForKafkaConsumerGroups(input);
    }

    private void waitForWorkers(Path projectDir, List<String> expectedServices, String overrideFileName) {
        Instant deadline = Instant.now().plus(Duration.ofMillis(readyTimeoutMillis));
        while (Instant.now().isBefore(deadline)) {
            String output = runCommand(projectDir,
                    List.of("docker", "compose", "-f", composeBaseFile, "-f", overrideFileName,
                            "ps", "--services", "--status", "running"),
                    "Failed to read running docker compose services"
            );
            List<String> running = output.lines()
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .toList();
            if (running.containsAll(expectedServices)) {
                return;
            }
            sleep(readyPollIntervalMillis);
        }

        throw new IllegalStateException("Timed out waiting for distributed workers readiness");
    }


    private List<String> expectedWorkerServices(ProductionLine.DistributedRouteInput input) {
        List<String> services = input.operations().stream()
                .filter(operation -> !isStore(operation.name()))
                .map(operation -> DistributedSimulationTopics.workerServiceName(input.routeId(), operation.id()))
                .sorted()
                .collect(Collectors.toCollection(ArrayList::new));
        services.add(DistributedSimulationTopics.finishStoreServiceName(input.routeId()));
        return services;
    }

    private List<String> expectedConsumerGroups(ProductionLine.DistributedRouteInput input) {
        List<String> groups = input.operations().stream()
                .filter(operation -> !isStore(operation.name()))
                .map(operation -> DistributedSimulationTopics.workerGroupId(input.routeId(), operation.id()))
                .sorted()
                .collect(Collectors.toCollection(ArrayList::new));
        groups.add(DistributedSimulationTopics.finishStoreGroupId(input.routeId()));
        return groups;
    }

    private void waitForKafkaConsumerGroups(ProductionLine.DistributedRouteInput input) {
        List<String> expectedGroups = expectedConsumerGroups(input);
        Instant deadline = Instant.now().plus(Duration.ofMillis(readyTimeoutMillis));

        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            while (Instant.now().isBefore(deadline)) {
                if (allConsumerGroupsReady(adminClient, expectedGroups)) {
                    return;
                }
                sleep(readyPollIntervalMillis);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed while waiting for Kafka consumer groups readiness", exception);
        }

        throw new IllegalStateException(
                "Timed out waiting for Kafka consumer groups readiness: " + String.join(", ", expectedGroups)
        );
    }

    private boolean allConsumerGroupsReady(AdminClient adminClient, Collection<String> expectedGroups)
            throws ExecutionException, InterruptedException {
        Map<String, ConsumerGroupDescription> descriptions = adminClient.describeConsumerGroups(expectedGroups)
                .all()
                .get();
        return descriptions.values().stream().allMatch(description -> !description.members().isEmpty());
    }

    private Path routeOverridePath(Path projectDir, String routeId) {
        String fileName = composeOverrideFile;
        int extensionIndex = composeOverrideFile.lastIndexOf('.');
        if (extensionIndex > 0) {
            fileName = composeOverrideFile.substring(0, extensionIndex)
                    + "-" + DistributedSimulationTopics.sanitize(routeId)
                    + composeOverrideFile.substring(extensionIndex);
        } else {
            fileName = composeOverrideFile + "-" + DistributedSimulationTopics.sanitize(routeId);
        }
        return projectDir.resolve(fileName);
    }

    private String runCommand(Path projectDir, List<String> command, String failureMessage) {
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(projectDir.toFile())
                .redirectErrorStream(true);

        try {
            Process process = builder.start();
            String output;
            try (var inputStream = process.getInputStream()) {
                output = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
            int exit = process.waitFor();
            if (exit != 0) {
                throw new IllegalStateException(failureMessage + ". Command: " + String.join(" ", command) +
                        ". Output: " + output);
            }
            return output;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(failureMessage + ". Command: " + String.join(" ", command), exception);
        } catch (IOException exception) {
            throw new IllegalStateException(failureMessage + ". Command: " + String.join(" ", command), exception);
        }
    }

    private void runCommandWithRetries(Path projectDir, List<String> command, String failureMessage) {
        IllegalStateException lastFailure = null;

        for (int attempt = 1; attempt <= startRetries; attempt++) {
            try {
                runCommand(projectDir, command, failureMessage);
                return;
            } catch (IllegalStateException exception) {
                lastFailure = exception;
                if (attempt >= startRetries || !isTransientDockerStartupFailure(exception.getMessage())) {
                    throw exception;
                }
                sleep(retryDelayMillis);
            }
        }

        throw lastFailure == null
                ? new IllegalStateException(failureMessage + ". Command: " + String.join(" ", command))
                : lastFailure;
    }

    boolean isTransientDockerStartupFailure(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("failed to set up container networking")
                || normalized.contains("failed to add interface")
                || normalized.contains("bridge port not forwarding");
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for workers readiness", exception);
        }
    }

    private boolean isStore(String name) {
        String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("startstore") || normalized.equals("finishstore");
    }
}
