package com.observation.portal.domain.state.model;

/**
 * starter heartbeat 축의 연결 의미를 metric state와 분리해 표현한다.
 */
public enum StarterConnectionMeaning {
    STARTER_CONNECTED,
    STARTER_DISCONNECTED,
    TELEMETRY_UNREACHABLE,
    UNKNOWN
}
