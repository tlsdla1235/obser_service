package com.observation.starter.model.metric;

import java.util.List;
import java.util.Objects;

/**
 * 하나의 bucket 안에서 method + normalized route별로 집계된 endpoint 메트릭이다.
 *
 * <p>endpoint 식별자는 Story 2.2의 {@link EndpointKey}만 사용하며 raw path, query string,
 * high-cardinality tag는 이 모델에 들어올 수 없다.</p>
 */
public record EndpointMetricRollup(
        EndpointKey endpointKey,
        long requestCount,
        long errorCount,
        List<HistogramBucket> durationBuckets
) {

    /**
     * endpoint rollup의 key와 count, histogram bucket 목록을 검증한다.
     */
    public EndpointMetricRollup {
        endpointKey = Objects.requireNonNull(endpointKey, "endpointKey must not be null");
        if (requestCount < 0) {
            throw new IllegalArgumentException("requestCount must not be negative");
        }
        if (errorCount < 0) {
            throw new IllegalArgumentException("errorCount must not be negative");
        }
        if (errorCount > requestCount) {
            throw new IllegalArgumentException("errorCount must not exceed requestCount");
        }
        durationBuckets = List.copyOf(Objects.requireNonNull(durationBuckets, "durationBuckets must not be null"));
    }
}
