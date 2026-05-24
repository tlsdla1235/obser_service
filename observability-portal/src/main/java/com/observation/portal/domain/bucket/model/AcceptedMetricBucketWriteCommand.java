package com.observation.portal.domain.bucket.model;

import com.observation.portal.domain.ingest.model.IngestEnvelopeRequest;
import com.observation.portal.domain.ingest.service.ValidatedIngestCandidate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 검증 완료 ingest 후보를 accepted_metric_buckets 저장 형태로 옮긴 내부 command다.
 *
 * <p>controller DTO나 JPA entity가 아니라 service와 repository 사이에서만 쓰는 persistence 입력 모델이다.</p>
 */
public record AcceptedMetricBucketWriteCommand(
        UUID projectId,
        String projectName,
        String applicationName,
        String environment,
        String instanceName,
        String schemaVersion,
        String idempotencyKey,
        String payloadHash,
        OffsetDateTime bucketStartUtc,
        OffsetDateTime bucketEndUtc,
        int durationSeconds,
        OffsetDateTime acceptedAt,
        long requestCount,
        long errorCount,
        List<DurationBucket> durationBuckets,
        Double cpuUsageRatio,
        Double heapUsedRatio,
        Double datasourcePoolUsageRatio,
        LocalPercentiles localPercentiles,
        List<EndpointBucket> endpoints
) {

    /**
     * repository 저장에 필요한 필수 값과 DB check constraint 이전의 기본 불변식을 검증한다.
     */
    public AcceptedMetricBucketWriteCommand {
        Objects.requireNonNull(projectId, "projectId must not be null");
        projectName = requireText(projectName, "projectName");
        applicationName = requireText(applicationName, "applicationName");
        environment = requireText(environment, "environment");
        instanceName = requireText(instanceName, "instanceName");
        schemaVersion = requireText(schemaVersion, "schemaVersion");
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        payloadHash = requireText(payloadHash, "payloadHash");
        Objects.requireNonNull(bucketStartUtc, "bucketStartUtc must not be null");
        Objects.requireNonNull(bucketEndUtc, "bucketEndUtc must not be null");
        Objects.requireNonNull(acceptedAt, "acceptedAt must not be null");
        if (!"1.0".equals(schemaVersion)) {
            throw new IllegalArgumentException("schemaVersion must be 1.0");
        }
        if (durationSeconds != 30) {
            throw new IllegalArgumentException("durationSeconds must be 30");
        }
        if (!bucketEndUtc.isAfter(bucketStartUtc)) {
            throw new IllegalArgumentException("bucketEndUtc must be after bucketStartUtc");
        }
        if (requestCount < 0) {
            throw new IllegalArgumentException("requestCount must not be negative");
        }
        if (errorCount < 0 || errorCount > requestCount) {
            throw new IllegalArgumentException("errorCount must be between zero and requestCount");
        }
        durationBuckets = List.copyOf(Objects.requireNonNull(durationBuckets, "durationBuckets must not be null"));
        endpoints = List.copyOf(Objects.requireNonNull(endpoints, "endpoints must not be null"));
        validateRatio(cpuUsageRatio, "cpuUsageRatio");
        validateRatio(heapUsedRatio, "heapUsedRatio");
        validateRatio(datasourcePoolUsageRatio, "datasourcePoolUsageRatio");
        if (localPercentiles != null && !Objects.equals(localPercentiles.requestCount(), requestCount)) {
            throw new IllegalArgumentException("localPercentiles requestCount must match requestCount");
        }
    }

    /**
     * validation을 통과한 ingest 후보와 payload hash를 accepted bucket 저장 command로 변환한다.
     */
    public static AcceptedMetricBucketWriteCommand from(
            ValidatedIngestCandidate candidate,
            String payloadHash,
            OffsetDateTime acceptedAt) {
        ValidatedIngestCandidate requiredCandidate = Objects.requireNonNull(candidate, "candidate must not be null");
        IngestEnvelopeRequest payload = requiredCandidate.payload();
        IngestEnvelopeRequest.Application application = payload.application();
        IngestEnvelopeRequest.Bucket bucket = payload.bucket();
        IngestEnvelopeRequest.Summary summary = payload.summary();
        IngestEnvelopeRequest.Jvm jvm = summary.jvm();
        IngestEnvelopeRequest.Datasource datasource = summary.datasource();

        return new AcceptedMetricBucketWriteCommand(
                requiredCandidate.verifiedProject().projectId(),
                requiredCandidate.verifiedProject().projectName(),
                application.name(),
                application.environment(),
                application.instance(),
                payload.schemaVersion(),
                requiredCandidate.idempotencyKey(),
                payloadHash,
                OffsetDateTime.parse(bucket.startUtc()),
                OffsetDateTime.parse(bucket.endUtc()),
                bucket.durationSeconds(),
                acceptedAt,
                summary.requestCount(),
                summary.errorCount(),
                summary.httpServerDurationBuckets().stream()
                        .map(DurationBucket::from)
                        .toList(),
                jvm == null ? null : jvm.cpuUsage(),
                jvm == null ? null : jvm.heapUsedRatio(),
                datasource == null ? null : datasource.poolUsageRatio(),
                summary.localPercentiles() == null ? null : LocalPercentiles.from(summary.localPercentiles()),
                payload.endpoints().stream()
                        .map(EndpointBucket::from)
                        .toList());
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static void validateRatio(Double value, String fieldName) {
        if (value != null && (value.isNaN() || value < 0.0d || value > 1.0d)) {
            throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0");
        }
    }

    /**
     * cumulative duration histogram bucket 한 칸을 JSON 저장용으로 보존한다.
     */
    public record DurationBucket(Long leMs, Long count) {

        /**
         * histogram upper bound와 cumulative count가 존재하도록 보장한다.
         */
        public DurationBucket {
            Objects.requireNonNull(leMs, "leMs must not be null");
            Objects.requireNonNull(count, "count must not be null");
        }

        private static DurationBucket from(IngestEnvelopeRequest.DurationBucket bucket) {
            return new DurationBucket(bucket.leMs(), bucket.count());
        }
    }

    /**
     * bounded endpoint bucket JSON 배열의 한 원소를 표현한다.
     */
    public record EndpointBucket(
            String method,
            String route,
            Long requestCount,
            Long errorCount,
            List<DurationBucket> durationBuckets
    ) {

        /**
         * endpoint별 method/route/count/histogram 저장 값을 immutable하게 고정한다.
         */
        public EndpointBucket {
            method = requireText(method, "method");
            route = requireText(route, "route");
            Objects.requireNonNull(requestCount, "requestCount must not be null");
            Objects.requireNonNull(errorCount, "errorCount must not be null");
            durationBuckets = List.copyOf(Objects.requireNonNull(
                    durationBuckets,
                    "durationBuckets must not be null"));
        }

        private static EndpointBucket from(IngestEnvelopeRequest.Endpoint endpoint) {
            return new EndpointBucket(
                    endpoint.method(),
                    endpoint.route(),
                    endpoint.requestCount(),
                    endpoint.errorCount(),
                    endpoint.durationBuckets().stream()
                            .map(DurationBucket::from)
                            .toList());
        }
    }

    /**
     * starter가 보낸 instance bucket scope의 canonical percentile point를 JSON 저장용으로 보존한다.
     */
    public record LocalPercentiles(
            String scope,
            String source,
            String bucketStartUtc,
            String bucketEndUtc,
            Long requestCount,
            Long p95Ms,
            Long p99Ms,
            Boolean mergeable
    ) {

        /**
         * service validation 이후 저장 가능한 필수 field만 다시 확인한다.
         */
        public LocalPercentiles {
            scope = requireText(scope, "localPercentiles.scope");
            source = requireText(source, "localPercentiles.source");
            bucketStartUtc = requireText(bucketStartUtc, "localPercentiles.bucketStartUtc");
            bucketEndUtc = requireText(bucketEndUtc, "localPercentiles.bucketEndUtc");
            Objects.requireNonNull(requestCount, "localPercentiles.requestCount must not be null");
            Objects.requireNonNull(p95Ms, "localPercentiles.p95Ms must not be null");
            Objects.requireNonNull(p99Ms, "localPercentiles.p99Ms must not be null");
            Objects.requireNonNull(mergeable, "localPercentiles.mergeable must not be null");
            if (!"instance_bucket".equals(scope)) {
                throw new IllegalArgumentException("localPercentiles.scope must be instance_bucket");
            }
            if (!"starter_local".equals(source)) {
                throw new IllegalArgumentException("localPercentiles.source must be starter_local");
            }
            if (requestCount < 0) {
                throw new IllegalArgumentException("localPercentiles.requestCount must not be negative");
            }
            if (p95Ms < 0) {
                throw new IllegalArgumentException("localPercentiles.p95Ms must not be negative");
            }
            if (p99Ms < 0 || p99Ms < p95Ms) {
                throw new IllegalArgumentException("localPercentiles.p99Ms must be greater than or equal to p95Ms");
            }
            if (!Boolean.FALSE.equals(mergeable)) {
                throw new IllegalArgumentException("localPercentiles.mergeable must be false");
            }
        }

        private static LocalPercentiles from(IngestEnvelopeRequest.LocalPercentiles localPercentiles) {
            return new LocalPercentiles(
                    localPercentiles.scope(),
                    localPercentiles.source(),
                    localPercentiles.bucketStartUtc(),
                    localPercentiles.bucketEndUtc(),
                    localPercentiles.requestCount(),
                    localPercentiles.p95Ms(),
                    localPercentiles.p99Ms(),
                    localPercentiles.mergeable());
        }
    }
}
