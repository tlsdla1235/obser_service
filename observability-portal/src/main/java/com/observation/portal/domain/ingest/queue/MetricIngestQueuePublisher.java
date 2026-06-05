package com.observation.portal.domain.ingest.queue;

/**
 * 검증되고 size guard를 통과한 ingest queue message를 실제 queue backend로 보내는 feature-level publisher 경계다.
 */
@FunctionalInterface
public interface MetricIngestQueuePublisher {

    /**
     * enqueue 성공 후에만 receipt를 반환한다. 실패는 sanitized publish exception으로 수렴한다.
     */
    MetricIngestEnqueueReceipt enqueue(MetricIngestQueueMessage message);
}
