package com.observation.starter.client;

/**
 * heartbeat 전송 실패를 로그와 retry/backoff 정책에서 사용할 안정적인 범주로 표현한다.
 */
public enum HeartbeatFailureCategory {

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

    HeartbeatFailureCategory(String logValue) {
        this.logValue = logValue;
    }

    /**
     * 로그와 test assertion에서 사용할 계약상 category 문자열을 반환한다.
     */
    public String logValue() {
        return logValue;
    }
}
