package com.observation.portal.domain.bucket.model;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * dashboard read model이 instance bucket percentile evidence를 해석할 수 있도록 DB row identity와 JSON 원문을 전달한다.
 *
 * <p>이 projection은 p95/p99를 합치거나 재계산하지 않고, service가 persisted JSON의 source/scope를 검증할 수 있는
 * 중립 조회 결과로만 사용된다.</p>
 */
public record LocalPercentileEvidenceRow(
        UUID applicationId,
        UUID applicationInstanceId,
        String instanceName,
        OffsetDateTime bucketStartUtc,
        OffsetDateTime bucketEndUtc,
        String localPercentilesJson
) {

    /**
     * percentile evidence 조회 결과가 service 검증에 필요한 최소 identity와 JSON payload를 갖는지 확인한다.
     */
    public LocalPercentileEvidenceRow {
        Objects.requireNonNull(applicationId, "applicationId must not be null");
        Objects.requireNonNull(applicationInstanceId, "applicationInstanceId must not be null");
        instanceName = requireText(instanceName, "instanceName");
        Objects.requireNonNull(bucketStartUtc, "bucketStartUtc must not be null");
        Objects.requireNonNull(bucketEndUtc, "bucketEndUtc must not be null");
        if (!bucketEndUtc.isAfter(bucketStartUtc)) {
            throw new IllegalArgumentException("bucketEndUtc must be after bucketStartUtc");
        }
        localPercentilesJson = requireText(localPercentilesJson, "localPercentilesJson");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
