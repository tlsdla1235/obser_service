package com.observation.portal.domain.snapshot.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Snapshot marker collection API가 반환하는 bounded timeline point read model이다.
 *
 * <p>marker는 stored dashboard snapshot annotation이며 operational event list, alert delivery log, incident ack list가
 * 아니다.</p>
 */
public record DashboardSnapshotMarkerReadModel(
        OffsetDateTime generatedAt,
        UUID applicationId,
        String source,
        Horizon horizon,
        EmptyState emptyState,
        List<DashboardSnapshotMarkerItem> markers
) {

    public static final String SOURCE = "dashboard_snapshots";
    public static final String DEFAULT_SINCE = "24h";
    public static final String MAX_SINCE = "14d";
    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 336;
    public static final String ORDER = "capturedAt_asc";

    /**
     * marker response의 source, horizon, bounded marker list를 검증한다.
     */
    public DashboardSnapshotMarkerReadModel {
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        Objects.requireNonNull(applicationId, "applicationId must not be null");
        source = requireText(source, "source");
        if (!SOURCE.equals(source)) {
            throw new IllegalArgumentException("source must be " + SOURCE);
        }
        Objects.requireNonNull(horizon, "horizon must not be null");
        markers = List.copyOf(Objects.requireNonNull(markers, "markers must not be null"));
        if (markers.size() > horizon.limit()) {
            throw new IllegalArgumentException("markers must not exceed horizon limit");
        }
    }

    /**
     * marker query에 적용된 effective horizon, requested token, retention clamp, limit clamp, 정렬 정책을 담는다.
     */
    public record Horizon(
            OffsetDateTime since,
            OffsetDateTime until,
            String requestedSince,
            String defaultSince,
            String maxSince,
            int limit,
            int maxLimit,
            String order
    ) {

        /**
         * since/limit가 Story 5.8-b marker query 범위 안에 있는지 검증한다.
         */
        public Horizon {
            Objects.requireNonNull(since, "since must not be null");
            Objects.requireNonNull(until, "until must not be null");
            if (!until.isAfter(since)) {
                throw new IllegalArgumentException("until must be after since");
            }
            requestedSince = requireText(requestedSince, "requestedSince");
            defaultSince = requireText(defaultSince, "defaultSince");
            if (!DEFAULT_SINCE.equals(defaultSince)) {
                throw new IllegalArgumentException("defaultSince must be " + DEFAULT_SINCE);
            }
            maxSince = requireText(maxSince, "maxSince");
            if (!MAX_SINCE.equals(maxSince)) {
                throw new IllegalArgumentException("maxSince must be " + MAX_SINCE);
            }
            if (limit <= 0 || limit > MAX_LIMIT) {
                throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
            }
            if (maxLimit != MAX_LIMIT) {
                throw new IllegalArgumentException("maxLimit must be " + MAX_LIMIT);
            }
            order = requireText(order, "order");
            if (!ORDER.equals(order)) {
                throw new IllegalArgumentException("order must be " + ORDER);
            }
        }
    }

    /**
     * retention horizon 안에 표시할 snapshot row가 없을 때의 안전한 empty copy다.
     */
    public record EmptyState(
            String reasonCode,
            String message,
            String recommendedAction
    ) {

        public static EmptyState noSnapshotsInRetention() {
            return new EmptyState(
                    "no_snapshots_in_retention",
                    "보관 기간 안에 표시할 snapshot marker가 없습니다.",
                    "현재 상태는 application dashboard에서 확인하세요.");
        }

        /**
         * empty state가 health/recovery 완료를 단정하지 않는 bounded copy로 채워졌는지 검증한다.
         */
        public EmptyState {
            reasonCode = requireText(reasonCode, "reasonCode");
            message = requireText(message, "message");
            recommendedAction = requireText(recommendedAction, "recommendedAction");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
