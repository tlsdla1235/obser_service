package com.observation.portal.domain.snapshot.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * repository/entity가 저장할 dashboard snapshot row 값을 한 번에 전달하는 내부 persistence 모델이다.
 */
public record DashboardSnapshotWriteValues(
        UUID snapshotId,
        UUID projectId,
        UUID applicationId,
        OffsetDateTime generatedAt,
        OffsetDateTime currentWindowStartUtc,
        OffsetDateTime currentWindowEndUtc,
        OffsetDateTime baselineWindowStartUtc,
        OffsetDateTime baselineWindowEndUtc,
        OffsetDateTime lastAcceptedIngestAt,
        OffsetDateTime lastObservedAt,
        String stateCode,
        String captureReason,
        String primaryRuleId,
        String primaryEndpointKey,
        BigDecimal maxConfidence,
        String readModelJson,
        OffsetDateTime createdAt
) {

    /**
     * `dashboard_snapshots` row insert/update에 필요한 필수 값과 helper column 길이 제약을 검증한다.
     */
    public DashboardSnapshotWriteValues {
        Objects.requireNonNull(snapshotId, "snapshotId must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(applicationId, "applicationId must not be null");
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        Objects.requireNonNull(currentWindowStartUtc, "currentWindowStartUtc must not be null");
        Objects.requireNonNull(currentWindowEndUtc, "currentWindowEndUtc must not be null");
        Objects.requireNonNull(baselineWindowStartUtc, "baselineWindowStartUtc must not be null");
        Objects.requireNonNull(baselineWindowEndUtc, "baselineWindowEndUtc must not be null");
        if (!currentWindowEndUtc.isAfter(currentWindowStartUtc)) {
            throw new IllegalArgumentException("currentWindowEndUtc must be after currentWindowStartUtc");
        }
        if (!baselineWindowEndUtc.isAfter(baselineWindowStartUtc)) {
            throw new IllegalArgumentException("baselineWindowEndUtc must be after baselineWindowStartUtc");
        }
        stateCode = requireText(stateCode, "stateCode", 40);
        captureReason = requireNullableText(captureReason, "captureReason", 64);
        primaryRuleId = requireNullableText(primaryRuleId, "primaryRuleId", 80);
        primaryEndpointKey = requireNullableText(primaryEndpointKey, "primaryEndpointKey", 240);
        readModelJson = requireText(readModelJson, "readModelJson", Integer.MAX_VALUE);
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    private static String requireText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " is too long");
        }
        return trimmed;
    }

    private static String requireNullableText(String value, String fieldName, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " is too long");
        }
        return trimmed;
    }
}
