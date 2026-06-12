package com.observation.starter.config;

import com.observation.starter.service.HikariDatasourcePoolMetricSampler;
import com.observation.starter.service.JdkJvmMetricSampler;
import com.observation.starter.service.ObservationSampleCollector;
import com.observation.starter.service.StarterResourceMetricSampler;
import com.observation.starter.spring.StarterResourceMetricSamplerScheduler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

/**
 * starter resource metric sampler를 Spring runtime에 연결하는 auto-configuration이다.
 *
 * <p>JVM 샘플은 기본으로 수집하고, datasource pool 샘플은 관측 가능한 DataSource/Hikari pool이 있을 때만 기록한다.</p>
 */
@AutoConfiguration(after = MetricDrainAutoConfiguration.class)
@EnableScheduling
public class ResourceMetricSamplerAutoConfiguration {

    /**
     * JDK MXBean 기반 JVM CPU/heap sampler를 등록한다.
     */
    @Bean
    @ConditionalOnMissingBean
    public JdkJvmMetricSampler jdkJvmMetricSampler() {
        return new JdkJvmMetricSampler();
    }

    /**
     * runtime DataSource가 없거나 Hikari pool이 준비되지 않은 경우 빈 샘플을 반환하는 datasource sampler를 등록한다.
     */
    @Bean
    @ConditionalOnMissingBean
    public HikariDatasourcePoolMetricSampler hikariDatasourcePoolMetricSampler(
            ObjectProvider<DataSource> dataSources) {
        return new HikariDatasourcePoolMetricSampler(() -> dataSources.orderedStream().toList());
    }

    /**
     * sampler가 만든 resource 샘플을 starter local ingest boundary에 기록한다.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ObservationSampleCollector.class)
    public StarterResourceMetricSampler starterResourceMetricSampler(
            ObservationSampleCollector collector,
            JdkJvmMetricSampler jvmMetricSampler,
            HikariDatasourcePoolMetricSampler datasourcePoolMetricSampler) {
        return new StarterResourceMetricSampler(collector, jvmMetricSampler, datasourcePoolMetricSampler);
    }

    /**
     * 30초마다 resource sampler를 실행하는 scheduled adapter를 등록한다.
     */
    @Bean
    @ConditionalOnMissingBean(StarterResourceMetricSamplerScheduler.class)
    @ConditionalOnBean(StarterResourceMetricSampler.class)
    public StarterResourceMetricSamplerScheduler starterResourceMetricSamplerScheduler(
            StarterResourceMetricSampler resourceMetricSampler) {
        return new StarterResourceMetricSamplerScheduler(resourceMetricSampler);
    }
}
