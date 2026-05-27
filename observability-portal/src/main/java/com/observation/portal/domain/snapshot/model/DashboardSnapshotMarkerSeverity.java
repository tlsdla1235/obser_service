package com.observation.portal.domain.snapshot.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Snapshot marker가 저장 당시 read model에서 읽은 주의 수준이다.
 *
 * <p>severity는 stored state/triage/recovery field에서만 읽으며 capture reason 단독 값이나 operational event 승격 결과로
 * 확정하지 않는다.</p>
 */
public enum DashboardSnapshotMarkerSeverity {
    INFO("info"),
    WARNING("warning"),
    CRITICAL("critical");

    private final String value;

    DashboardSnapshotMarkerSeverity(String value) {
        this.value = value;
    }

    /**
     * public API에는 enum 이름이 아니라 계약의 lower-case severity token을 반환한다.
     */
    @JsonValue
    public String value() {
        return value;
    }
}
