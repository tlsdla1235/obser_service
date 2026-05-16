package com.observation.starter.service;

import com.observation.starter.model.metric.AppMetricRollup;
import com.observation.starter.model.metric.ClosedMetricBucket;
import com.observation.starter.model.metric.DatasourcePoolMetricSample;
import com.observation.starter.model.metric.EndpointMetricRollup;
import com.observation.starter.model.metric.HistogramBucket;
import com.observation.starter.model.metric.LowCardinalityHttpServerObservation;
import com.observation.starter.model.metric.JvmMetricSample;
import com.observation.starter.model.route.NormalizedRoute;
import com.observation.starter.model.time.MetricBucketInterval;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricBucketRollupServiceTest {

    @Test
    void mapsInstantToUtcThirtySecondBucketBoundary() {
        MetricBucketInterval first = MetricBucketInterval.containing(Instant.parse("2026-05-08T01:00:00Z"));
        MetricBucketInterval firstLastInstant = MetricBucketInterval.containing(
                Instant.parse("2026-05-08T01:00:29.999Z"));
        MetricBucketInterval second = MetricBucketInterval.containing(Instant.parse("2026-05-08T01:00:30Z"));

        assertEquals(Instant.parse("2026-05-08T01:00:00Z"), first.startUtc());
        assertEquals(Instant.parse("2026-05-08T01:00:30Z"), first.endUtc());
        assertEquals(first, firstLastInstant);
        assertEquals(Instant.parse("2026-05-08T01:00:30Z"), second.startUtc());
        assertEquals(Instant.parse("2026-05-08T01:01:00Z"), second.endUtc());
        assertTrue(first.contains(Instant.parse("2026-05-08T01:00:29.999Z")));
        assertFalse(first.contains(Instant.parse("2026-05-08T01:00:30Z")));
    }

    @Test
    void rollsUpAppSummaryAndEndpointCumulativeDurationBuckets() {
        MetricBucketRollupService service = new MetricBucketRollupService();

        service.recordHttpServerObservation(http("GET", "/orders/{orderId}",
                "2026-05-08T01:00:01Z", 200, false, 40));
        service.recordHttpServerObservation(http("GET", "/orders/{orderId}",
                "2026-05-08T01:00:03Z", 500, true, 60));
        service.recordHttpServerObservation(http("POST", "/orders",
                "2026-05-08T01:00:05Z", 503, true, 250));

        ClosedMetricBucket bucket = service.drainClosedBuckets(Instant.parse("2026-05-08T01:00:30Z")).get(0);

        AppMetricRollup summary = bucket.appSummary();
        assertEquals(3, summary.requestCount());
        assertEquals(2, summary.errorCount());
        assertEquals(1, countFor(summary.httpServerDurationBuckets(), 50));
        assertEquals(2, countFor(summary.httpServerDurationBuckets(), 100));
        assertEquals(3, countFor(summary.httpServerDurationBuckets(), 250));
        assertEquals(3, countFor(summary.httpServerDurationBuckets(), 500));
        assertEquals(3, countFor(summary.httpServerDurationBuckets(), 1000));

        EndpointMetricRollup getOrders = endpoint(bucket, "GET /orders/{orderId}");
        assertEquals(2, getOrders.requestCount());
        assertEquals(1, getOrders.errorCount());
        assertEquals(1, countFor(getOrders.durationBuckets(), 50));
        assertEquals(2, countFor(getOrders.durationBuckets(), 100));
        assertEquals(2, countFor(getOrders.durationBuckets(), 250));

        EndpointMetricRollup postOrders = endpoint(bucket, "POST /orders");
        assertEquals(1, postOrders.requestCount());
        assertEquals(1, postOrders.errorCount());
        assertEquals(0, countFor(postOrders.durationBuckets(), 100));
        assertEquals(1, countFor(postOrders.durationBuckets(), 250));
    }

    @Test
    void recordsLatestRuntimeRatioSamplesWithinBucketWithoutRequiringThem() {
        MetricBucketRollupService service = new MetricBucketRollupService();
        service.recordHttpServerObservation(http("GET", "/health", "2026-05-08T01:00:01Z", 200, false, 5));
        service.recordJvmMetricSample(new JvmMetricSample(Instant.parse("2026-05-08T01:00:02Z"), 0.2d, 0.3d));
        service.recordJvmMetricSample(new JvmMetricSample(Instant.parse("2026-05-08T01:00:20Z"), 0.8d, 0.9d));
        service.recordDatasourcePoolMetricSample(
                new DatasourcePoolMetricSample(Instant.parse("2026-05-08T01:00:10Z"), 0.7d));

        ClosedMetricBucket bucket = service.drainClosedBuckets(Instant.parse("2026-05-08T01:00:30Z")).get(0);

        assertEquals(0.8d, bucket.appSummary().jvm().orElseThrow().cpuUsageRatio());
        assertEquals(0.9d, bucket.appSummary().jvm().orElseThrow().heapUsedRatio());
        assertEquals(0.7d, bucket.appSummary().datasource().orElseThrow().poolUsageRatio());

        MetricBucketRollupService noRuntimeSamples = new MetricBucketRollupService();
        noRuntimeSamples.recordHttpServerObservation(http("GET", "/health",
                "2026-05-08T01:01:01Z", 200, false, 5));

        ClosedMetricBucket emptyRuntime = noRuntimeSamples.drainClosedBuckets(
                Instant.parse("2026-05-08T01:01:30Z")).get(0);

        assertTrue(emptyRuntime.appSummary().jvm().isEmpty());
        assertTrue(emptyRuntime.appSummary().datasource().isEmpty());
    }

    @Test
    void drainsOnlyClosedBucketsAsFlushCandidates() {
        MetricBucketRollupService service = new MetricBucketRollupService();
        service.recordHttpServerObservation(http("GET", "/health",
                "2026-05-08T01:00:29.999Z", 200, false, 5));
        service.recordHttpServerObservation(http("GET", "/health",
                "2026-05-08T01:00:30Z", 200, false, 5));

        assertTrue(service.drainClosedBuckets(Instant.parse("2026-05-08T01:00:29.999Z")).isEmpty());

        List<ClosedMetricBucket> firstDrain = service.drainClosedBuckets(Instant.parse("2026-05-08T01:00:30Z"));
        assertEquals(1, firstDrain.size());
        assertEquals(Instant.parse("2026-05-08T01:00:00Z"), firstDrain.get(0).interval().startUtc());

        List<ClosedMetricBucket> secondDrain = service.drainClosedBuckets(Instant.parse("2026-05-08T01:01:00Z"));
        assertEquals(1, secondDrain.size());
        assertEquals(Instant.parse("2026-05-08T01:00:30Z"), secondDrain.get(0).interval().startUtc());
    }

    @Test
    void dropsLateSamplesForAlreadyDrainedIntervalsWithoutCreatingDuplicateFlushCandidate() {
        MetricBucketRollupService service = new MetricBucketRollupService();
        service.recordHttpServerObservation(http("GET", "/health",
                "2026-05-08T01:00:01Z", 200, false, 5));

        List<ClosedMetricBucket> firstDrain = service.drainClosedBuckets(Instant.parse("2026-05-08T01:00:30Z"));
        assertEquals(1, firstDrain.size());

        service.recordHttpServerObservation(http("GET", "/health",
                "2026-05-08T01:00:02Z", 200, false, 5));
        service.recordJvmMetricSample(new JvmMetricSample(Instant.parse("2026-05-08T01:00:03Z"), 0.2d, 0.3d));
        service.recordDatasourcePoolMetricSample(
                new DatasourcePoolMetricSample(Instant.parse("2026-05-08T01:00:04Z"), 0.7d));

        assertTrue(service.drainClosedBuckets(Instant.parse("2026-05-08T01:01:00Z")).isEmpty());
        assertEquals(3, service.lateSampleDroppedCount());
    }

    @Test
    void acceptsFutureIntervalAfterDrainAndKeepsSealedWatermarkMonotonic() {
        MetricBucketRollupService service = new MetricBucketRollupService();
        service.recordHttpServerObservation(http("GET", "/health",
                "2026-05-08T01:00:01Z", 200, false, 5));

        service.drainClosedBuckets(Instant.parse("2026-05-08T01:00:30Z"));
        service.recordHttpServerObservation(http("GET", "/health",
                "2026-05-08T01:00:30Z", 200, false, 5));

        List<ClosedMetricBucket> secondInterval = service.drainClosedBuckets(Instant.parse("2026-05-08T01:01:00Z"));
        assertEquals(1, secondInterval.size());
        assertEquals(Instant.parse("2026-05-08T01:00:30Z"), secondInterval.get(0).interval().startUtc());

        service.drainClosedBuckets(Instant.parse("2026-05-08T01:00:30Z"));
        service.recordHttpServerObservation(http("GET", "/health",
                "2026-05-08T01:00:40Z", 200, false, 5));

        assertTrue(service.drainClosedBuckets(Instant.parse("2026-05-08T01:01:00Z")).isEmpty());
        assertEquals(1, service.lateSampleDroppedCount());
    }

    @Test
    void rollupInputBoundaryAcceptsOnlyLowCardinalityHttpObservation() {
        List<Class<?>> acceptedTypes = List.of(MetricBucketRollupService.class.getDeclaredMethods()).stream()
                .filter(method -> method.getName().equals("recordHttpServerObservation"))
                .map(Method::getParameterTypes)
                .map(types -> types[0])
                .toList();

        assertEquals(List.of(LowCardinalityHttpServerObservation.class), acceptedTypes);

        RecordComponent[] closedBucketComponents = ClosedMetricBucket.class.getRecordComponents();
        assertTrue(List.of(closedBucketComponents).stream()
                .noneMatch(component -> Map.class.isAssignableFrom(component.getType())));
        assertTrue(List.of(closedBucketComponents).stream()
                .noneMatch(component -> component.getName().toLowerCase(Locale.ROOT).contains("raw")));
        assertTrue(List.of(closedBucketComponents).stream()
                .noneMatch(component -> component.getName().toLowerCase(Locale.ROOT).contains("path")));
    }

    @Test
    void rollupModelsDoNotExposeP95LifecycleInsightOrPriorityCalculations() {
        List<Class<?>> rollupClasses = List.of(
                MetricBucketRollupService.class,
                MetricBucketInterval.class,
                ClosedMetricBucket.class,
                AppMetricRollup.class,
                EndpointMetricRollup.class);
        List<String> forbiddenTokens = List.of("p95", "percentile", "lifecycle", "insight", "priority");

        for (Class<?> rollupClass : rollupClasses) {
            for (Method method : rollupClass.getDeclaredMethods()) {
                String name = method.getName().toLowerCase(Locale.ROOT);
                assertTrue(forbiddenTokens.stream().noneMatch(name::contains),
                        () -> rollupClass.getName() + "#" + method.getName());
            }
            if (rollupClass.isRecord()) {
                for (RecordComponent component : rollupClass.getRecordComponents()) {
                    String name = component.getName().toLowerCase(Locale.ROOT);
                    assertTrue(forbiddenTokens.stream().noneMatch(name::contains),
                            () -> rollupClass.getName() + "#" + component.getName());
                }
            }
        }
    }

    private static EndpointMetricRollup endpoint(ClosedMetricBucket bucket, String endpointKey) {
        return bucket.endpointRollups().stream()
                .filter(rollup -> rollup.endpointKey().value().equals(endpointKey))
                .findFirst()
                .orElseThrow();
    }

    private static long countFor(List<HistogramBucket> buckets, long leMs) {
        return buckets.stream()
                .filter(bucket -> bucket.leMs() == leMs)
                .mapToLong(HistogramBucket::count)
                .findFirst()
                .orElseThrow();
    }

    private static LowCardinalityHttpServerObservation http(
            String method,
            String route,
            String observedAt,
            int statusCode,
            boolean error,
            long durationMillis) {
        return new LowCardinalityHttpServerObservation(
                Instant.parse(observedAt),
                method,
                statusCode,
                error,
                error ? "IllegalStateException" : null,
                Duration.ofMillis(durationMillis),
                NormalizedRoute.of(route));
    }
}
