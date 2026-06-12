package com.observation.starter.service;

import com.observation.starter.model.metric.DatasourcePoolMetricSample;
import com.observation.starter.model.metric.JvmMetricSample;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StarterResourceMetricSamplerTest {

    @Test
    void recordsAvailableJvmAndDatasourceSamples() {
        JvmMetricSample jvmSample = new JvmMetricSample(Instant.parse("2026-05-08T01:00:10Z"), 0.4d, 0.5d);
        DatasourcePoolMetricSample datasourceSample = new DatasourcePoolMetricSample(
                Instant.parse("2026-05-08T01:00:10Z"),
                0.6d);
        List<JvmMetricSample> jvmSamples = new ArrayList<>();
        List<DatasourcePoolMetricSample> datasourceSamples = new ArrayList<>();
        StarterResourceMetricSampler sampler = new StarterResourceMetricSampler(
                () -> Optional.of(jvmSample),
                () -> Optional.of(datasourceSample),
                jvmSamples::add,
                datasourceSamples::add);

        sampler.sampleAndRecord();

        assertEquals(List.of(jvmSample), jvmSamples);
        assertEquals(List.of(datasourceSample), datasourceSamples);
    }

    @Test
    void datasourceAbsenceDoesNotBlockJvmSample() {
        JvmMetricSample jvmSample = new JvmMetricSample(Instant.parse("2026-05-08T01:00:10Z"), 0.4d, 0.5d);
        List<JvmMetricSample> jvmSamples = new ArrayList<>();
        List<DatasourcePoolMetricSample> datasourceSamples = new ArrayList<>();
        StarterResourceMetricSampler sampler = new StarterResourceMetricSampler(
                () -> Optional.of(jvmSample),
                Optional::empty,
                jvmSamples::add,
                datasourceSamples::add);

        sampler.sampleAndRecord();

        assertEquals(List.of(jvmSample), jvmSamples);
        assertEquals(List.of(), datasourceSamples);
    }

    @Test
    void samplerFailureDoesNotPropagateOrBlockOtherResourceSamples() {
        DatasourcePoolMetricSample datasourceSample = new DatasourcePoolMetricSample(
                Instant.parse("2026-05-08T01:00:10Z"),
                0.6d);
        List<DatasourcePoolMetricSample> datasourceSamples = new ArrayList<>();
        StarterResourceMetricSampler sampler = new StarterResourceMetricSampler(
                () -> {
                    throw new IllegalStateException("jvm unavailable");
                },
                () -> Optional.of(datasourceSample),
                sample -> {
                    throw new IllegalStateException("not used");
                },
                datasourceSamples::add);

        assertDoesNotThrow(sampler::sampleAndRecord);

        assertEquals(List.of(datasourceSample), datasourceSamples);
    }
}
