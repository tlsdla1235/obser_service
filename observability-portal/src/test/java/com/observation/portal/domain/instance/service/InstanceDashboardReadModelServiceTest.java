package com.observation.portal.domain.instance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.portal.common.time.TimeBucketWindowCalculator;
import com.observation.portal.domain.bucket.model.EndpointEvidenceRow;
import com.observation.portal.domain.bucket.model.HistogramBucketEvidenceRow;
import com.observation.portal.domain.bucket.model.RuntimeRatioEvidenceRow;
import com.observation.portal.domain.bucket.model.WindowBucketAggregate;
import com.observation.portal.domain.bucket.repository.MetricBucketRepository;
import com.observation.portal.domain.catalog.entity.ApplicationEntity;
import com.observation.portal.domain.catalog.entity.ApplicationInstanceEntity;
import com.observation.portal.domain.catalog.repository.ApplicationInstanceRepository;
import com.observation.portal.domain.catalog.repository.ApplicationRepository;
import com.observation.portal.domain.ingest.repository.StarterHeartbeatTelemetryRepository;
import com.observation.portal.domain.instance.model.InstanceDashboardReadModel;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailRow;
import com.observation.portal.domain.snapshot.repository.DashboardSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InstanceDashboardReadModelServiceTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000013801");
    private static final UUID APPLICATION_ID = UUID.fromString("00000000-0000-0000-0000-000000013811");
    private static final UUID INSTANCE_ID = UUID.fromString("00000000-0000-0000-0000-000000013821");
    private static final UUID SNAPSHOT_ID = UUID.fromString("00000000-0000-0000-0000-000000013831");
    private static final Instant QUERY_AT = Instant.parse("2026-06-10T08:10:35Z");
    private static final Instant EVALUATION_AT = Instant.parse("2026-06-10T08:10:30Z");
    private static final Instant LIVE_WINDOW_START = Instant.parse("2026-06-10T07:40:30Z");
    private static final OffsetDateTime SNAPSHOT_WINDOW_START = offset("2026-06-10T07:30:00Z");
    private static final OffsetDateTime SNAPSHOT_WINDOW_END = offset("2026-06-10T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(QUERY_AT, ZoneOffset.UTC);

    private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
    private final ApplicationInstanceRepository instanceRepository = mock(ApplicationInstanceRepository.class);
    private final DashboardSnapshotRepository snapshotRepository = mock(DashboardSnapshotRepository.class);
    private final MetricBucketRepository metricBucketRepository = mock(MetricBucketRepository.class);
    private final StarterHeartbeatTelemetryRepository heartbeatRepository =
            mock(StarterHeartbeatTelemetryRepository.class);

    private InstanceDashboardReadModelService service;

    @BeforeEach
    void setUp() {
        service = new InstanceDashboardReadModelService(
                applicationRepository,
                instanceRepository,
                snapshotRepository,
                metricBucketRepository,
                heartbeatRepository,
                new TimeBucketWindowCalculator(CLOCK),
                new ObjectMapper(),
                CLOCK,
                14);
        when(applicationRepository.findByIdAndProjectId(APPLICATION_ID, PROJECT_ID))
                .thenReturn(Optional.of(application()));
        when(instanceRepository.findByIdAndApplicationId(INSTANCE_ID, APPLICATION_ID))
                .thenReturn(Optional.of(instance()));
        stubWindowMetricEvidence(LIVE_WINDOW_START, EVALUATION_AT, 120L, 6L);
    }

    @Test
    @DisplayName("live Instance Dashboard는 accepted bucket 최근 30분 계약을 노출한다")
    void liveDashboardUsesRecentThirtyMinutesAcceptedBucketContract() {
        InstanceDashboardReadModel dashboard = service.getLiveDashboard(PROJECT_ID, APPLICATION_ID, INSTANCE_ID)
                .orElseThrow();

        assertThat(dashboard.schemaVersion()).isEqualTo("instance_dashboard_read_model.v1");
        assertThat(dashboard.mode()).isEqualTo("live");
        assertThat(dashboard.window().name()).isEqualTo("recent_30_minutes");
        assertThat(dashboard.window().windowSource()).isEqualTo("live_recent_30_minutes");
        assertThat(dashboard.window().startUtc()).isEqualTo(offset(LIVE_WINDOW_START));
        assertThat(dashboard.window().endUtc()).isEqualTo(offset(EVALUATION_AT));
        assertThat(dashboard.readSemantics().source()).isEqualTo("accepted_metric_buckets");
        assertThat(dashboard.readSemantics().acceptedAtCutoffApplied()).isFalse();
        assertThat(dashboard.readSemantics().includesLateAcceptedMetrics()).isFalse();
        assertThat(dashboard.readSemantics().instanceEvidenceReconstructedFromMetrics()).isFalse();
        assertThat(dashboard.readSemantics().applicationSnapshotRecalculated()).isFalse();
        assertThat(dashboard.thresholds().minimumRequestCount()).isEqualTo(30L);
        assertThat(dashboard.signals().red().requestCount()).isEqualTo(120L);
        assertThat(dashboard.signals().red().errorCount()).isEqualTo(6L);
        assertThat(dashboard.toString()).doesNotContain("current_15m");

        verify(metricBucketRepository).findWindowAggregateByApplicationInstanceId(
                INSTANCE_ID,
                LIVE_WINDOW_START,
                EVALUATION_AT);
    }

    @Test
    @DisplayName("retention 밖 selected snapshot row는 current metric으로 복원하지 않고 empty로 수렴한다")
    void snapshotDashboardOutsideRetentionReturnsEmptyWithoutMetricFallback() {
        when(snapshotRepository.findDetailRow(PROJECT_ID, APPLICATION_ID, SNAPSHOT_ID))
                .thenReturn(Optional.of(snapshotRowAt(
                        offset("2026-05-26T07:30:00Z"),
                        offset("2026-05-26T08:00:00Z"),
                        """
                                {"instanceSummary":{"items":[]}}
                                """)));

        assertThat(service.getSnapshotDashboard(PROJECT_ID, APPLICATION_ID, SNAPSHOT_ID, INSTANCE_ID)).isEmpty();

        verify(metricBucketRepository, never()).findWindowAggregateByApplicationInstanceId(
                any(),
                any(),
                any());
    }

    @Test
    @DisplayName("retention cutoff와 같은 selected snapshot row는 metric evidence 조회 대상으로 유지한다")
    void snapshotDashboardAtRetentionCutoffBoundaryIsStillRetained() {
        OffsetDateTime cutoffWindowEnd = offset("2026-05-27T08:10:35Z");
        OffsetDateTime cutoffWindowStart = cutoffWindowEnd.minusMinutes(30);
        when(snapshotRepository.findDetailRow(PROJECT_ID, APPLICATION_ID, SNAPSHOT_ID))
                .thenReturn(Optional.of(snapshotRowAt(
                        cutoffWindowStart,
                        cutoffWindowEnd,
                        """
                                {"instanceSummary":{"items":[]}}
                                """)));
        stubWindowMetricEvidence(cutoffWindowStart.toInstant(), cutoffWindowEnd.toInstant(), 31L, 0L);

        InstanceDashboardReadModel dashboard = service.getSnapshotDashboard(
                        PROJECT_ID,
                        APPLICATION_ID,
                        SNAPSHOT_ID,
                        INSTANCE_ID)
                .orElseThrow();

        assertThat(dashboard.window().endUtc()).isEqualTo(cutoffWindowEnd);
        assertThat(dashboard.signals().red().requestCount()).isEqualTo(31L);
        verify(metricBucketRepository).findWindowAggregateByApplicationInstanceId(
                INSTANCE_ID,
                cutoffWindowStart.toInstant(),
                cutoffWindowEnd.toInstant());
    }

    @Test
    @DisplayName("snapshot endpoint anchor가 selected instance에서 없으면 not_observed로 연결한다")
    void snapshotDashboardConnectsStoredEndpointAnchorAsNotObservedWhenSelectedInstanceDoesNotHaveIt() {
        when(snapshotRepository.findDetailRow(PROJECT_ID, APPLICATION_ID, SNAPSHOT_ID))
                .thenReturn(Optional.of(snapshotRow("""
                        {
                          "snapshotEndpointEvidence": {
                            "items": [
                              {
                                "anchorId": "endpoint-evidence-1",
                                "method": "GET",
                                "route": "/health",
                                "endpointKey": "GET /health"
                              }
                            ]
                          },
                          "instanceSummary": {"items": []}
                        }
                        """)));
        stubWindowMetricEvidence(
                SNAPSHOT_WINDOW_START.toInstant(),
                SNAPSHOT_WINDOW_END.toInstant(),
                42L,
                3L);
        when(metricBucketRepository.findEndpointEvidenceRowsByApplicationInstanceId(
                INSTANCE_ID,
                SNAPSHOT_WINDOW_START.toInstant(),
                SNAPSHOT_WINDOW_END.toInstant()))
                .thenReturn(List.of());

        InstanceDashboardReadModel dashboard = service.getSnapshotDashboard(
                        PROJECT_ID,
                        APPLICATION_ID,
                        SNAPSHOT_ID,
                        INSTANCE_ID)
                .orElseThrow();

        assertThat(dashboard.endpointEvidence().items()).singleElement().satisfies(item -> {
            assertThat(item.endpointKey()).isEqualTo("GET /health");
            assertThat(item.presenceOnSelectedInstance()).isEqualTo("not_observed");
            assertThat(item.relatedApplicationEndpointEvidenceRef()).isEqualTo("endpoint-evidence-1");
        });
    }

    @Test
    @DisplayName("application anchor가 없는 저신호 selected endpoint는 evidence item으로 노출하지 않는다")
    void snapshotDashboardDoesNotPromoteLowSignalSelectedEndpointWithoutApplicationAnchor() {
        when(snapshotRepository.findDetailRow(PROJECT_ID, APPLICATION_ID, SNAPSHOT_ID))
                .thenReturn(Optional.of(snapshotRow("""
                        {"instanceSummary":{"items":[]}}
                        """)));
        stubWindowMetricEvidence(
                SNAPSHOT_WINDOW_START.toInstant(),
                SNAPSHOT_WINDOW_END.toInstant(),
                20L,
                0L);
        when(metricBucketRepository.findSummaryDurationBucketEvidenceRowsByApplicationInstanceId(
                INSTANCE_ID,
                SNAPSHOT_WINDOW_START.toInstant(),
                SNAPSHOT_WINDOW_END.toInstant()))
                .thenReturn(List.of());
        when(metricBucketRepository.findEndpointEvidenceRowsByApplicationInstanceId(
                INSTANCE_ID,
                SNAPSHOT_WINDOW_START.toInstant(),
                SNAPSHOT_WINDOW_END.toInstant()))
                .thenReturn(List.of(endpointRow(
                        SNAPSHOT_WINDOW_END.toInstant().minusSeconds(30),
                        "GET",
                        "/noise",
                        20L,
                        0L)));

        InstanceDashboardReadModel dashboard = service.getSnapshotDashboard(
                        PROJECT_ID,
                        APPLICATION_ID,
                        SNAPSHOT_ID,
                        INSTANCE_ID)
                .orElseThrow();

        assertThat(dashboard.endpointEvidence().status()).isEqualTo("missing");
        assertThat(dashboard.endpointEvidence().items()).isEmpty();
    }

    @Test
    @DisplayName("snapshot Instance Dashboard는 snapshot row window와 non-cutoff bucket query만 사용한다")
    void snapshotDashboardUsesSelectedSnapshotWindowAndNonCutoffMetricQueries() {
        when(snapshotRepository.findDetailRow(PROJECT_ID, APPLICATION_ID, SNAPSHOT_ID))
                .thenReturn(Optional.of(snapshotRow("""
                        {"instanceSummary":{"items":[]}}
                        """)));
        stubWindowMetricEvidence(
                SNAPSHOT_WINDOW_START.toInstant(),
                SNAPSHOT_WINDOW_END.toInstant(),
                42L,
                3L);

        InstanceDashboardReadModel dashboard = service.getSnapshotDashboard(
                        PROJECT_ID,
                        APPLICATION_ID,
                        SNAPSHOT_ID,
                        INSTANCE_ID)
                .orElseThrow();

        assertThat(dashboard.mode()).isEqualTo("snapshot");
        assertThat(dashboard.window().windowSource()).isEqualTo("selected_application_snapshot");
        assertThat(dashboard.window().startUtc()).isEqualTo(SNAPSHOT_WINDOW_START);
        assertThat(dashboard.window().endUtc()).isEqualTo(SNAPSHOT_WINDOW_END);
        assertThat(dashboard.snapshot()).isNotNull();
        assertThat(dashboard.snapshot().snapshotId()).isEqualTo(SNAPSHOT_ID);
        assertThat(dashboard.snapshot().snapshotRowSource()).isEqualTo("dashboard_snapshots");
        assertThat(dashboard.readSemantics().snapshotRowSource()).isEqualTo("dashboard_snapshots");
        assertThat(dashboard.readSemantics().acceptedAtCutoffApplied()).isFalse();
        assertThat(dashboard.readSemantics().includesLateAcceptedMetrics()).isTrue();
        assertThat(dashboard.readSemantics().mayDifferFromStoredApplicationSnapshot()).isTrue();
        assertThat(dashboard.readSemantics().applicationSnapshotRecalculated()).isFalse();
        assertThat(dashboard.readSemantics().instanceEvidenceReconstructedFromMetrics()).isTrue();
        assertThat(dashboard.readSemantics().markerIsStateSource()).isFalse();
        assertThat(dashboard.signals().red().requestCount()).isEqualTo(42L);
        assertThat(dashboard.signals().red().errorCount()).isEqualTo(3L);

        verify(metricBucketRepository).findWindowAggregateByApplicationInstanceId(
                INSTANCE_ID,
                SNAPSHOT_WINDOW_START.toInstant(),
                SNAPSHOT_WINDOW_END.toInstant());
        verify(metricBucketRepository).findEndpointEvidenceRowsByApplicationInstanceId(
                INSTANCE_ID,
                SNAPSHOT_WINDOW_START.toInstant(),
                SNAPSHOT_WINDOW_END.toInstant());
        verify(metricBucketRepository, never()).findWindowAggregateByApplicationInstanceIdAcceptedAtOrBefore(
                any(),
                any(),
                any(),
                any());
        verify(metricBucketRepository, never()).findLatestBucketEndUtcByApplicationInstanceIdAtOrBeforeAcceptedAt(
                any(),
                any(),
                any());
        verify(metricBucketRepository, never()).findEndpointEvidenceRowsByApplicationInstanceIdAcceptedAtOrBefore(
                any(),
                any(),
                any(),
                any());
        verify(metricBucketRepository, never()).findLatestRuntimeRatioEvidenceRowByApplicationInstanceIdAcceptedAtOrBefore(
                any(),
                any(),
                any(),
                any());
    }

    @Test
    @DisplayName("snapshot row 또는 catalog membership이 없으면 live fallback 없이 empty로 수렴한다")
    void snapshotDashboardMissingRowsReturnEmptyWithoutMetricFallback() {
        when(snapshotRepository.findDetailRow(PROJECT_ID, APPLICATION_ID, SNAPSHOT_ID)).thenReturn(Optional.empty());

        assertThat(service.getSnapshotDashboard(PROJECT_ID, APPLICATION_ID, SNAPSHOT_ID, INSTANCE_ID)).isEmpty();

        verify(snapshotRepository).findDetailRow(PROJECT_ID, APPLICATION_ID, SNAPSHOT_ID);
        verify(metricBucketRepository, never()).findWindowAggregateByApplicationInstanceId(
                any(),
                any(),
                any());

        when(applicationRepository.findByIdAndProjectId(APPLICATION_ID, PROJECT_ID)).thenReturn(Optional.empty());

        assertThat(service.getLiveDashboard(PROJECT_ID, APPLICATION_ID, INSTANCE_ID)).isEmpty();
        verifyNoInteractions(heartbeatRepository);
    }

    @Test
    @DisplayName("snapshot window에 selected instance bucket이 없으면 current metric으로 복원하지 않는다")
    void snapshotDashboardMissingMetricEvidenceConvergesToDataQualityLimitation() {
        when(snapshotRepository.findDetailRow(PROJECT_ID, APPLICATION_ID, SNAPSHOT_ID))
                .thenReturn(Optional.of(snapshotRow("""
                        {"instanceSummary":{"items":[]}}
                        """)));
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationInstanceIdAtOrBefore(
                INSTANCE_ID,
                SNAPSHOT_WINDOW_END.toInstant()))
                .thenReturn(Optional.empty());
        when(metricBucketRepository.findWindowAggregateByApplicationInstanceId(
                INSTANCE_ID,
                SNAPSHOT_WINDOW_START.toInstant(),
                SNAPSHOT_WINDOW_END.toInstant()))
                .thenReturn(WindowBucketAggregate.zero());

        InstanceDashboardReadModel dashboard = service.getSnapshotDashboard(
                        PROJECT_ID,
                        APPLICATION_ID,
                        SNAPSHOT_ID,
                        INSTANCE_ID)
                .orElseThrow();

        assertThat(dashboard.observationStatus().code()).isEqualTo("metric_missing");
        assertThat(dashboard.dataQuality().state()).isEqualTo("metric_missing");
        assertThat(dashboard.dataQuality().limitations()).contains("no_metric_bucket_for_selected_snapshot_window");
        assertThat(dashboard.readSemantics().mayDifferFromStoredApplicationSnapshot()).isTrue();
        verify(metricBucketRepository).findWindowAggregateByApplicationInstanceId(
                INSTANCE_ID,
                SNAPSHOT_WINDOW_START.toInstant(),
                SNAPSHOT_WINDOW_END.toInstant());
        verify(metricBucketRepository, never()).findWindowAggregateByApplicationInstanceId(
                INSTANCE_ID,
                LIVE_WINDOW_START,
                EVALUATION_AT);
    }

    private void stubWindowMetricEvidence(
            Instant windowStartUtc,
            Instant windowEndUtc,
            long requestCount,
            long errorCount) {
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationInstanceIdAtOrBefore(
                INSTANCE_ID,
                windowEndUtc))
                .thenReturn(Optional.of(offset(windowEndUtc.minusSeconds(30))));
        when(metricBucketRepository.findWindowAggregateByApplicationInstanceId(
                INSTANCE_ID,
                windowStartUtc,
                windowEndUtc))
                .thenReturn(new WindowBucketAggregate(requestCount, errorCount));
        when(metricBucketRepository.findSummaryDurationBucketEvidenceRowsByApplicationInstanceId(
                INSTANCE_ID,
                windowStartUtc,
                windowEndUtc))
                .thenReturn(List.of(histogramRow(windowEndUtc.minusSeconds(30))));
        when(metricBucketRepository.findLatestRuntimeRatioEvidenceRowByApplicationInstanceId(
                INSTANCE_ID,
                windowStartUtc,
                windowEndUtc))
                .thenReturn(Optional.of(new RuntimeRatioEvidenceRow(
                        APPLICATION_ID,
                        offset(windowEndUtc.minusSeconds(30)),
                        offset(windowEndUtc),
                        BigDecimal.valueOf(0.72d),
                        BigDecimal.valueOf(0.61d),
                        BigDecimal.valueOf(0.44d))));
        when(metricBucketRepository.findEndpointEvidenceRowsByApplicationInstanceId(
                INSTANCE_ID,
                windowStartUtc,
                windowEndUtc))
                .thenReturn(List.of(endpointRow(windowEndUtc.minusSeconds(30))));
    }

    private static DashboardSnapshotDetailRow snapshotRow(String readModelJson) {
        return snapshotRowAt(SNAPSHOT_WINDOW_START, SNAPSHOT_WINDOW_END, readModelJson);
    }

    private static DashboardSnapshotDetailRow snapshotRowAt(
            OffsetDateTime currentWindowStartUtc,
            OffsetDateTime currentWindowEndUtc,
            String readModelJson) {
        return new DashboardSnapshotDetailRow(
                SNAPSHOT_ID,
                PROJECT_ID,
                APPLICATION_ID,
                currentWindowEndUtc.plusSeconds(5),
                currentWindowStartUtc,
                currentWindowEndUtc,
                currentWindowStartUtc.minusMinutes(30),
                currentWindowStartUtc,
                "degraded",
                "hourly_scheduled",
                "application_error_rate_high",
                "POST /orders",
                BigDecimal.valueOf(0.83d),
                readModelJson);
    }

    private static ApplicationEntity application() {
        return new ApplicationEntity(
                APPLICATION_ID,
                PROJECT_ID,
                "orders-api",
                "prod",
                "active",
                offset("2026-06-10T07:00:00Z"),
                offset("2026-06-10T08:10:00Z"),
                offset("2026-06-10T07:00:00Z"),
                offset("2026-06-10T08:10:00Z"));
    }

    private static ApplicationInstanceEntity instance() {
        return new ApplicationInstanceEntity(
                INSTANCE_ID,
                APPLICATION_ID,
                "pod-a",
                offset("2026-06-10T07:00:30Z"),
                offset("2026-06-10T08:10:00Z"),
                offset("2026-06-10T07:00:30Z"),
                offset("2026-06-10T08:10:00Z"));
    }

    private static HistogramBucketEvidenceRow histogramRow(Instant bucketStartUtc) {
        return new HistogramBucketEvidenceRow(
                APPLICATION_ID,
                offset(bucketStartUtc),
                offset(bucketStartUtc.plusSeconds(30)),
                """
                        [
                          {"leMs": 500, "count": 30},
                          {"leMs": 1000, "count": 42}
                        ]
                        """);
    }

    private static EndpointEvidenceRow endpointRow(Instant bucketStartUtc) {
        return endpointRow(bucketStartUtc, "POST", "/orders", 42L, 3L);
    }

    private static EndpointEvidenceRow endpointRow(
            Instant bucketStartUtc,
            String method,
            String route,
            long requestCount,
            long errorCount) {
        return new EndpointEvidenceRow(
                APPLICATION_ID,
                offset(bucketStartUtc),
                offset(bucketStartUtc.plusSeconds(30)),
                """
                        [
                          {
                            "method": "%s",
                            "route": "%s",
                            "requestCount": %d,
                            "errorCount": %d
                          }
                        ]
                        """.formatted(method, route, requestCount, errorCount));
    }

    private static OffsetDateTime offset(String instant) {
        return offset(Instant.parse(instant));
    }

    private static OffsetDateTime offset(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
