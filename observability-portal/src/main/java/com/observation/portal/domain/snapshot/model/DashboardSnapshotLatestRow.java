package com.observation.portal.domain.snapshot.model;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * dashboard query fallback freshness 판단에 필요한 최신 snapshot row metadata만 담는 projection이다.
 */
public record DashboardSnapshotLatestRow(
        UUID snapshotId,
        OffsetDateTime generatedAt,
        OffsetDateTime currentWindowEndUtc,
        String stateCode,
        String captureReason
) {

    /**
     * latest snapshot 판단에 필요한 row identity와 생성 시각을 검증한다.
     */
    public DashboardSnapshotLatestRow {
        Objects.requireNonNull(snapshotId, "snapshotId must not be null");
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        Objects.requireNonNull(currentWindowEndUtc, "currentWindowEndUtc must not be null");
        Objects.requireNonNull(stateCode, "stateCode must not be null");
    }
}
