package com.observation.starter.config;

import com.observation.starter.queue.BoundedMetricQueue;
import com.observation.starter.service.LowCardinalityHttpObservationGuard;
import com.observation.starter.service.MetricBucketRollupService;
import com.observation.starter.service.StarterMetricIngestService;
import com.observation.starter.spring.StarterMetricDrainScheduler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Instant;

/**
 * Story 2.4의 idle drain tick을 Spring runtime에 연결하는 starter auto-configuration이다.
 *
 * <p>clean starter runtime에서도 local rollup, bounded queue, ingest service, scheduled tick을 함께
 * 구성한다. portal client/HTTP transport/envelope serialization은 Story 2.5 이후 경계로 남긴다.</p>
 */
@AutoConfiguration(after = RouteAttributionAutoConfiguration.class)
@EnableConfigurationProperties(MetricDrainProperties.class)
@EnableScheduling
public class MetricDrainAutoConfiguration {

    /**
     * request path와 background worker 사이에서 쓸 finite bounded queue를 등록한다.
     */
    @Bean
    @ConditionalOnMissingBean
    public BoundedMetricQueue boundedMetricQueue(MetricDrainProperties properties) {
        return new BoundedMetricQueue(properties.getQueueCapacity(), properties.getDropPolicy());
    }

    /**
     * Story 2.3 bucket rollup 정책을 runtime bean으로 등록한다.
     */
    @Bean
    @ConditionalOnMissingBean
    public MetricBucketRollupService metricBucketRollupService() {
        return new MetricBucketRollupService();
    }

    /**
     * Micrometer binding이 사용할 local ingest boundary를 runtime bean으로 등록한다.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(LowCardinalityHttpObservationGuard.class)
    public StarterMetricIngestService starterMetricIngestService(
            LowCardinalityHttpObservationGuard httpObservationGuard,
            MetricBucketRollupService rollupService,
            BoundedMetricQueue flushQueue) {
        return new StarterMetricIngestService(httpObservationGuard, rollupService, flushQueue, Instant::now);
    }

    /**
     * 30초마다 {@link StarterMetricIngestService#drainDueBuckets()}를 호출하는 얇은 scheduled adapter를 등록한다.
     */
    @Bean
    @ConditionalOnMissingBean(StarterMetricDrainScheduler.class)
    @ConditionalOnBean(StarterMetricIngestService.class)
    public StarterMetricDrainScheduler starterMetricDrainScheduler(StarterMetricIngestService ingestService) {
        return new StarterMetricDrainScheduler(ingestService);
    }
}
