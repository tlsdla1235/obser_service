package com.observation.starter.client.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.starter.client.PortalMetricBucketClient;
import com.observation.starter.config.MetricDrainProperties;
import com.observation.starter.model.ingest.IngestEnvelopeCandidate;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * Spring Web 의존성 없이 JDK HttpClient로 metric bucket ingest API를 호출하는 기본 구현체다.
 *
 * <p>이 client는 {@link IngestEnvelopeCandidate}가 이미 가진 payload와 Idempotency-Key를 그대로
 * 직렬화/전송하며, payload shape나 idempotency identity를 재계산하지 않는다.</p>
 */
public final class JdkPortalMetricBucketClient implements PortalMetricBucketClient {

    private static final String PROJECT_KEY_HEADER = "X-OBS-Project-Key";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String CONTENT_TYPE = "application/json";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI bucketIngestUri;
    private final String projectKey;
    private final Duration timeout;

    /**
     * bucket ingest endpoint URI와 raw project key, bounded timeout으로 default HTTP client를 만든다.
     */
    public JdkPortalMetricBucketClient(
            URI bucketIngestUri,
            String projectKey,
            Duration timeout,
            ObjectMapper objectMapper) {
        this.bucketIngestUri = Objects.requireNonNull(bucketIngestUri, "bucketIngestUri must not be null");
        this.projectKey = requireHeaderText(projectKey, MetricDrainProperties.PREFIX + ".project-key");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException(MetricDrainProperties.PREFIX + ".timeout-millis must be positive");
        }
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
    }

    /**
     * candidate payload를 JSON body로 직렬화하고 bucket ingest API에 POST한다.
     */
    @Override
    public void flush(IngestEnvelopeCandidate candidate) {
        IngestEnvelopeCandidate requiredCandidate = Objects.requireNonNull(candidate, "candidate must not be null");
        try {
            HttpRequest request = HttpRequest.newBuilder(bucketIngestUri)
                    .timeout(timeout)
                    .header(PROJECT_KEY_HEADER, projectKey)
                    .header(IDEMPOTENCY_KEY_HEADER, requiredCandidate.idempotencyKey())
                    .header("Content-Type", CONTENT_TYPE)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            writeJson(requiredCandidate),
                            StandardCharsets.UTF_8))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw PortalMetricBucketException.forStatus(statusCode);
            }
        } catch (PortalMetricBucketException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw PortalMetricBucketException.forTransportFailure(exception);
        } catch (IOException exception) {
            throw PortalMetricBucketException.forTransportFailure(exception);
        } catch (RuntimeException exception) {
            throw PortalMetricBucketException.forTransportFailure(exception);
        }
    }

    private String writeJson(IngestEnvelopeCandidate candidate) {
        try {
            return objectMapper.writeValueAsString(candidate.payload());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("metric bucket ingest JSON serialization failed", exception);
        }
    }

    private static String requireHeaderText(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(propertyName + " must not be blank");
        }
        String trimmed = value.trim();
        if (containsControlCharacter(trimmed)) {
            throw new IllegalArgumentException(propertyName + " must not contain control characters");
        }
        return trimmed;
    }

    private static boolean containsControlCharacter(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character <= 0x1F || character == 0x7F) {
                return true;
            }
        }
        return false;
    }
}
