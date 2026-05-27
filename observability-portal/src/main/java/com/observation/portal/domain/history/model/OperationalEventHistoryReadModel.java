package com.observation.portal.domain.history.model;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Operational event history collection API가 반환하는 compact read model이다.
 *
 * <p>top-level source는 `dashboard_snapshots`로 고정하고, event list는 `occurredAt desc`, `eventId asc` 순서로
 * 정렬한다.</p>
 */
public record OperationalEventHistoryReadModel(
        OffsetDateTime generatedAt,
        UUID applicationId,
        String source,
        Horizon horizon,
        List<OperationalEventItem> events
) {

    public static final String SOURCE = "dashboard_snapshots";
    public static final String DEFAULT_SINCE = "24h";
    public static final String MAX_SINCE = "14d";
    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;
    public static final String ORDER = "occurredAt_desc";

    private static final Comparator<OperationalEventItem> EVENT_ORDER = Comparator
            .comparing(OperationalEventItem::occurredAt, Comparator.reverseOrder())
            .thenComparing(OperationalEventItem::eventId);

    /**
     * response source/horizon을 검증하고 event order contract를 생성자에서 고정한다.
     */
    public OperationalEventHistoryReadModel {
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        Objects.requireNonNull(applicationId, "applicationId must not be null");
        source = requireText(source, "source");
        if (!SOURCE.equals(source)) {
            throw new IllegalArgumentException("source must be " + SOURCE);
        }
        Objects.requireNonNull(horizon, "horizon must not be null");
        events = Objects.requireNonNull(events, "events must not be null").stream()
                .sorted(EVENT_ORDER)
                .limit(horizon.limit())
                .toList();
    }

    /**
     * query에 적용된 effective horizon, requested token, clamp metadata, limit, order를 담는다.
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
         * Story 5.9-a query policy metadata가 response에 안정적으로 포함되도록 검증한다.
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

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
