package com.observation.starter.service;

import com.observation.starter.model.metric.JvmMetricSample;
import org.junit.jupiter.api.Test;

import java.lang.management.MemoryUsage;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdkJvmMetricSamplerTest {

    @Test
    void samplesDirectProcessCpuLoadAndHeapUsageRatio() {
        AtomicReference<Instant> nowUtc = new AtomicReference<>(Instant.parse("2026-05-08T01:00:10Z"));
        FakeOperatingSystemReader operatingSystemReader = new FakeOperatingSystemReader(
                OptionalDouble.of(0.42d),
                1_000_000L,
                4);
        JdkJvmMetricSampler sampler = new JdkJvmMetricSampler(
                nowUtc::get,
                () -> new MemoryUsage(0L, 25L, 50L, 100L),
                operatingSystemReader);

        JvmMetricSample sample = sampler.sample().orElseThrow();

        assertEquals(nowUtc.get(), sample.observedAt());
        assertEquals(0.42d, sample.cpuUsageRatio());
        assertEquals(0.25d, sample.heapUsedRatio());
    }

    @Test
    void fallsBackToProcessCpuTimeDeltaWhenDirectLoadIsUnavailable() {
        AtomicReference<Instant> nowUtc = new AtomicReference<>(Instant.parse("2026-05-08T01:00:00Z"));
        FakeOperatingSystemReader operatingSystemReader = new FakeOperatingSystemReader(
                OptionalDouble.empty(),
                1_000_000_000L,
                2);
        JdkJvmMetricSampler sampler = new JdkJvmMetricSampler(
                nowUtc::get,
                () -> new MemoryUsage(0L, 40L, 80L, 100L),
                operatingSystemReader);

        JvmMetricSample first = sampler.sample().orElseThrow();

        assertEquals(0.0d, first.cpuUsageRatio());
        assertEquals(0.4d, first.heapUsedRatio());

        nowUtc.set(Instant.parse("2026-05-08T01:00:01Z"));
        operatingSystemReader.processCpuTimeNanos.set(1_500_000_000L);

        JvmMetricSample second = sampler.sample().orElseThrow();

        assertEquals(0.25d, second.cpuUsageRatio());
        assertEquals(0.4d, second.heapUsedRatio());
    }

    @Test
    void omitsJvmSampleWhenCpuCannotBeObserved() {
        AtomicReference<Instant> nowUtc = new AtomicReference<>(Instant.parse("2026-05-08T01:00:00Z"));
        FakeOperatingSystemReader operatingSystemReader = new FakeOperatingSystemReader(
                OptionalDouble.empty(),
                -1L,
                2);
        JdkJvmMetricSampler sampler = new JdkJvmMetricSampler(
                nowUtc::get,
                () -> new MemoryUsage(0L, 40L, 80L, 100L),
                operatingSystemReader);

        Optional<JvmMetricSample> sample = sampler.sample();

        assertTrue(sample.isEmpty());
    }

    private static final class FakeOperatingSystemReader implements JdkJvmMetricSampler.OperatingSystemMetricsReader {

        private final OptionalDouble processCpuLoadRatio;
        private final AtomicLong processCpuTimeNanos;
        private final int availableProcessors;

        private FakeOperatingSystemReader(
                OptionalDouble processCpuLoadRatio,
                long processCpuTimeNanos,
                int availableProcessors) {
            this.processCpuLoadRatio = processCpuLoadRatio;
            this.processCpuTimeNanos = new AtomicLong(processCpuTimeNanos);
            this.availableProcessors = availableProcessors;
        }

        @Override
        public OptionalDouble processCpuLoadRatio() {
            return processCpuLoadRatio;
        }

        @Override
        public OptionalLong processCpuTimeNanos() {
            long value = processCpuTimeNanos.get();
            if (value < 0L) {
                return OptionalLong.empty();
            }
            return OptionalLong.of(value);
        }

        @Override
        public int availableProcessors() {
            return availableProcessors;
        }
    }
}
