package com.observation.portal.domain.ingest.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.observation.portal.domain.ingest.service.IngestPayloadHasher;
import com.observation.portal.domain.ingest.service.PortalIngestValidationFixture;
import com.observation.portal.domain.ingest.service.ValidatedIngestCandidate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SqsMetricIngestQueuePublisherTest {

    @Test
    void lazySupplierCreatesClientOnceAndReusesItAcrossEnqueues() throws Exception {
        SqsClient sqsClient = mock(SqsClient.class);
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(SendMessageResponse.builder().messageId("aws-message-1").build());
        AtomicInteger clientBuildCount = new AtomicInteger();
        SqsMetricIngestQueuePublisher publisher = new SqsMetricIngestQueuePublisher(
                () -> {
                    clientBuildCount.incrementAndGet();
                    return sqsClient;
                },
                sqsProperties("https://sqs.us-east-1.amazonaws.com/123/ingest"));
        MetricIngestQueueMessage message = message();

        publisher.enqueue(message);
        publisher.enqueue(message);

        assertThat(clientBuildCount.get()).isEqualTo(1);
        verify(sqsClient, times(2)).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void closeClosesLazyCreatedClient() throws Exception {
        SqsClient sqsClient = mock(SqsClient.class);
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(SendMessageResponse.builder().messageId("aws-message-1").build());
        SqsMetricIngestQueuePublisher publisher = new SqsMetricIngestQueuePublisher(
                () -> sqsClient,
                sqsProperties("https://sqs.us-east-1.amazonaws.com/123/ingest"));

        publisher.enqueue(message());
        publisher.close();

        verify(sqsClient).close();
    }

    @Test
    void sendsMessageBodyAndAllowListAttributesToConfiguredQueueUrl() throws Exception {
        SqsClient sqsClient = mock(SqsClient.class);
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(SendMessageResponse.builder().messageId("aws-message-1").build());
        IngestBufferProperties properties = sqsProperties("https://sqs.us-east-1.amazonaws.com/123/ingest");
        SqsMetricIngestQueuePublisher publisher = new SqsMetricIngestQueuePublisher(sqsClient, properties);
        MetricIngestQueueMessage message = message();

        MetricIngestEnqueueReceipt receipt = publisher.enqueue(message);

        ArgumentCaptor<SendMessageRequest> requestCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient).sendMessage(requestCaptor.capture());
        SendMessageRequest request = requestCaptor.getValue();
        assertThat(request.queueUrl()).isEqualTo("https://sqs.us-east-1.amazonaws.com/123/ingest");
        assertThat(request.messageBody()).isEqualTo(message.bodyJson());
        assertThat(request.messageAttributes()).containsOnlyKeys(
                "messageVersion",
                "projectId",
                "schemaVersion",
                "bucketStartUtc",
                "bucketEndUtc",
                "applicationName",
                "environment",
                "instanceName");
        assertThat(request.messageAttributes().get("messageVersion").stringValue()).isEqualTo("1");
        assertThat(receipt.messageId()).isEqualTo("aws-message-1");
        assertThat(receipt.enqueuedAt()).isEqualTo(message.body().enqueuedAt());
    }

    @Test
    void mapsSdkFailureToSanitizedPublishException() throws Exception {
        SqsClient sqsClient = mock(SqsClient.class);
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenThrow(SqsException.builder()
                        .message("https://sqs.us-east-1.amazonaws.com/123/secret")
                        .build());
        SqsMetricIngestQueuePublisher publisher =
                new SqsMetricIngestQueuePublisher(sqsClient, sqsProperties("https://sqs.us-east-1.amazonaws.com/123/secret"));

        assertThatThrownBy(() -> publisher.enqueue(message()))
                .isInstanceOf(MetricIngestQueuePublishException.class)
                .hasMessageContaining("sqs_send_failed")
                .hasMessageNotContaining("secret");
    }

    @Test
    void mapsLazyClientCreationFailureToSanitizedPublishException() throws Exception {
        SqsMetricIngestQueuePublisher publisher = new SqsMetricIngestQueuePublisher(
                () -> {
                    throw new IllegalStateException("https://sqs.us-east-1.amazonaws.com/123/secret");
                },
                sqsProperties("https://sqs.us-east-1.amazonaws.com/123/secret"));

        assertThatThrownBy(() -> publisher.enqueue(message()))
                .isInstanceOf(MetricIngestQueuePublishException.class)
                .hasMessageContaining("sqs_send_failed")
                .hasMessageNotContaining("secret");
    }

    private static MetricIngestQueueMessage message() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new MetricIngestQueueMessageFactory(objectMapper, new IngestPayloadHasher(objectMapper)).build(
                new ValidatedIngestCandidate(
                        PortalIngestValidationFixture.VERIFIED_PROJECT,
                        PortalIngestValidationFixture.IDEMPOTENCY_KEY,
                        PortalIngestValidationFixture.goldenRequest()),
                OffsetDateTime.parse("2026-05-08T01:00:31Z"),
                OffsetDateTime.parse("2026-05-08T01:00:31.120Z"));
    }

    private static IngestBufferProperties sqsProperties(String queueUrl) {
        IngestBufferProperties properties = new IngestBufferProperties();
        properties.setMode(IngestBufferMode.SQS);
        properties.setMessageSizeLimitBytes(1_048_576L);
        properties.setPublisherTimeout(Duration.ofSeconds(3));
        properties.getSqs().setQueueUrl(queueUrl);
        return properties;
    }
}
