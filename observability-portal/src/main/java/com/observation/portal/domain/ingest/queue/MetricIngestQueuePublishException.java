package com.observation.portal.domain.ingest.queue;

import java.util.Objects;

/**
 * fake/SQS publisher 실패를 raw queue URL이나 AWS detail 없이 service에 전달하는 예외다.
 */
public class MetricIngestQueuePublishException extends RuntimeException {

    private final String failureType;

    public MetricIngestQueuePublishException(String failureType, Throwable cause) {
        super(Objects.requireNonNull(failureType, "failureType must not be null"), cause);
        this.failureType = failureType;
    }

    /**
     * response body에 노출하지 않는 내부 diagnostic category다.
     */
    public String failureType() {
        return failureType;
    }
}
