package com.observation.starter.model.ingest;

import com.observation.starter.model.metric.EndpointKey;
import com.observation.starter.model.route.NormalizedRoute;
import com.observation.starter.model.time.MetricBucketInterval;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * starter가 포털 ingest API로 보낼 수 있는 schemaVersion 1.0 envelope payload 모델이다.
 *
 * <p>이 모델은 contract에 허용된 bounded metric만 표현하며 raw path, query string, arbitrary
 * tag map, custom metric payload, app/project/window percentile rollup, state/rule 계산 결과를 담지 않는다.</p>
 */
public record IngestEnvelope(
        String schemaVersion,
        Application application,
        Bucket bucket,
        Summary summary,
        List<Endpoint> endpoints
) {
    private static final String SUPPORTED_SCHEMA_VERSION = "1.0";

    /**
     * envelope payload의 필수 하위 객체와 MVP에서 허용하는 schema version을 검증한다.
     */
    public IngestEnvelope {
        schemaVersion = requireText(schemaVersion, "schemaVersion");
        if (!SUPPORTED_SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("schemaVersion must be 1.0");
        }
        application = Objects.requireNonNull(application, "application must not be null");
        bucket = Objects.requireNonNull(bucket, "bucket must not be null");
        summary = Objects.requireNonNull(summary, "summary must not be null");
        validateSummaryLocalPercentilesMatchBucket(summary.localPercentiles(), bucket);
        endpoints = List.copyOf(Objects.requireNonNull(endpoints, "endpoints must not be null"));
    }

    /**
     * application/environment/instance identity를 payload shape에 맞춰 표현한다.
     */
    public record Application(String name, String environment, String instance) {

        /**
         * identity 값은 전송 전에 모두 nonblank여야 한다.
         */
        public Application {
            name = requireText(name, "application.name");
            environment = requireText(environment, "application.environment");
            instance = requireText(instance, "application.instance");
        }
    }

    /**
     * UTC 30초 bucket metadata를 contract field 이름으로 표현한다.
     */
    public record Bucket(String startUtc, String endUtc, int durationSeconds) {

        /**
         * bucket timestamp가 UTC 30초 boundary와 고정 duration을 만족하는지 검증한다.
         */
        public Bucket {
            startUtc = requireText(startUtc, "bucket.startUtc");
            endUtc = requireText(endUtc, "bucket.endUtc");
            if (durationSeconds != MetricBucketInterval.DURATION.toSeconds()) {
                throw new IllegalArgumentException("bucket.durationSeconds must be 30");
            }
            Instant start = parseInstant(startUtc, "bucket.startUtc");
            Instant end = parseInstant(endUtc, "bucket.endUtc");
            if (!Duration.between(start, end).equals(MetricBucketInterval.DURATION)) {
                throw new IllegalArgumentException("bucket interval must be exactly 30 seconds");
            }
            new MetricBucketInterval(start, end);
        }
    }

    /**
     * application-level bounded metric summary를 표현한다.
     */
    public record Summary(
            long requestCount,
            long errorCount,
            List<DurationBucket> httpServerDurationBuckets,
            Jvm jvm,
            Datasource datasource,
            LocalPercentiles localPercentiles
    ) {

        /**
         * 이전 story fixture가 local percentile 없이 summary를 만들 수 있게 유지한다.
         */
        public Summary(
                long requestCount,
                long errorCount,
                List<DurationBucket> httpServerDurationBuckets,
                Jvm jvm,
                Datasource datasource) {
            this(requestCount, errorCount, httpServerDurationBuckets, jvm, datasource, null);
        }

        /**
         * count와 histogram/local percentile shape를 검증한다. JVM/datasource sample은 없으면 null로 둘 수 있다.
         */
        public Summary {
            validateCounts(requestCount, errorCount);
            httpServerDurationBuckets = List.copyOf(Objects.requireNonNull(
                    httpServerDurationBuckets,
                    "httpServerDurationBuckets must not be null"));
            validateHistogram("summary.httpServerDurationBuckets", httpServerDurationBuckets, requestCount);
            if (localPercentiles != null && localPercentiles.requestCount() != requestCount) {
                throw new IllegalArgumentException("summary.localPercentiles requestCount must match requestCount");
            }
        }
    }

    /**
     * instance bucket scope에서 starter가 직접 관측해 보고하는 canonical p95/p99 point다.
     */
    public record LocalPercentiles(
            String scope,
            String source,
            String bucketStartUtc,
            String bucketEndUtc,
            long requestCount,
            long p95Ms,
            long p99Ms,
            boolean mergeable
    ) {

        /**
         * contract의 fixed source/scope와 저장 가능한 primitive value를 검증한다.
         */
        public LocalPercentiles {
            scope = requireText(scope, "summary.localPercentiles.scope");
            source = requireText(source, "summary.localPercentiles.source");
            bucketStartUtc = requireText(bucketStartUtc, "summary.localPercentiles.bucketStartUtc");
            bucketEndUtc = requireText(bucketEndUtc, "summary.localPercentiles.bucketEndUtc");
            if (!"instance_bucket".equals(scope)) {
                throw new IllegalArgumentException("summary.localPercentiles.scope must be instance_bucket");
            }
            if (!"starter_local".equals(source)) {
                throw new IllegalArgumentException("summary.localPercentiles.source must be starter_local");
            }
            Instant start = parseInstant(bucketStartUtc, "summary.localPercentiles.bucketStartUtc");
            Instant end = parseInstant(bucketEndUtc, "summary.localPercentiles.bucketEndUtc");
            if (!Duration.between(start, end).equals(MetricBucketInterval.DURATION)) {
                throw new IllegalArgumentException("summary.localPercentiles interval must be exactly 30 seconds");
            }
            new MetricBucketInterval(start, end);
            if (requestCount < 0) {
                throw new IllegalArgumentException("summary.localPercentiles.requestCount must not be negative");
            }
            if (p95Ms < 0) {
                throw new IllegalArgumentException("summary.localPercentiles.p95Ms must not be negative");
            }
            if (p99Ms < 0) {
                throw new IllegalArgumentException("summary.localPercentiles.p99Ms must not be negative");
            }
            if (p99Ms < p95Ms) {
                throw new IllegalArgumentException(
                        "summary.localPercentiles.p99Ms must be greater than or equal to p95Ms");
            }
            if (mergeable) {
                throw new IllegalArgumentException("summary.localPercentiles.mergeable must be false");
            }
        }
    }

    /**
     * schemaVersion 1.0에서 허용하는 latest JVM ratio shape다.
     */
    public record Jvm(double cpuUsage, double heapUsedRatio) {

        /**
         * JVM ratio 값은 0.0부터 1.0 사이여야 한다.
         */
        public Jvm {
            validateRatio("cpuUsage", cpuUsage);
            validateRatio("heapUsedRatio", heapUsedRatio);
        }
    }

    /**
     * schemaVersion 1.0에서 허용하는 latest datasource pool ratio shape다.
     */
    public record Datasource(double poolUsageRatio) {

        /**
         * datasource pool ratio 값은 0.0부터 1.0 사이여야 한다.
         */
        public Datasource {
            validateRatio("poolUsageRatio", poolUsageRatio);
        }
    }

    /**
     * method + normalized route 기준의 endpoint metric summary를 표현한다.
     */
    public record Endpoint(
            String method,
            String route,
            long requestCount,
            long errorCount,
            List<DurationBucket> durationBuckets
    ) {

        /**
         * endpoint key와 count, histogram shape를 검증한다.
         */
        public Endpoint {
            method = EndpointKey.normalizeMethod(method);
            route = NormalizedRoute.of(route).value();
            validateCounts(requestCount, errorCount);
            durationBuckets = List.copyOf(Objects.requireNonNull(durationBuckets, "durationBuckets must not be null"));
            validateHistogram("endpoint.durationBuckets", durationBuckets, requestCount);
        }
    }

    /**
     * HTTP server duration cumulative histogram bucket 한 칸이다.
     */
    public record DurationBucket(long leMs, long count) {

        /**
         * histogram upper bound와 cumulative count를 검증한다.
         */
        public DurationBucket {
            if (leMs <= 0) {
                throw new IllegalArgumentException("leMs must be positive");
            }
            if (count < 0) {
                throw new IllegalArgumentException("count must not be negative");
            }
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static Instant parseInstant(String value, String name) {
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(name + " must be an ISO-8601 UTC instant", exception);
        }
    }

    private static void validateCounts(long requestCount, long errorCount) {
        if (requestCount < 0) {
            throw new IllegalArgumentException("requestCount must not be negative");
        }
        if (errorCount < 0) {
            throw new IllegalArgumentException("errorCount must not be negative");
        }
        if (errorCount > requestCount) {
            throw new IllegalArgumentException("errorCount must not exceed requestCount");
        }
    }

    private static void validateRatio(String name, double value) {
        if (Double.isNaN(value) || value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(name + " must be between 0.0 and 1.0");
        }
    }

    private static void validateHistogram(String name, List<DurationBucket> buckets, long requestCount) {
        if (buckets.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        long previousLeMs = Long.MIN_VALUE;
        long previousCount = Long.MIN_VALUE;
        for (DurationBucket bucket : buckets) {
            if (bucket.leMs() <= previousLeMs) {
                throw new IllegalArgumentException(name + " leMs must be strictly increasing");
            }
            if (bucket.count() < previousCount) {
                throw new IllegalArgumentException(name + " count must be cumulative and non-decreasing");
            }
            if (bucket.count() > requestCount) {
                throw new IllegalArgumentException(name + " count must not exceed requestCount");
            }
            previousLeMs = bucket.leMs();
            previousCount = bucket.count();
        }
    }

    private static void validateSummaryLocalPercentilesMatchBucket(
            LocalPercentiles localPercentiles,
            Bucket bucket) {
        if (localPercentiles == null) {
            return;
        }
        Instant bucketStart = parseInstant(bucket.startUtc(), "bucket.startUtc");
        Instant bucketEnd = parseInstant(bucket.endUtc(), "bucket.endUtc");
        Instant localStart = parseInstant(
                localPercentiles.bucketStartUtc(),
                "summary.localPercentiles.bucketStartUtc");
        Instant localEnd = parseInstant(
                localPercentiles.bucketEndUtc(),
                "summary.localPercentiles.bucketEndUtc");
        if (!bucketStart.equals(localStart)) {
            throw new IllegalArgumentException(
                    "summary.localPercentiles.bucketStartUtc must match bucket.startUtc");
        }
        if (!bucketEnd.equals(localEnd)) {
            throw new IllegalArgumentException(
                    "summary.localPercentiles.bucketEndUtc must match bucket.endUtc");
        }
    }
}
