package com.observation.portal.domain.ingest.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.net.URI;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * malformed/conflict로 분류된 sanitized envelope를 application-level SQS DLQ로 전송한다.
 */
@Component
@ConditionalOnProperty(name = "portal.ingest.buffer.mode", havingValue = "sqs")
public class SqsMetricIngestDlqPublisher implements MetricIngestDlqPublisher {

    private final Supplier<SqsClient> sqsClientSupplier;
    private final IngestBufferProperties properties;
    private final ObjectMapper objectMapper;
    private final Object sqsClientLock = new Object();
    private volatile SqsClient sqsClient;

    /**
     * AWS client build를 DLQ publish 시점까지 늦춰 worker disabled/default 실행이 startup failure로 이어지지 않게 한다.
     */
    @Autowired
    public SqsMetricIngestDlqPublisher(IngestBufferProperties properties, ObjectMapper objectMapper) {
        this(() -> buildClient(properties), properties, objectMapper);
    }

    /**
     * SQS unit test에서 mock client를 주입할 수 있게 하는 생성자다.
     */
    public SqsMetricIngestDlqPublisher(SqsClient sqsClient, IngestBufferProperties properties, ObjectMapper objectMapper) {
        this(() -> sqsClient, properties, objectMapper);
    }

    SqsMetricIngestDlqPublisher(
            Supplier<SqsClient> sqsClientSupplier,
            IngestBufferProperties properties,
            ObjectMapper objectMapper) {
        this.sqsClientSupplier = Objects.requireNonNull(sqsClientSupplier, "sqsClientSupplier must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * sanitized DLQ envelope만 message body로 직렬화해 configured DLQ URL에 전송한다.
     */
    @Override
    public void publish(MetricIngestDlqEnvelope envelope) {
        MetricIngestDlqEnvelope requiredEnvelope = Objects.requireNonNull(envelope, "envelope must not be null");
        String dlqUrl = properties.getWorker().getDlqUrl();
        if (dlqUrl == null || dlqUrl.isBlank()) {
            throw new MetricIngestDlqPublishException("application_dlq_url_missing", null);
        }
        try {
            sqsClient().sendMessage(SendMessageRequest.builder()
                    .queueUrl(dlqUrl)
                    .messageBody(objectMapper.writeValueAsString(requiredEnvelope))
                    .build());
        } catch (JsonProcessingException exception) {
            throw new MetricIngestDlqPublishException("application_dlq_envelope_serialization_failed", exception);
        } catch (SdkException exception) {
            throw new MetricIngestDlqPublishException("application_dlq_send_failed", exception);
        } catch (RuntimeException exception) {
            throw new MetricIngestDlqPublishException("application_dlq_send_failed", exception);
        }
    }

    /**
     * Spring bean 종료 시 lazy하게 생성된 AWS client의 HTTP 리소스를 정리한다.
     */
    @PreDestroy
    public void close() {
        SqsClient current = sqsClient;
        if (current != null) {
            current.close();
        }
    }

    private SqsClient sqsClient() {
        SqsClient current = sqsClient;
        if (current != null) {
            return current;
        }
        synchronized (sqsClientLock) {
            current = sqsClient;
            if (current == null) {
                current = Objects.requireNonNull(sqsClientSupplier.get(), "sqsClient must not be null");
                sqsClient = current;
            }
            return current;
        }
    }

    private static SqsClient buildClient(IngestBufferProperties properties) {
        SqsClientBuilder builder = SqsClient.builder();
        String endpointOverride = properties.getSqs().getEndpointOverride();
        if (endpointOverride != null && !endpointOverride.isBlank()) {
            builder.endpointOverride(URI.create(endpointOverride));
        }
        return builder.build();
    }
}
