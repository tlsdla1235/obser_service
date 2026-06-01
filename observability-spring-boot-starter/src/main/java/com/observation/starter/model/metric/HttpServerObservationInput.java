package com.observation.starter.model.metric;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * HTTP 서버 관측값이 변환된 뒤 starter 내부에서 사용하는 요청 수준 샘플이다.
 *
 * <p>Spring/Micrometer 객체, 서블릿 요청/응답 객체, 원본 경로는 보관하지 않는다.
 * {@code routePattern}은 프레임워크가 제공한 저카디널리티 라우트 후보일 뿐이며,
 * {@code rawPathCandidate}는 allowlist matcher와 prefix collapse에 넘기는 starter 내부 임시
 * 경계다. 실제 query string은 이 모델에 들어오기 전에 폐기하되, routePattern의 {@code ?...}
 * bounded omission marker는 보존한다.</p>
 */
public record HttpServerObservationInput(
        Instant observedAt,
        String method,
        Integer statusCode,
        boolean error,
        String errorType,
        Duration duration,
        Optional<String> routePattern,
        Optional<String> rawPathCandidate
) {

    /**
     * 입력 샘플의 필수 필드와 소요 시간 제약 조건을 검증한다.
     */
    public HttpServerObservationInput {
        observedAt = Objects.requireNonNull(observedAt, "observedAt must not be null");
        method = normalizeRequired(method, "UNKNOWN");
        if (statusCode != null && (statusCode < 100 || statusCode > 999)) {
            throw new IllegalArgumentException("statusCode must be a three digit HTTP status when present");
        }
        errorType = normalizeOptionalText(errorType);
        duration = Objects.requireNonNull(duration, "duration must not be null");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration must not be negative");
        }
        routePattern = Objects.requireNonNull(routePattern, "routePattern must not be null")
                .map(HttpServerObservationInput::stripActualQueryString)
                .map(String::trim)
                .filter(value -> !value.isEmpty());
        rawPathCandidate = Objects.requireNonNull(rawPathCandidate, "rawPathCandidate must not be null")
                .map(HttpServerObservationInput::stripQueryString)
                .map(String::trim)
                .filter(value -> !value.isEmpty());
    }

    private static String normalizeRequired(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String stripQueryString(String value) {
        int queryStart = value.indexOf('?');
        if (queryStart < 0) {
            return value;
        }
        return value.substring(0, queryStart);
    }

    private static String stripActualQueryString(String value) {
        int queryStart = actualQueryStart(value);
        if (queryStart < 0) {
            return value;
        }
        return value.substring(0, queryStart);
    }

    private static int actualQueryStart(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) != '?') {
                continue;
            }
            if (value.startsWith("...", index + 1)) {
                index += 3;
                continue;
            }
            return index;
        }
        return -1;
    }
}
