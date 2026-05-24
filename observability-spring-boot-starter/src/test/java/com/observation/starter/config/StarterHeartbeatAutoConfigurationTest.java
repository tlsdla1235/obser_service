package com.observation.starter.config;

import com.observation.starter.client.PortalHeartbeatClient;
import com.observation.starter.model.heartbeat.HeartbeatRequest;
import com.observation.starter.service.StarterHeartbeatService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StarterHeartbeatAutoConfigurationTest {

    @Test
    void autoConfigurationImportsStarterHeartbeatWiring() throws Exception {
        List<String> autoConfigurationImports = Files.readAllLines(Path.of(
                "src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"));

        assertTrue(autoConfigurationImports.contains("com.observation.starter.config.StarterHeartbeatAutoConfiguration"));
    }

    @Test
    void missingPortalConnectionSettingsCreatesDisabledClientWithoutBlockingContext() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(StarterHeartbeatAutoConfiguration.class);
            context.refresh();

            PortalHeartbeatClient client = context.getBean(PortalHeartbeatClient.class);
            StarterHeartbeatService service = context.getBean(StarterHeartbeatService.class);

            assertFalse(client.isEnabled());
            assertFalse(service.canSend());
        }
    }

    @Test
    void customHeartbeatClientSendsBackgroundHeartbeatWithConfiguredMetadata() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "observation.metric-flush.project-id", "project-123",
                    "observation.metric-flush.application-name", "orders-api",
                    "observation.metric-flush.environment", "prod",
                    "observation.metric-flush.instance", "instance-1",
                    "observation.heartbeat.interval-seconds", "1",
                    "observation.heartbeat.starter-version", "0.1.0-test")));
            context.register(FakeHeartbeatClientConfiguration.class, StarterHeartbeatAutoConfiguration.class);
            context.refresh();

            CapturingHeartbeatClient client = context.getBean(CapturingHeartbeatClient.class);

            assertTrue(client.awaitSend());
            assertInstanceOf(HeartbeatRequest.class, client.request());
            assertEquals("0.1.0-test", client.request().starterVersion());
            assertEquals("orders-api", client.request().application().name());
            assertEquals("prod", client.request().application().environment());
            assertEquals("instance-1", client.request().application().instance());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class FakeHeartbeatClientConfiguration {

        @Bean
        CapturingHeartbeatClient portalHeartbeatClient() {
            return new CapturingHeartbeatClient();
        }
    }

    static final class CapturingHeartbeatClient implements PortalHeartbeatClient {

        private final CountDownLatch sent = new CountDownLatch(1);
        private final AtomicReference<HeartbeatRequest> request = new AtomicReference<>();

        @Override
        public void send(HeartbeatRequest request) {
            this.request.set(request);
            sent.countDown();
        }

        boolean awaitSend() throws InterruptedException {
            return sent.await(2, TimeUnit.SECONDS);
        }

        HeartbeatRequest request() {
            return request.get();
        }
    }
}
