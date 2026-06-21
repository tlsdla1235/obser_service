package com.observation.portal.domain.health.service;

import com.observation.portal.domain.health.model.HealthCheckState;

/**
 * ready endpoint가 노출할 수 있는 component 상태만 담는 probe 결과다.
 */
public record ReadinessCheckResult(
        HealthCheckState database,
        HealthCheckState flyway) {

    /**
     * 운영 요청을 받을 수 있는 전체 ready 상태인지 계산한다.
     */
    public boolean ready() {
        return database == HealthCheckState.UP && flyway == HealthCheckState.UP;
    }

    /**
     * DB와 Flyway가 모두 통과한 ready 결과를 만든다.
     */
    public static ReadinessCheckResult readyResult() {
        return new ReadinessCheckResult(HealthCheckState.UP, HealthCheckState.UP);
    }

    /**
     * DB에 접근할 수 없어 Flyway 상태도 안전하게 확인할 수 없는 결과를 만든다.
     */
    public static ReadinessCheckResult databaseDown() {
        return new ReadinessCheckResult(HealthCheckState.DOWN, HealthCheckState.UNKNOWN);
    }

    /**
     * DB는 응답하지만 Flyway 상태가 ready 조건을 만족하지 못한 결과를 만든다.
     */
    public static ReadinessCheckResult flywayDown() {
        return new ReadinessCheckResult(HealthCheckState.UP, HealthCheckState.DOWN);
    }
}
