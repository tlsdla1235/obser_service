package com.observation.starter.service;

import com.observation.starter.client.PortalHeartbeatClient;
import com.observation.starter.config.HeartbeatProperties;
import com.observation.starter.model.heartbeat.HeartbeatRequest;
import com.observation.starter.model.ingest.IngestEnvelopeIdentity;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StarterHeartbeatSenderTest {

    @Test
    void startReturnsBeforeSlowHeartbeatClientCompletes() throws Exception {
        CountDownLatch called = new CountDownLatch(1);
        AtomicReference<String> clientThreadName = new AtomicReference<>();
        PortalHeartbeatClient slowClient = new PortalHeartbeatClient() {
            @Override
            public void send(HeartbeatRequest request) {
                clientThreadName.set(Thread.currentThread().getName());
                called.countDown();
                try {
                    Thread.sleep(300);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        StarterHeartbeatService service = new StarterHeartbeatService(
                slowClient,
                new IngestEnvelopeIdentity("project-123", "orders-api", "prod", "instance-1"),
                new HeartbeatProperties(),
                () -> Instant.parse("2026-05-24T08:30:00Z"));
        StarterHeartbeatSender sender = new StarterHeartbeatSender(service, Duration.ofSeconds(60));

        String callerThreadName = Thread.currentThread().getName();
        long startedAtNanos = System.nanoTime();
        sender.start();
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
        try {
            assertTrue(elapsedMillis < 100, "sender start should not wait for portal heartbeat");
            assertTrue(called.await(1, TimeUnit.SECONDS));
            assertNotEquals(callerThreadName, clientThreadName.get());
            assertTrue(clientThreadName.get().contains("heartbeat-sender"));
        } finally {
            sender.close();
        }
    }
}
