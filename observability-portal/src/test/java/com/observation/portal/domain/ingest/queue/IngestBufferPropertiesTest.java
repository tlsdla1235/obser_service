package com.observation.portal.domain.ingest.queue;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ingest buffer 설정이 SQS hard limit을 넘는 app-level guard로 바인딩되지 않게 검증한다.
 */
class IngestBufferPropertiesTest {

    @Test
    void rejectsMessageSizeLimitAboveSqsHardLimit() {
        IngestBufferProperties properties = new IngestBufferProperties();

        assertThatThrownBy(() -> properties.setMessageSizeLimitBytes(
                IngestBufferProperties.SQS_MESSAGE_SIZE_LIMIT_BYTES + 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1048576");
    }

    @Test
    void allowsMessageSizeLimitAtSqsHardLimit() {
        IngestBufferProperties properties = new IngestBufferProperties();

        properties.setMessageSizeLimitBytes(IngestBufferProperties.SQS_MESSAGE_SIZE_LIMIT_BYTES);

        assertThat(properties.getMessageSizeLimitBytes())
                .isEqualTo(IngestBufferProperties.SQS_MESSAGE_SIZE_LIMIT_BYTES);
    }

    @Test
    void exposesSafeWorkerDefaults() {
        IngestBufferProperties properties = new IngestBufferProperties();

        assertThat(properties.getWorker().isEnabled()).isFalse();
        assertThat(properties.getWorker().getDlqUrl()).isEmpty();
        assertThat(properties.getWorker().getLongPollSeconds()).isEqualTo(20);
        assertThat(properties.getWorker().getMaxMessagesPerPoll()).isEqualTo(10);
        assertThat(properties.getWorker().getVisibilityTimeout()).isEqualTo(java.time.Duration.ofSeconds(60));
        assertThat(properties.getWorker().getMaxReceiveCount()).isEqualTo(5);
        assertThat(properties.getWorker().getMaxBatchSize()).isEqualTo(10);
        assertThat(properties.getWorker().getMaxBatchAge()).isEqualTo(java.time.Duration.ofSeconds(2));
    }

    @Test
    void rejectsInvalidWorkerReceiveBoundsAndDurations() {
        IngestBufferProperties.Worker worker = new IngestBufferProperties().getWorker();

        assertThatThrownBy(() -> worker.setLongPollSeconds(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("longPollSeconds");
        assertThatThrownBy(() -> worker.setLongPollSeconds(21))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("longPollSeconds");
        assertThatThrownBy(() -> worker.setMaxMessagesPerPoll(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxMessagesPerPoll");
        assertThatThrownBy(() -> worker.setMaxMessagesPerPoll(11))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxMessagesPerPoll");
        assertThatThrownBy(() -> worker.setVisibilityTimeout(java.time.Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("visibilityTimeout");
        assertThatThrownBy(() -> worker.setMaxReceiveCount(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxReceiveCount");
        assertThatThrownBy(() -> worker.setMaxBatchSize(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxBatchSize");
        assertThatThrownBy(() -> worker.setMaxBatchAge(java.time.Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxBatchAge");
    }
}
