package com.observation.portal.domain.ingest.queue;

import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * AWS SDK v2 `SendMessage`를 사용해 검증된 ingest queue message를 SQS Standard queue로 전송한다.
 */
@Component
@ConditionalOnProperty(name = "portal.ingest.buffer.mode", havingValue = "sqs")
public class SqsMetricIngestQueuePublisher implements MetricIngestQueuePublisher {

    private final Supplier<SqsClient> sqsClientSupplier;
    private final IngestBufferProperties properties;
    private final Object sqsClientLock = new Object();
    private volatile SqsClient sqsClient;

    /**
     * AWS client build를 enqueue 시점까지 늦춰 queue-url/config missing이 startup failure가 아니라 503 path로 닫히게 한다.
     */
    @Autowired
    public SqsMetricIngestQueuePublisher(IngestBufferProperties properties) {
        this(() -> buildClient(properties), properties);
    }

    /**
     * SQS unit test에서 mock client를 주입할 수 있게 하는 생성자다.
     */
    public SqsMetricIngestQueuePublisher(SqsClient sqsClient, IngestBufferProperties properties) {
        this(() -> sqsClient, properties);
    }

    /**
     * production lazy-client 동작을 unit test에서 검증할 수 있게 supplier를 주입한다.
     */
    SqsMetricIngestQueuePublisher(Supplier<SqsClient> sqsClientSupplier, IngestBufferProperties properties) {
        this.sqsClientSupplier = Objects.requireNonNull(sqsClientSupplier, "sqsClientSupplier must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /**
     * message body와 8개 allow-list attribute만 SQS에 전송한다.
     */
    @Override
    public MetricIngestEnqueueReceipt enqueue(MetricIngestQueueMessage message) {
        MetricIngestQueueMessage requiredMessage = Objects.requireNonNull(message, "message must not be null");
        String queueUrl = properties.getSqs().getQueueUrl();
        if (queueUrl == null || queueUrl.isBlank()) {
            throw new MetricIngestQueuePublishException("queue_url_missing", null);
        }
        try {
            SendMessageResponse response = sqsClient().sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(requiredMessage.bodyJson())
                    .messageAttributes(toSqsAttributes(requiredMessage))
                    .build());
            return new MetricIngestEnqueueReceipt(response.messageId(), requiredMessage.body().enqueuedAt());
        } catch (SdkException exception) {
            throw new MetricIngestQueuePublishException("sqs_send_failed", exception);
        } catch (RuntimeException exception) {
            throw new MetricIngestQueuePublishException("sqs_send_failed", exception);
        }
    }

    /**
     * SqsClient는 생성 비용과 HTTP 리소스를 가지므로 첫 enqueue 때 한 번만 만들고 이후 재사용한다.
     */
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

    private static SqsClient buildClient(IngestBufferProperties properties) {
        SqsClientBuilder builder = SqsClient.builder()
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(properties.getPublisherTimeout())
                        .build());
        String endpointOverride = properties.getSqs().getEndpointOverride();
        if (endpointOverride != null && !endpointOverride.isBlank()) {
            builder.endpointOverride(URI.create(endpointOverride));
        }
        return builder.build();
    }

    private static Map<String, MessageAttributeValue> toSqsAttributes(MetricIngestQueueMessage message) {
        Map<String, MessageAttributeValue> attributes = new LinkedHashMap<>();
        for (MetricIngestMessageAttribute attribute : message.attributes()) {
            attributes.put(attribute.name(), MessageAttributeValue.builder()
                    .dataType(attribute.dataType())
                    .stringValue(attribute.stringValue())
                    .build());
        }
        return attributes;
    }
}
