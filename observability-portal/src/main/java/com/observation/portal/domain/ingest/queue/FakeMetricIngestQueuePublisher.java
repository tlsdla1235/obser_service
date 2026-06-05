package com.observation.portal.domain.ingest.queue;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SQS 없이 queue-mode request path와 message contract를 검증하기 위한 in-memory fake publisher다.
 */
@Component
@ConditionalOnProperty(name = "portal.ingest.buffer.mode", havingValue = "fake")
public class FakeMetricIngestQueuePublisher
        implements MetricIngestQueuePublisher, MetricIngestQueueConsumer, MetricIngestDlqPublisher {

    private final CopyOnWriteArrayList<MetricIngestQueueMessage> enqueuedMessages = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<FakeQueueEntry> queueEntries = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<MetricIngestReceivedMessage> deletedMessages = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<MetricIngestDlqEnvelope> dlqEnvelopes = new CopyOnWriteArrayList<>();

    /**
     * enqueue 성공으로 간주하고 message snapshot을 보존한다.
     */
    @Override
    public MetricIngestEnqueueReceipt enqueue(MetricIngestQueueMessage message) {
        MetricIngestQueueMessage requiredMessage = Objects.requireNonNull(message, "message must not be null");
        enqueuedMessages.add(requiredMessage);
        String messageId = "fake-" + enqueuedMessages.size();
        queueEntries.add(new FakeQueueEntry(messageId, "fake-receipt-" + enqueuedMessages.size(), requiredMessage));
        return new MetricIngestEnqueueReceipt(messageId, requiredMessage.body().enqueuedAt());
    }

    /**
     * test가 fake queue에 들어간 message body/attribute를 검증할 수 있도록 immutable snapshot을 반환한다.
     */
    public List<MetricIngestQueueMessage> enqueuedMessages() {
        return List.copyOf(enqueuedMessages);
    }

    /**
     * delete되지 않은 fake source message를 receiveCount 증가와 함께 반환한다.
     */
    @Override
    public List<MetricIngestReceivedMessage> receive() {
        return queueEntries.stream()
                .filter(entry -> !entry.deleted())
                .map(FakeQueueEntry::receive)
                .toList();
    }

    /**
     * worker가 처리 완료로 판단한 fake source message를 deleted 상태로 표시한다.
     */
    @Override
    public void delete(MetricIngestReceivedMessage message) {
        MetricIngestReceivedMessage requiredMessage = Objects.requireNonNull(message, "message must not be null");
        queueEntries.stream()
                .filter(entry -> entry.messageId().equals(requiredMessage.messageId()))
                .findFirst()
                .ifPresent(FakeQueueEntry::markDeleted);
        deletedMessages.add(requiredMessage);
    }

    /**
     * fake queue smoke에서는 visibility 조정이 필요 없으므로 no-op으로 둔다.
     */
    @Override
    public void changeVisibility(MetricIngestReceivedMessage message, java.time.Duration visibilityTimeout) {
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(visibilityTimeout, "visibilityTimeout must not be null");
    }

    /**
     * application-level DLQ 전송 후보 envelope를 fake store에 보존한다.
     */
    @Override
    public void publish(MetricIngestDlqEnvelope envelope) {
        dlqEnvelopes.add(Objects.requireNonNull(envelope, "envelope must not be null"));
    }

    /**
     * worker가 source delete한 fake message snapshot을 반환한다.
     */
    public List<MetricIngestReceivedMessage> deletedMessages() {
        return List.copyOf(deletedMessages);
    }

    /**
     * 아직 delete되지 않아 다음 receive에서 다시 보일 fake message snapshot을 반환한다.
     */
    public List<MetricIngestReceivedMessage> visibleMessages() {
        return queueEntries.stream()
                .filter(entry -> !entry.deleted())
                .map(FakeQueueEntry::latestReceived)
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * fake application DLQ에 publish된 sanitized envelope snapshot을 반환한다.
     */
    public List<MetricIngestDlqEnvelope> dlqEnvelopes() {
        return List.copyOf(dlqEnvelopes);
    }

    /**
     * 여러 test request를 같은 fake publisher로 검증할 때 capture buffer를 비운다.
     */
    public void clear() {
        enqueuedMessages.clear();
        queueEntries.clear();
        deletedMessages.clear();
        dlqEnvelopes.clear();
    }

    private static final class FakeQueueEntry {

        private final String messageId;
        private final String receiptHandle;
        private final MetricIngestQueueMessage message;
        private volatile boolean deleted;
        private volatile int receiveCount;
        private volatile MetricIngestReceivedMessage latestReceived;

        private FakeQueueEntry(String messageId, String receiptHandle, MetricIngestQueueMessage message) {
            this.messageId = messageId;
            this.receiptHandle = receiptHandle;
            this.message = message;
        }

        private String messageId() {
            return messageId;
        }

        private boolean deleted() {
            return deleted;
        }

        private MetricIngestReceivedMessage receive() {
            receiveCount++;
            latestReceived = MetricIngestReceivedMessage.fromBodyJson(
                    messageId,
                    receiptHandle,
                    message.bodyJson(),
                    MetricIngestReceivedMessage.attributesFrom(message.attributes()),
                    receiveCount);
            return latestReceived;
        }

        private Optional<MetricIngestReceivedMessage> latestReceived() {
            return Optional.ofNullable(latestReceived);
        }

        private void markDeleted() {
            deleted = true;
        }
    }
}
