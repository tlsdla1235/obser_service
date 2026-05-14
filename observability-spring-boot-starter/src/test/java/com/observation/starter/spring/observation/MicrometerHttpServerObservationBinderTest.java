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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void frameworkRouteTemplateHasPriorityAndRawPathCandidateIsNotStored() {
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
        assertTrue(input.rawPathCandidate().isEmpty(),
                "http.route가 있으면 raw path candidate를 input boundary에 저장하지 않는다");
    }

    @Test
    void invalidPresentHttpRouteDoesNotFallbackToAllowlistMatchableRawPath() {
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
        assertTrue(input.routePattern().isEmpty());
        assertTrue(input.rawPathCandidate().isEmpty(),
                "invalid http.route가 present이면 uri/path allowlist fallback으로 우회하지 않는다");

        LowCardinalityHttpObservationGuard guard = new LowCardinalityHttpObservationGuard(
                new RouteNormalizationService(List.of("/orders/{orderId}")));
        LowCardinalityHttpServerObservation guarded = guard.guard(input);
        assertEquals("UNKNOWN", guarded.normalizedRoute().value());
        assertEquals("GET UNKNOWN", guarded.endpointKey().value());
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
}
