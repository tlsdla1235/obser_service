package com.observation.starter.client.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.starter.model.ingest.IngestEnvelope;
import com.observation.starter.model.ingest.IngestEnvelopeCandidate;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdkPortalMetricBucketClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String PROJECT_KEY_FIXTURE = "fixture-project-key";
    private static final String RESPONSE_BODY_MARKER = "response-body-marker";
    private static final String IDEMPOTENCY_KEY = "project-123:orders-api:prod:instance-1:20260508T010000Z";

    @Test
    void sendsBucketPostWithProjectKeyIdempotencyHeaderAndJsonBody() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<List<String>> projectKeyHeaders = new AtomicReference<>();
        AtomicReference<List<String>> idempotencyHeaders = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<JsonNode> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/ingest/v1/buckets", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            projectKeyHeaders.set(exchange.getRequestHeaders().get("X-OBS-Project-Key"));
            idempotencyHeaders.set(exchange.getRequestHeaders().get("Idempotency-Key"));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(OBJECT_MAPPER.readTree(exchange.getRequestBody()));
            byte[] response = "{\"status\":\"accepted\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            JdkPortalMetricBucketClient client = client(server, Duration.ofSeconds(1));

            client.flush(candidate());

            assertEquals("POST", method.get());
            assertEquals("/api/ingest/v1/buckets", path.get());
            assertEquals(List.of(PROJECT_KEY_FIXTURE), projectKeyHeaders.get());
            assertEquals(List.of(IDEMPOTENCY_KEY), idempotencyHeaders.get());
            assertEquals("application/json", contentType.get());
            assertEquals("1.0", body.get().get("schemaVersion").asText());
            assertEquals("orders-api", body.get().get("application").get("name").asText());
            assertEquals("2026-05-08T01:00:00Z", body.get().get("bucket").get("startUtc").asText());
            assertEquals(2L, body.get().get("summary").get("requestCount").asLong());
            assertEquals("/orders/{orderId}", body.get().get("endpoints").get(0).get("route").asText());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void acceptsAnyTwoHundredStatusAsSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/ingest/v1/buckets", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            assertDoesNotThrow(() -> client(server, Duration.ofSeconds(1)).flush(candidate()));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void nonTwoHundredStatusExceptionDoesNotExposeRawProjectKeyOrResponseBody() throws Exception {
        PortalMetricBucketException exception = assertStatusCategory(
                401,
                PortalMetricBucketFailureCategory.UNAUTHORIZED);

        assertEquals("portal metric bucket ingest failed category=unauthorized status=401", exception.getMessage());
        assertFalse(exception.getMessage().contains(PROJECT_KEY_FIXTURE));
        assertFalse(exception.getMessage().contains(RESPONSE_BODY_MARKER));
    }

    @Test
    void classifiesClientConflictAndServerErrorStatus() throws Exception {
        assertEquals(
                PortalMetricBucketFailureCategory.CLIENT_4XX,
                assertStatusCategory(409, PortalMetricBucketFailureCategory.CLIENT_4XX).failureCategory());
        assertEquals(
                PortalMetricBucketFailureCategory.SERVER_5XX,
                assertStatusCategory(503, PortalMetricBucketFailureCategory.SERVER_5XX).failureCategory());
    }

    @Test
    void timeoutTransportExceptionIsSanitized() throws Exception {
        CountDownLatch serverEntered = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/ingest/v1/buckets", exchange -> {
            serverEntered.countDown();
            try {
                Thread.sleep(500);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        server.start();
        try {
            JdkPortalMetricBucketClient client = client(server, Duration.ofMillis(50));

            PortalMetricBucketException exception = assertThrows(
                    PortalMetricBucketException.class,
                    () -> client.flush(candidate()));

            assertTrue(serverEntered.await(1, TimeUnit.SECONDS));
            assertEquals(PortalMetricBucketFailureCategory.READ_TIMEOUT, exception.failureCategory());
            assertNoSecretInException(exception);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void connectionFailureExceptionChainIsSanitized() throws Exception {
        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }
        JdkPortalMetricBucketClient client = new JdkPortalMetricBucketClient(
                URI.create("http://127.0.0.1:" + unusedPort + "/api/ingest/v1/buckets"),
                PROJECT_KEY_FIXTURE,
                Duration.ofMillis(200),
                OBJECT_MAPPER);

        PortalMetricBucketException exception = assertThrows(
                PortalMetricBucketException.class,
                () -> client.flush(candidate()));

        assertNoSecretInException(exception);
    }

    @Test
    void interruptedSendRestoresInterruptFlagAndSanitizesFailure() throws Exception {
        CountDownLatch serverEntered = new CountDownLatch(1);
        CountDownLatch threadFinished = new CountDownLatch(1);
        AtomicReference<PortalMetricBucketException> failure = new AtomicReference<>();
        AtomicBoolean interruptedFlagRestored = new AtomicBoolean();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/ingest/v1/buckets", exchange -> {
            serverEntered.countDown();
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        server.start();
        try {
            JdkPortalMetricBucketClient client = client(server, Duration.ofSeconds(5));
            Thread thread = new Thread(() -> {
                try {
                    client.flush(candidate());
                } catch (PortalMetricBucketException exception) {
                    failure.set(exception);
                    interruptedFlagRestored.set(Thread.currentThread().isInterrupted());
                } finally {
                    threadFinished.countDown();
                }
            }, "bucket-client-interrupt-test");
            thread.start();
            assertTrue(serverEntered.await(1, TimeUnit.SECONDS));

            thread.interrupt();

            assertTrue(threadFinished.await(2, TimeUnit.SECONDS));
            assertNotNull(failure.get());
            assertTrue(interruptedFlagRestored.get());
            assertNoSecretInException(failure.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void constructorRejectsInvalidLocalConnectionSettingsWithoutSecretValues() {
        IllegalArgumentException blankKey = assertThrows(IllegalArgumentException.class, () -> new JdkPortalMetricBucketClient(
                URI.create("http://127.0.0.1/api/ingest/v1/buckets"),
                " ",
                Duration.ofSeconds(1),
                OBJECT_MAPPER));
        IllegalArgumentException timeout = assertThrows(IllegalArgumentException.class, () -> new JdkPortalMetricBucketClient(
                URI.create("http://127.0.0.1/api/ingest/v1/buckets"),
                PROJECT_KEY_FIXTURE,
                Duration.ZERO,
                OBJECT_MAPPER));

        assertEquals("observation.metric-flush.project-key must not be blank", blankKey.getMessage());
        assertEquals("observation.metric-flush.timeout-millis must be positive", timeout.getMessage());
        assertFalse(blankKey.getMessage().contains(PROJECT_KEY_FIXTURE));
        assertFalse(timeout.getMessage().contains(PROJECT_KEY_FIXTURE));
    }

    @Test
    void syntheticTransportFailureSanitizesRawCauseChainAndStackTrace() {
        RuntimeException rawCause = new RuntimeException(
                "portal rejected raw project key " + PROJECT_KEY_FIXTURE,
                new IllegalStateException("nested raw project key " + PROJECT_KEY_FIXTURE));

        PortalMetricBucketException exception = PortalMetricBucketException.forTransportFailure(rawCause);

        assertEquals(PortalMetricBucketFailureCategory.UNKNOWN, exception.failureCategory());
        assertNoSecretInException(exception);
        assertNotSame(rawCause, exception.getCause());
    }

    private static PortalMetricBucketException assertStatusCategory(
            int statusCode,
            PortalMetricBucketFailureCategory expectedCategory) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/ingest/v1/buckets", exchange -> {
            byte[] response = ("{\"error\":\"" + RESPONSE_BODY_MARKER + " " + PROJECT_KEY_FIXTURE + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            RuntimeException exception = assertThrows(
                    RuntimeException.class,
                    () -> client(server, Duration.ofSeconds(1)).flush(candidate()));
            PortalMetricBucketException metricException = assertInstanceOf(
                    PortalMetricBucketException.class,
                    exception);

            assertEquals(expectedCategory, metricException.failureCategory());
            assertEquals(statusCode, metricException.statusCode());
            assertNoSecretInException(metricException);
            return metricException;
        } finally {
            server.stop(0);
        }
    }

    private static JdkPortalMetricBucketClient client(HttpServer server, Duration timeout) {
        return new JdkPortalMetricBucketClient(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/ingest/v1/buckets"),
                PROJECT_KEY_FIXTURE,
                timeout,
                OBJECT_MAPPER);
    }

    private static IngestEnvelopeCandidate candidate() {
        return new IngestEnvelopeCandidate(new IngestEnvelope(
                "1.0",
                new IngestEnvelope.Application("orders-api", "prod", "instance-1"),
                new IngestEnvelope.Bucket(
                        "2026-05-08T01:00:00Z",
                        "2026-05-08T01:00:30Z",
                        30),
                new IngestEnvelope.Summary(
                        2,
                        1,
                        List.of(
                                new IngestEnvelope.DurationBucket(50, 1),
                                new IngestEnvelope.DurationBucket(100, 2)),
                        new IngestEnvelope.Jvm(0.50, 0.60),
                        new IngestEnvelope.Datasource(0.70),
                        new IngestEnvelope.LocalPercentiles(
                                "instance_bucket",
                                "starter_local",
                                "2026-05-08T01:00:00Z",
                                "2026-05-08T01:00:30Z",
                                2,
                                50,
                                100,
                                false)),
                List.of(new IngestEnvelope.Endpoint(
                        "GET",
                        "/orders/{orderId}",
                        2,
                        1,
                        List.of(
                                new IngestEnvelope.DurationBucket(50, 1),
                                new IngestEnvelope.DurationBucket(100, 2))))),
                IDEMPOTENCY_KEY);
    }

    private static void assertNoSecretInException(Throwable throwable) {
        assertFalse(throwableChainContains(throwable, PROJECT_KEY_FIXTURE));
        assertFalse(throwableChainContains(throwable, RESPONSE_BODY_MARKER));
        assertFalse(stackTrace(throwable).contains(PROJECT_KEY_FIXTURE));
        assertFalse(stackTrace(throwable).contains(RESPONSE_BODY_MARKER));
    }

    private static boolean throwableChainContains(Throwable throwable, String value) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current.toString().contains(value)) {
                return true;
            }
        }
        return false;
    }

    private static String stackTrace(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
