package com.observation.portal.domain.bucket.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * saturation hint rule이 참고할 current window의 latest runtime ratio sample projection이다.
 *
 * <p>ratio latest sample은 단독으로 state, root cause, degraded, endpoint priority를 만들지 않으며, service가
 * latency/error 동반 신호와 함께 확인 힌트로만 사용한다.</p>
 */
public record RuntimeRatioEvidenceRow(
        UUID applicationId,
        OffsetDateTime bucketStartUtc,
        OffsetDateTime bucketEndUtc,
        BigDecimal cpuUsageRatio,
        BigDecimal heapUsedRatio,
        BigDecimal datasourcePoolUsageRatio
) {

    /**
     * projection identity와 bucket boundary를 검증하고 ratio는 nullable latest sample로 보존한다.
     */
    public RuntimeRatioEvidenceRow {
        Objects.requireNonNull(applicationId, "applicationId must not be null");
        Objects.requireNonNull(bucketStartUtc, "bucketStartUtc must not be null");
        Objects.requireNonNull(bucketEndUtc, "bucketEndUtc must not be null");
        validateRatio(cpuUsageRatio, "cpuUsageRatio");
        validateRatio(heapUsedRatio, "heapUsedRatio");
        validateRatio(datasourcePoolUsageRatio, "datasourcePoolUsageRatio");
    }

    private static void validateRatio(BigDecimal value, String fieldName) {
        if (value != null
                && (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0)) {
            throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0");
        }
    }
}
