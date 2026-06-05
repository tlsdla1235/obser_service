package com.observation.portal.domain.ingest.queue;

/**
 * worker가 source message별로 delete/DLQ/no-delete 결정을 내릴 때 사용하는 처리 결과 분류다.
 */
public enum MetricIngestQueueProcessStatus {
    INSERTED,
    DUPLICATE_NOOP,
    APPLICATION_DLQ,
    TRANSIENT_FAILURE
}
