package com.observation.starter.spring;

import com.observation.starter.service.StarterResourceMetricSampler;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Objects;

/**
 * starter runtime에서 30초마다 resource metric sampler를 호출하는 scheduled adapter다.
 *
 * <p>sampling과 local ingest 기록은 {@link StarterResourceMetricSampler}에 위임하고 Spring scheduling 경계만 담당한다.</p>
 */
public final class StarterResourceMetricSamplerScheduler {

    public static final long SAMPLE_CADENCE_MILLIS = 30_000L;

    private final StarterResourceMetricSampler resourceMetricSampler;

    /**
     * scheduled tick이 호출할 resource sampler를 주입받는다.
     */
    public StarterResourceMetricSamplerScheduler(StarterResourceMetricSampler resourceMetricSampler) {
        this.resourceMetricSampler = Objects.requireNonNull(
                resourceMetricSampler,
                "resourceMetricSampler must not be null");
    }

    /**
     * starter bucket cadence에 맞춰 JVM/datasource resource 샘플을 수집한다.
     */
    @Scheduled(fixedDelay = SAMPLE_CADENCE_MILLIS, initialDelay = SAMPLE_CADENCE_MILLIS)
    public void sampleResourcesOnTick() {
        resourceMetricSampler.sampleAndRecord();
    }
}
