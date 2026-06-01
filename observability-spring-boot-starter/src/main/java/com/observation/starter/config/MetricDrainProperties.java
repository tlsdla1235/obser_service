package com.observation.starter.config;

import com.observation.starter.model.ingest.IngestEnvelopeIdentity;
import com.observation.starter.queue.MetricQueueDropPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

/**
 * Story 2.4 runtime drain/flush queue에 필요한 starter 설정값을 담는다.
 *
 * <p>bounded queue는 host request path 보호가 목적이므로 기본값도 finite capacity와 명시적 drop
 * policy를 사용한다.</p>
 */
@ConfigurationProperties(prefix = MetricDrainProperties.PREFIX)
public class MetricDrainProperties {

    public static final String PREFIX = "observation.metric-flush";
    static final String DEFAULT_PROJECT_ID = "local-project";
    static final String DEFAULT_APPLICATION_NAME = "application";
    static final String DEFAULT_ENVIRONMENT = "default";
    static final String DEFAULT_INSTANCE = "local-instance";
    private static final String BUCKET_INGEST_PATH = "/api/ingest/v1/buckets";

    private int queueCapacity = 1024;
    private MetricQueueDropPolicy dropPolicy = MetricQueueDropPolicy.DROP_NEWEST;
    private String projectId = DEFAULT_PROJECT_ID;
    private String applicationName = DEFAULT_APPLICATION_NAME;
    private String environment = DEFAULT_ENVIRONMENT;
    private String instance = DEFAULT_INSTANCE;
    private String portalBaseUrl;
    private String projectKey;
    private int timeoutMillis = 1000;

    /**
     * runtime bounded queue capacity를 반환한다.
     */
    public int getQueueCapacity() {
        return queueCapacity;
    }

