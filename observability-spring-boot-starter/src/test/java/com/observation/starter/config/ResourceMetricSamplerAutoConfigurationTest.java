package com.observation.starter.config;

import com.observation.starter.service.HikariDatasourcePoolMetricSampler;
import com.observation.starter.service.JdkJvmMetricSampler;
import com.observation.starter.service.StarterResourceMetricSampler;
import com.observation.starter.spring.StarterResourceMetricSamplerScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceMetricSamplerAutoConfigurationTest {

    @Test
    void autoConfigurationImportsResourceMetricSamplerWiring() throws Exception {
        List<String> autoConfigurationImports = Files.readAllLines(Path.of(
                "src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"));

        assertTrue(autoConfigurationImports.contains(
                "com.observation.starter.config.ResourceMetricSamplerAutoConfiguration"));
    }

    @Test
    void cleanStarterContextCreatesResourceSamplerBeansWithoutDatasource() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(
                    RouteAttributionAutoConfiguration.class,
                    MetricDrainAutoConfiguration.class,
                    ResourceMetricSamplerAutoConfiguration.class);
            context.refresh();

            assertInstanceOf(JdkJvmMetricSampler.class, context.getBean(JdkJvmMetricSampler.class));
            assertInstanceOf(
                    HikariDatasourcePoolMetricSampler.class,
                    context.getBean(HikariDatasourcePoolMetricSampler.class));
            assertInstanceOf(
                    StarterResourceMetricSampler.class,
                    context.getBean(StarterResourceMetricSampler.class));
            assertInstanceOf(
                    StarterResourceMetricSamplerScheduler.class,
                    context.getBean(StarterResourceMetricSamplerScheduler.class));
            assertTrue(context.getBeansOfType(DataSource.class).isEmpty());
        }
    }

    @Test
    void resourceSamplerOrchestratorWaitsForCollectorBoundary() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(ResourceMetricSamplerAutoConfiguration.class);
            context.refresh();

            assertInstanceOf(JdkJvmMetricSampler.class, context.getBean(JdkJvmMetricSampler.class));
            assertTrue(context.getBeansOfType(StarterResourceMetricSampler.class).isEmpty());
            assertTrue(context.getBeansOfType(StarterResourceMetricSamplerScheduler.class).isEmpty());
        }
    }
}
