package com.observation.portal.domain.snapshot.model;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Instance snapshot trend projection에 필요한 dashboard snapshot row metadata만 담는 read-only repository row다.
 *
 * <p>`readModelJson`은 parser가 `instanceSummary.items[]`만 읽도록 전달하는 저장 JSON이며, repository는 state,
 * rule, priority, p95/p99, marker 의미를 계산하지 않는다.</p>
 */
public record DashboardSnapshotTrendRow(
        UUID snapshotId,
        OffsetDateTime generatedAt,
        OffsetDateTime currentWindowEndUtc,
        String stateCode,
        String captureReason,
        String readModelJson
) {

    /**
     * trend point row metadata와 저장 JSON source가 비어 있지 않은지 검증한다.
     *
     * <p>`captureReason`은 marker 의미가 없는 opaque metadata이므로 null 여부만 유지하고 문자열은 정규화하지 않는다.</p>
     */
    public DashboardSnapshotTrendRow {
        Objects.requireNonNull(snapshotId, "snapshotId must not be null");
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        Objects.requireNonNull(currentWindowEndUtc, "currentWindowEndUtc must not be null");
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
