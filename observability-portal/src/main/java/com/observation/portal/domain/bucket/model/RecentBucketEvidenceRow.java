package com.observation.portal.domain.bucket.model;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * degraded hysteresis의 최근 5개 30초 bucket bad count 계산에 필요한 중립 bucket evidence다.
 *
 * <p>repository는 request/error count와 summary histogram JSON만 반환하고, bad bucket 여부나 rule/state 판단은
 * service layer에 남긴다.</p>
 */
public record RecentBucketEvidenceRow(
        UUID applicationId,
        OffsetDateTime bucketStartUtc,
        OffsetDateTime bucketEndUtc,
        long requestCount,
        long errorCount,
        String durationBucketsJson
) {

    /**
     * bucket boundary와 bounded metric count가 service 계산에 안전한 범위인지 검증한다.
     */
    public RecentBucketEvidenceRow {
        Objects.requireNonNull(applicationId, "applicationId must not be null");
        Objects.requireNonNull(bucketStartUtc, "bucketStartUtc must not be null");
        Objects.requireNonNull(bucketEndUtc, "bucketEndUtc must not be null");
        if (requestCount < 0L) {
            throw new IllegalArgumentException("requestCount must not be negative");
        }
        if (errorCount < 0L) {
            throw new IllegalArgumentException("errorCount must not be negative");
        }
        durationBucketsJson = requireText(durationBucketsJson, "durationBucketsJson");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
