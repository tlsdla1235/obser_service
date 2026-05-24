package com.observation.portal.domain.state.model;

/**
 * 마지막 starter heartbeat telemetry row 또는 부재 상태를 요약한다.
 */
public enum StarterHeartbeatStatus {
    RECEIVED,
    MISSING,
    FAILED,
    UNKNOWN
}
