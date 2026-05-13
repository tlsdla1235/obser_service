package com.observation.starter.spring.observation;

import com.observation.starter.model.metric.DatasourcePoolMetricSample;
import com.observation.starter.model.metric.HttpServerObservationInput;
import com.observation.starter.model.metric.JvmMetricSample;
import com.observation.starter.service.ObservationSampleCollector;
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
    }

    @Test
    void doesNotPromoteUriRawPathAsStory21RouteCandidate() {
        RecordingCollector collector = new RecordingCollector();
        AtomicLong nanos = new AtomicLong(1L);
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig()
                .observationHandler(new MicrometerHttpServerObservationBinder(collector, nanos::get));

        Observation orderObservation = Observation.start("http.server.requests", registry)
                .lowCardinalityKeyValue("method", "GET")
                .lowCardinalityKeyValue("status", "200")
                .lowCardinalityKeyValue("uri", "/orders/123");

        nanos.addAndGet(5_000_000L);
        orderObservation.stop();

        Observation userObservation = Observation.start("http.server.requests", registry)
                .lowCardinalityKeyValue("method", "GET")
                .lowCardinalityKeyValue("status", "200")
                .lowCardinalityKeyValue("uri", "/users/alice");

        nanos.addAndGet(5_000_000L);
        userObservation.stop();

        assertEquals(2, collector.httpInputs.size());
        assertTrue(collector.httpInputs.get(0).routePattern().isEmpty(),
                "Story 2.1은 uri=/orders/123 같은 원본 경로를 routePattern 후보로 확정하지 않는다");
        assertTrue(collector.httpInputs.get(1).routePattern().isEmpty(),
                "Story 2.1은 uri=/users/alice 같은 원본 경로를 routePattern 후보로 확정하지 않는다");
    }

    @Test
    void doesNotPromotePathOrHighCardinalityTagsBeforeStory22Guard() {
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
        assertTrue(input.routePattern().isEmpty(),
                "Story 2.1은 path, http.url, userId 같은 raw/high-cardinality tag를 라우트 후보로 보지 않는다");
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
