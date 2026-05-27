package com.observation.portal.domain.history.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Operational event item의 compact severity token이다.
 *
 * <p>marker severity를 그대로 재사용하지 않고 event type 기반 mapping을 받을 별도 field로 유지한다.</p>
 */
public enum OperationalEventSeverity {
    INFO("info"),
    WARNING("warning"),
    CRITICAL("critical");

    private final String value;

    OperationalEventSeverity(String value) {
        this.value = value;
    }

    /**
     * public API에는 enum 이름 대신 lower-case severity token을 직렬화한다.
     */
    @JsonValue
    public String value() {
        return value;
    }
}
