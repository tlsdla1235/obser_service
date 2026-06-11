package com.observation.portal.domain.cleanup.service;

import com.observation.portal.domain.bucket.repository.MetricBucketRepository;
import com.observation.portal.domain.snapshot.repository.DashboardSnapshotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RetentionCleanupServiceTest {

    private static final Instant RUN_AT = Instant.parse("2026-06-10T16:15:00Z");
    private static final Clock CLOCK = Clock.fixed(RUN_AT, ZoneOffset.UTC);

    private final DashboardSnapshotRepository dashboardSnapshotRepository = mock(DashboardSnapshotRepository.class);
    private final MetricBucketRepository metricBucketRepository = mock(MetricBucketRepository.class);

    @Test
    @DisplayName("cleanup cutoff는 UTC runAt에서 14일과 30분 evidence grace를 계산한다")
    void calculatesUtcCutoffsAndDeletesWithStrictBeforeRepositoryPredicates() {
        RetentionCleanupService service = service(new RetentionCleanupProperties(14, true, false));
        OffsetDateTime snapshotCutoff = OffsetDateTime.parse("2026-05-27T16:15:00Z");
        OffsetDateTime metricEvidenceCutoff = OffsetDateTime.parse("2026-05-27T15:45:00Z");
        when(dashboardSnapshotRepository.deleteDashboardSnapshotsWindowEndedBefore(snapshotCutoff)).thenReturn(2L);
        when(metricBucketRepository.deleteAcceptedMetricBucketsEndedBefore(metricEvidenceCutoff)).thenReturn(3L);

        RetentionCleanupResult result = service.cleanupAt(RUN_AT);

        assertThat(result.runAtUtc()).isEqualTo(OffsetDateTime.parse("2026-06-10T16:15:00Z"));
        assertThat(result.snapshotCutoffUtc()).isEqualTo(snapshotCutoff);
        assertThat(result.metricEvidenceCutoffUtc()).isEqualTo(metricEvidenceCutoff);
        assertThat(result.retentionDays()).isEqualTo(14);
        assertThat(result.deletedDashboardSnapshots()).isEqualTo(2L);
        assertThat(result.deletedAcceptedMetricBuckets()).isEqualTo(3L);
        assertThat(result.dryRun()).isFalse();
        verify(dashboardSnapshotRepository).deleteDashboardSnapshotsWindowEndedBefore(snapshotCutoff);
        verify(metricBucketRepository).deleteAcceptedMetricBucketsEndedBefore(metricEvidenceCutoff);
    }

    @Test
    @DisplayName("dry-run cleanup은 cutoff 의미를 유지하되 물리 삭제를 호출하지 않는다")
    void dryRunReturnsCutoffsWithoutDeletingRows() {
        RetentionCleanupService service = service(new RetentionCleanupProperties(14, true, true));

        RetentionCleanupResult result = service.cleanupAt(RUN_AT);

        assertThat(result.snapshotCutoffUtc()).isEqualTo(OffsetDateTime.parse("2026-05-27T16:15:00Z"));
        assertThat(result.metricEvidenceCutoffUtc()).isEqualTo(OffsetDateTime.parse("2026-05-27T15:45:00Z"));
        assertThat(result.deletedDashboardSnapshots()).isZero();
        assertThat(result.deletedAcceptedMetricBuckets()).isZero();
        assertThat(result.dryRun()).isTrue();
        verifyNoInteractions(dashboardSnapshotRepository, metricBucketRepository);
    }

    @Test
    @DisplayName("disabled cleanup은 cutoff 의미를 유지하되 물리 삭제를 호출하지 않는다")
    void disabledCleanupReturnsCutoffsWithoutDeletingRows() {
        RetentionCleanupService service = service(new RetentionCleanupProperties(14, false, false));

        RetentionCleanupResult result = service.cleanupAt(RUN_AT);

        assertThat(result.snapshotCutoffUtc()).isEqualTo(OffsetDateTime.parse("2026-05-27T16:15:00Z"));
        assertThat(result.metricEvidenceCutoffUtc()).isEqualTo(OffsetDateTime.parse("2026-05-27T15:45:00Z"));
        assertThat(result.deletedDashboardSnapshots()).isZero();
        assertThat(result.deletedAcceptedMetricBuckets()).isZero();
        assertThat(result.enabled()).isFalse();
        assertThat(result.dryRun()).isFalse();
        verifyNoInteractions(dashboardSnapshotRepository, metricBucketRepository);
    }

    @Test
    @DisplayName("같은 cutoff로 두 번 실행하면 두 번째 삭제 count는 0으로 수렴한다")
    void repeatedCleanupWithSameCutoffIsIdempotent() {
        RetentionCleanupService service = service(new RetentionCleanupProperties(14, true, false));
        OffsetDateTime snapshotCutoff = OffsetDateTime.parse("2026-05-27T16:15:00Z");
        OffsetDateTime metricEvidenceCutoff = OffsetDateTime.parse("2026-05-27T15:45:00Z");
        when(dashboardSnapshotRepository.deleteDashboardSnapshotsWindowEndedBefore(snapshotCutoff))
                .thenReturn(5L, 0L);
        when(metricBucketRepository.deleteAcceptedMetricBucketsEndedBefore(metricEvidenceCutoff))
                .thenReturn(8L, 0L);

        RetentionCleanupResult first = service.cleanupAt(RUN_AT);
        RetentionCleanupResult second = service.cleanupAt(RUN_AT);

        assertThat(first.deletedDashboardSnapshots()).isEqualTo(5L);
        assertThat(first.deletedAcceptedMetricBuckets()).isEqualTo(8L);
        assertThat(second.deletedDashboardSnapshots()).isZero();
        assertThat(second.deletedAcceptedMetricBuckets()).isZero();
        assertThat(second.snapshotCutoffUtc()).isEqualTo(first.snapshotCutoffUtc());
        assertThat(second.metricEvidenceCutoffUtc()).isEqualTo(first.metricEvidenceCutoffUtc());
    }

    @Test
    @DisplayName("table별 partial delete 실패 후 같은 cutoff 재시도는 idempotent하게 수렴한다")
    void partialDeleteFailureCanBeRetriedWithSameCutoff() {
        RetentionCleanupService service = service(new RetentionCleanupProperties(14, true, false));
        OffsetDateTime snapshotCutoff = OffsetDateTime.parse("2026-05-27T16:15:00Z");
        OffsetDateTime metricEvidenceCutoff = OffsetDateTime.parse("2026-05-27T15:45:00Z");
        when(dashboardSnapshotRepository.deleteDashboardSnapshotsWindowEndedBefore(snapshotCutoff))
                .thenReturn(4L, 0L);
        when(metricBucketRepository.deleteAcceptedMetricBucketsEndedBefore(metricEvidenceCutoff))
                .thenThrow(new IllegalStateException("metric cleanup failed"))
                .thenReturn(6L);

        assertThatThrownBy(() -> service.cleanupAt(RUN_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("metric cleanup failed");
        RetentionCleanupResult retry = service.cleanupAt(RUN_AT);

        assertThat(retry.deletedDashboardSnapshots()).isZero();
        assertThat(retry.deletedAcceptedMetricBuckets()).isEqualTo(6L);
        assertThat(retry.snapshotCutoffUtc()).isEqualTo(snapshotCutoff);
        assertThat(retry.metricEvidenceCutoffUtc()).isEqualTo(metricEvidenceCutoff);
    }

    private RetentionCleanupService service(RetentionCleanupProperties properties) {
        return new RetentionCleanupService(
                dashboardSnapshotRepository,
                metricBucketRepository,
                properties,
                CLOCK);
    }
}
