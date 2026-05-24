package com.observation.starter.service;

import com.observation.starter.client.HeartbeatFailureCategory;
import com.observation.starter.client.PortalHeartbeatClient;
import com.observation.starter.client.PortalHeartbeatException;
import com.observation.starter.config.HeartbeatProperties;
import com.observation.starter.model.heartbeat.HeartbeatRequest;
import com.observation.starter.model.ingest.IngestEnvelopeIdentity;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StarterHeartbeatSenderTest {

    @Test
    void defaultFailureReporterUsesSlf4jWarnBoundaryWithoutRawProjectKey() {
        AtomicReference<String> loggedWarning = new AtomicReference<>();
        HeartbeatFailureReporter reporter = new Slf4jHeartbeatFailureReporter(capturingSlf4jLogger(loggedWarning));

        assertInstanceOf(Slf4jHeartbeatFailureReporter.class, HeartbeatFailureReporter.logger());

        reporter.warn(new HeartbeatFailureReporter.Warning(
                "portal-heartbeat",
                HeartbeatFailureCategory.READ_TIMEOUT,
                Duration.ofMillis(250)));

        String message = loggedWarning.get();
        assertTrue(message.contains("endpointAlias=portal-heartbeat"));
        assertTrue(message.contains("failureCategory=read_timeout"));
        assertTrue(message.contains("hostApplicationContinues=true"));
        assertTrue(message.contains("retryBackoffApplied=true"));
        assertTrue(message.contains("nextRetryDelayMillis=250"));
        assertFalse(message.contains("pk_live.secret"));
    }

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

    @Test
    void firstFailureWarnsOnceAndRepeatedFailuresBackOffWithoutLogSpam() throws Exception {
        PortalHeartbeatClient failingClient = request -> {
            throw PortalHeartbeatException.forTransportFailure(new java.net.http.HttpTimeoutException("timeout"));
        };
        StarterHeartbeatService service = service(failingClient);
        List<Duration> delays = new ArrayList<>();
        CountDownLatch attempts = new CountDownLatch(3);
        HeartbeatRetryBackoff backoff = duration -> {
            delays.add(duration);
            attempts.countDown();
            if (delays.size() >= 3) {
                throw new InterruptedException("stop test loop");
            }
        };
        List<HeartbeatFailureReporter.Warning> warnings = new ArrayList<>();
        StarterHeartbeatSender sender = new StarterHeartbeatSender(
                service,
                Duration.ofMillis(10),
                backoff,
                warnings::add);

        sender.start();
        try {
            assertTrue(attempts.await(1, TimeUnit.SECONDS));
        } finally {
            sender.close();
        }

        assertEquals(List.of(Duration.ofMillis(20), Duration.ofMillis(40), Duration.ofMillis(80)), delays);
        assertEquals(1, warnings.size());
        HeartbeatFailureReporter.Warning warning = warnings.get(0);
        assertEquals("portal-heartbeat", warning.endpointAlias());
        assertEquals(HeartbeatFailureCategory.READ_TIMEOUT, warning.failureCategory());
        assertTrue(warning.message().contains("hostApplicationContinues=true"));
        assertTrue(warning.message().contains("retryBackoffApplied=true"));
        assertFalse(warning.message().contains("pk_live.secret"));
    }

    @Test
    void categoryChangeBeforeSuccessWarnsAgainWithoutOpeningLogSpam() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        PortalHeartbeatClient failingClient = request -> {
            int attempt = attempts.incrementAndGet();
            if (attempt == 1) {
                throw PortalHeartbeatException.forTransportFailure(new java.net.http.HttpTimeoutException("timeout"));
            }
            throw PortalHeartbeatException.forStatus(503);
        };
        StarterHeartbeatService service = service(failingClient);
        List<Duration> delays = new ArrayList<>();
        CountDownLatch attemptLatch = new CountDownLatch(3);
        HeartbeatRetryBackoff backoff = duration -> {
            delays.add(duration);
            attemptLatch.countDown();
            if (delays.size() >= 3) {
                throw new InterruptedException("stop test loop");
            }
        };
        List<HeartbeatFailureReporter.Warning> warnings = new ArrayList<>();
        StarterHeartbeatSender sender = new StarterHeartbeatSender(
                service,
                Duration.ofMillis(10),
                backoff,
                warnings::add);

        sender.start();
        try {
            assertTrue(attemptLatch.await(1, TimeUnit.SECONDS));
        } finally {
            sender.close();
        }

        assertEquals(List.of(Duration.ofMillis(20), Duration.ofMillis(40), Duration.ofMillis(80)), delays);
        assertEquals(2, warnings.size());
        assertEquals(HeartbeatFailureCategory.READ_TIMEOUT, warnings.get(0).failureCategory());
        assertEquals(HeartbeatFailureCategory.SERVER_5XX, warnings.get(1).failureCategory());
    }

    @Test
    void successResetsFailureWindowSoNextFailureIsWarnedAgain() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        PortalHeartbeatClient flakyClient = request -> {
            int attempt = attempts.incrementAndGet();
            if (attempt == 1) {
                throw PortalHeartbeatException.forTransportFailure(new java.net.ConnectException("Connection refused"));
            }
            if (attempt == 3) {
                throw PortalHeartbeatException.forStatus(503);
            }
        };
        List<Duration> delays = new ArrayList<>();
        CountDownLatch attemptLatch = new CountDownLatch(3);
        HeartbeatRetryBackoff backoff = duration -> {
            delays.add(duration);
            attemptLatch.countDown();
            if (delays.size() >= 3) {
                throw new InterruptedException("stop test loop");
            }
        };
        List<HeartbeatFailureReporter.Warning> warnings = new ArrayList<>();
        StarterHeartbeatSender sender = new StarterHeartbeatSender(
                service(flakyClient),
                Duration.ofMillis(10),
                backoff,
                warnings::add);

        sender.start();
        try {
            assertTrue(attemptLatch.await(1, TimeUnit.SECONDS));
        } finally {
            sender.close();
        }

        assertEquals(List.of(Duration.ofMillis(20), Duration.ofMillis(10), Duration.ofMillis(20)), delays);
        assertEquals(2, warnings.size());
        assertEquals(HeartbeatFailureCategory.CONNECTION_REFUSED, warnings.get(0).failureCategory());
        assertEquals(HeartbeatFailureCategory.SERVER_5XX, warnings.get(1).failureCategory());
    }

    @Test
    void warningMessageDoesNotUseRawExceptionMessage() throws Exception {
        PortalHeartbeatClient failingClient = request -> {
            throw new IllegalStateException("portal rejected raw key pk_live.secret");
        };
        List<HeartbeatFailureReporter.Warning> warnings = new ArrayList<>();
        CountDownLatch warned = new CountDownLatch(1);
        HeartbeatRetryBackoff backoff = duration -> {
            throw new InterruptedException("stop test loop");
        };
        StarterHeartbeatSender sender = new StarterHeartbeatSender(
                service(failingClient),
                Duration.ofMillis(10),
                backoff,
                warning -> {
                    warnings.add(warning);
                    warned.countDown();
                });

        sender.start();
        try {
            assertTrue(warned.await(1, TimeUnit.SECONDS));
        } finally {
            sender.close();
        }

        assertEquals(1, warnings.size());
        assertEquals(HeartbeatFailureCategory.UNKNOWN, warnings.get(0).failureCategory());
        assertFalse(warnings.get(0).message().contains("pk_live.secret"));
    }

    private static StarterHeartbeatService service(PortalHeartbeatClient client) {
        return new StarterHeartbeatService(
                client,
                new IngestEnvelopeIdentity("project-123", "orders-api", "prod", "instance-1"),
                new HeartbeatProperties(),
                () -> Instant.parse("2026-05-24T08:30:00Z"));
    }

    /**
     * SLF4J reporter가 logger.warn(String) 경계로 WARN 문자열을 넘기는지 확인하는 logger spy다.
     */
    private static Logger capturingSlf4jLogger(AtomicReference<String> loggedWarning) {
        return (Logger) Proxy.newProxyInstance(
                Logger.class.getClassLoader(),
                new Class<?>[]{Logger.class},
                (proxy, method, args) -> {
                    if ("getName".equals(method.getName())) {
                        return "capturing-heartbeat-logger";
                    }
                    if ("warn".equals(method.getName()) && args != null && args.length > 0
                            && args[0] instanceof String message) {
                        loggedWarning.set(message);
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive() || returnType == Void.TYPE) {
            return null;
        }
        if (returnType == Boolean.TYPE) {
            return false;
        }
        if (returnType == Character.TYPE) {
            return '\0';
        }
        return 0;
    }
}
