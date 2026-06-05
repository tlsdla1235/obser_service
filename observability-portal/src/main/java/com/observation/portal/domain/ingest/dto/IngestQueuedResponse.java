package com.observation.portal.domain.ingest.dto;

import com.observation.portal.domain.ingest.service.IngestQueuedResult;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * SQS/fake mode에서 enqueue 성공만 표현하는 `202 Accepted` response DTO다.
 */
public record IngestQueuedResponse(
        String status,
        boolean queued,
        boolean persisted,
        String idempotencyKey,
        String messageVersion,
        OffsetDateTime receivedAt,
        OffsetDateTime enqueuedAt
) {

    /**
     * DB 저장 완료로 오해될 수 있는 bucketId/acceptedAt/duplicate/messageId를 의도적으로 포함하지 않는다.
     */
    public IngestQueuedResponse {
        if (!"queued".equals(status)) {
            throw new IllegalArgumentException("status must be queued");
        }
        if (!queued) {
            throw new IllegalArgumentException("queued must be true");
        }
        if (persisted) {
            throw new IllegalArgumentException("persisted must be false");
        }
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        messageVersion = requireText(messageVersion, "messageVersion");
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        Objects.requireNonNull(enqueuedAt, "enqueuedAt must not be null");
    }

    /**
     * service-level queued result를 public response shape로 변환한다.
     */
    public static IngestQueuedResponse queued(IngestQueuedResult result) {
        IngestQueuedResult requiredResult = Objects.requireNonNull(result, "result must not be null");
        return new IngestQueuedResponse(
                "queued",
                true,
                false,
                requiredResult.idempotencyKey(),
                requiredResult.messageVersion(),
                requiredResult.receivedAt(),
                requiredResult.enqueuedAt());
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
