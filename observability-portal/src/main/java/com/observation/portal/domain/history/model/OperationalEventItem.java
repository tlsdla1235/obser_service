package com.observation.portal.domain.history.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Operational event history API의 compact event item이다.
 *
 * <p>`eventId`는 snapshot-derived deterministic id이며 durable incident workflow id가 아니다. `resolvedAt`은 stored
 * snapshot에서 해소 조건이 관찰된 시작 성격 event에만 채워질 수 있다.</p>
 */
public record OperationalEventItem(
        String eventId,
        OperationalEventType type,
        OperationalEventSeverity severity,
        String title,
        String summary,
        OffsetDateTime occurredAt,
        OffsetDateTime resolvedAt,
        String stateCode,
        BigDecimal confidence,
        UUID snapshotId,
        OperationalEventEvidence evidence,
        OperationalEventLinks links
) {

    /**
     * compact event shape의 필수 field와 nullable field 경계를 검증한다.
     */
    public OperationalEventItem {
        eventId = requireText(eventId, "eventId");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        title = requireText(title, "title");
        summary = requireText(summary, "summary");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        stateCode = requireText(stateCode, "stateCode");
        Objects.requireNonNull(snapshotId, "snapshotId must not be null");
        Objects.requireNonNull(evidence, "evidence must not be null");
        Objects.requireNonNull(links, "links must not be null");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
