package com.observation.portal.domain.ingest.queue;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * AWS SDK v2 `ReceiveMessage`/`DeleteMessage`를 사용하는 portal 내부 SQS source queue consumer다.
 */
@Component
@ConditionalOnProperty(name = "portal.ingest.buffer.mode", havingValue = "sqs")
public class SqsMetricIngestQueueConsumer implements MetricIngestQueueConsumer {

    private final Supplier<SqsClient> sqsClientSupplier;
    private final IngestBufferProperties properties;
    private final Object sqsClientLock = new Object();
    private volatile SqsClient sqsClient;

    /**
     * SQS client 생성을 receive 시점까지 늦춰 worker disabled/default 실행이 startup failure로 이어지지 않게 한다.
     */
    @Autowired
    public SqsMetricIngestQueueConsumer(IngestBufferProperties properties) {
        this(() -> buildClient(properties), properties);
    }

    /**
     * SQS unit test에서 mock client를 주입할 수 있게 하는 생성자다.
     */
    public SqsMetricIngestQueueConsumer(SqsClient sqsClient, IngestBufferProperties properties) {
        this(() -> sqsClient, properties);
    }

    SqsMetricIngestQueueConsumer(Supplier<SqsClient> sqsClientSupplier, IngestBufferProperties properties) {
        this.sqsClientSupplier = Objects.requireNonNull(sqsClientSupplier, "sqsClientSupplier must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /**
     * configured source queue URL에서 long poll receive를 수행하고 8개 message attribute와 receive count를 보존한다.
     */
    @Override
    public List<MetricIngestReceivedMessage> receive() {
        String queueUrl = sourceQueueUrl();
        try {
            return sqsClient().receiveMessage(ReceiveMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .waitTimeSeconds(properties.getWorker().getLongPollSeconds())
                            .maxNumberOfMessages(properties.getWorker().getMaxMessagesPerPoll())
                            .visibilityTimeout(toSeconds(properties.getWorker().getVisibilityTimeout()))
                            .messageAttributeNames("All")
                            .messageSystemAttributeNames(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT)
                            .build())
                    .messages()
                    .stream()
                    .map(this::toReceivedMessage)
                    .toList();
        } catch (MetricIngestQueueConsumerException exception) {
            throw exception;
        } catch (SdkException exception) {
            throw new MetricIngestQueueConsumerException("sqs_receive_failed", exception);
        } catch (RuntimeException exception) {
            throw new MetricIngestQueueConsumerException("sqs_receive_failed", exception);
        }
    }

    /**
     * 처리 완료가 확정된 source message를 receipt handle만 사용해 삭제한다.
     */
    @Override
    public void delete(MetricIngestReceivedMessage message) {
        MetricIngestReceivedMessage requiredMessage = Objects.requireNonNull(message, "message must not be null");
        try {
            sqsClient().deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(sourceQueueUrl())
                    .receiptHandle(requiredMessage.receiptHandle())
                    .build());
        } catch (SdkException exception) {
            throw new MetricIngestQueueConsumerException("sqs_delete_failed", exception);
        } catch (RuntimeException exception) {
            throw new MetricIngestQueueConsumerException("sqs_delete_failed", exception);
        }
    }

    /**
     * 운영자가 필요 시 visibility timeout을 조정할 수 있게 경계만 제공한다.
     */
    @Override
    public void changeVisibility(MetricIngestReceivedMessage message, Duration visibilityTimeout) {
        MetricIngestReceivedMessage requiredMessage = Objects.requireNonNull(message, "message must not be null");
        Duration requiredVisibilityTimeout = Objects.requireNonNull(
                visibilityTimeout,
                "visibilityTimeout must not be null");
        try {
            sqsClient().changeMessageVisibility(ChangeMessageVisibilityRequest.builder()
                    .queueUrl(sourceQueueUrl())
                    .receiptHandle(requiredMessage.receiptHandle())
                    .visibilityTimeout(toSeconds(requiredVisibilityTimeout))
                    .build());
        } catch (SdkException exception) {
            throw new MetricIngestQueueConsumerException("sqs_change_visibility_failed", exception);
        } catch (RuntimeException exception) {
            throw new MetricIngestQueueConsumerException("sqs_change_visibility_failed", exception);
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

    private MetricIngestReceivedMessage toReceivedMessage(Message message) {
        return MetricIngestReceivedMessage.fromBodyJson(
                requireText(message.messageId(), "messageId"),
                requireText(message.receiptHandle(), "receiptHandle"),
                Objects.requireNonNullElse(message.body(), ""),
                toStringAttributes(message.messageAttributes()),
                receiveCount(message.attributes()));
    }

    private String sourceQueueUrl() {
        String queueUrl = properties.getSqs().getQueueUrl();
        if (queueUrl == null || queueUrl.isBlank()) {
            throw new MetricIngestQueueConsumerException("source_queue_url_missing", null);
        }
        return queueUrl;
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

    private static Map<String, String> toStringAttributes(Map<String, MessageAttributeValue> messageAttributes) {
        Map<String, String> values = new LinkedHashMap<>();
        if (messageAttributes == null) {
            return values;
        }
        for (Map.Entry<String, MessageAttributeValue> entry : messageAttributes.entrySet()) {
            MessageAttributeValue value = entry.getValue();
            if (value != null && value.stringValue() != null) {
                values.put(entry.getKey(), value.stringValue());
            }
        }
        return values;
    }

    private static int receiveCount(Map<MessageSystemAttributeName, String> attributes) {
        if (attributes == null) {
            return 1;
        }
        String value = attributes.get(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT);
        if (value == null || value.isBlank()) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private static int toSeconds(Duration duration) {
        long seconds = duration.toSeconds();
        if (seconds > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.max(1, seconds);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
