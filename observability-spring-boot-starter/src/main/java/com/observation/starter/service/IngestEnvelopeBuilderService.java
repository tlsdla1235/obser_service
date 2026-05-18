package com.observation.starter.service;

import com.observation.starter.model.ingest.IngestEnvelope;
import com.observation.starter.model.ingest.IngestEnvelopeCandidate;
import com.observation.starter.model.ingest.IngestEnvelopeIdentity;
import com.observation.starter.model.metric.AppMetricRollup;
import com.observation.starter.model.metric.ClosedMetricBucket;
import com.observation.starter.model.metric.DatasourcePoolMetricSample;
import com.observation.starter.model.metric.EndpointMetricRollup;
import com.observation.starter.model.metric.HistogramBucket;
import com.observation.starter.model.metric.JvmMetricSample;
import com.observation.starter.model.time.MetricBucketInterval;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * grace 이후 drain된 sealed 30초 bucket을 ingest envelope payload와 idempotency header 후보로 변환한다.
 *
 * <p>이 builder는 로컬 identity 설정과 bucket snapshot만 사용한다. 포털 lookup, network call,
 * stateful dedupe 저장소, flush 시각 입력은 사용하지 않는다.</p>
 */
public final class IngestEnvelopeBuilderService {

    static final String SCHEMA_VERSION = "1.0";

    private final IngestEnvelopeIdentity identity;

    /**
     * 로컬 starter 설정에서 주입된 identity로 builder를 만든다.
     */
    public IngestEnvelopeBuilderService(IngestEnvelopeIdentity identity) {
        this.identity = Objects.requireNonNull(identity, "identity must not be null");
    }

    /**
     * 같은 closed bucket 입력에 대해 항상 같은 payload와 Idempotency-Key 후보를 만든다.
     */
    public IngestEnvelopeCandidate build(ClosedMetricBucket bucket) {
        ClosedMetricBucket requiredBucket = Objects.requireNonNull(bucket, "bucket must not be null");
        validateInterval(requiredBucket.interval());

        IngestEnvelope payload = new IngestEnvelope(
                SCHEMA_VERSION,
                new IngestEnvelope.Application(
                        identity.applicationName(),
                        identity.environment(),
                        identity.instance()),
                new IngestEnvelope.Bucket(
                        requiredBucket.interval().startUtc().toString(),
                        requiredBucket.interval().endUtc().toString(),
                        (int) MetricBucketInterval.DURATION.toSeconds()),
                toSummary(requiredBucket.appSummary()),
                toEndpoints(requiredBucket.endpointRollups()));
        return new IngestEnvelopeCandidate(payload, idempotencyKey(requiredBucket));
    }

    private IngestEnvelope.Summary toSummary(AppMetricRollup appSummary) {
        AppMetricRollup requiredSummary = Objects.requireNonNull(appSummary, "appSummary must not be null");
        return new IngestEnvelope.Summary(
                requiredSummary.requestCount(),
                requiredSummary.errorCount(),
                toDurationBuckets(requiredSummary.httpServerDurationBuckets()),
                requiredSummary.jvm().map(this::toJvm).orElse(null),
                requiredSummary.datasource().map(this::toDatasource).orElse(null));
    }

    private IngestEnvelope.Jvm toJvm(JvmMetricSample sample) {
        return new IngestEnvelope.Jvm(sample.cpuUsageRatio(), sample.heapUsedRatio());
    }

    private IngestEnvelope.Datasource toDatasource(DatasourcePoolMetricSample sample) {
        return new IngestEnvelope.Datasource(sample.poolUsageRatio());
    }

    private List<IngestEnvelope.Endpoint> toEndpoints(List<EndpointMetricRollup> endpointRollups) {
        return Objects.requireNonNull(endpointRollups, "endpointRollups must not be null").stream()
                .sorted(Comparator.comparing(rollup -> rollup.endpointKey().value()))
                .map(rollup -> new IngestEnvelope.Endpoint(
                        rollup.endpointKey().method(),
                        rollup.endpointKey().normalizedRoute().value(),
                        rollup.requestCount(),
                        rollup.errorCount(),
                        toDurationBuckets(rollup.durationBuckets())))
                .toList();
    }

    private List<IngestEnvelope.DurationBucket> toDurationBuckets(List<HistogramBucket> buckets) {
        return Objects.requireNonNull(buckets, "buckets must not be null").stream()
                .sorted(Comparator.comparingLong(HistogramBucket::leMs))
                .map(bucket -> new IngestEnvelope.DurationBucket(bucket.leMs(), bucket.count()))
                .toList();
    }

    private String idempotencyKey(ClosedMetricBucket bucket) {
        return String.join(":",
                identity.projectId(),
                identity.applicationName(),
                identity.environment(),
                identity.instance(),
                bucket.interval().startUtc().toString());
    }

    private static void validateInterval(MetricBucketInterval interval) {
        MetricBucketInterval requiredInterval = Objects.requireNonNull(interval, "interval must not be null");
        Duration duration = Duration.between(requiredInterval.startUtc(), requiredInterval.endUtc());
        if (!duration.equals(MetricBucketInterval.DURATION)) {
            throw new IllegalArgumentException("bucket interval must be exactly 30 seconds");
        }
    }
}
