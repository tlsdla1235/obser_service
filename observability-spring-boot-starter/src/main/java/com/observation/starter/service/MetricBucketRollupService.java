package com.observation.starter.service;

import com.observation.starter.model.metric.AppMetricRollup;
import com.observation.starter.model.metric.ClosedMetricBucket;
import com.observation.starter.model.metric.DatasourcePoolMetricSample;
import com.observation.starter.model.metric.EndpointKey;
import com.observation.starter.model.metric.EndpointMetricRollup;
import com.observation.starter.model.metric.HistogramBucket;
import com.observation.starter.model.metric.LowCardinalityHttpServerObservation;
import com.observation.starter.model.metric.JvmMetricSample;
import com.observation.starter.model.time.MetricBucketInterval;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Story 2.2 guard를 통과한 low-cardinality 샘플을 UTC 30초 bucket으로 집계한다.
 *
 * <p>이 서비스는 local rollup boundary만 담당한다. network call, HTTP client, queue worker,
 * ingest envelope 생성, portal persistence는 이후 story의 책임으로 남긴다.</p>
 */
public final class MetricBucketRollupService {

    private static final List<Long> DEFAULT_DURATION_BUCKET_UPPER_BOUNDS_MS =
            List.of(50L, 100L, 250L, 500L, 1000L);
    private static final Duration BUCKET_DURATION = MetricBucketInterval.DURATION;
    // 한 duration 이후 seal함
    private static final Duration DRAIN_GRACE_WINDOW = BUCKET_DURATION;

    private final Object lock = new Object();
    private final NavigableMap<MetricBucketInterval, MutableMetricBucket> buckets = new TreeMap<>();
    private final List<Long> durationBucketUpperBoundsMs;
    private Instant sealedThroughUtc = Instant.MIN;
    private long lateSampleDroppedCount;

    /**
     * MVP ingest contract의 기본 HTTP server duration histogram bucket 경계로 rollup 서비스를 만든다.
     */
    public MetricBucketRollupService() {
        this(DEFAULT_DURATION_BUCKET_UPPER_BOUNDS_MS);
    }

    /**
     * 테스트와 향후 설정 확장을 위해 duration histogram upper bound를 명시해 서비스를 만든다.
     */
    public MetricBucketRollupService(List<Long> durationBucketUpperBoundsMs) {
        this.durationBucketUpperBoundsMs = normalizeBucketBounds(durationBucketUpperBoundsMs);
    }

    /**
     * HTTP 서버 관측 샘플을 해당 UTC 30초 bucket의 app/endpoint rollup에 반영한다.
     */
    public void recordHttpServerObservation(LowCardinalityHttpServerObservation observation) {
        LowCardinalityHttpServerObservation requiredObservation =
                Objects.requireNonNull(observation, "observation must not be null");
        synchronized (lock) {
            recordIfOpen(requiredObservation.observedAt(), bucket -> bucket.record(requiredObservation));
        }
    }

    /**
     * JVM CPU/heap 비율 샘플을 해당 bucket의 latest valid sample로 반영한다.
     */
    public void recordJvmMetricSample(JvmMetricSample sample) {
        JvmMetricSample requiredSample = Objects.requireNonNull(sample, "sample must not be null");
        synchronized (lock) {
            recordIfOpen(requiredSample.observedAt(), bucket -> bucket.record(requiredSample));
        }
    }

    /**
     * datasource pool 사용률 샘플을 해당 bucket의 latest valid sample로 반영한다.
     */
    public void recordDatasourcePoolMetricSample(DatasourcePoolMetricSample sample) {
        DatasourcePoolMetricSample requiredSample = Objects.requireNonNull(sample, "sample must not be null");
        synchronized (lock) {
            recordIfOpen(requiredSample.observedAt(), bucket -> bucket.record(requiredSample));
        }
    }

