package com.observation.starter.client;

import java.util.Objects;

/**
 * metric bucket ingest 전송 실패를 raw project key 없이 worker boundary로 전달하는 예외다.
 */
public final class PortalMetricBucketException extends RuntimeException {

    private final Integer statusCode;

    private PortalMetricBucketException(Integer statusCode, String message, Throwable cause) {
        super(Objects.requireNonNull(message, "message must not be null"), cause);
        this.statusCode = statusCode;
    }

    /**
     * HTTP non-success status를 raw project key 없는 예외로 변환한다.
     */
    public static PortalMetricBucketException forStatus(int statusCode) {
        return new PortalMetricBucketException(
                statusCode,
                "portal metric bucket ingest failed status=" + statusCode,
                null);
    }

    /**
     * I/O 또는 runtime 전송 실패를 원본 cause message 없이 감싼다.
     */
    public static PortalMetricBucketException forTransportFailure(Throwable cause) {
        return new PortalMetricBucketException(
                null,
                "portal metric bucket ingest request failed",
                sanitizedTransportCause(cause));
    }

    /**
     * HTTP status 기반 실패가 아닐 때는 null을 반환한다.
     */
    public Integer statusCode() {
        return statusCode;
    }

    private static Throwable sanitizedTransportCause(Throwable cause) {
        if (cause == null) {
            return null;
        }
        return new SanitizedTransportCause();
    }

    /**
     * 원본 transport cause의 message/stack trace에 secret이 섞여도 외부 chain에는 노출하지 않는다.
     */
    private static final class SanitizedTransportCause extends RuntimeException {

        private SanitizedTransportCause() {
            super("portal metric bucket transport cause sanitized", null, false, false);
        }
    }
}
