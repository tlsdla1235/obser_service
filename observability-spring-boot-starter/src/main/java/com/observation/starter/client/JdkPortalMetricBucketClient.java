package com.observation.starter.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.starter.model.ingest.IngestEnvelopeCandidate;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/**
 * Spring Web 의존성 없이 JDK HttpClient로 metric bucket ingest API를 호출하는 기본 구현체다.
 *
 * <p>project key는 HTTP header에만 사용하며 exception message나 cause chain에는 남기지 않는다.</p>
 */
public final class JdkPortalMetricBucketClient implements PortalMetricBucketClient {

    private static final String PROJECT_KEY_HEADER = "X-OBS-Project-Key";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String CONTENT_TYPE = "application/json";
    private static final String BUCKET_INGEST_PATH = "/api/ingest/v1/buckets";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI bucketIngestUri;
    private final String projectKey;
    private final Duration timeout;

    /**
     * portal bucket ingest URI와 raw project key를 받아 bounded HTTP client를 만든다.
     */
    public JdkPortalMetricBucketClient(
            URI bucketIngestUri,
            String projectKey,
            Duration timeout,
            ObjectMapper objectMapper) {
        this.bucketIngestUri = Objects.requireNonNull(bucketIngestUri, "bucketIngestUri must not be null");
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
     * portal base URL에 metric bucket ingest API path를 붙인 URI를 만든다.
     */
    public static URI bucketIngestUri(String portalBaseUrl) {
        String base = requireText(portalBaseUrl, "portalBaseUrl");
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return URI.create(base + BUCKET_INGEST_PATH);
    }

    /**
     * deterministic envelope payload와 idempotency key를 portal bucket ingest API로 전송한다.
     */
    @Override
    public void flush(IngestEnvelopeCandidate candidate) {
        IngestEnvelopeCandidate requiredCandidate = Objects.requireNonNull(
                candidate,
                "candidate must not be null");
        String requestBody = writeJson(requiredCandidate);
        HttpResponse<String> response;
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(bucketIngestUri)
                    .timeout(timeout)
                    .header(PROJECT_KEY_HEADER, projectKey)
                    .header(IDEMPOTENCY_KEY_HEADER, requiredCandidate.idempotencyKey())
                    .header("Content-Type", CONTENT_TYPE)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw PortalMetricBucketException.forTransportFailure(exception);
        } catch (IOException exception) {
            throw PortalMetricBucketException.forTransportFailure(exception);
        } catch (RuntimeException exception) {
            throw PortalMetricBucketException.forTransportFailure(exception);
        }
        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            throw PortalMetricBucketException.forStatus(statusCode);
        }
    }

    private String writeJson(IngestEnvelopeCandidate candidate) {
        try {
            return objectMapper.writeValueAsString(candidate.payload());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("metric bucket JSON serialization failed", exception);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
