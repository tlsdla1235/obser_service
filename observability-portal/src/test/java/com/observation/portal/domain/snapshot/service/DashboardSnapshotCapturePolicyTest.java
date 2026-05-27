package com.observation.portal.domain.snapshot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.portal.domain.bucket.model.RecentBucketEvidenceRow;
import com.observation.portal.domain.bucket.repository.MetricBucketRepository;
import com.observation.portal.domain.dashboard.model.ApplicationDashboardReadModel;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotCaptureReason;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotLatestRow;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotWriteCommand;
import com.observation.portal.domain.snapshot.repository.DashboardSnapshotRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardSnapshotCapturePolicyTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000005801");
    private static final UUID APPLICATION_ID = UUID.fromString("00000000-0000-0000-0000-000000005811");
    private static final OffsetDateTime WINDOW_END = OffsetDateTime.parse("2026-05-27T13:00:00Z");

    private final DashboardSnapshotRepository snapshotRepository = mock(DashboardSnapshotRepository.class);
    private final MetricBucketRepository metricBucketRepository = mock(MetricBucketRepository.class);
    private final DashboardSnapshotCapturePolicy policy = new DashboardSnapshotCapturePolicy(
            snapshotRepository,
            metricBucketRepository,
            new ObjectMapper());

    @Test
    void promotesStateChangeOnlyWhenStoredStateChanges() {
        when(snapshotRepository.findLatestByApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(latest("active")));

        DashboardSnapshotWriteCommand changed = command(
                DashboardSnapshotCaptureReason.HOURLY_SCHEDULED,
                "degraded",
                List.of());

        assertThat(policy.isEligible(DashboardSnapshotCaptureReason.STATE_CHANGE, changed)).isTrue();
        assertThat(policy.representativeReason(changed)).isEqualTo(DashboardSnapshotCaptureReason.STATE_CHANGE);

        DashboardSnapshotWriteCommand unchanged = command(
                DashboardSnapshotCaptureReason.STATE_CHANGE,
                "active",
                List.of());

        assertThat(policy.isEligible(DashboardSnapshotCaptureReason.STATE_CHANGE, unchanged)).isFalse();
        assertThat(policy.representativeReason(unchanged)).isEqualTo(DashboardSnapshotCaptureReason.HOURLY_SCHEDULED);
    }

    @Test
    void gatesHighConfidenceConcernAtThreshold() {
        when(snapshotRepository.findLatestByApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(latest("active")));

        DashboardSnapshotWriteCommand belowThreshold = command(
                DashboardSnapshotCaptureReason.HIGH_CONFIDENCE_CONCERN,
                "active",
                List.of(triageCard(0.819d)));
        DashboardSnapshotWriteCommand atThreshold = command(
                DashboardSnapshotCaptureReason.HIGH_CONFIDENCE_CONCERN,
                "active",
                List.of(triageCard(0.82d)));

        assertThat(policy.isEligible(DashboardSnapshotCaptureReason.HIGH_CONFIDENCE_CONCERN, belowThreshold))
                .isFalse();
        assertThat(policy.isEligible(DashboardSnapshotCaptureReason.HIGH_CONFIDENCE_CONCERN, atThreshold))
                .isTrue();
    }

    @Test
    void treatsShortStrongSpikeAsIndependentRecentBadBucketEvidence() {
        when(snapshotRepository.findLatestByApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(latest("active")));
        when(metricBucketRepository.findRecentFiveBucketEvidenceRowsByApplicationIdAtOrBefore(
                APPLICATION_ID,
                WINDOW_END.toInstant()))
                .thenReturn(List.of(
                        recentBucket("2026-05-27T12:59:00Z", 100L, 5L),
                        recentBucket("2026-05-27T12:59:30Z", 100L, 7L)));

        DashboardSnapshotWriteCommand strongSpike = command(
                DashboardSnapshotCaptureReason.HOURLY_SCHEDULED,
                "active",
                List.of(),
                List.of(endpointPriority(0.91d)));

        assertThat(policy.isEligible(DashboardSnapshotCaptureReason.SHORT_STRONG_SPIKE, strongSpike)).isTrue();
        assertThat(policy.representativeReason(strongSpike))
                .isEqualTo(DashboardSnapshotCaptureReason.SHORT_STRONG_SPIKE);
    }

    @Test
    void rejectsShortStrongSpikeWhenConfidenceGuardDoesNotPass() {
        when(snapshotRepository.findLatestByApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(latest("active")));
        when(metricBucketRepository.findRecentFiveBucketEvidenceRowsByApplicationIdAtOrBefore(
                APPLICATION_ID,
                WINDOW_END.toInstant()))
                .thenReturn(List.of(
                        recentBucket("2026-05-27T12:59:00Z", 100L, 5L),
                        recentBucket("2026-05-27T12:59:30Z", 100L, 7L)));

        DashboardSnapshotWriteCommand weakConfidence = command(
                DashboardSnapshotCaptureReason.SHORT_STRONG_SPIKE,
                "active",
                List.of(),
                List.of(endpointPriority(0.899d)));

        assertThat(policy.isEligible(DashboardSnapshotCaptureReason.SHORT_STRONG_SPIKE, weakConfidence)).isFalse();
        assertThat(policy.representativeReason(weakConfidence))
                .isEqualTo(DashboardSnapshotCaptureReason.HOURLY_SCHEDULED);
    }

    @Test
    void rejectsShortStrongSpikeWhenBadBucketGuardDoesNotPass() {
        when(snapshotRepository.findLatestByApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(latest("active")));
        when(metricBucketRepository.findRecentFiveBucketEvidenceRowsByApplicationIdAtOrBefore(
                APPLICATION_ID,
                WINDOW_END.toInstant()))
                .thenReturn(List.of(recentBucket("2026-05-27T12:59:30Z", 100L, 5L)));

        DashboardSnapshotWriteCommand weakSpike = command(
                DashboardSnapshotCaptureReason.SHORT_STRONG_SPIKE,
                "active",
                List.of(),
                List.of(endpointPriority(0.91d)));

        assertThat(policy.isEligible(DashboardSnapshotCaptureReason.SHORT_STRONG_SPIKE, weakSpike)).isFalse();
    }

    @Test
    void fixedPriorityKeepsHighConfidenceConcernAheadOfShortStrongSpike() {
        when(snapshotRepository.findLatestByApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(latest("active")));
        when(metricBucketRepository.findRecentFiveBucketEvidenceRowsByApplicationIdAtOrBefore(
                APPLICATION_ID,
                WINDOW_END.toInstant()))
                .thenReturn(List.of(
                        recentBucket("2026-05-27T12:59:00Z", 100L, 5L),
                        recentBucket("2026-05-27T12:59:30Z", 100L, 7L)));

        DashboardSnapshotWriteCommand command = command(
                DashboardSnapshotCaptureReason.HOURLY_SCHEDULED,
                "active",
                List.of(triageCard(0.95d)));

        assertThat(policy.representativeReason(command))
                .isEqualTo(DashboardSnapshotCaptureReason.HIGH_CONFIDENCE_CONCERN);
    }

    private static DashboardSnapshotWriteCommand command(
            DashboardSnapshotCaptureReason reason,
            String stateCode,
            List<ApplicationDashboardReadModel.TriageCard> triageCards) {
        return command(reason, stateCode, triageCards, List.of());
    }

    private static DashboardSnapshotWriteCommand command(
            DashboardSnapshotCaptureReason reason,
            String stateCode,
            List<ApplicationDashboardReadModel.TriageCard> triageCards,
            List<ApplicationDashboardReadModel.EndpointPriorityItem> endpointPriority) {
        return new DashboardSnapshotWriteCommand(
                PROJECT_ID,
                APPLICATION_ID,
                readModel(stateCode, triageCards, endpointPriority),
                reason,
                WINDOW_END,
                WINDOW_END.plusSeconds(5),
                "test");
    }

    private static DashboardSnapshotLatestRow latest(String stateCode) {
        return new DashboardSnapshotLatestRow(
                UUID.fromString("00000000-0000-0000-0000-000000005899"),
                WINDOW_END.minusHours(1),
                WINDOW_END.minusHours(1),
                stateCode,
                "hourly_scheduled");
    }

    private static RecentBucketEvidenceRow recentBucket(String bucketEndUtc, long requestCount, long errorCount) {
        OffsetDateTime end = OffsetDateTime.parse(bucketEndUtc);
        return new RecentBucketEvidenceRow(
                APPLICATION_ID,
                end.minusSeconds(30),
                end,
                requestCount,
                errorCount,
                "[{\"leMs\":500,\"count\":90},{\"leMs\":1000,\"count\":100}]");
    }

    private static ApplicationDashboardReadModel readModel(
            String stateCode,
            List<ApplicationDashboardReadModel.TriageCard> triageCards,
            List<ApplicationDashboardReadModel.EndpointPriorityItem> endpointPriority) {
        return new ApplicationDashboardReadModel(
                WINDOW_END.plusSeconds(5),
                new ApplicationDashboardReadModel.Application(
                        PROJECT_ID,
                        APPLICATION_ID,
                        "orders-api",
                        "prod",
                        WINDOW_END.minusSeconds(30),
                        null,
                        new ApplicationDashboardReadModel.SourceWindow(
                                new ApplicationDashboardReadModel.Window(WINDOW_END.minusMinutes(15), WINDOW_END),
                                new ApplicationDashboardReadModel.Window(
                                        WINDOW_END.minusMinutes(30),
                                        WINDOW_END.minusMinutes(15))),
                        new ApplicationDashboardReadModel.Freshness(
                                WINDOW_END.minusSeconds(30),
                                WINDOW_END.plusSeconds(60),
                                WINDOW_END.plusSeconds(150))),
                new ApplicationDashboardReadModel.State(
                        stateCode,
                        "상태",
                        "테스트 상태입니다.",
                        "테스트 액션을 확인하세요.",
                        "application"),
                new ApplicationDashboardReadModel.StarterConnection(
                        "starter_heartbeat",
                        WINDOW_END.minusSeconds(20),
                        "received",
                        "starter_connected",
                        "none"),
                triageCards.isEmpty() ? new ApplicationDashboardReadModel.ZeroInsight(
                        "no_action_needed",
                        "현재 우선 조치가 필요한 신호는 없습니다.",
                        "트래픽이 유지되는지 관찰하세요.") : null,
                new ApplicationDashboardReadModel.Recovery(false, null, null, null),
                new ApplicationDashboardReadModel.Metrics(100L, 5L, new BigDecimal("0.05")),
                ApplicationDashboardReadModel.SourceScopedPercentiles.empty(),
                ApplicationDashboardReadModel.HistogramDistribution.empty(),
                triageCards,
                endpointPriority,
                List.of(),
                null);
    }

    private static ApplicationDashboardReadModel.TriageCard triageCard(double confidence) {
        return new ApplicationDashboardReadModel.TriageCard(
                "global_error_spike",
                ApplicationDashboardReadModel.TriageSeverity.WARNING,
                "오류율 증가",
                "오류율이 증가했습니다.",
                "관련 endpoint를 먼저 확인하세요.",
                confidence,
                (int) Math.round(confidence * 100),
                "POST /orders",
                new ApplicationDashboardReadModel.TriageEvidence(
                        100L,
                        5L,
                        new BigDecimal("0.05"),
                        100L,
                        0L,
                        BigDecimal.ZERO,
                        new BigDecimal("0.05"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        "current",
                null));
    }

    private static ApplicationDashboardReadModel.EndpointPriorityItem endpointPriority(double confidence) {
        return new ApplicationDashboardReadModel.EndpointPriorityItem(
                1,
                "POST",
                "/orders",
                "POST /orders",
                ApplicationDashboardReadModel.EndpointPriorityReason.ERROR_SPIKE,
                List.of("endpoint_error_spike"),
                confidence,
                (int) Math.round(confidence * 100),
                new ApplicationDashboardReadModel.EndpointPriorityFreshness(
                        "current",
                        WINDOW_END.minusSeconds(30),
                        "current",
                        null),
                new ApplicationDashboardReadModel.EndpointPriorityEvidence(
                        100L,
                        5L,
                        new BigDecimal("0.05"),
                        100L,
                        0L,
                        BigDecimal.ZERO,
                        new BigDecimal("0.05"),
                        List.of(new ApplicationDashboardReadModel.HistogramBucket(500L, 100L)),
                        List.of(new ApplicationDashboardReadModel.HistogramBucket(500L, 100L)),
                        null,
                        null,
                        null,
                        "histogram_bucket_distribution",
                        ApplicationDashboardReadModel.EndpointEvidenceStatus.AVAILABLE,
                        ApplicationDashboardReadModel.EndpointEvidenceStatus.AVAILABLE),
                "이 endpoint를 먼저 확인하세요.");
    }
}
