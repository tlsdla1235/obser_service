package com.observation.portal.domain.snapshot.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Snapshot marker API와 detail marker block이 공유하는 stored read-model point annotation이다.
 *
 * <p>`markerId`는 snapshot marker 식별자일 뿐 Story 5.9 operational event id가 아니며, `resolvedAt` 같은 event field를
 * 포함하지 않는다.</p>
 */
public record DashboardSnapshotMarkerItem(
        String markerId,
        UUID snapshotId,
        OffsetDateTime capturedAt,
        OffsetDateTime currentWindowEndUtc,
        DashboardSnapshotMarkerType type,
        DashboardSnapshotMarkerSeverity severity,
        String readMeaning,
        String captureReason,
        String storedApplicationStateCode,
        DashboardSnapshotDetailReadModel.PreviousState previousState,
        String title,
        String summary,
        String recommendedAction,
        BigDecimal confidence,
        String primaryRuleId,
        String primaryEndpointKey,
        Links links
) {

    public static final String READ_MEANING = "stored_read_model_point";

    /**
     * marker가 stored snapshot point의 bounded annotation shape를 유지하는지 검증한다.
     */
    public DashboardSnapshotMarkerItem {
        markerId = requireText(markerId, "markerId");
        Objects.requireNonNull(snapshotId, "snapshotId must not be null");
        Objects.requireNonNull(capturedAt, "capturedAt must not be null");
        Objects.requireNonNull(currentWindowEndUtc, "currentWindowEndUtc must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        readMeaning = requireText(readMeaning, "readMeaning");
        if (!READ_MEANING.equals(readMeaning)) {
            throw new IllegalArgumentException("readMeaning must be " + READ_MEANING);
        }
        storedApplicationStateCode = requireText(storedApplicationStateCode, "storedApplicationStateCode");
        Objects.requireNonNull(previousState, "previousState must not be null");
        title = requireText(title, "title");
        summary = requireText(summary, "summary");
        recommendedAction = trimNullable(recommendedAction);
        primaryRuleId = trimNullable(primaryRuleId);
        primaryEndpointKey = trimNullable(primaryEndpointKey);
        Objects.requireNonNull(links, "links must not be null");
    }

    /**
     * marker에서 해당 stored snapshot detail로 이동하기 위한 link block이다.
     */
    public record Links(String snapshot) {

        /**
         * snapshot detail link가 실제 API path로 채워졌는지 검증한다.
         */
        public Links {
            snapshot = requireText(snapshot, "snapshot");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String trimNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
