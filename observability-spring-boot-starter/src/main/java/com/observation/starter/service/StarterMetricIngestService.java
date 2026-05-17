package com.observation.starter.service;

import com.observation.starter.model.metric.ClosedMetricBucket;
import com.observation.starter.model.metric.DatasourcePoolMetricSample;
import com.observation.starter.model.metric.HttpServerObservationInput;
import com.observation.starter.model.metric.JvmMetricSample;
import com.observation.starter.queue.BoundedMetricQueue;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * host request path에서 들어온 관측 샘플을 local rollup에 기록하고 drain 대상 bucket만 bounded queue에 넣는다.
 *
 * <p>이 서비스는 portal client, HTTP 구현, retry/backoff를 알지 못한다. request path의 책임은
 * low-cardinality guard, local rollup, non-blocking enqueue에서 끝난다.</p>
 */
public final class StarterMetricIngestService implements ObservationSampleCollector {

    private final LowCardinalityHttpObservationGuard httpObservationGuard;
    private final MetricBucketRollupService rollupService;
    private final BoundedMetricQueue flushQueue;
    private final Supplier<Instant> nowUtcSupplier;

    /**
     * 현재 시각 supplier를 주입받아 request path enqueue 경계를 만든다.
     */
    public StarterMetricIngestService(
            LowCardinalityHttpObservationGuard httpObservationGuard,
            MetricBucketRollupService rollupService,
            BoundedMetricQueue flushQueue,
            Supplier<Instant> nowUtcSupplier) {
        this.httpObservationGuard = Objects.requireNonNull(httpObservationGuard, "httpObservationGuard must not be null");
        this.rollupService = Objects.requireNonNull(rollupService, "rollupService must not be null");
        this.flushQueue = Objects.requireNonNull(flushQueue, "flushQueue must not be null");
        this.nowUtcSupplier = Objects.requireNonNull(nowUtcSupplier, "nowUtcSupplier must not be null");
    }

    /**
     * HTTP observation input을 guard/rollup 처리한 뒤 drain 대상 bucket을 bounded queue로 넘긴다.
     */
    @Override
    public void recordHttpServerObservation(HttpServerObservationInput input) {
        rollupService.recordHttpServerObservation(httpObservationGuard.guard(input));
        drainDueBuckets();
    }

    /**
     * JVM 샘플을 local rollup에 기록하고 drain 대상 bucket을 bounded queue로 넘긴다.
     */
    @Override
    public void recordJvmMetricSample(JvmMetricSample sample) {
        rollupService.recordJvmMetricSample(sample);
        drainDueBuckets();
    }

    /**
     * datasource pool 샘플을 local rollup에 기록하고 drain 대상 bucket을 bounded queue로 넘긴다.
     */
    @Override
    public void recordDatasourcePoolMetricSample(DatasourcePoolMetricSample sample) {
        rollupService.recordDatasourcePoolMetricSample(sample);
        drainDueBuckets();
    }

    /**
     * scheduler/tick 경계에서 현재 시각 기준 drain 대상이 된 bucket을 bounded queue로 넘긴다.
     *
     * <p>새 샘플이 없어도 호출할 수 있으며, Story 2.3의 grace 정책에 따라 아직 due가 아닌 bucket은
     * {@link MetricBucketRollupService#drainClosedBuckets(Instant)}에서 반환되지 않는다.</p>
     */
    public void drainDueBuckets() {
        Instant nowUtc = Objects.requireNonNull(nowUtcSupplier.get(), "nowUtcSupplier must not return null");
        List<ClosedMetricBucket> closedBuckets = rollupService.drainClosedBuckets(nowUtc);
        for (ClosedMetricBucket closedBucket : closedBuckets) {
            flushQueue.offer(closedBucket);
        }
    }
}
