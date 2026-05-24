package com.observation.starter.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.starter.model.heartbeat.HeartbeatRequest;
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

class JdkPortalHeartbeatClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void sendsHeartbeatPostWithProjectKeyHeaderAndJsonBody() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<List<String>> projectKeyHeaders = new AtomicReference<>();
        AtomicReference<JsonNode> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/ingest/v1/heartbeat", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            projectKeyHeaders.set(exchange.getRequestHeaders().get("X-OBS-Project-Key"));
            body.set(OBJECT_MAPPER.readTree(exchange.getRequestBody()));
            byte[] response = "{\"status\":\"received\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                    + "/api/ingest/v1/heartbeat");
            JdkPortalHeartbeatClient client = new JdkPortalHeartbeatClient(
                    uri,
                    "pk_live.secret",
                    Duration.ofSeconds(1),
                    OBJECT_MAPPER);

            client.send(request());

            assertEquals("POST", method.get());
            assertEquals("/api/ingest/v1/heartbeat", path.get());
            assertEquals(List.of("pk_live.secret"), projectKeyHeaders.get());
            assertEquals("1.0", body.get().get("schemaVersion").asText());
            assertEquals("orders-api", body.get().get("application").get("name").asText());
            assertEquals(1L, body.get().get("heartbeat").get("sequence").asLong());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void nonSuccessStatusExceptionDoesNotExposeRawProjectKey() throws Exception {
        PortalHeartbeatException exception = assertStatusCategory(401, HeartbeatFailureCategory.UNAUTHORIZED);

        assertEquals("portal heartbeat failed category=unauthorized status=401", exception.getMessage());
        assertFalse(exception.getMessage().contains("pk_live.secret"));
    }

    @Test
    void classifiesServerAndClientErrorStatus() throws Exception {
        assertEquals(HeartbeatFailureCategory.SERVER_5XX, assertStatusCategory(503, HeartbeatFailureCategory.SERVER_5XX)
                .failureCategory());
        assertEquals(HeartbeatFailureCategory.CLIENT_4XX, assertStatusCategory(404, HeartbeatFailureCategory.CLIENT_4XX)
                .failureCategory());
    }

    @Test
    void transportExceptionSanitizesRawCauseChain() {
        RuntimeException rawCause = new RuntimeException(
                "portal rejected raw project key pk_live.secret",
                new IllegalStateException("nested raw project key pk_live.secret"));

        PortalHeartbeatException exception = PortalHeartbeatException.forTransportFailure(rawCause);

        assertEquals(HeartbeatFailureCategory.UNKNOWN, exception.failureCategory());
        assertFalse(exception.getMessage().contains("pk_live.secret"));
        assertNotNull(exception.getCause());
        assertNotSame(rawCause, exception.getCause());
        assertFalse(throwableChainContains(exception, "pk_live.secret"));
        assertFalse(stackTrace(exception).contains("pk_live.secret"));
    }

    private static PortalHeartbeatException assertStatusCategory(
            int statusCode,
            HeartbeatFailureCategory expectedCategory) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/ingest/v1/heartbeat", exchange -> {
            byte[] response = "{\"error\":\"heartbeat failed\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                    + "/api/ingest/v1/heartbeat");
            JdkPortalHeartbeatClient client = new JdkPortalHeartbeatClient(
                    uri,
                    "pk_live.secret",
                    Duration.ofSeconds(1),
                    OBJECT_MAPPER);

            RuntimeException exception = assertThrows(RuntimeException.class, () -> client.send(request()));
            PortalHeartbeatException heartbeatException = assertInstanceOf(PortalHeartbeatException.class, exception);

            assertEquals(expectedCategory, heartbeatException.failureCategory());
            assertEquals(statusCode, heartbeatException.statusCode());
            assertFalse(heartbeatException.getMessage().contains("pk_live.secret"));
            return heartbeatException;
        } finally {
            server.stop(0);
        }
    }

    private static HeartbeatRequest request() {
        return new HeartbeatRequest(
                "1.0",
                "0.1.0-test",
                new HeartbeatRequest.Heartbeat("2026-05-24T08:30:00Z", 1L, 30),
                new HeartbeatRequest.Application("orders-api", "prod", "instance-1"));
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
