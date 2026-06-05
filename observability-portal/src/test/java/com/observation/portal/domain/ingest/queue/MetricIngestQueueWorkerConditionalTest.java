package com.observation.portal.domain.ingest.queue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class MetricIngestQueueWorkerConditionalTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    IngestBufferProperties.class,
                    MetricIngestQueueWorker.class);

    @Test
    void defaultWorkerDisabledDoesNotCreateReceiveLoopBean() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(MetricIngestQueueWorker.class));
    }
}
