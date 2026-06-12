package com.observation.starter.service;

import com.observation.starter.model.metric.DatasourcePoolMetricSample;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/**
 * classpath에 HikariCP가 있는 host application에서 datasource pool ratio를 선택적으로 읽는다.
 *
 * <p>starter는 HikariCP에 직접 의존하지 않으며, pool MXBean이 없거나 아직 준비되지 않은 datasource는 조용히 생략한다.</p>
 */
public final class HikariDatasourcePoolMetricSampler {

    private final Supplier<Instant> nowUtcSupplier;
    private final Supplier<List<DataSource>> dataSourcesSupplier;

    /**
     * runtime DataSource 목록을 지연 조회하는 sampler를 만든다.
     */
    public HikariDatasourcePoolMetricSampler(Supplier<List<DataSource>> dataSourcesSupplier) {
        this(Instant::now, dataSourcesSupplier);
    }

    HikariDatasourcePoolMetricSampler(
            Supplier<Instant> nowUtcSupplier,
            Supplier<List<DataSource>> dataSourcesSupplier) {
        this.nowUtcSupplier = Objects.requireNonNull(nowUtcSupplier, "nowUtcSupplier must not be null");
        this.dataSourcesSupplier = Objects.requireNonNull(
                dataSourcesSupplier,
                "dataSourcesSupplier must not be null");
    }

    /**
     * 관측 가능한 datasource pool 중 가장 높은 사용률을 단일 application-level 샘플로 반환한다.
     */
    public Optional<DatasourcePoolMetricSample> sample() {
        Instant observedAt = Objects.requireNonNull(nowUtcSupplier.get(), "nowUtcSupplier must not return null");
        OptionalDouble poolUsageRatio = dataSourcesSupplier.get().stream()
                .map(this::poolUsageRatio)
                .filter(OptionalDouble::isPresent)
                .mapToDouble(OptionalDouble::getAsDouble)
                .max();
        if (poolUsageRatio.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new DatasourcePoolMetricSample(observedAt, poolUsageRatio.getAsDouble()));
    }

    private OptionalDouble poolUsageRatio(DataSource dataSource) {
        try {
            Optional<Object> hikariPoolMxBean = invokeNoArg(dataSource, "getHikariPoolMXBean");
            if (hikariPoolMxBean.isEmpty()) {
                return OptionalDouble.empty();
            }
            Optional<Integer> activeConnections = intValue(invokeNoArg(
                    hikariPoolMxBean.orElseThrow(),
                    "getActiveConnections"));
            Optional<Integer> totalConnections = intValue(invokeNoArg(
                    hikariPoolMxBean.orElseThrow(),
                    "getTotalConnections"));
            if (activeConnections.isEmpty() || totalConnections.isEmpty() || totalConnections.orElseThrow() <= 0) {
                return OptionalDouble.empty();
            }
            return OptionalDouble.of(clampRatio(activeConnections.orElseThrow() / (double) totalConnections.orElseThrow()));
        } catch (RuntimeException exception) {
            return OptionalDouble.empty();
        }
    }

    private static Optional<Object> invokeNoArg(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return Optional.ofNullable(method.invoke(target));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static Optional<Integer> intValue(Optional<Object> value) {
        if (value.isEmpty() || !(value.orElseThrow() instanceof Number number)) {
            return Optional.empty();
        }
        return Optional.of(number.intValue());
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
}
