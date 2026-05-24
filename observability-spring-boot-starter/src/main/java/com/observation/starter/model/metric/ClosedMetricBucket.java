package com.observation.starter.model.metric;

import com.observation.starter.model.time.MetricBucketInterval;

import java.util.List;
import java.util.Objects;

/**
 * flush 대상으로 넘길 수 있는 닫힌 metric bucket snapshot이다.
 *
 * <p>하나의 {@code ClosedMetricBucket}은 닫힌 30초 구간 하나의 전체 집계 결과이며,
 * 아래 구조로 읽으면 된다.</p>
 *
 * <pre>{@code
 * ClosedMetricBucket
 * |-- interval: MetricBucketInterval
 * |-- appSummary: AppMetricRollup
 * |   |-- requestCount
 * |   |-- errorCount
 * |   |-- httpServerDurationBuckets: List<HistogramBucket>
 * |   |-- jvm: Optional<JvmMetricSample>
 * |   |-- datasource: Optional<DatasourcePoolMetricSample>
 * |   `-- localPercentiles: Optional<LocalPercentileRollup>
 * `-- endpointRollups: List<EndpointMetricRollup>
 *     |-- endpointKey: EndpointKey
 *     |-- requestCount
 *     |-- errorCount
 *     `-- durationBuckets: List<HistogramBucket>
 * }</pre>
 *
 * <p>{@code appSummary}는 bucket 전체를 합친 application-level 요약이고,
 * {@code endpointRollups}는 같은 bucket 안에서 {@code method + normalized route}별로
 * 나눈 endpoint-level 요약이다.</p>
 *
 * <p>이 모델은 queue worker나 ingest envelope를 만들지 않고, 다음 story가 소비할 수 있는
 * local rollup 결과 경계만 제공한다.</p>
 */
public record ClosedMetricBucket(
        MetricBucketInterval interval,
        AppMetricRollup appSummary,
        List<EndpointMetricRollup> endpointRollups
) {

    /**
     * 닫힌 bucket snapshot의 interval, app summary, endpoint rollup 목록을 검증한다.
     */
    public ClosedMetricBucket {
        interval = Objects.requireNonNull(interval, "interval must not be null");
        appSummary = Objects.requireNonNull(appSummary, "appSummary must not be null");
        endpointRollups = List.copyOf(Objects.requireNonNull(endpointRollups, "endpointRollups must not be null"));
    }
}
