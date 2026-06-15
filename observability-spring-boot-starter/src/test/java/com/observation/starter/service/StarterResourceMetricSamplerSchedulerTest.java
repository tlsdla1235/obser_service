package com.observation.starter.service;

import com.observation.starter.model.metric.JvmMetricSample;
import com.observation.starter.spring.StarterResourceMetricSamplerScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StarterResourceMetricSamplerSchedulerTest {

    @Test
    void declaresThirtySecondScheduledResourceSamplingCadence() throws Exception {
        Method method = StarterResourceMetricSamplerScheduler.class.getMethod("sampleResourcesOnTick");

        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertNotNull(scheduled);
        assertEquals(30_000L, scheduled.fixedDelay());
        assertEquals(30_000L, scheduled.initialDelay());
    }

    @Test
    void scheduledTickInvokesResourceSampler() {
        List<JvmMetricSample> samples = new ArrayList<>();
        JvmMetricSample sample = new JvmMetricSample(Instant.parse("2026-05-08T01:00:10Z"), 0.3d, 0.4d);
        StarterResourceMetricSampler sampler = new StarterResourceMetricSampler(
                () -> Optional.of(sample),
                Optional::empty,
                samples::add,
                ignored -> {
                });
        StarterResourceMetricSamplerScheduler scheduler = new StarterResourceMetricSamplerScheduler(sampler);

        scheduler.sampleResourcesOnTick();

        assertEquals(List.of(sample), samples);
    }
}
