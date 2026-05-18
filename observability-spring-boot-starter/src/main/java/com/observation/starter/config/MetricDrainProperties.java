package com.observation.starter.config;

import com.observation.starter.model.ingest.IngestEnvelopeIdentity;
import com.observation.starter.queue.MetricQueueDropPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;

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

    private int queueCapacity = 1024;
    private MetricQueueDropPolicy dropPolicy = MetricQueueDropPolicy.DROP_NEWEST;
    private String projectId = DEFAULT_PROJECT_ID;
    private String applicationName = DEFAULT_APPLICATION_NAME;
    private String environment = DEFAULT_ENVIRONMENT;
    private String instance = DEFAULT_INSTANCE;

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

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
