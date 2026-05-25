package com.observation.portal.domain.bucket.model;

import java.util.Objects;

/**
 * dashboard current window에서 합산한 request/error count만 담는 중립 조회 모델이다.
 *
 * <p>repository는 이 값으로 state, sample readiness, zeroInsight를 판단하지 않고 service 계층에 원시 합계만 전달한다.</p>
 */
public record WindowBucketAggregate(long requestCount, long errorCount) {

    /**
     * window 합계가 음수가 되지 않도록 보호한다.
     */
    public WindowBucketAggregate {
        if (requestCount < 0) {
            throw new IllegalArgumentException("requestCount must not be negative");
        }
        if (errorCount < 0) {
            throw new IllegalArgumentException("errorCount must not be negative");
        }
    }

    /**
     * JPQL aggregate 결과가 없을 때 null sum을 0으로 치환해 neutral model을 만든다.
     */
    public WindowBucketAggregate(Long requestCount, Long errorCount) {
        this(zeroIfNull(requestCount), zeroIfNull(errorCount));
    }

    /**
     * matching bucket이 없을 때 사용할 빈 합계다.
     */
    public static WindowBucketAggregate zero() {
        return new WindowBucketAggregate(0L, 0L);
    }

    private static long zeroIfNull(Long value) {
        return Objects.requireNonNullElse(value, 0L);
    }
}
