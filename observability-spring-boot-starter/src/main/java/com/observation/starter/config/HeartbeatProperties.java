package com.observation.starter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/**
 * starter heartbeat 전송 경로의 portal 연결과 주기 설정을 담는다.
 */
@ConfigurationProperties(prefix = HeartbeatProperties.PREFIX)
public class HeartbeatProperties {

    public static final String PREFIX = "observation.heartbeat";
    static final String DEFAULT_STARTER_VERSION = "0.1.0";
    private static final String HEARTBEAT_PATH = "/api/ingest/v1/heartbeat";

    private boolean enabled = true;
    private String portalBaseUrl;
    private String projectKey;
    private String starterVersion = DEFAULT_STARTER_VERSION;
    private int intervalSeconds = 30;
    private int timeoutMillis = 1000;

    /**
     * heartbeat background sender 사용 여부를 반환한다.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Spring 설정 바인딩에서 받은 enabled flag를 저장한다.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * portal base URL 설정값을 반환한다.
     */
    public String getPortalBaseUrl() {
        return portalBaseUrl;
    }

    /**
     * heartbeat API가 붙을 portal base URL을 저장한다.
     */
    public void setPortalBaseUrl(String portalBaseUrl) {
        this.portalBaseUrl = trimToNull(portalBaseUrl);
    }

    /**
     * raw project key 설정값을 반환한다. 로그나 response에 노출하면 안 된다.
     */
    public String getProjectKey() {
        return projectKey;
    }

    /**
     * portal 인증 header에 사용할 raw project key를 저장한다.
     */
    public void setProjectKey(String projectKey) {
        this.projectKey = trimToNull(projectKey);
    }

    /**
     * heartbeat payload에 담을 starter version을 반환한다.
     */
    public String getStarterVersion() {
        return starterVersion;
    }

    /**
     * starter version metadata를 저장한다.
     */
    public void setStarterVersion(String starterVersion) {
        this.starterVersion = requireText(starterVersion, "starter version");
    }

    /**
     * heartbeat 전송 interval seconds를 반환한다.
     */
    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    /**
     * background sender 전송 주기를 양수 seconds로 검증한다.
     */
    public void setIntervalSeconds(int intervalSeconds) {
        if (intervalSeconds <= 0) {
            throw new IllegalArgumentException("heartbeat interval seconds must be positive");
        }
        this.intervalSeconds = intervalSeconds;
    }

    /**
     * heartbeat HTTP timeout milliseconds를 반환한다.
     */
    public int getTimeoutMillis() {
        return timeoutMillis;
    }

    /**
     * HTTP connect/request timeout을 양수 milliseconds로 검증한다.
     */
    public void setTimeoutMillis(int timeoutMillis) {
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("heartbeat timeout millis must be positive");
        }
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * 실제 portal HTTP client를 만들 수 있는 최소 연결 설정이 있는지 확인한다.
     */
    public boolean hasPortalConnectionSettings() {
        return portalBaseUrl != null && projectKey != null;
    }

    /**
     * heartbeat request timeout 값으로 변환한다.
     */
    public Duration timeout() {
        return Duration.ofMillis(timeoutMillis);
    }

    /**
     * sender loop interval 값으로 변환한다.
     */
    public Duration interval() {
        return Duration.ofSeconds(intervalSeconds);
    }

    /**
     * portal base URL에 heartbeat API path를 붙인 URI를 반환한다.
     */
    public URI heartbeatUri() {
        if (portalBaseUrl == null) {
            throw new IllegalStateException(PREFIX + ".portal-base-url must be configured");
        }
        String base = portalBaseUrl.endsWith("/")
                ? portalBaseUrl.substring(0, portalBaseUrl.length() - 1)
                : portalBaseUrl;
        return URI.create(base + HEARTBEAT_PATH);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