    /**
     * {@code nowUtc} 기준으로 grace window가 지난 bucket을 flush candidate snapshot으로 반환하고 내부 버퍼에서 제거한다.
     *
     * <p>MVP에서는 30초 bucket duration과 같은 30초 grace window를 둔다. 따라서
     * {@code bucket.endUtc + bucketDuration <= nowUtc}인 interval만 drain 대상이 되며,
     * 한 번 drain된 interval은 sealed로 간주한다. 이후 같은 interval의 늦은 샘플은 duplicate
     * flush candidate를 만들지 않도록 drop한다.</p>
     */
    public List<ClosedMetricBucket> drainClosedBuckets(Instant nowUtc) {
        Instant requiredNowUtc = Objects.requireNonNull(nowUtc, "nowUtc must not be null");
        synchronized (lock) {
            List<MetricBucketInterval> closedIntervals = buckets.keySet().stream()
                    .filter(interval -> isDrainEligible(interval, requiredNowUtc))
                    .toList();
            List<ClosedMetricBucket> closedBuckets = new ArrayList<>(closedIntervals.size());
            for (MetricBucketInterval interval : closedIntervals) {
                MutableMetricBucket mutableBucket = buckets.remove(interval);
                if (mutableBucket != null) {
                    closedBuckets.add(mutableBucket.snapshot());
                }
            }
            closedIntervals.stream()
                    .map(MetricBucketInterval::endUtc)
                    .max(Comparator.naturalOrder())
                    .ifPresent(this::advanceSealedThroughUtc);
            closedBuckets.sort(Comparator.comparing(bucket -> bucket.interval().startUtc()));
            return List.copyOf(closedBuckets);
        }
    }

    /**
     * sealed interval에 들어와 drop된 late sample 수를 반환한다.
     */
    public long lateSampleDroppedCount() {
        synchronized (lock) {
            return lateSampleDroppedCount;
        }
    }

    private void recordIfOpen(Instant observedAt, Consumer<MutableMetricBucket> recorder) {
        MetricBucketInterval interval = MetricBucketInterval.containing(observedAt);
        if (isSealed(interval)) {
            lateSampleDroppedCount++;
            return;
        }
        MutableMetricBucket bucket = buckets.computeIfAbsent(interval,
                key -> new MutableMetricBucket(key, durationBucketUpperBoundsMs));
        recorder.accept(bucket);
    }

    private boolean isSealed(MetricBucketInterval interval) {
        return !interval.endUtc().isAfter(sealedThroughUtc);
    }

    private static boolean isDrainEligible(MetricBucketInterval interval, Instant nowUtc) {
        return !interval.endUtc().plus(DRAIN_GRACE_WINDOW).isAfter(nowUtc);
    }

    private void advanceSealedThroughUtc(Instant drainedEndUtc) {
        if (drainedEndUtc.isAfter(sealedThroughUtc)) {
            sealedThroughUtc = drainedEndUtc;
        }
    }

    private static List<Long> normalizeBucketBounds(List<Long> durationBucketUpperBoundsMs) {
        List<Long> bounds = List.copyOf(Objects.requireNonNull(
                durationBucketUpperBoundsMs, "durationBucketUpperBoundsMs must not be null"));
        if (bounds.isEmpty()) {
            throw new IllegalArgumentException("durationBucketUpperBoundsMs must not be empty");
        }

        long previous = 0;
        for (Long bound : bounds) {
            long requiredBound = Objects.requireNonNull(bound, "duration bucket bound must not be null");
            if (requiredBound <= 0) {
                throw new IllegalArgumentException("duration bucket bound must be positive");
            }
            if (requiredBound <= previous) {
                throw new IllegalArgumentException("duration bucket bounds must be strictly increasing");
            }
            previous = requiredBound;
        }
        return bounds;
    }






    private static final class MutableMetricBucket {

        private final MetricBucketInterval interval;
        private final DurationHistogramAccumulator appDurationHistogram;
        private final Map<EndpointKey, MutableEndpointRollup> endpoints = new TreeMap<>(
                Comparator.comparing(EndpointKey::value));
        private long requestCount;
        private long errorCount;
        private JvmMetricSample latestJvmSample;
        private DatasourcePoolMetricSample latestDatasourceSample;

