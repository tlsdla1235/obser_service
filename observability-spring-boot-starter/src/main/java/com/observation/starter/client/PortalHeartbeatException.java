package com.observation.starter.client;

import java.util.Objects;

/**
 * portal heartbeat 전송 실패를 raw project key 없이 category와 함께 전달하는 starter 전용 예외다.
 */
public final class PortalHeartbeatException extends RuntimeException {

    private final HeartbeatFailureCategory failureCategory;
    private final Integer statusCode;

    /**
     * 이미 분류된 heartbeat 실패를 감싸 service/sender가 fail-open으로 해석할 수 있게 한다.
     */
    private PortalHeartbeatException(
            HeartbeatFailureCategory failureCategory,
            Integer statusCode,
            String message,
            Throwable cause) {
        super(Objects.requireNonNull(message, "message must not be null"), cause);
        this.failureCategory = Objects.requireNonNull(failureCategory, "failureCategory must not be null");
        this.statusCode = statusCode;
    }

    /**
     * HTTP non-success status를 category가 있는 예외로 변환한다.
     */
    public static PortalHeartbeatException forStatus(int statusCode) {
        HeartbeatFailureCategory category = HeartbeatFailureClassifier.classifyStatus(statusCode);
        return new PortalHeartbeatException(
                category,
                statusCode,
                "portal heartbeat failed category=" + category.logValue() + " status=" + statusCode,
                null);
    }

    /**
     * I/O 또는 runtime 전송 실패를 category가 있는 예외로 변환한다.
     */
    public static PortalHeartbeatException forTransportFailure(Throwable cause) {
        HeartbeatFailureCategory category = HeartbeatFailureClassifier.classify(cause);
        return new PortalHeartbeatException(
                category,
                null,
                "portal heartbeat request failed category=" + category.logValue(),
                sanitizedTransportCause(cause));
    }

    /**
     * sender logging과 retry/backoff 정책에서 사용할 실패 category를 반환한다.
     */
    public HeartbeatFailureCategory failureCategory() {
        return failureCategory;
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
     * 원본 transport cause의 message/stack trace에 raw project key가 섞여도 외부 exception chain으로
     * 전파하지 않도록 안전한 자리표시자만 연결한다.
     */
    private static final class SanitizedTransportCause extends RuntimeException {

        private SanitizedTransportCause() {
            super("portal heartbeat transport cause sanitized", null, false, false);
        }
    }
}
