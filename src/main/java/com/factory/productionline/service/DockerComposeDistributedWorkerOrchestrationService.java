package com.factory.productionline.service;

import com.factory.productionline.model.ProductionLine;
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
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "simulation.orchestration.from-api.enabled", havingValue = "true")
public class DockerComposeDistributedWorkerOrchestrationService implements DistributedWorkerOrchestrationService {

    private final DistributedComposeGenerator distributedComposeGenerator;
    private final DistributedSimulationLauncher distributedSimulationLauncher;
    private final String composeProjectDir;
    private final String composeBaseFile;
    private final String composeOverrideFile;
    private final long readyTimeoutMillis;
    private final long readyPollIntervalMillis;

    public DockerComposeDistributedWorkerOrchestrationService(
            DistributedComposeGenerator distributedComposeGenerator,
            DistributedSimulationLauncher distributedSimulationLauncher,
            @Value("${simulation.orchestration.compose.project-dir:.}") String composeProjectDir,
            @Value("${simulation.orchestration.compose.base-file:docker-compose.yml}") String composeBaseFile,
            @Value("${simulation.orchestration.compose.override-file:docker-compose.operations.yml}") String composeOverrideFile,
            @Value("${simulation.orchestration.workers.ready-timeout-ms:120000}") long readyTimeoutMillis,
            @Value("${simulation.orchestration.workers.ready-poll-interval-ms:3000}") long readyPollIntervalMillis
    ) {
        this.distributedComposeGenerator = distributedComposeGenerator;
        this.distributedSimulationLauncher = distributedSimulationLauncher;
        this.composeProjectDir = composeProjectDir;
        this.composeBaseFile = composeBaseFile;
        this.composeOverrideFile = composeOverrideFile;
        this.readyTimeoutMillis = readyTimeoutMillis;
        this.readyPollIntervalMillis = readyPollIntervalMillis;
    }

    @Override
    public void ensureWorkersAndStartBatch(ProductionLine.LinearSimulationInput input) {
        Path projectDir = Path.of(composeProjectDir).toAbsolutePath().normalize();
        List<String> expectedServices = expectedServices(input);

        if (!areWorkersRunning(projectDir, expectedServices)) {
            String overrideYaml = distributedComposeGenerator.generate(input);
            Path overridePath = projectDir.resolve(composeOverrideFile);
            try {
                Files.createDirectories(overridePath.getParent());
                Files.writeString(overridePath, overrideYaml, StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to write compose override file: " + overridePath, exception);
            }

            runCommand(projectDir,
                    List.of("docker", "compose", "-f", composeBaseFile, "-f", composeOverrideFile, "up", "--build", "-d"),
                    "Failed to start operation workers with docker compose"
            );
        }

        waitForWorkers(projectDir, expectedServices);
        distributedSimulationLauncher.start(input);
    }

    private void waitForWorkers(Path projectDir, List<String> expectedServices) {
        Instant deadline = Instant.now().plus(Duration.ofMillis(readyTimeoutMillis));
        while (Instant.now().isBefore(deadline)) {
            List<String> running = runningServices(projectDir);
            if (running.containsAll(expectedServices)) {
                return;
            }

            List<String> dockerComposeV1 = new ArrayList<>();
            dockerComposeV1.add("docker-compose");
            dockerComposeV1.addAll(composeArguments);
            return runCommand(projectDir, dockerComposeV1, failureMessage);
        }
    }

    private boolean isComposePluginMissing(IllegalStateException exception) {
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("docker: 'compose' is not a docker command")
                || normalized.contains("docker-compose-plugin")
                || normalized.contains("unknown command \"compose\"");
    }


    private boolean areWorkersRunning(Path projectDir, List<String> expectedServices) {
        List<String> running = runningServices(projectDir);
        return running.containsAll(expectedServices);
    }

    private List<String> expectedServices(ProductionLine.LinearSimulationInput input) {
        List<String> expectedServices = input.operations().stream()
                .filter(operation -> !isStore(operation.name()))
                .map(operation -> "productionline-operation" + operation.id() + "-app")
                .sorted()
                .collect(Collectors.toCollection(ArrayList::new));
        expectedServices.add("productionline-finish-store-app");
        return expectedServices;
    }

    private List<String> runningServices(Path projectDir) {
        String output = runCommand(projectDir,
                List.of("docker", "compose", "-f", composeBaseFile, "-f", composeOverrideFile,
                        "ps", "--services", "--status", "running"),
                "Failed to read running docker compose services"
        );
        return output.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
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
