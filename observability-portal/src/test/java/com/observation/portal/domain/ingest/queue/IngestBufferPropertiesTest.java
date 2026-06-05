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
}
