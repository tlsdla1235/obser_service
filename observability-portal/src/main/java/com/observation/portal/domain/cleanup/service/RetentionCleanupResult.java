package com.observation.portal.domain.cleanup.service;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * 한 번의 retention cleanup 실행에서 계산된 UTC cutoff와 table별 삭제 count를 전달하는 결과 model이다.
 *
 * <p>운영 로그와 테스트가 같은 값을 검증할 수 있도록 runAt/cutoff/enabled/dry-run 상태를 함께 담는다.</p>
 */
public record RetentionCleanupResult(
        OffsetDateTime runAtUtc,
        OffsetDateTime snapshotCutoffUtc,
        OffsetDateTime metricEvidenceCutoffUtc,
        int retentionDays,
        long deletedDashboardSnapshots,
        long deletedAcceptedMetricBuckets,
        boolean enabled,
        boolean dryRun
) {

    /**
     * cleanup result의 필수 timestamp와 count 불변식을 검증한다.
     */
    public RetentionCleanupResult {
        Objects.requireNonNull(runAtUtc, "runAtUtc must not be null");
        Objects.requireNonNull(snapshotCutoffUtc, "snapshotCutoffUtc must not be null");
        Objects.requireNonNull(metricEvidenceCutoffUtc, "metricEvidenceCutoffUtc must not be null");
        if (retentionDays <= 0) {
            throw new IllegalArgumentException("retentionDays must be positive");
        }
        if (deletedDashboardSnapshots < 0) {
            throw new IllegalArgumentException("deletedDashboardSnapshots must not be negative");
        }
        if (deletedAcceptedMetricBuckets < 0) {
            throw new IllegalArgumentException("deletedAcceptedMetricBuckets must not be negative");
        }
    }
}
