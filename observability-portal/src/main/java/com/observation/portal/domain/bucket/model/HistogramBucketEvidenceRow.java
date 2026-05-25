package com.observation.portal.domain.bucket.model;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * dashboard histogram distribution 조립에 필요한 application-level summary duration bucket JSON row다.
 *
 * <p>Repository는 duration bucket JSON 원문과 window boundary만 전달하고, boundary mismatch나 totalCount 판단은 service
 * read model 조립 단계에 남긴다.</p>
 */
public record HistogramBucketEvidenceRow(
        UUID applicationId,
        OffsetDateTime bucketStartUtc,
        OffsetDateTime bucketEndUtc,
        String durationBucketsJson
) {

    /**
     * histogram evidence projection이 window 합산에 필요한 bucket boundary와 JSON payload를 갖는지 확인한다.
     */
    public HistogramBucketEvidenceRow {
        Objects.requireNonNull(applicationId, "applicationId must not be null");
        Objects.requireNonNull(bucketStartUtc, "bucketStartUtc must not be null");
        Objects.requireNonNull(bucketEndUtc, "bucketEndUtc must not be null");
        if (!bucketEndUtc.isAfter(bucketStartUtc)) {
            throw new IllegalArgumentException("bucketEndUtc must be after bucketStartUtc");
        }
        durationBucketsJson = requireText(durationBucketsJson, "durationBucketsJson");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
