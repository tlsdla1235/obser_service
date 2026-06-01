package com.observation.starter.spring.observation;

import com.observation.starter.model.metric.DatasourcePoolMetricSample;
import com.observation.starter.model.metric.HttpServerObservationInput;
import com.observation.starter.model.metric.JvmMetricSample;
import com.observation.starter.model.metric.LowCardinalityHttpServerObservation;
import com.observation.starter.service.LowCardinalityHttpObservationGuard;
import com.observation.starter.service.ObservationSampleCollector;
import com.observation.starter.service.RouteNormalizationService;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MicrometerHttpServerObservationBinderTest {

    @Test
    void mapsSyntheticHttpServerObservationAndKeepsFrameworkRouteTemplateCandidate() {
        RecordingCollector collector = new RecordingCollector();
        AtomicLong nanos = new AtomicLong(10_000L);
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig()
                .observationHandler(new MicrometerHttpServerObservationBinder(collector, nanos::get));

        Observation observation = Observation.start("http.server.requests", registry)
                .lowCardinalityKeyValue("method", "POST")
                .lowCardinalityKeyValue("status", "500")
                .lowCardinalityKeyValue("http.route", "/orders/{orderId}");

        nanos.addAndGet(250_000_000L);
        observation.error(new IllegalStateException("boom"));
        observation.stop();

        HttpServerObservationInput input = collector.onlyHttp();
        assertEquals("POST", input.method());
        assertEquals(500, input.statusCode());
        assertTrue(input.error());
        assertEquals("IllegalStateException", input.errorType());
        assertEquals(Duration.ofMillis(250), input.duration());
        assertEquals(Optional.of("/orders/{orderId}"), input.routePattern());
        assertTrue(input.rawPathCandidate().isEmpty());
    }

    @Test
    void frameworkRouteTemplateAndLowCardinalityRawCandidateAreBothStoredForGuardDecision() {
        RecordingCollector collector = new RecordingCollector();
        AtomicLong nanos = new AtomicLong(1L);
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig()
                .observationHandler(new MicrometerHttpServerObservationBinder(collector, nanos::get));

        Observation observation = Observation.start("http.server.requests", registry)
                .lowCardinalityKeyValue("method", "GET")
                .lowCardinalityKeyValue("status", "200")
                .lowCardinalityKeyValue("http.route", "/orders/{orderId}")
                .lowCardinalityKeyValue("uri", "/orders/123?debug=true");

        nanos.addAndGet(5_000_000L);
        observation.stop();

        HttpServerObservationInput input = collector.onlyHttp();
        assertEquals(Optional.of("/orders/{orderId}"), input.routePattern());
        assertEquals(Optional.of("/orders/123"), input.rawPathCandidate(),
                "guard가 http.route 성공 여부를 판단할 수 있도록 low-cardinality raw 후보를 함께 넘긴다");
    }

    @Test
    void invalidPresentHttpRouteKeepsLowCardinalityRawCandidateForServiceFallback() {
        RecordingCollector collector = new RecordingCollector();
        AtomicLong nanos = new AtomicLong(1L);
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig()
                .observationHandler(new MicrometerHttpServerObservationBinder(collector, nanos::get));

        Observation observation = Observation.start("http.server.requests", registry)
                .lowCardinalityKeyValue("method", "GET")
                .lowCardinalityKeyValue("status", "200")
                .lowCardinalityKeyValue("http.route", "https://example.test/orders/{orderId}")
                .lowCardinalityKeyValue("uri", "/orders/123?debug=true");

        nanos.addAndGet(5_000_000L);
        observation.stop();

        HttpServerObservationInput input = collector.onlyHttp();
        assertEquals(Optional.of("https://example.test/orders/{orderId}"), input.routePattern());
        assertEquals(Optional.of("/orders/123"), input.rawPathCandidate(),
                "invalid http.route가 present여도 low-cardinality uri/path 후보를 보존한다");

        LowCardinalityHttpObservationGuard guard = new LowCardinalityHttpObservationGuard(
                new RouteNormalizationService(List.of("/orders/{orderId}")));
        LowCardinalityHttpServerObservation guarded = guard.guard(input);
        assertEquals("/orders/{orderId}", guarded.normalizedRoute().value());
        assertEquals("GET /orders/{orderId}", guarded.endpointKey().value());
    }

    @Test
    void preservesBoundedOmissionMarkerRoutePatternFromLowCardinalityHttpRoute() {
        RecordingCollector collector = new RecordingCollector();
        AtomicLong nanos = new AtomicLong(1L);
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig()
                .observationHandler(new MicrometerHttpServerObservationBinder(collector, nanos::get));

        Observation observation = Observation.start("http.server.requests", registry)
                .lowCardinalityKeyValue("method", "GET")
                .lowCardinalityKeyValue("status", "200")
                .lowCardinalityKeyValue("http.route", "/{userId}?.../posts")
                .lowCardinalityKeyValue("uri", "/users/123/posts?debug=true");

        nanos.addAndGet(5_000_000L);
        observation.stop();

        HttpServerObservationInput input = collector.onlyHttp();
        LowCardinalityHttpServerObservation guarded = new LowCardinalityHttpObservationGuard().guard(input);

        assertEquals(Optional.of("/{userId}?.../posts"), input.routePattern());
        assertEquals("/{userId}?.../posts", guarded.normalizedRoute().value());
        assertEquals("GET /{userId}?.../posts", guarded.endpointKey().value());
    }

    @Test
    void passesUriRawPathCandidateWithoutQueryWhenFrameworkRouteIsAbsent() {
        RecordingCollector collector = new RecordingCollector();
        AtomicLong nanos = new AtomicLong(1L);
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig()
                .observationHandler(new MicrometerHttpServerObservationBinder(collector, nanos::get));

        Observation observation = Observation.start("http.server.requests", registry)
                .lowCardinalityKeyValue("method", "GET")
                .lowCardinalityKeyValue("status", "200")
                .lowCardinalityKeyValue("uri", "/orders/123?debug=true");

        nanos.addAndGet(5_000_000L);
        observation.stop();

        HttpServerObservationInput input = collector.onlyHttp();
        assertTrue(input.routePattern().isEmpty());
        assertEquals(Optional.of("/orders/123"), input.rawPathCandidate(),
                "query string은 matcher 입력 전에 폐기된다");
    }

    @Test
    void explicitUnknownHttpRouteAllowsRawPathCandidateFallback() {
        RecordingCollector collector = new RecordingCollector();
        AtomicLong nanos = new AtomicLong(1L);
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig()
                .observationHandler(new MicrometerHttpServerObservationBinder(collector, nanos::get));

        Observation observation = Observation.start("http.server.requests", registry)
                .lowCardinalityKeyValue("method", "GET")
                .lowCardinalityKeyValue("status", "200")
                .lowCardinalityKeyValue("http.route", "UNKNOWN")
                .lowCardinalityKeyValue("uri", "/orders/123?debug=true");

        nanos.addAndGet(5_000_000L);
        observation.stop();

        HttpServerObservationInput input = collector.onlyHttp();
        assertTrue(input.routePattern().isEmpty());
        assertEquals(Optional.of("/orders/123"), input.rawPathCandidate(),
                "명시적 UNKNOWN http.route는 framework route 부재와 같이 allowlist matcher 후보를 허용한다");
    }

    @Test
    void blankHttpRouteAllowsRawPathCandidateFallback() {
        RecordingCollector collector = new RecordingCollector();
        AtomicLong nanos = new AtomicLong(1L);
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig()
                .observationHandler(new MicrometerHttpServerObservationBinder(collector, nanos::get));

        Observation observation = Observation.start("http.server.requests", registry)
                .lowCardinalityKeyValue("method", "GET")
                .lowCardinalityKeyValue("status", "200")
                .lowCardinalityKeyValue("http.route", " ")
                .lowCardinalityKeyValue("path", "/orders/123?debug=true");

        nanos.addAndGet(5_000_000L);
        observation.stop();

        HttpServerObservationInput input = collector.onlyHttp();
        assertTrue(input.routePattern().isEmpty());
        assertEquals(Optional.of("/orders/123"), input.rawPathCandidate(),
                "blank http.route는 framework route 부재와 같이 allowlist matcher 후보를 허용한다");
    }

    @Test
    void passesPathRawPathCandidateAndIgnoresHighCardinalityUrlTags() {
        RecordingCollector collector = new RecordingCollector();
        AtomicLong nanos = new AtomicLong(1L);
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig()
                .observationHandler(new MicrometerHttpServerObservationBinder(collector, nanos::get));

        Observation observation = Observation.start("http.server.requests", registry)
                .lowCardinalityKeyValue("method", "GET")
                .lowCardinalityKeyValue("status", "200")
                .lowCardinalityKeyValue("path", "/orders/123?debug=true")
                .highCardinalityKeyValue("userId", "user-42")
                .highCardinalityKeyValue("http.url", "https://example.test/orders/123?debug=true");

        nanos.addAndGet(5_000_000L);
        observation.stop();

        HttpServerObservationInput input = collector.onlyHttp();
        assertEquals("GET", input.method());
        assertEquals(200, input.statusCode());
        assertTrue(input.routePattern().isEmpty());
        assertEquals(Optional.of("/orders/123"), input.rawPathCandidate(),
                "low-cardinality path만 raw path candidate가 되며 http.url/userId는 무시된다");
    }

    @Test
    void lowCardinalityUriTakesPriorityOverPathRawCandidate() {
        RecordingCollector collector = new RecordingCollector();
        AtomicLong nanos = new AtomicLong(1L);
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig()
                .observationHandler(new MicrometerHttpServerObservationBinder(collector, nanos::get));

        Observation observation = Observation.start("http.server.requests", registry)
                .lowCardinalityKeyValue("method", "GET")
                .lowCardinalityKeyValue("status", "200")
                .lowCardinalityKeyValue("uri", "/orders/123?debug=true")
                .lowCardinalityKeyValue("path", "/payments/999?debug=true");

        nanos.addAndGet(5_000_000L);
        observation.stop();

        HttpServerObservationInput input = collector.onlyHttp();
        LowCardinalityHttpServerObservation guarded = new LowCardinalityHttpObservationGuard(
                new RouteNormalizationService(List.of("/orders/{orderId}", "/payments/{paymentId}"))).guard(input);

        assertEquals(Optional.of("/orders/123"), input.rawPathCandidate());
        assertEquals("GET /orders/{orderId}", guarded.endpointKey().value());
    }

    @Test
    void ignoresHttpUrlAndHighCardinalityTagsWhenNoLowCardinalityPathExists() {
        RecordingCollector collector = new RecordingCollector();
        AtomicLong nanos = new AtomicLong(1L);
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig()
                .observationHandler(new MicrometerHttpServerObservationBinder(collector, nanos::get));

        Observation observation = Observation.start("http.server.requests", registry)
                .lowCardinalityKeyValue("method", "GET")
                .lowCardinalityKeyValue("status", "200")
                .highCardinalityKeyValue("userId", "user-42")
                .highCardinalityKeyValue("http.url", "https://example.test/orders/123?debug=true");

        nanos.addAndGet(5_000_000L);
        observation.stop();

        HttpServerObservationInput input = collector.onlyHttp();
        assertTrue(input.routePattern().isEmpty());
        assertTrue(input.rawPathCandidate().isEmpty());
    }

    @Test
    void highCardinalityCustomLabelsCannotBecomeIngestDimensions() {
        RecordingCollector collector = new RecordingCollector();
        AtomicLong nanos = new AtomicLong(1L);
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig()
                .observationHandler(new MicrometerHttpServerObservationBinder(collector, nanos::get));

        Observation observation = Observation.start("http.server.requests", registry)
                .lowCardinalityKeyValue("method", "GET")
                .lowCardinalityKeyValue("status", "200")
                .lowCardinalityKeyValue("tenantId", "tenant-low")
                .lowCardinalityKeyValue("customLabel", "vip-low")
                .lowCardinalityKeyValue("metricName", "checkout.low")
                .highCardinalityKeyValue("tenantId", "tenant-42")
                .highCardinalityKeyValue("userId", "user-99")
                .highCardinalityKeyValue("sessionId", "session-abc")
                .highCardinalityKeyValue("traceId", "trace-xyz")
                .highCardinalityKeyValue("customLabel", "vip")
                .highCardinalityKeyValue("metricName", "checkout.latency")
                .highCardinalityKeyValue("http.route", "/tenants/{tenantId}/orders/{orderId}")
                .highCardinalityKeyValue("uri", "/tenants/tenant-42/orders/123?debug=true");

        nanos.addAndGet(5_000_000L);
        observation.stop();

        HttpServerObservationInput input = collector.onlyHttp();
        LowCardinalityHttpServerObservation guarded = new LowCardinalityHttpObservationGuard().guard(input);

        assertTrue(input.routePattern().isEmpty());
        assertTrue(input.rawPathCandidate().isEmpty());
        assertEquals("GET UNKNOWN", guarded.endpointKey().value());
        assertDoesNotExpose(input, "tenant-low", "vip-low", "checkout.low",
                "tenant-42", "user-99", "session-abc", "trace-xyz", "vip", "checkout.latency");
        assertDoesNotExpose(guarded, "tenant-low", "vip-low", "checkout.low",
                "tenant-42", "user-99", "session-abc", "trace-xyz", "vip", "checkout.latency");
    }

    @Test
    void highCardinalityRouteLikeValuesCannotBecomeRawPathCandidate() {
        RecordingCollector collector = new RecordingCollector();
        AtomicLong nanos = new AtomicLong(1L);
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig()
                .observationHandler(new MicrometerHttpServerObservationBinder(collector, nanos::get));

        Observation observation = Observation.start("http.server.requests", registry)
                .lowCardinalityKeyValue("method", "GET")
                .lowCardinalityKeyValue("status", "200")
                .highCardinalityKeyValue("http.route", "/orders/{orderId}")
                .highCardinalityKeyValue("uri", "/orders/123?debug=true")
                .highCardinalityKeyValue("path", "/orders/123")
                .highCardinalityKeyValue("http.url", "https://example.test/orders/123?debug=true");

        nanos.addAndGet(5_000_000L);
        observation.stop();

        HttpServerObservationInput input = collector.onlyHttp();
        LowCardinalityHttpServerObservation guarded = new LowCardinalityHttpObservationGuard(
                new RouteNormalizationService(List.of("/orders/{orderId}"))).guard(input);

        assertTrue(input.routePattern().isEmpty());
        assertTrue(input.rawPathCandidate().isEmpty());
        assertEquals("GET UNKNOWN", guarded.endpointKey().value());
        assertDoesNotExpose(input, "/orders/123", "debug=true", "https://example.test");
        assertDoesNotExpose(guarded, "/orders/123", "debug=true", "https://example.test");
    }

    @Test
    void contextCustomValuesAreIgnoredAsRouteMethodRawPathAndTagCarriers() {
        RecordingCollector collector = new RecordingCollector();
        AtomicLong nanos = new AtomicLong(1L);
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig()
                .observationHandler(new MicrometerHttpServerObservationBinder(collector, nanos::get));

        Observation.Context context = new Observation.Context();
        context.put("method", "POST");
        context.put("http.route", "/orders/{orderId}");
        context.put("uri", "/orders/123?debug=true");
        context.put("path", "/orders/123");
        context.put("customLabels", Map.of(
                "tenantId", "tenant-42",
                "userId", "user-99",
                "metricName", "checkout.latency"));

        Observation observation = Observation.start("http.server.requests", () -> context, registry)
                .lowCardinalityKeyValue("method", "GET")
                .lowCardinalityKeyValue("status", "200");

        nanos.addAndGet(5_000_000L);
        observation.stop();

        HttpServerObservationInput input = collector.onlyHttp();
        LowCardinalityHttpServerObservation guarded = new LowCardinalityHttpObservationGuard(
                new RouteNormalizationService(List.of("/orders/{orderId}"))).guard(input);

        assertEquals("GET", input.method());
        assertEquals(200, input.statusCode());
        assertTrue(input.routePattern().isEmpty());
        assertTrue(input.rawPathCandidate().isEmpty());
        assertEquals("GET UNKNOWN", guarded.endpointKey().value());
        assertDoesNotExpose(input, "POST", "/orders/123", "debug=true",
                "tenant-42", "user-99", "checkout.latency");
        assertDoesNotExpose(guarded, "POST", "/orders/123", "debug=true",
                "tenant-42", "user-99", "checkout.latency");
    }

    @Test
    void httpServerObservationInputDoesNotExposeCustomLabelCarrier() {
        List<String> componentNames = Arrays.stream(HttpServerObservationInput.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        List<String> forbiddenNameTokens = List.of(
                "tag",
                "label",
                "custom",
                "tenant",
                "user",
                "session",
                "trace",
                "metric",
                "metricname",
                "dimension",
                "attribute",
                "keyvalue",
                "baggage");

        assertEquals(
                List.of(
                        "observedAt",
                        "method",
                        "statusCode",
                        "error",
                        "errorType",
                        "duration",
                        "routePattern",
                        "rawPathCandidate"),
                componentNames);

        for (RecordComponent component : HttpServerObservationInput.class.getRecordComponents()) {
            assertFalse(exposesMapCarrier(component),
                    () -> HttpServerObservationInput.class.getName() + "#" + component.getName()
                            + " must not expose a free-form high-cardinality label carrier");
            assertFalse(component.getType().isArray(),
                    () -> HttpServerObservationInput.class.getName() + "#" + component.getName()
                            + " must not expose raw timeseries arrays");

            if ("rawPathCandidate".equals(component.getName())) {
                continue;
            }

            String normalizedName = component.getName().toLowerCase(Locale.ROOT);
            assertTrue(forbiddenNameTokens.stream().noneMatch(normalizedName::contains),
                    () -> HttpServerObservationInput.class.getName() + "#" + component.getName()
                            + " must not expose tag/label/custom identity fields");
        }
    }

    @Test
    void acceptsJvmAndDatasourceSamplesThroughCollectionBoundary() {
        RecordingCollector collector = new RecordingCollector();
        Instant observedAt = Instant.parse("2026-05-10T12:00:00Z");

        JvmMetricSample jvmSample = new JvmMetricSample(observedAt, 0.64, 0.71);
        DatasourcePoolMetricSample datasourceSample = new DatasourcePoolMetricSample(observedAt, 0.82);

        collector.recordJvmMetricSample(jvmSample);
        collector.recordDatasourcePoolMetricSample(datasourceSample);

        assertEquals(jvmSample, collector.jvmSamples.get(0));
        assertEquals(datasourceSample, collector.datasourceSamples.get(0));
        assertThrows(IllegalArgumentException.class, () -> new JvmMetricSample(observedAt, 1.01, 0.2));
        assertThrows(IllegalArgumentException.class, () -> new DatasourcePoolMetricSample(observedAt, -0.01));
    }

    private static final class RecordingCollector implements ObservationSampleCollector {

        private final List<HttpServerObservationInput> httpInputs = new ArrayList<>();
        private final List<JvmMetricSample> jvmSamples = new ArrayList<>();
        private final List<DatasourcePoolMetricSample> datasourceSamples = new ArrayList<>();

        @Override
        public void recordHttpServerObservation(HttpServerObservationInput input) {
            httpInputs.add(input);
        }

        @Override
        public void recordJvmMetricSample(JvmMetricSample sample) {
            jvmSamples.add(sample);
        }

        @Override
        public void recordDatasourcePoolMetricSample(DatasourcePoolMetricSample sample) {
            datasourceSamples.add(sample);
        }

        private HttpServerObservationInput onlyHttp() {
            assertEquals(1, httpInputs.size());
            return httpInputs.get(0);
        }
    }

    private static boolean exposesMapCarrier(RecordComponent component) {
        return Map.class.isAssignableFrom(component.getType())
                || component.getGenericType().getTypeName().contains("java.util.Map");
    }

    private static void assertDoesNotExpose(Object output, String... forbiddenValues) {
        String text = String.valueOf(output);
        for (String forbiddenValue : forbiddenValues) {
            assertFalse(text.contains(forbiddenValue), () -> output.getClass().getSimpleName()
                    + " must not expose " + forbiddenValue);
        }
    }
}
