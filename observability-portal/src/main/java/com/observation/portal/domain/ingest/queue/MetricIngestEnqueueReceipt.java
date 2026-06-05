package com.observation.portal.domain.ingest.queue;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * publisher enqueue 성공을 나타내는 내부 receipt다. messageId는 public API response에 노출하지 않는다.
 */
public record MetricIngestEnqueueReceipt(String messageId, OffsetDateTime enqueuedAt) {

    /**
     * fake/AWS publisher가 반환한 diagnostic id와 enqueue timestamp를 보존한다.
     */
    public MetricIngestEnqueueReceipt {
        messageId = requireText(messageId, "messageId");
        Objects.requireNonNull(enqueuedAt, "enqueuedAt must not be null");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
