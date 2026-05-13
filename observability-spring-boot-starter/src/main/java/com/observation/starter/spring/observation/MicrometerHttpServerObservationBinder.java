package com.observation.starter.spring.observation;

import com.observation.starter.model.metric.HttpServerObservationInput;
import com.observation.starter.service.ObservationSampleCollector;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.LongSupplier;

/**
 * Micrometer HTTP 서버 관측값을 starter HTTP 샘플로 변환하는 핸들러다.
 * <p>
 * 이 클래스는 로컬 샘플을 {@link ObservationSampleCollector} 경계로만 전달한다.
 * 요청 경로에서 포털 클라이언트 호출, 큐 플러시, 수집 메시지 직렬화는 수행하지 않는다
 * <p>
 * ObservationHandler를 상속받음으로써, micrometer-observation이 관측마다 핸들러에게 계측을 알림. ->onStart, onStop으로 관리하게 된다
 * <p>
 *
 */
public final class MicrometerHttpServerObservationBinder implements ObservationHandler<Observation.Context> {

    /*
        observation.context 객체에는 다음과 같은 값들이 들어간다.
        name                  observation 이름
        contextualName         trace/span 등에 쓰일 수 있는 문맥 이름
        error                 observation 중 발생한 Throwable
        lowCardinalityKeyValues
        highCardinalityKeyValues
        parentObservation
        Map-like custom values

        여기서 lowCardinalityKeyValues는 spring에서
        method     GET, POST 같은 HTTP method
        status     200, 404, 500 같은 응답 코드
        http.route /users/{id} 같은 프레임워크 라우트 템플릿 후보
        uri        /users/123 같은 원본 경로가 들어올 수 있어 Story 2.1에서는 라우트 후보로 사용하지 않음
        outcome    SUCCESS, CLIENT_ERROR, SERVER_ERROR 등
        error      예외 클래스명 또는 none
        exception  deprecated, error와 비슷한 값

        highCardinalityKeyValues는
        http.url   실제 요청 URL

        로 구현체를 만든다
     */

    // 관측을 http.server.requests 로 제한
    private static final String HTTP_SERVER_OBSERVATION_NAME = "http.server.requests";
    private static final String UNKNOWN = "UNKNOWN";

    private final ObservationSampleCollector collector;
    private final LongSupplier monotonicNanos;
    private final Map<Observation.Context, Long> startNanosByContext = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * 시스템 단조 증가 시계로 소요 시간을 측정하는 바인딩 핸들러를 생성한다.
     */
    public MicrometerHttpServerObservationBinder(ObservationSampleCollector collector) {
        this(collector, System::nanoTime);
    }

    /**
     * 테스트에서 단조 증가 시계를 주입할 수 있는 바인딩 핸들러를 생성한다.
     */
    public MicrometerHttpServerObservationBinder(ObservationSampleCollector collector, LongSupplier monotonicNanos) {
        this.collector = Objects.requireNonNull(collector, "collector must not be null");
        this.monotonicNanos = Objects.requireNonNull(monotonicNanos, "monotonicNanos must not be null");
    }

    @Override
    public void onStart(Observation.Context context) {
        if (supportsContext(context)) {
            startNanosByContext.put(context, monotonicNanos.getAsLong());
        }
    }

    @Override
    public void onStop(Observation.Context context) {
        if (!supportsContext(context)) {
            return;
        }

        long stopNanos = monotonicNanos.getAsLong();
        Long startNanos = startNanosByContext.remove(context);
        Duration duration = startNanos == null
                ? Duration.ZERO
                : Duration.ofNanos(Math.max(0L, stopNanos - startNanos));

        collector.recordHttpServerObservation(toInput(context, duration));
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context != null && HTTP_SERVER_OBSERVATION_NAME.equals(context.getName());
    }

    /**
     Micrometer의 Observation.Context에서 우리가 필요한 값만 뽑아내는 함수
     */
    private static HttpServerObservationInput toInput(Observation.Context context, Duration duration) {
        Integer statusCode = lowCardinalityValue(context, "status")
                .flatMap(MicrometerHttpServerObservationBinder::parseStatusCode)
                .orElse(null);
        Throwable throwable = context.getError();
        String errorType = errorType(throwable, context).orElse(null);
        boolean error = throwable != null || errorType != null || (statusCode != null && statusCode >= 500);

        return new HttpServerObservationInput(
                Instant.now(),
                lowCardinalityValue(context, "method").orElse(UNKNOWN),
                statusCode,
                error,
                errorType,
                duration,
                routePattern(context));
    }

    private static Optional<String> routePattern(Observation.Context context) {
        return lowCardinalityValue(context, "http.route")
                .filter(MicrometerHttpServerObservationBinder::isUsableRouteCandidate);
    }

    /**
     * Story 2.1은 framework가 제공한 {@code http.route}만 후보로 넘긴다.
     * 원본 경로처럼 보이는 {@code uri}, {@code path}, URL, 임의 태그 판단은 Story 2.2의 최종 guard로 넘긴다.
     */
    private static boolean isUsableRouteCandidate(String value) {
        return !UNKNOWN.equalsIgnoreCase(value)
                && !value.contains("?")
                && !value.startsWith("http://")
                && !value.startsWith("https://");
    }

    /**
     * @param throwable
     * @param context
     * @return
     * 1. 실제 Throwable 객체가 있으면 그걸 신뢰한다.<p>
     * 2. Throwable이 없으면 태그에 남은 error/exception 문자열을 fallback으로 쓴다.
     * <p>
     * context.getError() -> IllegalStateException 객체<p>
     * low-cardinality "exception" -> "RuntimeException"<p>
     * 그렇기 때문에, context error를 먼저 확인 하는 것이 더 실제 객체에 가깝다
     */
    private static Optional<String> errorType(Throwable throwable, Observation.Context context) {
        if (throwable != null) {
            return Optional.of(throwable.getClass().getSimpleName());
        }
        return lowCardinalityValue(context, "exception")
                .filter(value -> !value.equalsIgnoreCase("none"))
                .filter(value -> !value.equalsIgnoreCase(UNKNOWN));
    }

    private static Optional<Integer> parseStatusCode(String value) {
        try {
            return Optional.of(Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    /**
        인자로 전달받은 context에 대해, 특정 key값을 찾는 method
     */
    private static Optional<String> lowCardinalityValue(Observation.Context context, String key) {
        for (KeyValue keyValue : context.getLowCardinalityKeyValues()) {
            if (key.equals(keyValue.getKey())) {
                String value = keyValue.getValue();
                if (value != null && !value.isBlank()) {
                    return Optional.of(value.trim());
                }
            }
        }
        return Optional.empty();
    }
}
