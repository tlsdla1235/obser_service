package com.observation.portal.domain.snapshot.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Stored dashboard snapshot을 timeline point annotation으로 읽을 때 사용하는 marker type 집합이다.
 *
 * <p>이 enum은 operational event type이 아니며, Story 5.9의 promotion/dedup/suppression 결과를 약속하지 않는다.</p>
 */
public enum DashboardSnapshotMarkerType {
    SCHEDULED_SNAPSHOT("scheduled_snapshot"),
    QUERY_FALLBACK_SNAPSHOT("query_fallback_snapshot"),
    STATE_CHANGE("state_change"),
    STATE_OBSERVATION("state_observation"),
    HIGH_CONFIDENCE_CONCERN("high_confidence_concern"),
    SHORT_STRONG_SPIKE("short_strong_spike"),
    RECOVERY_OBSERVED("recovery_observed"),
    STORED_SNAPSHOT("stored_snapshot");

    private final String value;

    DashboardSnapshotMarkerType(String value) {
        this.value = value;
    }

    /**
     * public API에는 enum 이름이 아니라 계약의 lower-case marker type token을 반환한다.
     */
    @JsonValue
    public String value() {
        return value;
    }
}
