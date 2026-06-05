package com.observation.portal.domain.ingest.queue;

import java.util.Objects;

/**
 * SQS receive/delete/change-visibility 실패를 queue URL이나 AWS detail 없이 worker에 전달하는 예외다.
 */
public class MetricIngestQueueConsumerException extends RuntimeException {

    private final String failureType;

    public MetricIngestQueueConsumerException(String failureType, Throwable cause) {
        super(Objects.requireNonNull(failureType, "failureType must not be null"), cause);
        this.failureType = failureType;
    }

    /**
     * log/metric 후보로 사용할 sanitized failure category를 반환한다.
     */
    public String failureType() {
        return failureType;
    }
}
