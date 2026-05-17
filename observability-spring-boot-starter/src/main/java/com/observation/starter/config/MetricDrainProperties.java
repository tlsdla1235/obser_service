package com.observation.starter.config;

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
}
