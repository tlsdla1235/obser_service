package com.observation.portal.domain.snapshot.model;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * previousState와 lastHealthyAt lookup에 쓰는 같은 application의 이전 snapshot source row다.
 *
 * <p>accepted bucket, heartbeat, current dashboard source를 섞지 않고 이전 stored snapshot identity와 state만 전달한다.</p>
 */
public record DashboardSnapshotSourceRow(
        UUID snapshotId,
        OffsetDateTime generatedAt,
        OffsetDateTime currentWindowEndUtc,
        String stateCode
) {

    /**
     * 이전 snapshot source로 사용할 최소 row metadata를 검증한다.
     */
    public DashboardSnapshotSourceRow {
        Objects.requireNonNull(snapshotId, "snapshotId must not be null");
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        Objects.requireNonNull(currentWindowEndUtc, "currentWindowEndUtc must not be null");
        stateCode = requireText(stateCode, "stateCode");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
