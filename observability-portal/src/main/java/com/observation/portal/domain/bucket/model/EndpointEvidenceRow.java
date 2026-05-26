package com.observation.portal.domain.bucket.model;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * endpoint priority read model이 accepted bucket endpoint JSON을 해석할 수 있도록 boundary와 JSON 원문만 전달한다.
 *
 * <p>Repository는 이 projection에서 endpoint rule, confidence, rank, recommended action을 계산하지 않고 service layer에
 * bounded source field만 넘긴다. endpoint p95/p99나 endpoint percentile rollup은 repository/service 어디에서도 계산하지
 * 않는다.</p>
 */
public record EndpointEvidenceRow(
        UUID applicationId,
        OffsetDateTime bucketStartUtc,
        OffsetDateTime bucketEndUtc,
        String endpointsJson
) {

    /**
     * endpoint evidence projection이 window scope와 JSON parsing에 필요한 최소 field를 갖는지 확인한다.
     */
    public EndpointEvidenceRow {
        Objects.requireNonNull(applicationId, "applicationId must not be null");
        Objects.requireNonNull(bucketStartUtc, "bucketStartUtc must not be null");
        Objects.requireNonNull(bucketEndUtc, "bucketEndUtc must not be null");
        if (!bucketEndUtc.isAfter(bucketStartUtc)) {
            throw new IllegalArgumentException("bucketEndUtc must be after bucketStartUtc");
        }
        endpointsJson = requireText(endpointsJson, "endpointsJson");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
