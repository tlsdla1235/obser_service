package com.observation.starter.config;

import com.observation.starter.client.PortalMetricBucketClient;
import com.observation.starter.client.http.JdkPortalMetricBucketClient;
import com.observation.starter.model.ingest.IngestEnvelopeCandidate;
import com.observation.starter.model.metric.AppMetricRollup;
import com.observation.starter.model.metric.ClosedMetricBucket;
import com.observation.starter.model.metric.HistogramBucket;
import com.observation.starter.model.time.MetricBucketInterval;
import com.observation.starter.queue.BoundedMetricQueue;
import com.observation.starter.queue.MetricQueueDropPolicy;
import com.observation.starter.service.IngestEnvelopeBuilderService;
import com.observation.starter.service.MetricBucketFlushWorker;
import com.observation.starter.service.MetricBucketRollupService;
import com.observation.starter.service.ObservationSampleCollector;
import com.observation.starter.service.StarterMetricIngestService;
import com.observation.starter.spring.StarterMetricDrainScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricDrainAutoConfigurationTest {

    private static final String PROJECT_KEY_FIXTURE = "fixture-project-key";

    @Test
    void autoConfigurationImportsRuntimeDrainWiring() throws Exception {
        List<String> autoConfigurationImports = Files.readAllLines(Path.of(
                "src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"));

        assertTrue(autoConfigurationImports.contains("com.observation.starter.config.MetricDrainAutoConfiguration"));
    }

    @Test
    void cleanStarterContextCreatesRuntimeDrainBeansWithoutHostIngestService() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(RouteAttributionAutoConfiguration.class, MetricDrainAutoConfiguration.class);
            context.refresh();

            StarterMetricIngestService ingestService = context.getBean(StarterMetricIngestService.class);

            assertInstanceOf(BoundedMetricQueue.class, context.getBean(BoundedMetricQueue.class));
            assertInstanceOf(MetricBucketRollupService.class, context.getBean(MetricBucketRollupService.class));
            assertInstanceOf(IngestEnvelopeBuilderService.class, context.getBean(IngestEnvelopeBuilderService.class));
            assertInstanceOf(StarterMetricDrainScheduler.class, context.getBean(StarterMetricDrainScheduler.class));
            assertSame(ingestService, context.getBean(ObservationSampleCollector.class));
            assertTrue(context.getBeansOfType(PortalMetricBucketClient.class).isEmpty());
            assertFalse(context.containsBean("metricBucketFlushWorker"));
            assertTrue(context.getBeansOfType(MetricBucketFlushWorker.class).isEmpty());
        }
    }

    @Test
    void metricFlushConnectionSettingsCreateDefaultHttpClientAndWorker() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "observation.metric-flush.project-id", "project-123",
                    "observation.metric-flush.application-name", "orders-api",
                    "observation.metric-flush.environment", "prod",
                    "observation.metric-flush.instance", "instance-1",
                    "observation.metric-flush.portal-base-url", "http://127.0.0.1:8080/",
                    "observation.metric-flush.project-key", PROJECT_KEY_FIXTURE,
                    "observation.metric-flush.timeout-millis", "750")));
            context.register(RouteAttributionAutoConfiguration.class, MetricDrainAutoConfiguration.class);
            context.refresh();

            MetricDrainProperties properties = context.getBean(MetricDrainProperties.class);
            PortalMetricBucketClient client = context.getBean(PortalMetricBucketClient.class);

            assertInstanceOf(JdkPortalMetricBucketClient.class, client);
            assertInstanceOf(MetricBucketFlushWorker.class, context.getBean(MetricBucketFlushWorker.class));
            assertTrue(properties.hasPortalConnectionSettings());
            assertEquals("http://127.0.0.1:8080/api/ingest/v1/buckets",
                    properties.bucketIngestUri().toString());
            assertEquals(750, properties.getTimeoutMillis());
        }
    }

    @Test
    void heartbeatConnectionSettingsDoNotEnableMetricBucketClientFallback() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "observation.heartbeat.portal-base-url", "http://127.0.0.1:8080",
                    "observation.heartbeat.project-key", PROJECT_KEY_FIXTURE)));
            context.register(RouteAttributionAutoConfiguration.class, MetricDrainAutoConfiguration.class);
            context.refresh();

            MetricDrainProperties properties = context.getBean(MetricDrainProperties.class);

            assertFalse(properties.hasPortalConnectionSettings());
            assertTrue(context.getBeansOfType(PortalMetricBucketClient.class).isEmpty());
            assertTrue(context.getBeansOfType(MetricBucketFlushWorker.class).isEmpty());
        }
    }

    @Test
    void customPortalMetricBucketClientOverridesDefaultHttpClient() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "observation.metric-flush.project-id", "project-123",
                    "observation.metric-flush.application-name", "orders-api",
                    "observation.metric-flush.environment", "prod",
                    "observation.metric-flush.instance", "instance-1",
                    "observation.metric-flush.portal-base-url", "http://127.0.0.1:8080",
                    "observation.metric-flush.project-key", PROJECT_KEY_FIXTURE)));
            context.register(FakePortalClientConfiguration.class, MetricDrainAutoConfiguration.class);
            context.refresh();

            Map<String, PortalMetricBucketClient> clients = context.getBeansOfType(PortalMetricBucketClient.class);

            assertEquals(1, clients.size());
            assertSame(context.getBean(CapturingPortalMetricBucketClient.class), clients.values().iterator().next());
            assertInstanceOf(MetricBucketFlushWorker.class, context.getBean(MetricBucketFlushWorker.class));
        }
    }

    @Test
    void defaultHttpClientWithGenericIdentityFailsContextBeforeWorkerCanSendDefaults() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "observation.metric-flush.portal-base-url", "http://127.0.0.1:8080",
                "observation.metric-flush.project-key", PROJECT_KEY_FIXTURE)));
        context.register(RouteAttributionAutoConfiguration.class, MetricDrainAutoConfiguration.class);

        RuntimeException exception = assertThrows(RuntimeException.class, context::refresh);

        assertTrue(rootCauseMessage(exception).contains("observation.metric-flush.project-id"));
        context.close();
    }

    @Test
    void partialMetricFlushConnectionSettingsFailFastInsteadOfDisablingClientSilently() {
        AnnotationConfigApplicationContext missingProjectKey = new AnnotationConfigApplicationContext();
        missingProjectKey.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "observation.metric-flush.portal-base-url", "http://127.0.0.1:8080")));
        missingProjectKey.register(RouteAttributionAutoConfiguration.class, MetricDrainAutoConfiguration.class);

        RuntimeException missingProjectKeyException = assertThrows(RuntimeException.class, missingProjectKey::refresh);

        assertTrue(rootCauseMessage(missingProjectKeyException).contains("observation.metric-flush.project-key"));
        missingProjectKey.close();

        AnnotationConfigApplicationContext missingPortalBaseUrl = new AnnotationConfigApplicationContext();
        missingPortalBaseUrl.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "observation.metric-flush.project-key", PROJECT_KEY_FIXTURE)));
        missingPortalBaseUrl.register(RouteAttributionAutoConfiguration.class, MetricDrainAutoConfiguration.class);

        RuntimeException missingPortalBaseUrlException = assertThrows(
                RuntimeException.class,
                missingPortalBaseUrl::refresh);

        assertTrue(rootCauseMessage(missingPortalBaseUrlException).contains("observation.metric-flush.portal-base-url"));
        missingPortalBaseUrl.close();
    }

    @Test
    void metricFlushPropertiesNormalizeBucketIngestUriAndRejectInvalidTimeout() {
        MetricDrainProperties properties = new MetricDrainProperties();
        properties.setPortalBaseUrl("http://localhost:8080/");
        properties.setProjectKey(PROJECT_KEY_FIXTURE);

        assertEquals("http://localhost:8080/api/ingest/v1/buckets", properties.bucketIngestUri().toString());

        properties.setPortalBaseUrl("http://localhost:8080");

        assertEquals("http://localhost:8080/api/ingest/v1/buckets", properties.bucketIngestUri().toString());
        assertThrows(IllegalArgumentException.class, () -> properties.setTimeoutMillis(0));
    }

    @Test
    void autoConfigurationBindsFiniteQueueCapacityAndDropPolicy() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "observation.metric-flush.queue-capacity", "3",
                    "observation.metric-flush.drop-policy", "drop_oldest",
                    "observation.metric-flush.project-id", "project-123",
                    "observation.metric-flush.application-name", "orders-api",
                    "observation.metric-flush.environment", "prod",
                    "observation.metric-flush.instance", "instance-1")));
            context.register(RouteAttributionAutoConfiguration.class, MetricDrainAutoConfiguration.class);
            context.refresh();

            BoundedMetricQueue queue = context.getBean(BoundedMetricQueue.class);
            MetricDrainProperties properties = context.getBean(MetricDrainProperties.class);

            assertEquals(3, queue.capacity());
            assertEquals(MetricQueueDropPolicy.DROP_OLDEST, properties.getDropPolicy());
            assertEquals("project-123", properties.ingestEnvelopeIdentity().projectId());
            assertEquals("orders-api", properties.ingestEnvelopeIdentity().applicationName());
            assertEquals("prod", properties.ingestEnvelopeIdentity().environment());
            assertEquals("instance-1", properties.ingestEnvelopeIdentity().instance());
        }
    }

    @Test
    void autoConfiguredWorkerFlushesQueuedBucketToPortalClientWhenClientBeanExists() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", explicitIdentity()));
            context.register(FakePortalClientConfiguration.class, MetricDrainAutoConfiguration.class);
            context.refresh();

            BoundedMetricQueue queue = context.getBean(BoundedMetricQueue.class);
            CapturingPortalMetricBucketClient client = context.getBean(CapturingPortalMetricBucketClient.class);

            queue.offer(bucket("2026-05-08T01:00:00Z"));

            assertTrue(client.awaitFlush());
            IngestEnvelopeCandidate candidate = client.candidate();
            assertEquals("project-123:orders-api:prod:instance-1:20260508T010000Z",
                    candidate.idempotencyKey());
            assertEquals("1.0", candidate.payload().schemaVersion());
            assertEquals("orders-api", candidate.payload().application().name());
            assertEquals("2026-05-08T01:00:00Z", candidate.payload().bucket().startUtc());
            assertInstanceOf(MetricBucketFlushWorker.class, context.getBean(MetricBucketFlushWorker.class));
        }
    }

    @Test
    void portalClientWithoutExplicitFlushIdentityFailsContextBeforeWorkerCanSendDefaults() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(FakePortalClientConfiguration.class, MetricDrainAutoConfiguration.class);

        RuntimeException exception = assertThrows(RuntimeException.class, context::refresh);

        assertTrue(rootCauseMessage(exception).contains("observation.metric-flush.project-id"));
        context.close();
    }

    @Test
    void portalFlushRejectsHeaderUnsafeIdentityConfig() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "observation.metric-flush.project-id", "project:123",
                "observation.metric-flush.application-name", "orders-api",
                "observation.metric-flush.environment", "prod",
                "observation.metric-flush.instance", "instance-1")));
        context.register(FakePortalClientConfiguration.class, MetricDrainAutoConfiguration.class);

        RuntimeException exception = assertThrows(RuntimeException.class, context::refresh);

        assertTrue(rootCauseMessage(exception).contains("projectId"));
        context.close();
    }

    private static Map<String, Object> explicitIdentity() {
        return Map.of(
                "observation.metric-flush.project-id", "project-123",
                "observation.metric-flush.application-name", "orders-api",
                "observation.metric-flush.environment", "prod",
                "observation.metric-flush.instance", "instance-1");
    }

    private static ClosedMetricBucket bucket(String startUtc) {
        Instant start = Instant.parse(startUtc);
        return new ClosedMetricBucket(
                new MetricBucketInterval(start, start.plusSeconds(30)),
                new AppMetricRollup(
                        1,
                        0,
                        List.of(new HistogramBucket(50, 1)),
                        Optional.empty(),
                        Optional.empty()),
                List.of());
    }

    private static String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }

    @Configuration(proxyBeanMethods = false)
    static class FakePortalClientConfiguration {

        @Bean
        CapturingPortalMetricBucketClient portalMetricBucketClient() {
            return new CapturingPortalMetricBucketClient();
        }
    }

    static final class CapturingPortalMetricBucketClient implements PortalMetricBucketClient {

        private final CountDownLatch flushed = new CountDownLatch(1);
        private final AtomicReference<IngestEnvelopeCandidate> candidate = new AtomicReference<>();

        @Override
        public void flush(IngestEnvelopeCandidate candidate) {
            this.candidate.set(candidate);
            flushed.countDown();
        }

        boolean awaitFlush() throws InterruptedException {
            return flushed.await(2, TimeUnit.SECONDS);
        }

        IngestEnvelopeCandidate candidate() {
            return candidate.get();
        }
    }
}
