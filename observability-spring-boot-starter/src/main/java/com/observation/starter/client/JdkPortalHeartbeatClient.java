package com.observation.starter.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.starter.model.heartbeat.HeartbeatRequest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/**
 * Spring Web 의존성 없이 JDK HttpClient로 heartbeat API를 호출하는 기본 구현체다.
 *
 * <p>connect/request timeout을 모두 bounded 값으로 설정하고, 실패 메시지에는 raw project key를 넣지 않는다.</p>
 */
public final class JdkPortalHeartbeatClient implements PortalHeartbeatClient {

    private static final String PROJECT_KEY_HEADER = "X-OBS-Project-Key";
    private static final String CONTENT_TYPE = "application/json";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI heartbeatUri;
    private final String projectKey;
    private final Duration timeout;

    /**
     * portal base URL과 raw project key를 받아 heartbeat 전용 client를 만든다.
     */
    public JdkPortalHeartbeatClient(
            URI heartbeatUri,
            String projectKey,
            Duration timeout,
            ObjectMapper objectMapper) {
        this.heartbeatUri = Objects.requireNonNull(heartbeatUri, "heartbeatUri must not be null");
        this.projectKey = requireText(projectKey, "projectKey");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
    }

    /**
     * JSON body를 만들어 bounded HTTP request로 전송한다.
     */
    @Override
    public void send(HeartbeatRequest request) {
        HttpRequest httpRequest = HttpRequest.newBuilder(heartbeatUri)
                .timeout(timeout)
                .header(PROJECT_KEY_HEADER, projectKey)
                .header("Content-Type", CONTENT_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString(writeJson(request)))
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("portal heartbeat request was interrupted", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("portal heartbeat request failed", exception);
        }
        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException("portal heartbeat returned non-success status " + statusCode);
        }
    }

    private String writeJson(HeartbeatRequest request) {
        try {
            return objectMapper.writeValueAsString(Objects.requireNonNull(request, "request must not be null"));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("heartbeat JSON serialization failed", exception);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
