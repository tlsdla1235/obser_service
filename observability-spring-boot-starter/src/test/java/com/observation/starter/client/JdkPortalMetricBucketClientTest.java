package com.observation.starter.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.starter.model.ingest.IngestEnvelope;
import com.observation.starter.model.ingest.IngestEnvelopeCandidate;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdkPortalMetricBucketClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String PROJECT_KEY_SENTINEL = "issued-project-key";

    @Test
    void sendsMetricBucketPostWithProjectKeyIdempotencyHeaderAndJsonBody() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<List<String>> projectKeyHeaders = new AtomicReference<>();
        AtomicReference<List<String>> idempotencyHeaders = new AtomicReference<>();
        AtomicReference<JsonNode> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/ingest/v1/buckets", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            projectKeyHeaders.set(exchange.getRequestHeaders().get("X-OBS-Project-Key"));
            idempotencyHeaders.set(exchange.getRequestHeaders().get("Idempotency-Key"));
            body.set(OBJECT_MAPPER.readTree(exchange.getRequestBody()));
            byte[] response = "{\"status\":\"accepted\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            URI uri = JdkPortalMetricBucketClient.bucketIngestUri(
                    "http://127.0.0.1:" + server.getAddress().getPort());
            JdkPortalMetricBucketClient client = new JdkPortalMetricBucketClient(
                    uri,
                    PROJECT_KEY_SENTINEL,
                    Duration.ofSeconds(1),
                    OBJECT_MAPPER);

            client.flush(candidate());

            assertEquals("POST", method.get());
            assertEquals("/api/ingest/v1/buckets", path.get());
            assertEquals(List.of(PROJECT_KEY_SENTINEL), projectKeyHeaders.get());
            assertEquals(List.of("project-123:orders-api:prod:instance-1:20260508T010000Z"),
                    idempotencyHeaders.get());
            assertEquals("1.0", body.get().get("schemaVersion").asText());
            assertEquals("orders-api", body.get().get("application").get("name").asText());
            assertEquals(1L, body.get().get("summary").get("requestCount").asLong());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void nonSuccessStatusExceptionDoesNotExposeRawProjectKey() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/ingest/v1/buckets", exchange -> {
            byte[] response = "{\"error\":\"unauthorized\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            URI uri = JdkPortalMetricBucketClient.bucketIngestUri(
                    "http://127.0.0.1:" + server.getAddress().getPort());
            JdkPortalMetricBucketClient client = new JdkPortalMetricBucketClient(
                    uri,
                    PROJECT_KEY_SENTINEL,
                    Duration.ofSeconds(1),
                    OBJECT_MAPPER);

            RuntimeException exception = assertThrows(RuntimeException.class, () -> client.flush(candidate()));
            PortalMetricBucketException bucketException = assertInstanceOf(
                    PortalMetricBucketException.class,
                    exception);

            assertEquals(401, bucketException.statusCode());
            assertFalse(bucketException.getMessage().contains(PROJECT_KEY_SENTINEL));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void invalidProjectKeyHeaderValueExceptionDoesNotExposeRawProjectKey() {
        String invalidProjectKey = PROJECT_KEY_SENTINEL + "\nsecret-sentinel";
        JdkPortalMetricBucketClient client = new JdkPortalMetricBucketClient(
                URI.create("http://127.0.0.1:1/api/ingest/v1/buckets"),
                invalidProjectKey,
                Duration.ofSeconds(1),
                OBJECT_MAPPER);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> client.flush(candidate()));
        PortalMetricBucketException bucketException = assertInstanceOf(
                PortalMetricBucketException.class,
                exception);

        assertFalse(bucketException.getMessage().contains(invalidProjectKey));
        assertNotNull(bucketException.getCause());
        assertFalse(bucketException.getCause().getMessage().contains(invalidProjectKey));
        assertFalse(throwableChainContains(bucketException, invalidProjectKey));
        assertFalse(throwableChainContains(bucketException, PROJECT_KEY_SENTINEL));
        assertFalse(stackTrace(bucketException).contains(invalidProjectKey));
        assertFalse(stackTrace(bucketException).contains(PROJECT_KEY_SENTINEL));
    }

    @Test
    void transportExceptionSanitizesRawCauseChain() {
        RuntimeException rawCause = new RuntimeException(
                "portal rejected raw project key " + PROJECT_KEY_SENTINEL,
                new IllegalStateException("nested raw project key " + PROJECT_KEY_SENTINEL));

        PortalMetricBucketException exception = PortalMetricBucketException.forTransportFailure(rawCause);

        assertFalse(exception.getMessage().contains(PROJECT_KEY_SENTINEL));
        assertNotNull(exception.getCause());
        assertNotSame(rawCause, exception.getCause());
        assertFalse(throwableChainContains(exception, PROJECT_KEY_SENTINEL));
        assertFalse(stackTrace(exception).contains(PROJECT_KEY_SENTINEL));
    }

    private static IngestEnvelopeCandidate candidate() {
        return new IngestEnvelopeCandidate(new IngestEnvelope(
                "1.0",
                new IngestEnvelope.Application("orders-api", "prod", "instance-1"),
                new IngestEnvelope.Bucket("2026-05-08T01:00:00Z", "2026-05-08T01:00:30Z", 30),
                new IngestEnvelope.Summary(
                        1,
                        0,
                        List.of(new IngestEnvelope.DurationBucket(50, 1)),
                        null,
                        null),
                List.of()), "project-123:orders-api:prod:instance-1:20260508T010000Z");
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
