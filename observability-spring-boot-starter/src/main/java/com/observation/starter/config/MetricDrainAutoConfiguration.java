package com.observation.starter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.starter.client.PortalMetricBucketClient;
import com.observation.starter.client.http.JdkPortalMetricBucketClient;
import com.observation.starter.queue.BoundedMetricQueue;
import com.observation.starter.service.IngestEnvelopeBuilderService;
import com.observation.starter.service.LowCardinalityHttpObservationGuard;
import com.observation.starter.service.MetricBucketFlushWorker;
import com.observation.starter.service.MetricBucketRollupService;
import com.observation.starter.service.StarterMetricIngestService;
import com.observation.starter.spring.StarterMetricDrainScheduler;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Instant;

/**
 * Story 2.4의 idle drain tick을 Spring runtime에 연결하는 starter auto-configuration이다.
 *
 * <p>clean starter runtime에서도 local rollup, bounded queue, ingest service, scheduled tick을 함께
 * 구성한다. 포털 client bean이 있을 때만 background flush worker를 추가로 기동한다.</p>
 */
//RouteAttributionConfiguration를 먼저 적용 시킨 뒤 설정 적용
@AutoConfiguration(after = RouteAttributionAutoConfiguration.class)
/*
    예시
    observation:
        metric-flush:
            queue-capacity: 2048
            drop-policy: DROP_NEWEST
            project-id: my-project

 */
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
     * sealed bucket snapshot을 ingest envelope payload와 idempotency key 후보로 바꾸는 builder를 등록한다.
     */
    @Bean
    @ConditionalOnMissingBean
    public IngestEnvelopeBuilderService ingestEnvelopeBuilderService(
            MetricDrainProperties properties,
            Environment environment) {
        return new IngestEnvelopeBuilderService(properties.ingestEnvelopeIdentity(environment));
    }

    /**
     * metric flush 연결 설정이 일부만 들어온 경우 no-op처럼 지나가지 않고 local configuration error로 닫는다.
     */
    @Bean
    @ConditionalOnMissingBean(name = "metricFlushPortalConnectionSettingsGuard")
    public SmartInitializingSingleton metricFlushPortalConnectionSettingsGuard(
            MetricDrainProperties properties,
            Environment environment) {
        return () -> properties.validatePortalConnectionSettings(environment);
    }

    /**
     * metric flush 전용 portal 연결 설정이 있을 때만 default JDK bucket ingest client를 등록한다.
     *
     * <p>connection 설정이 없으면 no-op client를 만들지 않아 worker가 잘못 시작되지 않게 한다. 사용자가
     * custom {@link PortalMetricBucketClient} bean을 제공하면 그 bean이 항상 우선한다.</p>
     */
    @Bean
    @ConditionalOnMissingBean(PortalMetricBucketClient.class)
    @ConditionalOnProperty(
            prefix = MetricDrainProperties.PREFIX,
            name = {"portal-base-url", "project-key"})
    public PortalMetricBucketClient portalMetricBucketClient(MetricDrainProperties properties) {
        return new JdkPortalMetricBucketClient(
                properties.bucketIngestUri(),
                properties.getProjectKey(),
                properties.timeout(),
                new ObjectMapper());
    }

    /**
     * 포털 client가 제공된 runtime에서 queue -> envelope builder -> client flush 경계를 자동으로 닫는다.
     *
     * <p>worker가 실제 전송 경로를 열기 직전에 generic local default identity가 남아 있지 않은지 검증한다.</p>
     */
    @Bean(initMethod = "start", destroyMethod = "close")
    @ConditionalOnMissingBean
    @ConditionalOnBean(PortalMetricBucketClient.class)
    public MetricBucketFlushWorker metricBucketFlushWorker(
            BoundedMetricQueue queue,
            IngestEnvelopeBuilderService envelopeBuilder,
            PortalMetricBucketClient client,
            MetricDrainProperties properties,
            Environment environment) {
        properties.validatePortalFlushIdentity(environment);
        return new MetricBucketFlushWorker(queue, envelopeBuilder, client);
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
