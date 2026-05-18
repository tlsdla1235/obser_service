package com.observation.starter.config;

import com.observation.starter.queue.BoundedMetricQueue;
import com.observation.starter.queue.MetricQueueDropPolicy;
import com.observation.starter.service.IngestEnvelopeBuilderService;
import com.observation.starter.service.MetricBucketRollupService;
import com.observation.starter.service.ObservationSampleCollector;
import com.observation.starter.service.StarterMetricIngestService;
import com.observation.starter.spring.StarterMetricDrainScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricDrainAutoConfigurationTest {

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
        }
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
}
