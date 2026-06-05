package com.observation.portal.domain.ingest.queue;

/**
 * malformed/conflict로 분류된 source message metadata를 application-level DLQ로 보내는 경계다.
 */
@FunctionalInterface
public interface MetricIngestDlqPublisher {

    /**
     * raw source body를 제외한 sanitized DLQ envelope만 전송한다.
     */
    void publish(MetricIngestDlqEnvelope envelope);
}
