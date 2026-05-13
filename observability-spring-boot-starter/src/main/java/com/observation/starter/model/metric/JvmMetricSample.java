package com.observation.starter.model.metric;

import java.time.Instant;
import java.util.Objects;

/**
 * 롤업 단계가 런타임 메트릭을 소비하기 전에 수집되는 애플리케이션 수준의 JVM 샘플이다.
 *
 * <p>CPU와 힙 값은 {@code 0.0 <= ratio <= 1.0} 범위의 사용률 비율이다.</p>
 */
public record JvmMetricSample(
        Instant observedAt,
        double cpuUsageRatio,
        double heapUsedRatio
) {

    /**
     * 관측 시각과 JVM 사용률 비율의 허용 범위를 검증한다.
     */
    public JvmMetricSample {
        observedAt = Objects.requireNonNull(observedAt, "observedAt must not be null");
        validateRatio("cpuUsageRatio", cpuUsageRatio);
        validateRatio("heapUsedRatio", heapUsedRatio);
    }

    private static void validateRatio(String name, double value) {
        if (Double.isNaN(value) || value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(name + " must be between 0.0 and 1.0");
        }
    }
}
