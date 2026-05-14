package com.observation.starter.model.metric;

import com.observation.starter.model.route.NormalizedRoute;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * route/tag guard를 통과한 HTTP 서버 관측 샘플이다.
 *
 * <p>Story 2.3 rollup과 Story 2.5 envelope builder가 이 모델을 입력으로 사용하면 raw path,
 * query string, high-cardinality tag map을 직렬화 후보로 받을 수 없다.</p>
 */
public record LowCardinalityHttpServerObservation(
        Instant observedAt,
        String method,
        Integer statusCode,
        boolean error,
        String errorType,
        Duration duration,
        NormalizedRoute normalizedRoute
) {

    /**
     * HTTP 관측 샘플에서 rollup에 필요한 값만 검증하고 보관한다.
     */
    public LowCardinalityHttpServerObservation {
        observedAt = Objects.requireNonNull(observedAt, "observedAt must not be null");
        method = EndpointKey.normalizeMethod(method);
        if (statusCode != null && (statusCode < 100 || statusCode > 999)) {
            throw new IllegalArgumentException("statusCode must be a three digit HTTP status when present");
        }
        errorType = normalizeOptionalText(errorType);
        duration = Objects.requireNonNull(duration, "duration must not be null");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration must not be negative");
        }
        normalizedRoute = Objects.requireNonNull(normalizedRoute, "normalizedRoute must not be null");
    }

    /**
     * endpoint rollup에서 사용할 method + normalized route key를 생성한다.
     */
    public EndpointKey endpointKey() {
        return new EndpointKey(method, normalizedRoute);
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
