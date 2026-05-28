package com.observation.starter.spring.web;

import com.observation.starter.model.metric.HttpServerObservationInput;
import com.observation.starter.service.ObservationSampleCollector;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Spring MVC servlet request를 starter 내부 HTTP observation input으로 바꾸는 얇은 filter adapter다.
 *
 * <p>이 filter는 request path에서 포털 HTTP client를 호출하지 않고, local collector에 sample을 전달하는 일만 한다.
 * route template은 Spring MVC가 남긴 low-cardinality attribute만 사용하고 raw query string은 보관하지 않는다.</p>
 */
public final class StarterHttpServerObservationFilter implements Filter {

    private static final String BEST_MATCHING_PATTERN_ATTRIBUTE =
            "org.springframework.web.servlet.HandlerMapping.bestMatchingPattern";
    private static final String UNKNOWN = "UNKNOWN";

    private final ObservationSampleCollector collector;

    /**
     * HTTP sample을 받을 starter collector boundary를 주입받는다.
     */
    public StarterHttpServerObservationFilter(ObservationSampleCollector collector) {
        this.collector = Objects.requireNonNull(collector, "collector must not be null");
    }

    /**
     * servlet chain 실행 전후의 시각과 response status를 이용해 HTTP server observation sample을 기록한다.
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }

        Instant observedAt = Instant.now();
        long startedAtNanos = System.nanoTime();
        Throwable failure = null;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException exception) {
            failure = exception;
            throw exception;
        } finally {
            collector.recordHttpServerObservation(toInput(
                    httpRequest,
                    httpResponse,
                    failure,
                    observedAt,
                    Duration.ofNanos(Math.max(0L, System.nanoTime() - startedAtNanos))));
        }
    }

    private static HttpServerObservationInput toInput(
            HttpServletRequest request,
            HttpServletResponse response,
            Throwable failure,
            Instant observedAt,
            Duration duration) {
        int statusCode = response.getStatus();
        String errorType = failure == null ? null : failure.getClass().getSimpleName();
        return new HttpServerObservationInput(
                observedAt,
                method(request),
                statusCode,
                failure != null || statusCode >= 500,
                errorType,
                duration,
                routePattern(request),
                rawPathCandidate(request));
    }

    private static String method(HttpServletRequest request) {
        String method = request.getMethod();
        return method == null || method.isBlank() ? UNKNOWN : method.trim();
    }

    private static Optional<String> routePattern(HttpServletRequest request) {
        Object pattern = request.getAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern instanceof String value && !value.isBlank()) {
            return Optional.of(value.trim());
        }
        return Optional.empty();
    }

    private static Optional<String> rawPathCandidate(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        if (requestUri == null || requestUri.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(requestUri.trim());
    }
}