        private MutableMetricBucket(MetricBucketInterval interval, List<Long> durationBucketUpperBoundsMs) {
            this.interval = interval;
            this.appDurationHistogram = new DurationHistogramAccumulator(durationBucketUpperBoundsMs);
        }

        private void record(LowCardinalityHttpServerObservation observation) {
            requestCount++;
            if (observation.error()) {
                errorCount++;
            }
            appDurationHistogram.record(observation.duration());
            endpoints.computeIfAbsent(observation.endpointKey(),
                    key -> new MutableEndpointRollup(key, appDurationHistogram.bounds()))
                    .record(observation);
        }

        private void record(JvmMetricSample sample) {
            if (latestJvmSample == null || !sample.observedAt().isBefore(latestJvmSample.observedAt())) {
                latestJvmSample = sample;
            }
        }

        private void record(DatasourcePoolMetricSample sample) {
            if (latestDatasourceSample == null
                    || !sample.observedAt().isBefore(latestDatasourceSample.observedAt())) {
                latestDatasourceSample = sample;
            }
        }

        private ClosedMetricBucket snapshot() {
            AppMetricRollup appSummary = new AppMetricRollup(
                    requestCount,
                    errorCount,
                    appDurationHistogram.snapshot(),
                    Optional.ofNullable(latestJvmSample),
                    Optional.ofNullable(latestDatasourceSample));
            List<EndpointMetricRollup> endpointRollups = endpoints.values().stream()
                    .map(MutableEndpointRollup::snapshot)
                    .toList();
            return new ClosedMetricBucket(interval, appSummary, endpointRollups);
        }
    }






    private static final class MutableEndpointRollup {

        private final EndpointKey endpointKey;
        private final DurationHistogramAccumulator durationHistogram;
        private long requestCount;
        private long errorCount;

        private MutableEndpointRollup(EndpointKey endpointKey, List<Long> durationBucketUpperBoundsMs) {
            this.endpointKey = endpointKey;
            this.durationHistogram = new DurationHistogramAccumulator(durationBucketUpperBoundsMs);
        }

        private void record(LowCardinalityHttpServerObservation observation) {
            requestCount++;
            if (observation.error()) {
                errorCount++;
            }
            durationHistogram.record(observation.duration());
        }

        private EndpointMetricRollup snapshot() {
            return new EndpointMetricRollup(endpointKey, requestCount, errorCount, durationHistogram.snapshot());
        }
    }








    private static final class DurationHistogramAccumulator {

        private final long[] bucketUpperBoundsMs;
        private final long[] cumulativeCounts;

        private DurationHistogramAccumulator(List<Long> bucketUpperBoundsMs) {
            this.bucketUpperBoundsMs = bucketUpperBoundsMs.stream().mapToLong(Long::longValue).toArray();
            this.cumulativeCounts = new long[this.bucketUpperBoundsMs.length];
        }

        private void record(Duration duration) {
            Duration requiredDuration = Objects.requireNonNull(duration, "duration must not be null");
            if (requiredDuration.isNegative()) {
                throw new IllegalArgumentException("duration must not be negative");
            }
            for (int index = 0; index < bucketUpperBoundsMs.length; index++) {
                if (requiredDuration.compareTo(Duration.ofMillis(bucketUpperBoundsMs[index])) <= 0) {
                    cumulativeCounts[index]++;
                }
            }
        }

        private List<Long> bounds() {
            return Arrays.stream(bucketUpperBoundsMs).boxed().toList();
        }

        private List<HistogramBucket> snapshot() {
            List<HistogramBucket> buckets = new ArrayList<>(bucketUpperBoundsMs.length);
            for (int index = 0; index < bucketUpperBoundsMs.length; index++) {
                buckets.add(new HistogramBucket(bucketUpperBoundsMs[index], cumulativeCounts[index]));
            }
            return List.copyOf(buckets);
        }
    }
}
