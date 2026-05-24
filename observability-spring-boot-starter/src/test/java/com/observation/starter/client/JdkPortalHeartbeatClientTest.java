package com.observation.starter.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.starter.model.heartbeat.HeartbeatRequest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/ingest/v1/heartbeat", exchange -> {
            byte[] response = "{\"error\":\"unauthorized\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, response.length);
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

            assertEquals("portal heartbeat returned non-success status 401", exception.getMessage());
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
}
