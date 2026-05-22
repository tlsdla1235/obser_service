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
}
