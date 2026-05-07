package com.factory.productionline.service;

import com.factory.productionline.model.DistributedBatchResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class TechnologicalTrajectoryCsvService {

    public record BatchTrajectory(
            String batchId,
            int partsCount,
            DistributedBatchResult result
    ) {
    }

    public String header(int partsCount) {
        StringBuilder csv = new StringBuilder("routeId;batchId;operationId");
        for (int partNumber = 1; partNumber <= partsCount; partNumber++) {
            csv.append(";part-").append(partNumber);
        }
        csv.append("\n");
        return csv.toString();
    }

    public String header(String batchId, int partsCount) {
        StringBuilder csv = new StringBuilder("routeId;batchId;operationId");
        for (int partNumber = 1; partNumber <= partsCount; partNumber++) {
            csv.append(";").append(escape(batchId + "-" + partNumber));
        }
        csv.append("\n");
        return csv.toString();
    }

    public String toCsvRows(String routeId, String batchId, int partsCount, DistributedBatchResult result) {
        Map<Integer, Map<Integer, Double>> transferTauByOperationAndPart = new HashMap<>();
        int maxOperationId = 0;

        for (DistributedBatchResult.KafkaTransferMessage message : result.kafkaMessages()) {
            if (!batchId.equals(message.batchId())) {
                continue;
            }
            transferTauByOperationAndPart
                    .computeIfAbsent(message.fromOperation(), ignored -> new HashMap<>())
                    .put(message.partNumber(), message.availableAtTau());
            maxOperationId = Math.max(maxOperationId, message.fromOperation());
        }

        StringBuilder csv = new StringBuilder();
        for (int operationId = 0; operationId <= maxOperationId; operationId++) {
            appendOperationRow(
                    csv,
                    routeId,
                    batchId,
                    operationId,
                    partsCount,
                    transferTauByOperationAndPart.getOrDefault(operationId, Map.of())
            );
        }

        return csv.toString();
    }

    public String toRouteCsv(String routeId, List<BatchTrajectory> batches) {
        return toRouteCsv(routeId, batches, Map.of());
    }

    public String toRouteCsv(
            String routeId,
            List<BatchTrajectory> batches,
            Map<Integer, Integer> outputBufferCapacities
    ) {
        if (!outputBufferCapacities.isEmpty()) {
            return toRouteCsv(capacityAwareTrajectory(batches, outputBufferCapacities), false);
        }

        List<PartColumn> columns = new ArrayList<>();
        Map<String, Map<Integer, Map<Integer, Double>>> transferTauByBatchOperationAndPart = new HashMap<>();
        int maxOperationId = 0;

        for (BatchTrajectory batch : batches) {
            Map<Integer, Map<Integer, Double>> transferTauByOperationAndPart = collectTransferTauByOperationAndPart(
                    batch.batchId(),
                    batch.result()
            );
            transferTauByBatchOperationAndPart.put(batch.batchId(), transferTauByOperationAndPart);
            maxOperationId = Math.max(maxOperationId, maxOperationId(transferTauByOperationAndPart));
            for (int partNumber = 1; partNumber <= batch.partsCount(); partNumber++) {
                columns.add(new PartColumn(batch.batchId(), partNumber));
            }
        }

        List<List<Cell>> rows = new ArrayList<>();
        List<Cell> header = new ArrayList<>();
        header.add(Cell.text("operationId"));
        for (PartColumn column : columns) {
            header.add(Cell.text(column.batchId() + "-" + column.partNumber()));
        }
        rows.add(header);

        for (int operationId = 0; operationId <= maxOperationId; operationId++) {
            List<Cell> row = new ArrayList<>();
            row.add(Cell.number(String.valueOf(operationId)));
            for (PartColumn column : columns) {
                Double transferTau = transferTauByBatchOperationAndPart
                        .getOrDefault(column.batchId(), Map.of())
                        .getOrDefault(operationId, Map.of())
                        .get(column.partNumber());
                row.add(transferTau == null ? Cell.text("") : Cell.number(formatTau(transferTau)));
            }
            rows.add(row);
        }

        return toAlignedCsv(rows);
    }

    public String toRouteFullCsv(String routeId, List<BatchTrajectory> batches) {
        return toRouteFullCsv(routeId, batches, Map.of());
    }

    public String toRouteFullCsv(
            String routeId,
            List<BatchTrajectory> batches,
            Map<Integer, Integer> outputBufferCapacities
    ) {
        if (!outputBufferCapacities.isEmpty()) {
            return toRouteCsv(capacityAwareTrajectory(batches, outputBufferCapacities), true);
        }

        List<PartColumn> columns = new ArrayList<>();
        Map<String, Map<Integer, Map<Integer, Double>>> transferTauByBatchOperationAndPart = new HashMap<>();
        Map<String, Map<Integer, Map<Integer, Double>>> startTauByBatchOperationAndPart = new HashMap<>();
        int maxOperationId = 0;

        for (BatchTrajectory batch : batches) {
            Map<Integer, Map<Integer, Double>> transferTauByOperationAndPart = collectTransferTauByOperationAndPart(
                    batch.batchId(),
                    batch.result()
            );
            Map<Integer, Map<Integer, Double>> startTauByOperationAndPart = collectStartTauByOperationAndPart(
                    batch.batchId(),
                    batch.result()
            );
            transferTauByBatchOperationAndPart.put(batch.batchId(), transferTauByOperationAndPart);
            startTauByBatchOperationAndPart.put(batch.batchId(), startTauByOperationAndPart);
            maxOperationId = Math.max(maxOperationId, maxOperationId(transferTauByOperationAndPart));
            for (int partNumber = 1; partNumber <= batch.partsCount(); partNumber++) {
                columns.add(new PartColumn(batch.batchId(), partNumber));
            }
        }

        List<List<Cell>> rows = new ArrayList<>();
        rows.add(routeHeader(columns));

        for (int operationId = 0; operationId <= maxOperationId; operationId++) {
            rows.add(routeValueRow(operationId, columns, transferTauByBatchOperationAndPart));
            if (operationId < maxOperationId) {
                rows.add(routeValueRow(operationId, columns, shiftOperationStartMap(startTauByBatchOperationAndPart, operationId + 1)));
            }
        }

        return toAlignedCsv(rows);
    }

    public String toRouteBunkersCsv(
            String routeId,
            List<BatchTrajectory> batches,
            Map<Integer, Integer> outputBufferCapacities
    ) {
        RouteTrajectory trajectory = capacityAwareTrajectory(batches, outputBufferCapacities);
        if (trajectory.maxOperationId() <= 0) {
            return "tau\n";
        }

        List<List<Cell>> rows = new ArrayList<>();
        List<Cell> header = new ArrayList<>();
        header.add(Cell.text("tau"));
        for (int operationId = 0; operationId < trajectory.maxOperationId(); operationId++) {
            header.add(Cell.text("bunker-" + operationId));
        }
        rows.add(header);

        Set<Double> tauPoints = new LinkedHashSet<>();
        for (int columnIndex = 0; columnIndex < trajectory.columns().size(); columnIndex++) {
            for (int operationId = 0; operationId < trajectory.maxOperationId(); operationId++) {
                tauPoints.add(trajectory.transferTauByColumnAndOperation()[columnIndex][operationId]);
                tauPoints.add(trajectory.startTauByColumnAndOperation()[columnIndex][operationId + 1]);
            }
        }

        tauPoints.stream()
                .sorted(Comparator.naturalOrder())
                .forEach(tau -> rows.add(bunkerLoadRow(tau, trajectory)));

        return toAlignedCsv(rows);
    }

    private List<Cell> bunkerLoadRow(double tau, RouteTrajectory trajectory) {
        List<Cell> row = new ArrayList<>();
        row.add(Cell.number(formatTau(tau)));
        for (int operationId = 0; operationId < trajectory.maxOperationId(); operationId++) {
            int load = 0;
            for (int columnIndex = 0; columnIndex < trajectory.columns().size(); columnIndex++) {
                double transferredFromOperationTau = trajectory.transferTauByColumnAndOperation()[columnIndex][operationId];
                double startedNextOperationTau = trajectory.startTauByColumnAndOperation()[columnIndex][operationId + 1];
                if (transferredFromOperationTau <= tau && tau < startedNextOperationTau) {
                    load++;
                }
            }
            row.add(Cell.number(String.valueOf(load)));
        }
        return row;
    }

    private List<Cell> routeHeader(List<PartColumn> columns) {
        List<Cell> header = new ArrayList<>();
        header.add(Cell.text("operationId"));
        for (PartColumn column : columns) {
            header.add(Cell.text(column.batchId() + "-" + column.partNumber()));
        }
        return header;
    }

    private List<Cell> routeValueRow(
            int operationId,
            List<PartColumn> columns,
            Map<String, Map<Integer, Map<Integer, Double>>> tauByBatchOperationAndPart
    ) {
        List<Cell> row = new ArrayList<>();
        row.add(Cell.number(String.valueOf(operationId)));
        for (PartColumn column : columns) {
            Double tau = tauByBatchOperationAndPart
                    .getOrDefault(column.batchId(), Map.of())
                    .getOrDefault(operationId, Map.of())
                    .get(column.partNumber());
            row.add(tau == null ? Cell.text("") : Cell.number(formatTau(tau)));
        }
        return row;
    }

    private Map<String, Map<Integer, Map<Integer, Double>>> shiftOperationStartMap(
            Map<String, Map<Integer, Map<Integer, Double>>> startTauByBatchOperationAndPart,
            int sourceOperationId
    ) {
        Map<String, Map<Integer, Map<Integer, Double>>> shifted = new HashMap<>();
        for (Map.Entry<String, Map<Integer, Map<Integer, Double>>> batchEntry : startTauByBatchOperationAndPart.entrySet()) {
            Map<Integer, Double> startTauByPart = batchEntry.getValue().get(sourceOperationId);
            if (startTauByPart != null) {
                shifted.put(batchEntry.getKey(), Map.of(sourceOperationId - 1, startTauByPart));
            }
        }
        return shifted;
    }

    private String toRouteCsv(RouteTrajectory trajectory, boolean includeStartRows) {
        List<List<Cell>> rows = new ArrayList<>();
        rows.add(routeHeader(trajectory.columns()));

        for (int operationId = 0; operationId <= trajectory.maxOperationId(); operationId++) {
            rows.add(arrayValueRow(
                    operationId,
                    trajectory.columns(),
                    trajectory.transferTauByColumnAndOperation(),
                    operationId
            ));
            if (includeStartRows && operationId < trajectory.maxOperationId()) {
                rows.add(arrayValueRow(
                        operationId,
                        trajectory.columns(),
                        trajectory.startTauByColumnAndOperation(),
                        operationId + 1
                ));
            }
        }

        return toAlignedCsv(rows);
    }

    private List<Cell> arrayValueRow(
            int rowOperationId,
            List<PartColumn> columns,
            double[][] valuesByColumnAndOperation,
            int valueOperationId
    ) {
        List<Cell> row = new ArrayList<>();
        row.add(Cell.number(String.valueOf(rowOperationId)));
        for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
            row.add(Cell.number(formatTau(valuesByColumnAndOperation[columnIndex][valueOperationId])));
        }
        return row;
    }

    private RouteTrajectory capacityAwareTrajectory(
            List<BatchTrajectory> batches,
            Map<Integer, Integer> outputBufferCapacities
    ) {
        List<PartColumn> columns = new ArrayList<>();
        Map<String, Map<Integer, Map<Integer, Double>>> processingTauByBatchOperationAndPart = new HashMap<>();
        Map<String, Map<Integer, Double>> initialTauByBatchAndPart = new HashMap<>();
        int maxOperationId = 0;

        for (BatchTrajectory batch : batches) {
            Map<Integer, Map<Integer, Double>> processingTauByOperationAndPart = collectProcessingTauByOperationAndPart(
                    batch.batchId(),
                    batch.result()
            );
            processingTauByBatchOperationAndPart.put(batch.batchId(), processingTauByOperationAndPart);
            initialTauByBatchAndPart.put(batch.batchId(), collectInitialTauByPart(batch.batchId(), batch.result()));
            maxOperationId = Math.max(maxOperationId, maxOperationId(processingTauByOperationAndPart));
            for (int partNumber = 1; partNumber <= batch.partsCount(); partNumber++) {
                columns.add(new PartColumn(batch.batchId(), partNumber));
            }
        }

        double[][] startTau = new double[columns.size()][maxOperationId + 1];
        double[][] transferTau = new double[columns.size()][maxOperationId + 1];

        for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
            PartColumn column = columns.get(columnIndex);
            double initialTau = initialTauByBatchAndPart
                    .getOrDefault(column.batchId(), Map.of())
                    .getOrDefault(column.partNumber(), 0d);
            for (int operationId = 0; operationId <= maxOperationId; operationId++) {
                double previousOperationTransferTau = operationId == 0
                        ? initialTau
                        : transferTau[columnIndex][operationId - 1];
                double previousPartTransferTau = columnIndex == 0
                        ? initialTau
                        : transferTau[columnIndex - 1][operationId];
                startTau[columnIndex][operationId] = Math.max(previousOperationTransferTau, previousPartTransferTau);

                double processingTau = processingTauByBatchOperationAndPart
                        .getOrDefault(column.batchId(), Map.of())
                        .getOrDefault(operationId, Map.of())
                        .getOrDefault(column.partNumber(), 0d);
                double processingFinishTau = startTau[columnIndex][operationId] + processingTau;
                transferTau[columnIndex][operationId] = Math.max(
                        processingFinishTau,
                        downstreamBufferBlockedUntilTau(
                                outputBufferCapacities,
                                startTau,
                                columnIndex,
                                operationId,
                                maxOperationId
                        )
                );
            }
        }

        return new RouteTrajectory(columns, maxOperationId, startTau, transferTau);
    }

    private double downstreamBufferBlockedUntilTau(
            Map<Integer, Integer> outputBufferCapacities,
            double[][] startTau,
            int columnIndex,
            int operationId,
            int maxOperationId
    ) {
        Integer capacity = outputBufferCapacities.get(operationId);
        if (capacity == null || capacity <= 0 || operationId >= maxOperationId) {
            return 0d;
        }

        int blockingColumnIndex = columnIndex - capacity;
        if (blockingColumnIndex < 0) {
            return 0d;
        }

        return startTau[blockingColumnIndex][operationId + 1];
    }

    private Map<Integer, Map<Integer, Double>> collectTransferTauByOperationAndPart(
            String batchId,
            DistributedBatchResult result
    ) {
        Map<Integer, Map<Integer, Double>> transferTauByOperationAndPart = new HashMap<>();
        for (DistributedBatchResult.KafkaTransferMessage message : result.kafkaMessages()) {
            if (!batchId.equals(message.batchId())) {
                continue;
            }
            transferTauByOperationAndPart
                    .computeIfAbsent(message.fromOperation(), ignored -> new HashMap<>())
                    .put(message.partNumber(), message.availableAtTau());
        }
        return transferTauByOperationAndPart;
    }

    private Map<Integer, Map<Integer, Double>> collectStartTauByOperationAndPart(
            String batchId,
            DistributedBatchResult result
    ) {
        Map<Integer, Map<Integer, Double>> startTauByOperationAndPart = new HashMap<>();
        for (DistributedBatchResult.KafkaTransferMessage message : result.kafkaMessages()) {
            if (!batchId.equals(message.batchId())) {
                continue;
            }
            startTauByOperationAndPart
                    .computeIfAbsent(message.fromOperation(), ignored -> new HashMap<>())
                    .put(message.partNumber(), message.processingStartTau());
        }
        return startTauByOperationAndPart;
    }

    private Map<Integer, Map<Integer, Double>> collectProcessingTauByOperationAndPart(
            String batchId,
            DistributedBatchResult result
    ) {
        Map<Integer, Map<Integer, Double>> processingTauByOperationAndPart = new HashMap<>();
        for (DistributedBatchResult.KafkaTransferMessage message : result.kafkaMessages()) {
            if (!batchId.equals(message.batchId())) {
                continue;
            }
            processingTauByOperationAndPart
                    .computeIfAbsent(message.fromOperation(), ignored -> new HashMap<>())
                    .put(message.partNumber(), message.processingTau());
        }
        return processingTauByOperationAndPart;
    }

    private Map<Integer, Double> collectInitialTauByPart(String batchId, DistributedBatchResult result) {
        Map<Integer, Double> initialTauByPart = new HashMap<>();
        for (DistributedBatchResult.KafkaTransferMessage message : result.kafkaMessages()) {
            if (batchId.equals(message.batchId()) && message.fromOperation() == 0) {
                initialTauByPart.put(message.partNumber(), message.availableAtTau());
            }
        }
        return initialTauByPart;
    }

    private int maxOperationId(Map<Integer, Map<Integer, Double>> transferTauByOperationAndPart) {
        return transferTauByOperationAndPart.keySet().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
    }

    private void appendOperationRow(
            StringBuilder csv,
            String routeId,
            String batchId,
            int operationId,
            int partsCount,
            Map<Integer, Double> transferTauByPart
    ) {
        csv.append(escape(routeId))
                .append(";")
                .append(escape(batchId))
                .append(";")
                .append(operationId);
        for (int partNumber = 1; partNumber <= partsCount; partNumber++) {
            csv.append(";");
            Double transferTau = transferTauByPart.get(partNumber);
            if (transferTau != null) {
                csv.append(transferTau);
            }
        }
        csv.append("\n");
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(";") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String formatTau(double value) {
        return String.format(Locale.US, "%.7f", value).replace(".", ",");
    }

    private String toAlignedCsv(List<List<Cell>> rows) {
        int columnsCount = rows.stream().mapToInt(List::size).max().orElse(0);
        int[] widths = new int[columnsCount];
        for (List<Cell> row : rows) {
            for (int columnIndex = 0; columnIndex < row.size(); columnIndex++) {
                widths[columnIndex] = Math.max(widths[columnIndex], escape(row.get(columnIndex).value()).length());
            }
        }

        StringBuilder csv = new StringBuilder();
        for (List<Cell> row : rows) {
            for (int columnIndex = 0; columnIndex < row.size(); columnIndex++) {
                if (columnIndex > 0) {
                    csv.append(";");
                }
                Cell cell = row.get(columnIndex);
                String value = escape(cell.value());
                csv.append(cell.numeric() ? padLeft(value, widths[columnIndex]) : padRight(value, widths[columnIndex]));
            }
            csv.append("\n");
        }
        return csv.toString();
    }

    private String padLeft(String value, int width) {
        if (value.length() >= width) {
            return value;
        }
        return " ".repeat(width - value.length()) + value;
    }

    private String padRight(String value, int width) {
        if (value.length() >= width) {
            return value;
        }
        return value + " ".repeat(width - value.length());
    }

    private record PartColumn(String batchId, int partNumber) {
    }

    private record RouteTrajectory(
            List<PartColumn> columns,
            int maxOperationId,
            double[][] startTauByColumnAndOperation,
            double[][] transferTauByColumnAndOperation
    ) {
    }

    private record Cell(String value, boolean numeric) {
        private static Cell text(String value) {
            return new Cell(value, false);
        }

        private static Cell number(String value) {
            return new Cell(value, true);
        }
    }
}
