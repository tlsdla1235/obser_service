package com.observation.starter.service;

import com.observation.starter.model.metric.JvmMetricSample;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.function.Supplier;

/**
 * JDK MXBean에서 starter가 전송할 JVM CPU/heap resource 샘플을 읽는다.
 *
 * <p>Micrometer meter registry나 actuator scrape endpoint 없이 현재 프로세스의 bounded ratio 값만 만든다.</p>
 */
public final class JdkJvmMetricSampler {

    private final Supplier<Instant> nowUtcSupplier;
    private final Supplier<MemoryUsage> heapUsageSupplier;
    private final OperatingSystemMetricsReader operatingSystemReader;
    private CpuSnapshot previousCpuSnapshot;

    /**
     * 기본 JVM MXBean을 사용하는 runtime sampler를 만든다.
     */
    public JdkJvmMetricSampler() {
        this(Instant::now,
                ManagementFactory.getMemoryMXBean()::getHeapMemoryUsage,
                new ManagementFactoryOperatingSystemMetricsReader(
                        ManagementFactory.getOperatingSystemMXBean()));
    }

    JdkJvmMetricSampler(
            Supplier<Instant> nowUtcSupplier,
            Supplier<MemoryUsage> heapUsageSupplier,
            OperatingSystemMetricsReader operatingSystemReader) {
        this.nowUtcSupplier = Objects.requireNonNull(nowUtcSupplier, "nowUtcSupplier must not be null");
        this.heapUsageSupplier = Objects.requireNonNull(heapUsageSupplier, "heapUsageSupplier must not be null");
        this.operatingSystemReader = Objects.requireNonNull(
                operatingSystemReader,
                "operatingSystemReader must not be null");
    }

    /**
     * CPU와 heap ratio가 모두 관측 가능한 경우에만 JVM 샘플을 반환한다.
     */
    public Optional<JvmMetricSample> sample() {
        Instant observedAt = Objects.requireNonNull(nowUtcSupplier.get(), "nowUtcSupplier must not return null");
        OptionalDouble cpuUsageRatio = cpuUsageRatio(observedAt);
        OptionalDouble heapUsedRatio = heapUsedRatio();
        if (cpuUsageRatio.isEmpty() || heapUsedRatio.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new JvmMetricSample(
                observedAt,
                cpuUsageRatio.getAsDouble(),
                heapUsedRatio.getAsDouble()));
    }

    private OptionalDouble cpuUsageRatio(Instant observedAt) {
        Optional<CpuSnapshot> currentSnapshot = cpuSnapshot(observedAt);
        CpuSnapshot previousSnapshot = previousCpuSnapshot;
        currentSnapshot.ifPresent(snapshot -> previousCpuSnapshot = snapshot);

        OptionalDouble directLoad = operatingSystemReader.processCpuLoadRatio();
        if (isValidRatio(directLoad)) {
            return OptionalDouble.of(clampRatio(directLoad.getAsDouble()));
        }

        if (currentSnapshot.isEmpty()) {
            return OptionalDouble.empty();
        }
        if (previousSnapshot == null) {
            return OptionalDouble.of(0.0d);
        }
        long elapsedNanos = Duration.between(previousSnapshot.observedAt(), currentSnapshot.orElseThrow().observedAt())
                .toNanos();
        long cpuNanos = currentSnapshot.orElseThrow().processCpuTimeNanos()
                - previousSnapshot.processCpuTimeNanos();
        int processors = operatingSystemReader.availableProcessors();
        if (elapsedNanos <= 0L || cpuNanos < 0L || processors <= 0) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(clampRatio(cpuNanos / (double) elapsedNanos / processors));
    }

    private Optional<CpuSnapshot> cpuSnapshot(Instant observedAt) {
        OptionalLong processCpuTimeNanos = operatingSystemReader.processCpuTimeNanos();
        if (processCpuTimeNanos.isEmpty()) {
            return Optional.empty();
        }
        long value = processCpuTimeNanos.getAsLong();
        if (value < 0L) {
            return Optional.empty();
        }
        return Optional.of(new CpuSnapshot(observedAt, value));
    }

    private OptionalDouble heapUsedRatio() {
        MemoryUsage heapUsage = Objects.requireNonNull(
                heapUsageSupplier.get(),
                "heapUsageSupplier must not return null");
        long denominator = heapUsage.getMax() > 0L ? heapUsage.getMax() : heapUsage.getCommitted();
        if (denominator <= 0L || heapUsage.getUsed() < 0L) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(clampRatio(heapUsage.getUsed() / (double) denominator));
    }

    private static boolean isValidRatio(OptionalDouble value) {
        return value.isPresent()
                && !Double.isNaN(value.getAsDouble())
                && value.getAsDouble() >= 0.0d;
    }

    private static double clampRatio(double value) {
        if (value <= 0.0d) {
            return 0.0d;
        }
        if (value >= 1.0d) {
            return 1.0d;
        }
        return value;
    }

    interface OperatingSystemMetricsReader {

        /**
         * JDK가 바로 제공하는 최근 process CPU load ratio를 반환한다.
         */
        OptionalDouble processCpuLoadRatio();

        /**
         * process CPU time 누적값을 nanoseconds 단위로 반환한다.
         */
        OptionalLong processCpuTimeNanos();

        /**
         * CPU time delta를 process-level ratio로 나눌 때 사용할 processor 수를 반환한다.
         */
        int availableProcessors();
    }

    private record CpuSnapshot(Instant observedAt, long processCpuTimeNanos) {
    }

    private static final class ManagementFactoryOperatingSystemMetricsReader
            implements OperatingSystemMetricsReader {

        private final java.lang.management.OperatingSystemMXBean operatingSystemMXBean;

        private ManagementFactoryOperatingSystemMetricsReader(
                java.lang.management.OperatingSystemMXBean operatingSystemMXBean) {
            this.operatingSystemMXBean = Objects.requireNonNull(
                    operatingSystemMXBean,
                    "operatingSystemMXBean must not be null");
        }

        @Override
        public OptionalDouble processCpuLoadRatio() {
            if (operatingSystemMXBean instanceof com.sun.management.OperatingSystemMXBean sunOperatingSystemMXBean) {
                return OptionalDouble.of(sunOperatingSystemMXBean.getProcessCpuLoad());
            }
            return OptionalDouble.empty();
        }

        @Override
        public OptionalLong processCpuTimeNanos() {
            if (operatingSystemMXBean instanceof com.sun.management.OperatingSystemMXBean sunOperatingSystemMXBean) {
                return OptionalLong.of(sunOperatingSystemMXBean.getProcessCpuTime());
            }
            return OptionalLong.empty();
        }

        @Override
        public int availableProcessors() {
            return operatingSystemMXBean.getAvailableProcessors();
        }
    }
}
