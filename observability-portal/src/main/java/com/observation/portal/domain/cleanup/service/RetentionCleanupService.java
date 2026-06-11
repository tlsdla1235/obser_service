package com.observation.portal.domain.cleanup.service;

import com.observation.portal.domain.bucket.repository.MetricBucketRepository;
import com.observation.portal.domain.snapshot.repository.DashboardSnapshotRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Dashboard snapshot과 accepted metric bucket retention cleanup cutoff 계산 및 table별 물리 삭제를 조율한다.
 *
 * <p>각 repository delete method가 자체 transaction으로 실행되므로 table별 부분 삭제가 발생해도 다음 실행에서 같은
 * strict-before predicate로 idempotent하게 재시도할 수 있다. cutoff 계산과 DB 비교는 모두 UTC 기준이다.</p>
 */
@Service
public class RetentionCleanupService {

    private static final long ZERO_DELETED = 0L;

    private final DashboardSnapshotRepository dashboardSnapshotRepository;
    private final MetricBucketRepository metricBucketRepository;
    private final RetentionCleanupProperties properties;
    private final Clock clock;

    /**
     * cleanup 대상 repository, 설정, UTC clock을 주입한다.
     */
    public RetentionCleanupService(
            DashboardSnapshotRepository dashboardSnapshotRepository,
            MetricBucketRepository metricBucketRepository,
            RetentionCleanupProperties properties,
            Clock clock) {
        this.dashboardSnapshotRepository = Objects.requireNonNull(
                dashboardSnapshotRepository,
                "dashboardSnapshotRepository must not be null");
        this.metricBucketRepository = Objects.requireNonNull(
                metricBucketRepository,
                "metricBucketRepository must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null").withZone(ZoneOffset.UTC);
    }

    /**
     * 현재 clock 기준으로 retention cleanup을 실행한다.
     */
    public RetentionCleanupResult cleanup() {
        return cleanupAt(clock.instant());
    }

    /**
     * 지정된 실행 시각 기준으로 UTC cutoff를 계산하고 cleanup result를 반환한다.
     *
     * <p>실제 운영에서는 no-arg `cleanup()`을 사용하고, 테스트와 재현 가능한 수동 실행에서는 이 method로 같은 cutoff를
     * 반복 검증할 수 있다.</p>
     */
    public RetentionCleanupResult cleanupAt(Instant runAtUtc) {
        OffsetDateTime runAt = OffsetDateTime.ofInstant(
                Objects.requireNonNull(runAtUtc, "runAtUtc must not be null"),
                ZoneOffset.UTC);
        OffsetDateTime snapshotCutoffUtc = runAt.minusDays(properties.retentionDays());
        OffsetDateTime metricEvidenceCutoffUtc = snapshotCutoffUtc.minusMinutes(30);
        if (!properties.enabled() || properties.dryRun()) {
            return result(runAt, snapshotCutoffUtc, metricEvidenceCutoffUtc, ZERO_DELETED, ZERO_DELETED);
        }

        long deletedDashboardSnapshots =
                dashboardSnapshotRepository.deleteDashboardSnapshotsWindowEndedBefore(snapshotCutoffUtc);
        long deletedAcceptedMetricBuckets =
                metricBucketRepository.deleteAcceptedMetricBucketsEndedBefore(metricEvidenceCutoffUtc);
        return result(
                runAt,
                snapshotCutoffUtc,
                metricEvidenceCutoffUtc,
                deletedDashboardSnapshots,
                deletedAcceptedMetricBuckets);
    }

    private RetentionCleanupResult result(
            OffsetDateTime runAtUtc,
            OffsetDateTime snapshotCutoffUtc,
            OffsetDateTime metricEvidenceCutoffUtc,
            long deletedDashboardSnapshots,
            long deletedAcceptedMetricBuckets) {
        return new RetentionCleanupResult(
                runAtUtc,
                snapshotCutoffUtc,
                metricEvidenceCutoffUtc,
                properties.retentionDays(),
                deletedDashboardSnapshots,
                deletedAcceptedMetricBuckets,
                properties.enabled(),
                properties.dryRun());
    }
}
