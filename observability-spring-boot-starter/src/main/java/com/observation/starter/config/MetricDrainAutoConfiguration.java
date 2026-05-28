package com.observation.starter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.starter.client.JdkPortalMetricBucketClient;
import com.observation.starter.client.PortalMetricBucketClient;
import com.observation.starter.queue.BoundedMetricQueue;
import com.observation.starter.service.IngestEnvelopeBuilderService;
import com.observation.starter.service.LowCardinalityHttpObservationGuard;
import com.observation.starter.service.MetricBucketFlushWorker;
import com.observation.starter.service.MetricBucketRollupService;
import com.observation.starter.service.ObservationSampleCollector;
import com.observation.starter.service.StarterMetricIngestService;
import com.observation.starter.spring.StarterMetricDrainScheduler;
import com.observation.starter.spring.observation.MicrometerHttpServerObservationBinder;
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
@EnableConfigurationProperties({MetricDrainProperties.class, HeartbeatProperties.class})
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
     * setup guide의 portal base URL/project key 설정만으로 bucket ingest HTTP client를 등록한다.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = HeartbeatProperties.PREFIX, name = {"portal-base-url", "project-key"})
    public PortalMetricBucketClient portalMetricBucketClient(HeartbeatProperties properties) {
        if (!properties.hasPortalConnectionSettings()) {
            throw new IllegalStateException(
                    HeartbeatProperties.PREFIX + ".portal-base-url and "
                            + HeartbeatProperties.PREFIX + ".project-key must be configured");
        }
        return new JdkPortalMetricBucketClient(
                JdkPortalMetricBucketClient.bucketIngestUri(properties.getPortalBaseUrl()),
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
     * host runtime의 ObservationHandler 목록에 HTTP server observation collector를 연결한다.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ObservationSampleCollector.class)
    public MicrometerHttpServerObservationBinder micrometerHttpServerObservationBinder(
            ObservationSampleCollector collector) {
        return new MicrometerHttpServerObservationBinder(collector);
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
