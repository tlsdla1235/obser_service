package com.observation.portal.domain.ingest.queue;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SqsMetricIngestQueueConsumerTest {

    @Test
    void receiveUsesConfiguredQueueRequestBoundsAndAttributes() {
        SqsClient sqsClient = mock(SqsClient.class);
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder()
                        .messages(Message.builder()
                                .messageId("message-1")
                                .receiptHandle("receipt-1")
                                .body("{}")
                                .messageAttributes(Map.of(
                                        "messageVersion",
                                        MessageAttributeValue.builder().dataType("String").stringValue("1").build()))
                                .attributes(Map.of(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT, "3"))
                                .build())
                        .build());
        SqsMetricIngestQueueConsumer consumer = new SqsMetricIngestQueueConsumer(sqsClient, sqsProperties());

        List<MetricIngestReceivedMessage> messages = consumer.receive();

        ArgumentCaptor<ReceiveMessageRequest> requestCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsClient).receiveMessage(requestCaptor.capture());
        ReceiveMessageRequest request = requestCaptor.getValue();
        assertThat(request.queueUrl()).isEqualTo("https://sqs.example.test/source");
        assertThat(request.waitTimeSeconds()).isEqualTo(20);
        assertThat(request.maxNumberOfMessages()).isEqualTo(10);
        assertThat(request.visibilityTimeout()).isEqualTo(60);
        assertThat(request.messageAttributeNames()).containsExactly("All");
        assertThat(request.messageSystemAttributeNames()).contains(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT);
        assertThat(messages).singleElement().satisfies(message -> {
            assertThat(message.messageId()).isEqualTo("message-1");
            assertThat(message.receiptHandle()).isEqualTo("receipt-1");
            assertThat(message.bodyJson()).isEqualTo("{}");
            assertThat(message.attributes()).containsEntry("messageVersion", "1");
            assertThat(message.receiveCount()).isEqualTo(3);
        });
    }

    @Test
    void deleteUsesReceiptHandleAndConfiguredSourceQueue() {
        SqsClient sqsClient = mock(SqsClient.class);
        SqsMetricIngestQueueConsumer consumer = new SqsMetricIngestQueueConsumer(sqsClient, sqsProperties());

        consumer.delete(MetricIngestReceivedMessage.fromBodyJson(
                "message-1",
                "receipt-1",
                "{\"raw\":\"body\"}",
                Map.of(),
                1));

        ArgumentCaptor<DeleteMessageRequest> requestCaptor = ArgumentCaptor.forClass(DeleteMessageRequest.class);
        verify(sqsClient).deleteMessage(requestCaptor.capture());
        assertThat(requestCaptor.getValue().queueUrl()).isEqualTo("https://sqs.example.test/source");
        assertThat(requestCaptor.getValue().receiptHandle()).isEqualTo("receipt-1");
        assertThat(requestCaptor.getValue().toString()).doesNotContain("raw", "body");
    }

    @Test
    void mapsAwsFailuresToSanitizedConsumerException() {
        SqsClient sqsClient = mock(SqsClient.class);
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenThrow(SqsException.builder()
                        .message("https://sqs.example.test/source?credential=secret")
                        .build());
        SqsMetricIngestQueueConsumer consumer = new SqsMetricIngestQueueConsumer(sqsClient, sqsProperties());

        assertThatThrownBy(consumer::receive)
                .isInstanceOf(MetricIngestQueueConsumerException.class)
                .hasMessageContaining("sqs_receive_failed")
                .hasMessageNotContaining("credential=secret");
    }

    private static IngestBufferProperties sqsProperties() {
        IngestBufferProperties properties = new IngestBufferProperties();
        properties.setMode(IngestBufferMode.SQS);
        properties.getSqs().setQueueUrl("https://sqs.example.test/source");
        properties.getWorker().setEnabled(true);
        properties.getWorker().setDlqUrl("https://sqs.example.test/dlq");
        properties.getWorker().setLongPollSeconds(20);
        properties.getWorker().setMaxMessagesPerPoll(10);
        properties.getWorker().setVisibilityTimeout(Duration.ofSeconds(60));
        return properties;
    }
}
