package com.observation.starter.service;

import com.observation.starter.model.ingest.IngestEnvelope;
import com.observation.starter.model.ingest.IngestEnvelopeCandidate;
import com.observation.starter.model.ingest.IngestEnvelopeIdentity;
import com.observation.starter.model.metric.AppMetricRollup;
import com.observation.starter.model.metric.ClosedMetricBucket;
import com.observation.starter.model.metric.DatasourcePoolMetricSample;
import com.observation.starter.model.metric.EndpointKey;
import com.observation.starter.model.metric.EndpointMetricRollup;
import com.observation.starter.model.metric.HistogramBucket;
import com.observation.starter.model.metric.JvmMetricSample;
import com.observation.starter.model.metric.LocalPercentileRollup;
import com.observation.starter.model.route.NormalizedRoute;
import com.observation.starter.model.time.MetricBucketInterval;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngestEnvelopeBuilderServiceTest {

    @Test
    void buildsSchemaOneEnvelopeAndIdempotencyKeyFromClosedBucket() {
        IngestEnvelopeBuilderService builder = new IngestEnvelopeBuilderService(identity());

        IngestEnvelopeCandidate candidate = builder.build(representativeBucket());
        IngestEnvelope payload = candidate.payload();

        assertEquals("1.0", payload.schemaVersion());
        assertEquals("orders-api", payload.application().name());
        assertEquals("prod", payload.application().environment());
        assertEquals("orders-api-7f9c9c8c9d-x2p4k", payload.application().instance());
        assertEquals("2026-05-08T01:00:00Z", payload.bucket().startUtc());
        assertEquals("2026-05-08T01:00:30Z", payload.bucket().endUtc());
        assertEquals(30, payload.bucket().durationSeconds());

        assertEquals(3, payload.summary().requestCount());
        assertEquals(1, payload.summary().errorCount());
        assertEquals(List.of(
                new IngestEnvelope.DurationBucket(50, 1),
                new IngestEnvelope.DurationBucket(100, 2),
                new IngestEnvelope.DurationBucket(250, 3),
                new IngestEnvelope.DurationBucket(500, 3),
                new IngestEnvelope.DurationBucket(1000, 3)), payload.summary().httpServerDurationBuckets());
        assertEquals(0.64d, payload.summary().jvm().cpuUsage());
        assertEquals(0.71d, payload.summary().jvm().heapUsedRatio());
        assertEquals(0.82d, payload.summary().datasource().poolUsageRatio());
        assertEquals("instance_bucket", payload.summary().localPercentiles().scope());
        assertEquals("starter_local", payload.summary().localPercentiles().source());
        assertEquals("2026-05-08T01:00:00Z", payload.summary().localPercentiles().bucketStartUtc());
        assertEquals("2026-05-08T01:00:30Z", payload.summary().localPercentiles().bucketEndUtc());
        assertEquals(3, payload.summary().localPercentiles().requestCount());
        assertEquals(250, payload.summary().localPercentiles().p95Ms());
        assertEquals(250, payload.summary().localPercentiles().p99Ms());
        assertFalse(payload.summary().localPercentiles().mergeable());

        assertEquals(2, payload.endpoints().size());
        assertEquals("GET", payload.endpoints().get(0).method());
        assertEquals("/orders/{orderId}", payload.endpoints().get(0).route());
        assertEquals("POST", payload.endpoints().get(1).method());
        assertEquals("/orders", payload.endpoints().get(1).route());

        assertEquals(
                "project-123:orders-api:prod:orders-api-7f9c9c8c9d-x2p4k:20260508T010000Z",
                candidate.idempotencyKey());
    }

    @Test
    void rejectsBlankIdentityBeforeFlushClientCanSend() {
        assertThrows(IllegalArgumentException.class,
                () -> new IngestEnvelopeIdentity("", "orders-api", "prod", "instance-1"));
        assertThrows(IllegalArgumentException.class,
                () -> new IngestEnvelopeIdentity("project-123", " ", "prod", "instance-1"));
        assertThrows(IllegalArgumentException.class,
                () -> new IngestEnvelopeIdentity("project-123", "orders-api", "", "instance-1"));
        assertThrows(IllegalArgumentException.class,
                () -> new IngestEnvelopeIdentity("project-123", "orders-api", "prod", "\t"));
    }

    @Test
    void rejectsHeaderUnsafeIdentityComponentsBeforeIdempotencyKeyBuild() {
        assertThrows(IllegalArgumentException.class,
                () -> new IngestEnvelopeIdentity("project:123", "orders-api", "prod", "instance-1"));
        assertThrows(IllegalArgumentException.class,
                () -> new IngestEnvelopeIdentity("project-123", "orders api", "prod", "instance-1"));
        assertThrows(IllegalArgumentException.class,
                () -> new IngestEnvelopeIdentity("project-123", "orders-api", "pro\nd", "instance-1"));
        assertThrows(IllegalArgumentException.class,
                () -> new IngestEnvelopeIdentity("project-123", "orders-api", "prod", "instance/1"));
    }

    @Test
    void rejectsUnsupportedSchemaVersionWhenEnvelopeIsCreatedDirectly() {
        IngestEnvelope payload = new IngestEnvelopeBuilderService(identity())
                .build(representativeBucket())
                .payload();

        assertThrows(IllegalArgumentException.class,
                () -> new IngestEnvelope(
                        "1.1",
                        payload.application(),
                        payload.bucket(),
                        payload.summary(),
                        payload.endpoints()));
    }

    @Test
    void sameClosedBucketBuildsSamePayloadAndIdempotencyKeyWithoutClockInput() {
        IngestEnvelopeBuilderService builder = new IngestEnvelopeBuilderService(identity());
        ClosedMetricBucket bucket = representativeBucket();

        IngestEnvelopeCandidate first = builder.build(bucket);
        IngestEnvelopeCandidate second = builder.build(bucket);

        assertEquals(first, second);
    }

    @Test
    void idempotencyKeyChangesOnlyWithBucketStartIdentityTuple() {
        IngestEnvelopeBuilderService builder = new IngestEnvelopeBuilderService(identity());

        assertEquals(
                "project-123:orders-api:prod:orders-api-7f9c9c8c9d-x2p4k:20260508T010000Z",
                builder.build(bucketStartingAt("2026-05-08T01:00:00Z")).idempotencyKey());
        assertEquals(
                "project-123:orders-api:prod:orders-api-7f9c9c8c9d-x2p4k:20260508T010030Z",
                builder.build(bucketStartingAt("2026-05-08T01:00:30Z")).idempotencyKey());
    }

    @Test
    void idempotencyKeyComponentsDoNotContainDelimiterOrControlCharacters() {
        IngestEnvelopeBuilderService builder = new IngestEnvelopeBuilderService(identity());

        String idempotencyKey = builder.build(bucketStartingAt("2026-05-08T01:00:00Z")).idempotencyKey();

        assertEquals(5, idempotencyKey.split(":", -1).length);
        assertEquals("20260508T010000Z", idempotencyKey.split(":", -1)[4]);
        assertFalse(idempotencyKey.chars().anyMatch(character -> character <= 0x1F || character == 0x7F));
    }

    @Test
    void rejectsInvalidSummaryHistogramContracts() {
        IngestEnvelopeBuilderService builder = new IngestEnvelopeBuilderService(identity());

        assertThrows(IllegalArgumentException.class,
                () -> builder.build(bucketWithSummaryHistogram(List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> builder.build(bucketWithSummaryHistogram(List.of(
                        new HistogramBucket(50, 1),
                        new HistogramBucket(50, 1)))));
        assertThrows(IllegalArgumentException.class,
                () -> builder.build(bucketWithSummaryHistogram(List.of(
                        new HistogramBucket(50, 2),
                        new HistogramBucket(100, 1)))));
        assertThrows(IllegalArgumentException.class,
                () -> builder.build(bucketWithSummaryHistogram(List.of(
                        new HistogramBucket(50, 1),
                        new HistogramBucket(100, 3)))));
    }

    @Test
    void rejectsInvalidEndpointHistogramContracts() {
        IngestEnvelopeBuilderService builder = new IngestEnvelopeBuilderService(identity());

        assertThrows(IllegalArgumentException.class,
                () -> builder.build(bucketWithEndpointHistogram(List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> builder.build(bucketWithEndpointHistogram(List.of(
                        new HistogramBucket(50, 1),
                        new HistogramBucket(50, 1)))));
        assertThrows(IllegalArgumentException.class,
                () -> builder.build(bucketWithEndpointHistogram(List.of(
                        new HistogramBucket(50, 2),
                        new HistogramBucket(100, 1)))));
        assertThrows(IllegalArgumentException.class,
                () -> builder.build(bucketWithEndpointHistogram(List.of(
                        new HistogramBucket(50, 1),
                        new HistogramBucket(100, 3)))));
    }

    @Test
    void envelopeConvergesUnsupportedEndpointMethodAndInvalidRouteToUnknown() {
        IngestEnvelope.Endpoint endpoint = new IngestEnvelope.Endpoint(
                "CONNECT",
                "/orders/",
                1,
                0,
                List.of(new IngestEnvelope.DurationBucket(50, 1)));

        assertEquals("UNKNOWN", endpoint.method());
        assertEquals("UNKNOWN", endpoint.route());
    }

    @Test
    void payloadShapeDoesNotExposeRawPathQueryTagsCustomMetricsOrDerivedPortalFields() {
        List<Class<?>> payloadClasses = List.of(
                IngestEnvelope.class,
                IngestEnvelope.Application.class,
                IngestEnvelope.Bucket.class,
                IngestEnvelope.Summary.class,
                IngestEnvelope.LocalPercentiles.class,
                IngestEnvelope.Endpoint.class,
                IngestEnvelope.DurationBucket.class);
        List<String> forbiddenTokens = List.of(
                "raw", "query", "tag", "custom", "path", "state", "rule", "priority");

        for (Class<?> payloadClass : payloadClasses) {
            for (RecordComponent component : payloadClass.getRecordComponents()) {
                String name = component.getName().toLowerCase(Locale.ROOT);
                assertTrue(forbiddenTokens.stream().noneMatch(name::contains),
                        () -> payloadClass.getName() + "#" + component.getName());
                assertFalse(Map.class.isAssignableFrom(component.getType()),
                        () -> payloadClass.getName() + "#" + component.getName());
            }
        }
    }

    @Test
    void builderHasNoPortalClientNetworkOrStatefulDedupeDependency() {
        List<Class<?>> fieldTypes = Arrays.stream(IngestEnvelopeBuilderService.class.getDeclaredFields())
                .map(Field::getType)
                .toList();
        List<String> fieldTypeNames = fieldTypes.stream()
                .map(Class::getName)
                .map(String::toLowerCase)
                .toList();

        assertTrue(fieldTypeNames.stream().noneMatch(name -> name.contains("client")
                || name.contains("repository")
                || name.contains("http")
                || name.contains("dedupe")
                || name.contains("cache")));

        List<Class<?>> buildInputTypes = Arrays.stream(IngestEnvelopeBuilderService.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("build"))
                .map(Method::getParameterTypes)
                .map(types -> types[0])
                .toList();

        assertEquals(List.of(ClosedMetricBucket.class), buildInputTypes);
    }

    private static IngestEnvelopeIdentity identity() {
        return new IngestEnvelopeIdentity(
                "project-123",
                "orders-api",
                "prod",
                "orders-api-7f9c9c8c9d-x2p4k");
    }

    private static ClosedMetricBucket representativeBucket() {
        return new ClosedMetricBucket(
                interval("2026-05-08T01:00:00Z"),
                new AppMetricRollup(
                        3,
                        1,
                        List.of(
                                new HistogramBucket(250, 3),
                                new HistogramBucket(50, 1),
                                new HistogramBucket(1000, 3),
                                new HistogramBucket(100, 2),
                                new HistogramBucket(500, 3)),
                        Optional.of(new JvmMetricSample(Instant.parse("2026-05-08T01:00:20Z"), 0.64d, 0.71d)),
                        Optional.of(new DatasourcePoolMetricSample(
                                Instant.parse("2026-05-08T01:00:25Z"),
                                0.82d)),
                        Optional.of(new LocalPercentileRollup(3, 250, 250))),
                List.of(
                        endpoint("POST", "/orders", 1, 1, List.of(
                                new HistogramBucket(250, 1),
                                new HistogramBucket(50, 0),
                                new HistogramBucket(100, 0),
                                new HistogramBucket(500, 1),
                                new HistogramBucket(1000, 1))),
                        endpoint("GET", "/orders/{orderId}", 2, 0, List.of(
                                new HistogramBucket(250, 2),
                                new HistogramBucket(50, 1),
                                new HistogramBucket(100, 2),
                                new HistogramBucket(500, 2),
                                new HistogramBucket(1000, 2)))));
    }

    private static ClosedMetricBucket bucketStartingAt(String startUtc) {
        return new ClosedMetricBucket(
                interval(startUtc),
                new AppMetricRollup(0, 0, List.of(new HistogramBucket(50, 0)), Optional.empty(), Optional.empty()),
                List.of());
    }

    private static ClosedMetricBucket bucketWithSummaryHistogram(List<HistogramBucket> buckets) {
        return new ClosedMetricBucket(
                interval("2026-05-08T01:00:00Z"),
                new AppMetricRollup(2, 0, buckets, Optional.empty(), Optional.empty()),
                List.of());
    }

    private static ClosedMetricBucket bucketWithEndpointHistogram(List<HistogramBucket> buckets) {
        return new ClosedMetricBucket(
                interval("2026-05-08T01:00:00Z"),
                new AppMetricRollup(
                        2,
                        0,
                        List.of(new HistogramBucket(50, 1), new HistogramBucket(100, 2)),
                        Optional.empty(),
                        Optional.empty()),
                List.of(endpoint("GET", "/orders", 2, 0, buckets)));
    }

    private static MetricBucketInterval interval(String startUtc) {
        Instant start = Instant.parse(startUtc);
        return new MetricBucketInterval(start, start.plusSeconds(30));
    }

    private static EndpointMetricRollup endpoint(
            String method,
            String route,
            long requestCount,
            long errorCount,
            List<HistogramBucket> durationBuckets) {
        return new EndpointMetricRollup(
                new EndpointKey(method, NormalizedRoute.of(route)),
                requestCount,
                errorCount,
                durationBuckets);
    }
}
