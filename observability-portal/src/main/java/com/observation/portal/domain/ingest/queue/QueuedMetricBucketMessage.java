package com.observation.portal.domain.ingest.queue;

import com.observation.portal.domain.ingest.model.IngestEnvelopeRequest;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * SQS/fake queue에 넣는 messageVersion 1 metric bucket body 계약이다.
 *
 * <p>raw project key나 starter credential은 포함하지 않고, worker 재검증에 필요한 verified project reference와
 * validated ingest payload만 담는다.</p>
 */
public record QueuedMetricBucketMessage(
        String messageVersion,
        UUID projectId,
        String projectName,
        String idempotencyKey,
        String payloadHash,
        OffsetDateTime receivedAt,
        OffsetDateTime enqueuedAt,
        IngestEnvelopeRequest payload
) {

    /**
     * queue body가 worker handoff에 필요한 필수 값을 모두 갖췄는지 확인한다.
     */
    public QueuedMetricBucketMessage {
        messageVersion = requireText(messageVersion, "messageVersion");
        Objects.requireNonNull(projectId, "projectId must not be null");
        projectName = requireText(projectName, "projectName");
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        payloadHash = requireText(payloadHash, "payloadHash");
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        Objects.requireNonNull(enqueuedAt, "enqueuedAt must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
