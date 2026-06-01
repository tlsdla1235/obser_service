package com.observation.starter.service;

import com.observation.starter.model.metric.HttpServerObservationInput;
import com.observation.starter.model.metric.LowCardinalityHttpServerObservation;
import com.observation.starter.model.route.NormalizedRoute;

import java.util.Objects;

/**
 * Story 2.1 HTTP 관측 입력을 route/tag guard를 통과한 starter 내부 모델로 변환한다.
 *
 * <p>이 guard는 raw path와 high-cardinality tag를 다음 단계 입력에 남기지 않는다. route 정규화가
 * 실패하더라도 host request path로 예외를 전파하지 않고 {@code UNKNOWN} route로 수렴시킨다.</p>
 */
public final class LowCardinalityHttpObservationGuard {

    private final RouteNormalizationService routeNormalizationService;

    /**
     * 기본 route 정규화 정책을 사용하는 guard를 생성한다.
     */
    public LowCardinalityHttpObservationGuard() {
        this(new RouteNormalizationService());
    }

    /**
     * 테스트나 설정에서 주입한 route 정규화 정책을 사용하는 guard를 생성한다.
     */
    public LowCardinalityHttpObservationGuard(RouteNormalizationService routeNormalizationService) {
        this.routeNormalizationService = Objects.requireNonNull(
                routeNormalizationService,
                "routeNormalizationService must not be null");
    }

    /**
     * HTTP observation input을 normalized route만 가진 rollup 후보로 변환한다.
     */
    public LowCardinalityHttpServerObservation guard(HttpServerObservationInput input) {
        Objects.requireNonNull(input, "input must not be null");

        NormalizedRoute normalizedRoute;
        try {
            normalizedRoute = routeNormalizationService.normalize(input.routePattern(), input.rawPathCandidate());
        } catch (RuntimeException ignored) {
            normalizedRoute = NormalizedRoute.unknown();
        }

        return new LowCardinalityHttpServerObservation(
                input.observedAt(),
                input.method(),
                input.statusCode(),
                input.error(),
                input.errorType(),
                input.duration(),
                normalizedRoute);
    }
}
