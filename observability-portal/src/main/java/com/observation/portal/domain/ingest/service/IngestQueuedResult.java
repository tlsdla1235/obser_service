package com.observation.portal.domain.ingest.service;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * queue enqueue 성공 후 controller가 `202 queued` body로 매핑할 수 있는 API-safe result다.
 */
public record IngestQueuedResult(
        String idempotencyKey,
        String messageVersion,
        OffsetDateTime receivedAt,
        OffsetDateTime enqueuedAt
) {

    /**
     * public response에 필요한 queue success 의미만 보존하고 SQS messageId는 포함하지 않는다.
     */
    public IngestQueuedResult {
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        messageVersion = requireText(messageVersion, "messageVersion");
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        Objects.requireNonNull(enqueuedAt, "enqueuedAt must not be null");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
