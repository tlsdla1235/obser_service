package com.observation.portal.domain.history.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Operational event history가 노출할 수 있는 event type vocabulary다.
 *
 * <p>Story 5.9-a는 vocabulary와 response shape만 고정하고, 실제 promotion 조건과 suppression/dedup/period folding은
 * 후속 5.9-b가 구현한다.</p>
 */
public enum OperationalEventType {
    DEGRADED_ENTERED("degraded_entered"),
    DEGRADED_RESOLVED("degraded_resolved"),
    STALE_ENTERED("stale_entered"),
    DOWN_ENTERED("down_entered"),
    RECOVERY_OBSERVED("recovery_observed"),
    HIGH_CONFIDENCE_CONCERN("high_confidence_concern"),
    STATE_CHANGED("state_changed");

    private final String value;

    OperationalEventType(String value) {
        this.value = value;
    }

    /**
     * public API에는 enum 이름 대신 lower-case contract token을 직렬화한다.
     */
    @JsonValue
    public String value() {
        return value;
    }
}
