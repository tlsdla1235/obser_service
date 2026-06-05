package com.observation.portal.domain.ingest.queue;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetricIngestQueueWorkerBoundaryTest {

    @Test
    void deletesOnlyInsertedDuplicateAndDlqPublishedMessages() {
        IngestBufferProperties properties = fakeWorkerProperties();
        RecordingConsumer consumer = new RecordingConsumer(List.of(
                source("inserted"),
                source("duplicate"),
                source("malformed"),
                source("transient")));
        RecordingDlqPublisher dlqPublisher = new RecordingDlqPublisher(false);
        MetricIngestQueueProcessor processor = mock(MetricIngestQueueProcessor.class);
        when(processor.processBatch(any())).thenReturn(List.of(
                MetricIngestQueueProcessResult.inserted(),
                MetricIngestQueueProcessResult.duplicateNoop(),
                MetricIngestQueueProcessResult.applicationDlq(
                        dlqEnvelope("source-malformed", "malformed", "invalid_json")),
                MetricIngestQueueProcessResult.transientFailure("database_transient_failure")));
        MetricIngestQueueWorker worker = new MetricIngestQueueWorker(properties, consumer, dlqPublisher, processor);

        worker.pollOnce();

        assertThat(consumer.deletedMessageIds()).containsExactly("source-inserted", "source-duplicate", "source-malformed");
        assertThat(dlqPublisher.envelopes()).hasSize(1);
    }

    @Test
    void dlqSendFailureLeavesSourceMessageForNativeRedrive() {
        IngestBufferProperties properties = fakeWorkerProperties();
        MetricIngestReceivedMessage source = source("conflict");
        RecordingConsumer consumer = new RecordingConsumer(List.of(source));
        RecordingDlqPublisher dlqPublisher = new RecordingDlqPublisher(true);
        MetricIngestQueueProcessor processor = mock(MetricIngestQueueProcessor.class);
        when(processor.processBatch(List.of(source))).thenReturn(List.of(MetricIngestQueueProcessResult.applicationDlq(
                dlqEnvelope("source-conflict", "conflict", "idempotency_payload_conflict"))));
        MetricIngestQueueWorker worker = new MetricIngestQueueWorker(properties, consumer, dlqPublisher, processor);

        worker.pollOnce();

        assertThat(consumer.deletedMessageIds()).isEmpty();
        assertThat(dlqPublisher.envelopes()).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void receivePageIsSplitByConfiguredMaxBatchSize() {
        IngestBufferProperties properties = fakeWorkerProperties();
        properties.getWorker().setMaxBatchSize(2);
        RecordingConsumer consumer = new RecordingConsumer(List.of(
                source("one"),
                source("two"),
                source("three"),
                source("four"),
                source("five")));
        MetricIngestQueueProcessor processor = mock(MetricIngestQueueProcessor.class);
        when(processor.processBatch(anyList())).thenAnswer(invocation -> {
            List<MetricIngestReceivedMessage> messages = invocation.getArgument(0);
            return messages.stream()
                    .map(ignored -> MetricIngestQueueProcessResult.inserted())
                    .toList();
        });
        MetricIngestQueueWorker worker = new MetricIngestQueueWorker(
                properties,
                consumer,
                new RecordingDlqPublisher(false),
                processor);

        worker.pollOnce();

        ArgumentCaptor<List<MetricIngestReceivedMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(processor, times(3)).processBatch(captor.capture());
        assertThat(captor.getAllValues()).extracting(List::size).containsExactly(2, 2, 1);
        assertThat(consumer.deletedMessageIds()).containsExactly(
                "source-one",
                "source-two",
                "source-three",
                "source-four",
                "source-five");
    }

    @Test
    void sqsModeWithoutDlqUrlFailsClosedWithoutReceive() {
        IngestBufferProperties properties = new IngestBufferProperties();
        properties.setMode(IngestBufferMode.SQS);
        properties.getSqs().setQueueUrl("https://sqs.example.test/source");
        properties.getWorker().setEnabled(true);
        properties.getWorker().setDlqUrl("");
        RecordingConsumer consumer = new RecordingConsumer(List.of(source("ignored")));
        MetricIngestQueueWorker worker = new MetricIngestQueueWorker(
                properties,
                consumer,
                new RecordingDlqPublisher(false),
                mock(MetricIngestQueueProcessor.class));

        worker.pollOnce();

        assertThat(consumer.receiveCount()).isZero();
        assertThat(consumer.deletedMessageIds()).isEmpty();
    }

    private static IngestBufferProperties fakeWorkerProperties() {
        IngestBufferProperties properties = new IngestBufferProperties();
        properties.setMode(IngestBufferMode.FAKE);
        properties.getWorker().setEnabled(true);
        properties.getWorker().setDlqUrl("fake-dlq");
        return properties;
    }

    private static MetricIngestReceivedMessage source(String suffix) {
        return MetricIngestReceivedMessage.fromBodyJson(
                "source-" + suffix,
                "receipt-" + suffix,
                "{}",
                java.util.Map.of(),
                1);
    }

    private static MetricIngestDlqEnvelope dlqEnvelope(String sourceMessageId, String category, String code) {
        return new MetricIngestDlqEnvelope(
                "1",
                category,
                code,
                sourceMessageId,
                1,
                java.time.OffsetDateTime.parse("2026-05-08T01:00:35Z"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "sent_to_application_dlq");
    }

    private static final class RecordingConsumer implements MetricIngestQueueConsumer {

        private final List<MetricIngestReceivedMessage> messages;
        private final List<String> deletedMessageIds = new ArrayList<>();
        private int receiveCount;

        private RecordingConsumer(List<MetricIngestReceivedMessage> messages) {
            this.messages = List.copyOf(messages);
        }

        @Override
        public List<MetricIngestReceivedMessage> receive() {
            receiveCount++;
            return messages;
        }

        @Override
        public void delete(MetricIngestReceivedMessage message) {
            deletedMessageIds.add(message.messageId());
        }

        @Override
        public void changeVisibility(MetricIngestReceivedMessage message, Duration visibilityTimeout) {
        }

        private List<String> deletedMessageIds() {
            return List.copyOf(deletedMessageIds);
        }

        private int receiveCount() {
            return receiveCount;
        }
    }

    private static final class RecordingDlqPublisher implements MetricIngestDlqPublisher {

        private final boolean fail;
        private final List<MetricIngestDlqEnvelope> envelopes = new ArrayList<>();

        private RecordingDlqPublisher(boolean fail) {
            this.fail = fail;
        }

        @Override
        public void publish(MetricIngestDlqEnvelope envelope) {
            envelopes.add(envelope);
            if (fail) {
                throw new MetricIngestDlqPublishException("application_dlq_send_failed", null);
            }
        }

        private List<MetricIngestDlqEnvelope> envelopes() {
            return List.copyOf(envelopes);
        }
    }
}
