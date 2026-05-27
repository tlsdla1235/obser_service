package com.observation.portal.domain.snapshot.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Snapshot detail/marker projection에 필요한 `dashboard_snapshots` row metadata와 저장 JSON을 담는 read-only row다.
 *
 * <p>이 모델은 저장된 값만 운반하며 marker type, severity, endpoint priority, p95/p99 같은 의미 계산을 repository에
 * 숨기지 않는다.</p>
 */
public record DashboardSnapshotDetailRow(
        UUID snapshotId,
        UUID projectId,
        UUID applicationId,
        OffsetDateTime generatedAt,
        OffsetDateTime currentWindowStartUtc,
        OffsetDateTime currentWindowEndUtc,
        OffsetDateTime baselineWindowStartUtc,
        OffsetDateTime baselineWindowEndUtc,
        String stateCode,
        String captureReason,
        String primaryRuleId,
        String primaryEndpointKey,
        BigDecimal maxConfidence,
        String readModelJson
) {

    /**
     * detail/marker read model의 source가 될 필수 row 값과 stored JSON이 존재하는지 검증한다.
     */
    public DashboardSnapshotDetailRow {
        Objects.requireNonNull(snapshotId, "snapshotId must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(applicationId, "applicationId must not be null");
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        Objects.requireNonNull(currentWindowStartUtc, "currentWindowStartUtc must not be null");
        Objects.requireNonNull(currentWindowEndUtc, "currentWindowEndUtc must not be null");
        Objects.requireNonNull(baselineWindowStartUtc, "baselineWindowStartUtc must not be null");
        Objects.requireNonNull(baselineWindowEndUtc, "baselineWindowEndUtc must not be null");
        stateCode = requireText(stateCode, "stateCode");
        readModelJson = requireText(readModelJson, "readModelJson");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
