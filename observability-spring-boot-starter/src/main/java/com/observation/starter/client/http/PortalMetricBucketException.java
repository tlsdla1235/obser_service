package com.observation.starter.client.http;

import com.observation.starter.client.PortalMetricBucketFailure;

import java.util.Objects;

/**
 * bucket ingest 전송 실패를 raw project key나 response body 없이 worker에 전달하는 starter 전용 예외다.
 */
public final class PortalMetricBucketException extends RuntimeException implements PortalMetricBucketFailure {

    private final PortalMetricBucketFailureCategory failureCategory;
    private final Integer statusCode;

    /**
     * 이미 분류된 bucket ingest 실패를 retry/backoff가 해석할 수 있는 runtime exception으로 감싼다.
     */
    private PortalMetricBucketException(
            PortalMetricBucketFailureCategory failureCategory,
            Integer statusCode,
            String message,
            Throwable cause) {
        super(Objects.requireNonNull(message, "message must not be null"), cause);
        this.failureCategory = Objects.requireNonNull(failureCategory, "failureCategory must not be null");
        this.statusCode = statusCode;
    }

    /**
     * HTTP non-2xx status를 response body 없이 sanitized exception으로 변환한다.
     */
    public static PortalMetricBucketException forStatus(int statusCode) {
        PortalMetricBucketFailureCategory category = PortalMetricBucketFailureClassifier.classifyStatus(statusCode);
        return new PortalMetricBucketException(
                category,
                statusCode,
                "portal metric bucket ingest failed category=" + category.logValue() + " status=" + statusCode,
                null);
    }

    /**
     * I/O, timeout, interruption 같은 transport 실패를 raw cause message 없이 sanitized exception으로 변환한다.
     */
    public static PortalMetricBucketException forTransportFailure(Throwable cause) {
        PortalMetricBucketFailureCategory category = PortalMetricBucketFailureClassifier.classify(cause);
        return new PortalMetricBucketException(
                category,
                null,
                "portal metric bucket ingest request failed category=" + category.logValue(),
                sanitizedTransportCause(cause));
    }

    /**
     * worker retry/backoff와 test assertion에서 사용할 실패 category를 반환한다.
     */
    public PortalMetricBucketFailureCategory failureCategory() {
        return failureCategory;
    }

    /**
     * worker logging 경계가 HTTP 구현 enum에 의존하지 않고 category 문자열만 사용할 수 있게 한다.
     */
    @Override
    public String failureCategoryLogValue() {
        return failureCategory.logValue();
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
     * 원본 transport cause의 message/stack trace에 raw project key가 섞여도 외부 chain에는 남기지 않는다.
     */
    private static final class SanitizedTransportCause extends RuntimeException {

        private SanitizedTransportCause() {
            super("portal metric bucket ingest transport cause sanitized", null, false, false);
        }
    }
}
