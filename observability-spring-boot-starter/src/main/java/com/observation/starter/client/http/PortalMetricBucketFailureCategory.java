package com.observation.starter.client.http;

/**
 * bucket ingest 전송 실패를 worker retry/backoff와 테스트에서 안정적으로 다룰 수 있는 범주로 표현한다.
 */
public enum PortalMetricBucketFailureCategory {

    DNS("dns"),
    CONNECT_TIMEOUT("connect_timeout"),
    READ_TIMEOUT("read_timeout"),
    CONNECTION_REFUSED("connection_refused"),
    TLS("tls"),
    SERVER_5XX("server_5xx"),
    UNAUTHORIZED("unauthorized"),
    CLIENT_4XX("client_4xx"),
    UNKNOWN("unknown");

    private final String logValue;

    PortalMetricBucketFailureCategory(String logValue) {
        this.logValue = logValue;
    }

    /**
     * 로그와 assertion에서 사용할 API-safe category 문자열을 반환한다.
     */
    public String logValue() {
        return logValue;
    }
}
