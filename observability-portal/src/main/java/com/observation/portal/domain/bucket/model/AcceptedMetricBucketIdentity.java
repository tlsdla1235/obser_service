package com.observation.portal.domain.bucket.model;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * queue worker가 idempotency/instance bucket conflict를 판정할 때 필요한 저장 row 식별 projection이다.
 *
 * <p>JPA entity를 service에 노출하지 않고 bucket id, project/application instance identity, hash, boundary만 전달한다.</p>
 */
public record AcceptedMetricBucketIdentity(
        UUID bucketId,
        UUID projectId,
        UUID applicationId,
        UUID applicationInstanceId,
        String idempotencyKey,
        String payloadHash,
        OffsetDateTime bucketStartUtc,
        OffsetDateTime bucketEndUtc,
        OffsetDateTime acceptedAt
) {

    /**
     * duplicate/conflict 분류에 필요한 저장 identity 값이 모두 존재하도록 보장한다.
     */
    public AcceptedMetricBucketIdentity {
        Objects.requireNonNull(bucketId, "bucketId must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(applicationId, "applicationId must not be null");
        Objects.requireNonNull(applicationInstanceId, "applicationInstanceId must not be null");
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        payloadHash = requireText(payloadHash, "payloadHash");
        Objects.requireNonNull(bucketStartUtc, "bucketStartUtc must not be null");
        Objects.requireNonNull(bucketEndUtc, "bucketEndUtc must not be null");
        Objects.requireNonNull(acceptedAt, "acceptedAt must not be null");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
