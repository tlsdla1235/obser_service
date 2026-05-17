package com.observation.starter.service;

import java.time.Duration;
import java.util.Objects;

/**
 * background flush worker 안에서만 적용되는 retry/backoff 정책이다.
 */
public record MetricFlushRetryPolicy(
        int maxAttempts,
        Duration backoff
) {

    /**
     * retry 횟수와 backoff duration의 기본 제약을 검증한다.
     */
    public MetricFlushRetryPolicy {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        backoff = Objects.requireNonNull(backoff, "backoff must not be null");
        if (backoff.isNegative()) {
            throw new IllegalArgumentException("backoff must not be negative");
        }
    }

    /**
     * MVP 기본 retry 정책을 반환한다.
     */
    public static MetricFlushRetryPolicy defaults() {
        return new MetricFlushRetryPolicy(3, Duration.ofMillis(100));
    }
}
