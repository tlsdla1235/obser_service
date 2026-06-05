package com.observation.portal.domain.ingest.queue;

import java.util.Objects;

/**
 * payload hash, queue body 생성, JSON 직렬화 실패를 service의 sanitized 503 path로 전달하는 예외다.
 */
public class MetricIngestMessageBuildException extends RuntimeException {

    private final String failureType;

    public MetricIngestMessageBuildException(String failureType, Throwable cause) {
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
