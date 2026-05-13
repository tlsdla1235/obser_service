package com.observation.starter.model.metric;

import java.time.Instant;
import java.util.Objects;

/**
 * 롤업 단계가 소비하기 전에 수집되는 애플리케이션 수준의 데이터소스 풀 사용률 샘플이다.
 *
 * <p>스토리 2.1은 데이터소스 태그 정책을 확정하지 않고, 제한된 비율 샘플만 전달한다.</p>
 */
public record DatasourcePoolMetricSample(
        Instant observedAt,
        double poolUsageRatio
) {

    /**
     * 관측 시각과 데이터소스 풀 사용률 비율의 허용 범위를 검증한다.
     */
    public DatasourcePoolMetricSample {
        observedAt = Objects.requireNonNull(observedAt, "observedAt must not be null");
        if (Double.isNaN(poolUsageRatio) || poolUsageRatio < 0.0d || poolUsageRatio > 1.0d) {
            throw new IllegalArgumentException("poolUsageRatio must be between 0.0 and 1.0");
        }
    }
}
