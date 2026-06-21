package com.observation.portal.domain.health.model;

/**
 * 내부 health 응답에서 component별 상태를 표현하는 비밀 없는 enum이다.
 */
public enum HealthCheckState {
    UP,
    DOWN,
    UNKNOWN
}
