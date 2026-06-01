package com.observation.starter.config;

import com.observation.starter.spring.observation.MicrometerHttpServerObservationBinder;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservationBindingAutoConfigurationTest {

    @Test
    void autoConfigurationImportsObservationBindingWiring() throws Exception {
        List<String> autoConfigurationImports = Files.readAllLines(Path.of(
                "src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"));

        assertTrue(autoConfigurationImports.contains("com.observation.starter.config.ObservationBindingAutoConfiguration"));
    }

    @Test
    void contextRegistersMicrometerHttpServerObservationHandlerWhenCollectorExists() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(
                    RouteAttributionAutoConfiguration.class,
                    MetricDrainAutoConfiguration.class,
                    ObservationBindingAutoConfiguration.class);
            context.refresh();

            Map<String, ObservationHandler> handlers = context.getBeansOfType(ObservationHandler.class);

            assertEquals(1, handlers.size());
            ObservationHandler handler = handlers.values().iterator().next();
            assertInstanceOf(MicrometerHttpServerObservationBinder.class, handler);
            assertTrue(handler.supportsContext(httpServerContext()));
        }
    }

    @Test
    void observationHandlerIsNotRegisteredWithoutCollectorBoundary() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(ObservationBindingAutoConfiguration.class);
            context.refresh();

            assertTrue(context.getBeansOfType(ObservationHandler.class).isEmpty());
        }
    }

    private static Observation.Context httpServerContext() {
        Observation.Context context = new Observation.Context();
        context.setName("http.server.requests");
        return context;
    }
}
