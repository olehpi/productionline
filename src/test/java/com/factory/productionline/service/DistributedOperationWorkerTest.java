package com.factory.productionline.service;

import com.factory.productionline.model.DistributedPartMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DistributedOperationWorkerTest {

    @Test
    void processMessageStartsNextPartOnlyAfterPreviousPartFinishes() {
        DistributedOperationWorker worker = new DistributedOperationWorker(
                mock(KafkaTemplate.class),
                new ObjectMapper(),
                7,
                8,
                10.0,
                0.0,
                7L
        );

        DistributedPartMessage first = worker.processMessage(new DistributedPartMessage("route-42", 1, "batch-42", 50.0, 10.0, 60.0));
        DistributedPartMessage second = worker.processMessage(new DistributedPartMessage("route-42", 2, "batch-42", 50.0, 10.0, 60.0));
        DistributedPartMessage third = worker.processMessage(new DistributedPartMessage("route-42", 3, "batch-42", 50.0, 10.0, 60.0));

        assertThat(first.startTau()).isEqualTo(60.0);
        assertThat(first.finishTau()).isEqualTo(70.0);
        assertThat(second.startTau()).isEqualTo(70.0);
        assertThat(second.finishTau()).isEqualTo(80.0);
        assertThat(third.startTau()).isEqualTo(80.0);
        assertThat(third.finishTau()).isEqualTo(90.0);
    }

    @Test
    void processMessageResetsBusyStateWhenSameBatchIsStartedAgain() {
        DistributedOperationWorker worker = new DistributedOperationWorker(
                mock(KafkaTemplate.class),
                new ObjectMapper(),
                1,
                2,
                30.0,
                0.0,
                1L
        );

        worker.processMessage(new DistributedPartMessage("route-42", 1, "batch51", 0.0, 0.0, 0.0));
        worker.processMessage(new DistributedPartMessage("route-42", 2, "batch51", 0.0, 0.0, 0.0));
        worker.processMessage(new DistributedPartMessage("route-42", 3, "batch51", 0.0, 0.0, 0.0));

        DistributedPartMessage restartedFirst = worker.processMessage(new DistributedPartMessage("route-42", 1, "batch51", 0.0, 0.0, 0.0));
        DistributedPartMessage restartedSecond = worker.processMessage(new DistributedPartMessage("route-42", 2, "batch51", 0.0, 0.0, 0.0));
        DistributedPartMessage restartedThird = worker.processMessage(new DistributedPartMessage("route-42", 3, "batch51", 0.0, 0.0, 0.0));

        assertThat(restartedFirst.startTau()).isEqualTo(0.0);
        assertThat(restartedFirst.finishTau()).isEqualTo(30.0);
        assertThat(restartedSecond.startTau()).isEqualTo(30.0);
        assertThat(restartedSecond.finishTau()).isEqualTo(60.0);
        assertThat(restartedThird.startTau()).isEqualTo(60.0);
        assertThat(restartedThird.finishTau()).isEqualTo(90.0);
    }

    @Test
    void processMessageKeepsBusyStateForDifferentBatchIds() {
        DistributedOperationWorker worker = new DistributedOperationWorker(
                mock(KafkaTemplate.class),
                new ObjectMapper(),
                1,
                2,
                30.0,
                0.0,
                1L
        );

        worker.processMessage(new DistributedPartMessage("route-42", 1, "batchA", 0.0, 0.0, 0.0));
        worker.processMessage(new DistributedPartMessage("route-42", 2, "batchA", 0.0, 0.0, 0.0));
        worker.processMessage(new DistributedPartMessage("route-42", 3, "batchA", 0.0, 0.0, 0.0));

        DistributedPartMessage nextBatchFirst = worker.processMessage(new DistributedPartMessage("route-42", 1, "batchB", 0.0, 0.0, 0.0));
        DistributedPartMessage nextBatchSecond = worker.processMessage(new DistributedPartMessage("route-42", 2, "batchB", 0.0, 0.0, 0.0));
        DistributedPartMessage nextBatchThird = worker.processMessage(new DistributedPartMessage("route-42", 3, "batchB", 0.0, 0.0, 0.0));

        assertThat(nextBatchFirst.startTau()).isEqualTo(90.0);
        assertThat(nextBatchFirst.finishTau()).isEqualTo(120.0);
        assertThat(nextBatchSecond.startTau()).isEqualTo(120.0);
        assertThat(nextBatchSecond.finishTau()).isEqualTo(150.0);
        assertThat(nextBatchThird.startTau()).isEqualTo(150.0);
        assertThat(nextBatchThird.finishTau()).isEqualTo(180.0);
    }

    @Test
    void processMessageResetsRepeatedBatchToItsPreviousStartTau() {
        DistributedOperationWorker worker = new DistributedOperationWorker(
                mock(KafkaTemplate.class),
                new ObjectMapper(),
                1,
                2,
                30.0,
                0.0,
                1L
        );

        worker.processMessage(new DistributedPartMessage("route-42", 1, "batchA", 0.0, 0.0, 0.0));
        worker.processMessage(new DistributedPartMessage("route-42", 2, "batchA", 0.0, 0.0, 0.0));
        worker.processMessage(new DistributedPartMessage("route-42", 3, "batchA", 0.0, 0.0, 0.0));

        worker.processMessage(new DistributedPartMessage("route-42", 1, "batchB", 0.0, 0.0, 0.0));
        worker.processMessage(new DistributedPartMessage("route-42", 2, "batchB", 0.0, 0.0, 0.0));
        worker.processMessage(new DistributedPartMessage("route-42", 3, "batchB", 0.0, 0.0, 0.0));

        DistributedPartMessage repeatedFirst = worker.processMessage(new DistributedPartMessage("route-42", 1, "batchB", 0.0, 0.0, 0.0));
        DistributedPartMessage repeatedSecond = worker.processMessage(new DistributedPartMessage("route-42", 2, "batchB", 0.0, 0.0, 0.0));
        DistributedPartMessage repeatedThird = worker.processMessage(new DistributedPartMessage("route-42", 3, "batchB", 0.0, 0.0, 0.0));

        assertThat(repeatedFirst.startTau()).isEqualTo(90.0);
        assertThat(repeatedFirst.finishTau()).isEqualTo(120.0);
        assertThat(repeatedSecond.startTau()).isEqualTo(120.0);
        assertThat(repeatedSecond.finishTau()).isEqualTo(150.0);
        assertThat(repeatedThird.startTau()).isEqualTo(150.0);
        assertThat(repeatedThird.finishTau()).isEqualTo(180.0);
    }
}
