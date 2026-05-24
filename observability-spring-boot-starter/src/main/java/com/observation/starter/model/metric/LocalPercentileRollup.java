package com.observation.starter.model.metric;

/**
 * instance 30초 bucket 안에서 starter가 직접 관측한 HTTP duration p95/p99 point다.
 *
 * <p>이 값은 다른 instance나 다른 bucket의 percentile 숫자와 병합하지 않고,
 * ingest envelope의 {@code summary.localPercentiles}로만 전달한다.</p>
 */
public record LocalPercentileRollup(
        long requestCount,
        long p95Ms,
        long p99Ms
) {

    /**
     * starter-local percentile point가 저장 가능한 non-negative primitive 값인지 검증한다.
     */
    public LocalPercentileRollup {
        if (requestCount < 0) {
            throw new IllegalArgumentException("requestCount must not be negative");
        }
        if (p95Ms < 0) {
            throw new IllegalArgumentException("p95Ms must not be negative");
        }
        if (p99Ms < 0) {
            throw new IllegalArgumentException("p99Ms must not be negative");
        }
        if (p99Ms < p95Ms) {
            throw new IllegalArgumentException("p99Ms must be greater than or equal to p95Ms");
        }
        if (requestCount == 0 && (p95Ms != 0 || p99Ms != 0)) {
            throw new IllegalArgumentException("empty request bucket percentiles must be zero");
        }
    }
}
