package com.observation.portal.domain.ingest.queue;

import java.util.Objects;

/**
 * application-level DLQ 전송 실패를 DLQ URL이나 AWS detail 없이 worker에 전달하는 예외다.
 */
public class MetricIngestDlqPublishException extends RuntimeException {

    private final String failureType;

    public MetricIngestDlqPublishException(String failureType, Throwable cause) {
        super(Objects.requireNonNull(failureType, "failureType must not be null"), cause);
        this.failureType = failureType;
    }

    /**
     * source delete 여부 판단과 운영 metric 후보에 사용할 sanitized category다.
     */
    public String failureType() {
        return failureType;
    }
}
