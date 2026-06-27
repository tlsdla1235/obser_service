package com.observation.portal.domain.health.model;

import com.observation.portal.domain.health.service.ReadinessCheckResult;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * `/internal/health/**` endpoint가 반환하는 최소 health payload다.
 *
 * <p>운영 검증에는 충분하지만 secret, datasource URL, SSM parameter 값, queue URL 같은 환경 실값은 담지 않는다.</p>
 */
public record InternalHealthResponse(
        HealthCheckState status,
        Map<String, HealthCheckState> checks) {

    /**
     * 응답 payload가 외부 mutation으로 바뀌지 않도록 component map을 복사한다.
     */
    public InternalHealthResponse {
        checks = Map.copyOf(checks);
    }

    /**
     * HTTP server가 요청에 응답 가능한 상태를 나타내는 liveness payload를 만든다.
     */
    public static InternalHealthResponse live() {
        return new InternalHealthResponse(HealthCheckState.UP, Map.of("http", HealthCheckState.UP));
    }

    /**
     * readiness probe 결과를 secret-free HTTP payload로 변환한다.
     */
    public static InternalHealthResponse ready(ReadinessCheckResult result) {
        Map<String, HealthCheckState> checks = new LinkedHashMap<>();
        checks.put("database", result.database());
        checks.put("flyway", result.flyway());
        return new InternalHealthResponse(result.ready() ? HealthCheckState.UP : HealthCheckState.DOWN, checks);
    }
}