    /**
     * Spring 설정 바인딩에서 받은 queue capacity가 finite positive value인지 검증한다.
     */
    public void setQueueCapacity(int queueCapacity) {
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("metric flush queue capacity must be positive");
        }
        this.queueCapacity = queueCapacity;
    }

    /**
     * queue full 시 적용할 overflow drop policy를 반환한다.
     */
    public MetricQueueDropPolicy getDropPolicy() {
        return dropPolicy;
    }

    /**
     * Spring 설정 바인딩에서 받은 overflow drop policy를 저장한다.
     */
    public void setDropPolicy(MetricQueueDropPolicy dropPolicy) {
        if (dropPolicy == null) {
            throw new IllegalArgumentException("metric flush drop policy must not be null");
        }
        this.dropPolicy = dropPolicy;
    }

    /**
     * Idempotency-Key prefix에 사용할 starter local project identity를 반환한다.
     */
    public String getProjectId() {
        return projectId;
    }

    /**
     * Spring 설정 바인딩에서 받은 project identity를 검증한다.
     */
    public void setProjectId(String projectId) {
        this.projectId = requireText(projectId, "metric flush project id");
    }

    /**
     * envelope application.name에 사용할 starter local application name을 반환한다.
     */
    public String getApplicationName() {
        return applicationName;
    }

    /**
     * Spring 설정 바인딩에서 받은 application name을 검증한다.
     */
    public void setApplicationName(String applicationName) {
        this.applicationName = requireText(applicationName, "metric flush application name");
    }

    /**
     * envelope application.environment에 사용할 starter local environment를 반환한다.
     */
    public String getEnvironment() {
        return environment;
    }

    /**
     * Spring 설정 바인딩에서 받은 environment를 검증한다.
     */
    public void setEnvironment(String environment) {
        this.environment = requireText(environment, "metric flush environment");
    }

    /**
     * envelope application.instance에 사용할 starter local instance identity를 반환한다.
     */
    public String getInstance() {
        return instance;
    }

    /**
     * Spring 설정 바인딩에서 받은 instance identity를 검증한다.
     */
    public void setInstance(String instance) {
        this.instance = requireText(instance, "metric flush instance");
    }

    /**
     * bucket ingest API가 붙을 portal base URL 설정값을 반환한다.
     */
    public String getPortalBaseUrl() {
        return portalBaseUrl;
    }

    /**
     * metric bucket flush 전용 portal base URL을 저장한다. Heartbeat 설정으로 fallback하지 않는다.
     */
    public void setPortalBaseUrl(String portalBaseUrl) {
        this.portalBaseUrl = trimToNull(portalBaseUrl);
    }

    /**
     * bucket ingest `X-OBS-Project-Key` header에 사용할 raw project key를 반환한다.
     *
     * <p>{@code project-id}는 Idempotency-Key 구성요소이고, 이 값은 portal 인증용 raw key다.
     * 두 값을 로그, 문서, 테스트에서 혼동하면 안 된다.</p>
     */
    public String getProjectKey() {
        return projectKey;
    }

    /**
     * metric bucket flush 전용 raw project key를 저장한다. 로그나 exception message에 노출하면 안 된다.
     */
    public void setProjectKey(String projectKey) {
        this.projectKey = trimToNull(projectKey);
    }

    /**
     * bucket ingest HTTP connect/request timeout milliseconds를 반환한다.
     */
    public int getTimeoutMillis() {
        return timeoutMillis;
    }

    /**
     * bucket ingest HTTP timeout은 host app 보호를 위해 양수 bounded 값만 허용한다.
     */
    public void setTimeoutMillis(int timeoutMillis) {
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException(PREFIX + ".timeout-millis must be positive");
        }
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * default bucket ingest HTTP client를 만들 수 있는 metric flush 전용 연결 설정이 있는지 확인한다.
     */
    public boolean hasPortalConnectionSettings() {
        return portalBaseUrl != null && projectKey != null;
    }

    /**
     * metric flush 전용 portal 연결 설정이 일부만 들어온 경우 starter가 조용히 비활성화되지 않도록 막는다.
     */
    public void validatePortalConnectionSettings(Environment springEnvironment) {
        Environment requiredEnvironment = java.util.Objects.requireNonNull(
                springEnvironment,
                "springEnvironment must not be null");
        if (!hasAnyPortalConnectionSetting(requiredEnvironment)) {
            return;
        }
        if (portalBaseUrl == null) {
            throw new IllegalStateException(PREFIX + ".portal-base-url must be configured with " + PREFIX + ".project-key");
        }
        if (projectKey == null) {
            throw new IllegalStateException(PREFIX + ".project-key must be configured with " + PREFIX + ".portal-base-url");
        }
        bucketIngestUri();
    }

    /**
     * connect timeout과 request timeout에 함께 사용할 bounded duration으로 변환한다.
     */
    public Duration timeout() {
        return Duration.ofMillis(timeoutMillis);
    }

    /**
     * portal base URL 뒤에 bucket ingest API path를 붙여 최종 endpoint URI를 만든다.
     */
    public URI bucketIngestUri() {
        if (portalBaseUrl == null) {
            throw new IllegalStateException(PREFIX + ".portal-base-url must be configured");
        }
        String base = portalBaseUrl.endsWith("/")
                ? portalBaseUrl.substring(0, portalBaseUrl.length() - 1)
                : portalBaseUrl;
        try {
            return URI.create(base + BUCKET_INGEST_PATH);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(PREFIX + ".portal-base-url must be a valid URI", exception);
        }
    }

    /**
     * builder가 사용할 local identity tuple로 변환한다.
     */
    public IngestEnvelopeIdentity ingestEnvelopeIdentity() {
        return new IngestEnvelopeIdentity(projectId, applicationName, environment, instance);
    }

    /**
     * Spring runtime에서 안정적으로 얻을 수 있는 application/instance 이름을 local default보다 우선 사용한다.
     */
    public IngestEnvelopeIdentity ingestEnvelopeIdentity(Environment springEnvironment) {
        Environment requiredEnvironment = java.util.Objects.requireNonNull(
                springEnvironment,
                "springEnvironment must not be null");
        return new IngestEnvelopeIdentity(
                projectId,
                resolvedApplicationName(requiredEnvironment),
                environment,
                resolvedInstance(requiredEnvironment));
    }

    /**
     * 실제 portal flush worker가 뜰 때 generic local default identity가 전송되지 않도록 막는다.
     */
    public void validatePortalFlushIdentity(Environment springEnvironment) {
        IngestEnvelopeIdentity identity = ingestEnvelopeIdentity(springEnvironment);
        rejectGenericDefault(identity.projectId(), DEFAULT_PROJECT_ID, PREFIX + ".project-id");
        rejectGenericDefault(identity.applicationName(), DEFAULT_APPLICATION_NAME, PREFIX + ".application-name");
        rejectGenericDefault(identity.environment(), DEFAULT_ENVIRONMENT, PREFIX + ".environment");
        rejectGenericDefault(identity.instance(), DEFAULT_INSTANCE, PREFIX + ".instance");
    }

    private String resolvedApplicationName(Environment springEnvironment) {
        if (!DEFAULT_APPLICATION_NAME.equals(applicationName)) {
            return applicationName;
        }
        return firstText(springEnvironment, "spring.application.name").orElse(applicationName);
    }

    private String resolvedInstance(Environment springEnvironment) {
        if (!DEFAULT_INSTANCE.equals(instance)) {
            return instance;
        }
        return firstText(
                springEnvironment,
                "POD_NAME",
                "HOSTNAME",
                "spring.application.instance-id",
                "spring.application.instance_id")
                .orElse(instance);
    }

    private static Optional<String> firstText(Environment springEnvironment, String... names) {
        for (String name : names) {
            String value = springEnvironment.getProperty(name);
            if (value != null && !value.isBlank()) {
                return Optional.of(value.trim());
            }
        }
        return Optional.empty();
    }

    private static void rejectGenericDefault(String value, String defaultValue, String propertyName) {
        if (defaultValue.equals(value)) {
            throw new IllegalStateException(propertyName + " must be configured before portal metric flush starts");
        }
    }

    private boolean hasAnyPortalConnectionSetting(Environment springEnvironment) {
        return portalBaseUrl != null
                || projectKey != null
                || springEnvironment.containsProperty(PREFIX + ".portal-base-url")
                || springEnvironment.containsProperty(PREFIX + ".project-key");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
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
