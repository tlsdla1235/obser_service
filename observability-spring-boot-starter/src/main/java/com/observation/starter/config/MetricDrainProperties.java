package com.observation.starter.config;

import com.observation.starter.model.ingest.IngestEnvelopeIdentity;
import com.observation.starter.queue.MetricQueueDropPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Story 2.4 runtime drain/flush queue에 필요한 starter 설정값을 담는다.
 *
 * <p>bounded queue는 host request path 보호가 목적이므로 기본값도 finite capacity와 명시적 drop
 * policy를 사용한다.</p>
 */
@ConfigurationProperties(prefix = MetricDrainProperties.PREFIX)
public class MetricDrainProperties {

    public static final String PREFIX = "observation.metric-flush";

    private int queueCapacity = 1024;
    private MetricQueueDropPolicy dropPolicy = MetricQueueDropPolicy.DROP_NEWEST;
    private String projectId = "local-project";
    private String applicationName = "application";
    private String environment = "default";
    private String instance = "local-instance";

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

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
