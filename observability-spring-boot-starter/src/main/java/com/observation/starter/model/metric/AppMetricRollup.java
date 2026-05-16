package com.observation.starter.model.metric;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 하나의 bucket 안에서 집계된 application-level 메트릭 요약이다.
 *
 * <p>HTTP 요청/오류 수와 cumulative duration histogram을 포함하며, JVM과 datasource
 * 비율 샘플은 bucket 안에서 관측된 latest valid sample이 있을 때만 담는다.</p>
 */
public record AppMetricRollup(
        long requestCount,
        long errorCount,
        List<HistogramBucket> httpServerDurationBuckets,
        Optional<JvmMetricSample> jvm,
        Optional<DatasourcePoolMetricSample> datasource
) {

    /**
     * application summary가 음수 count와 null optional/list를 보관하지 않도록 검증한다.
     */
    public AppMetricRollup {
        if (requestCount < 0) {
            throw new IllegalArgumentException("requestCount must not be negative");
        }
        if (errorCount < 0) {
            throw new IllegalArgumentException("errorCount must not be negative");
        }
        if (errorCount > requestCount) {
            throw new IllegalArgumentException("errorCount must not exceed requestCount");
        }
        httpServerDurationBuckets = List.copyOf(Objects.requireNonNull(
                httpServerDurationBuckets, "httpServerDurationBuckets must not be null"));
        jvm = Objects.requireNonNull(jvm, "jvm must not be null");
        datasource = Objects.requireNonNull(datasource, "datasource must not be null");
    }
}
