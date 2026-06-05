package com.observation.portal.domain.ingest.queue;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SQS 없이 queue-mode request path와 message contract를 검증하기 위한 in-memory fake publisher다.
 */
@Component
@ConditionalOnProperty(name = "portal.ingest.buffer.mode", havingValue = "fake")
public class FakeMetricIngestQueuePublisher implements MetricIngestQueuePublisher {

    private final CopyOnWriteArrayList<MetricIngestQueueMessage> enqueuedMessages = new CopyOnWriteArrayList<>();

    /**
     * enqueue 성공으로 간주하고 message snapshot을 보존한다.
     */
    @Override
    public MetricIngestEnqueueReceipt enqueue(MetricIngestQueueMessage message) {
        MetricIngestQueueMessage requiredMessage = Objects.requireNonNull(message, "message must not be null");
        enqueuedMessages.add(requiredMessage);
        return new MetricIngestEnqueueReceipt(
                "fake-" + enqueuedMessages.size(),
                requiredMessage.body().enqueuedAt());
    }

    /**
     * test가 fake queue에 들어간 message body/attribute를 검증할 수 있도록 immutable snapshot을 반환한다.
     */
    public List<MetricIngestQueueMessage> enqueuedMessages() {
        return List.copyOf(enqueuedMessages);
    }

    /**
     * 여러 test request를 같은 fake publisher로 검증할 때 capture buffer를 비운다.
     */
    public void clear() {
        enqueuedMessages.clear();
    }
}
