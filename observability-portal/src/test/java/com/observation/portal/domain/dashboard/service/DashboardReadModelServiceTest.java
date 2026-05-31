package com.observation.portal.domain.dashboard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.portal.common.time.AcceptedBucketFreshnessEvaluator;
import com.observation.portal.common.time.TimeBucketWindowCalculator;
import com.observation.portal.domain.bucket.model.AcceptedBucketGapEvidence;
import com.observation.portal.domain.bucket.model.EndpointEvidenceRow;
import com.observation.portal.domain.bucket.model.HistogramBucketEvidenceRow;
import com.observation.portal.domain.bucket.model.LocalPercentileEvidenceRow;
import com.observation.portal.domain.bucket.model.RecentBucketEvidenceRow;
import com.observation.portal.domain.bucket.model.RuntimeRatioEvidenceRow;
import com.observation.portal.domain.bucket.model.WindowBucketAggregate;
import com.observation.portal.domain.bucket.repository.MetricBucketRepository;
import com.observation.portal.domain.catalog.entity.ApplicationEntity;
import com.observation.portal.domain.catalog.entity.ApplicationInstanceEntity;
import com.observation.portal.domain.catalog.repository.ApplicationInstanceRepository;
import com.observation.portal.domain.catalog.repository.ApplicationRepository;
import com.observation.portal.domain.dashboard.model.ApplicationDashboardReadModel;
import com.observation.portal.domain.ingest.model.StarterHeartbeatTelemetryRecord;
import com.observation.portal.domain.ingest.repository.StarterHeartbeatTelemetryRepository;
import com.observation.portal.domain.state.service.LifecycleStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DashboardReadModelServiceTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000005201");
    private static final UUID APPLICATION_ID = UUID.fromString("00000000-0000-0000-0000-000000005211");
    private static final UUID INSTANCE_ID = UUID.fromString("00000000-0000-0000-0000-000000005221");
    private static final Instant QUERY_AT = Instant.parse("2026-05-25T10:32:38.421Z");
    private static final Instant EVALUATION_AT = Instant.parse("2026-05-25T10:32:30Z");
    private static final Instant CURRENT_START = Instant.parse("2026-05-25T10:17:30Z");
    private static final Instant BASELINE_START = Instant.parse("2026-05-25T10:02:30Z");
    private static final Clock CLOCK = Clock.fixed(QUERY_AT, ZoneOffset.UTC);
    private static final List<String> UNSAFE_OPERATIONAL_OUTCOME_COPY = List.of(
            "host application down",
            "host down",
            "앱 정상 확정",
            "정상 확정",
            "문제 없음",
            "복구 완료",
            "장애 해결 완료");

    private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
    private final ApplicationInstanceRepository applicationInstanceRepository = mock(ApplicationInstanceRepository.class);
    private final MetricBucketRepository metricBucketRepository = mock(MetricBucketRepository.class);
    private final StarterHeartbeatTelemetryRepository heartbeatRepository =
            mock(StarterHeartbeatTelemetryRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EndpointEvidenceAggregationService endpointEvidenceAggregationService =
            new EndpointEvidenceAggregationService(objectMapper);
    private final DashboardReadModelService service = new DashboardReadModelService(
            applicationRepository,
            applicationInstanceRepository,
            metricBucketRepository,
            heartbeatRepository,
            new AcceptedBucketFreshnessEvaluator(CLOCK),
            new TimeBucketWindowCalculator(CLOCK),
            new LifecycleStateService(),
            new TriageSummaryService(objectMapper),
            new EndpointPriorityService(endpointEvidenceAggregationService),
            CLOCK,
            objectMapper);

    @BeforeEach
    void stubEmptyDashboardEvidenceRows() {
        when(metricBucketRepository.findLocalPercentileEvidenceRowsByApplicationId(
                APPLICATION_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(List.of());
        when(metricBucketRepository.findSummaryDurationBucketEvidenceRowsByApplicationId(
                APPLICATION_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(List.of());
        when(metricBucketRepository.findSummaryDurationBucketEvidenceRowsByApplicationId(
                APPLICATION_ID,
                BASELINE_START,
                CURRENT_START))
                .thenReturn(List.of());
        when(metricBucketRepository.findEndpointEvidenceRowsByApplicationId(
                APPLICATION_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(List.of());
        when(metricBucketRepository.findEndpointEvidenceRowsByApplicationId(
                APPLICATION_ID,
                BASELINE_START,
                CURRENT_START))
                .thenReturn(List.of());
        when(metricBucketRepository.findWindowAggregateByApplicationId(APPLICATION_ID, BASELINE_START, CURRENT_START))
                .thenReturn(WindowBucketAggregate.zero());
        when(metricBucketRepository.findAcceptedBucketGapEvidenceByApplicationIdAtOrBefore(
                APPLICATION_ID,
                EVALUATION_AT))
                .thenReturn(Optional.empty());
        when(metricBucketRepository.findRecentFiveBucketEvidenceRowsByApplicationIdAtOrBefore(
                APPLICATION_ID,
                EVALUATION_AT))
                .thenReturn(List.of());
        when(metricBucketRepository.findLatestRuntimeRatioEvidenceRowByApplicationId(
                APPLICATION_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(Optional.empty());
    }

    @Test
    void assemblesDashboardWithFlooredWindowMetricsAndEvidenceFields() {
        ApplicationEntity application = application();
        when(applicationRepository.findByIdAndProjectId(APPLICATION_ID, PROJECT_ID))
                .thenReturn(Optional.of(application));
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationIdAtOrBefore(APPLICATION_ID, EVALUATION_AT))
                .thenReturn(Optional.of(offset("2026-05-25T10:31:30Z")));
        when(metricBucketRepository.findWindowAggregateByApplicationId(APPLICATION_ID, CURRENT_START, EVALUATION_AT))
                .thenReturn(new WindowBucketAggregate(100L, 3L));
        when(heartbeatRepository.findLatestByApplicationScope(PROJECT_ID, "orders-api", "prod"))
                .thenReturn(Optional.of(heartbeat("2026-05-25T10:32:15Z")));

        ApplicationDashboardReadModel dashboard = service.getDashboard(PROJECT_ID, APPLICATION_ID).orElseThrow();

        assertThat(dashboard.generatedAt()).isEqualTo(offset(QUERY_AT));
        assertThat(dashboard.application().projectId()).isEqualTo(PROJECT_ID);
        assertThat(dashboard.application().applicationId()).isEqualTo(APPLICATION_ID);
        assertThat(dashboard.application().name()).isEqualTo("orders-api");
        assertThat(dashboard.application().environment()).isEqualTo("prod");
        assertThat(dashboard.application().lastAcceptedBucketAt()).isEqualTo(offset("2026-05-25T10:31:30Z"));
        assertThat(dashboard.application().lastHealthyAt()).isNull();
        assertThat(dashboard.application().sourceWindow().current().startUtc()).isEqualTo(offset(CURRENT_START));
        assertThat(dashboard.application().sourceWindow().current().endUtc()).isEqualTo(offset(EVALUATION_AT));
        assertThat(dashboard.application().sourceWindow().baseline().startUtc()).isEqualTo(offset(BASELINE_START));
        assertThat(dashboard.application().sourceWindow().baseline().endUtc()).isEqualTo(offset(CURRENT_START));
        assertThat(dashboard.application().freshness().lastObservedAt()).isEqualTo(offset("2026-05-25T10:31:30Z"));
        assertThat(dashboard.application().freshness().staleAt()).isEqualTo(offset("2026-05-25T10:33:00Z"));
        assertThat(dashboard.application().freshness().downAt()).isEqualTo(offset("2026-05-25T10:34:30Z"));
        assertThat(dashboard.state().code()).isEqualTo("active");
        assertThat(dashboard.starterConnection().statusSource()).isEqualTo("starter_heartbeat");
        assertThat(dashboard.starterConnection().lastHeartbeatAt()).isEqualTo(offset("2026-05-25T10:32:15Z"));
        assertThat(dashboard.starterConnection().lastHeartbeatStatus()).isEqualTo("received");
        assertThat(dashboard.starterConnection().connectionMeaning()).isEqualTo("starter_connected");
        assertThat(dashboard.starterConnection().stateImpact()).isEqualTo("none");
        assertThat(dashboard.zeroInsight().reasonCode()).isEqualTo("no_action_needed");
        assertThat(dashboard.zeroInsight().reasonCode()).isNotEqualTo("observing_recovery");
        assertThat(dashboard.recovery().isRecovering()).isFalse();
        assertThat(dashboard.metrics().requestCount()).isEqualTo(100L);
        assertThat(dashboard.metrics().errorCount()).isEqualTo(3L);
        assertThat(dashboard.metrics().errorRate()).isEqualByComparingTo("0.03");
        assertThat(dashboard.sourceScopedPercentiles().source()).isEqualTo("starter_local");
        assertThat(dashboard.sourceScopedPercentiles().scope()).isEqualTo("instance_bucket");
        assertThat(dashboard.sourceScopedPercentiles().displayPolicy())
                .isEqualTo("latest_starter_point_per_instance_in_current_window");
        assertThat(dashboard.sourceScopedPercentiles().aggregatePolicy())
                .isEqualTo("no_average_no_max_no_merge_no_histogram_recalculation");
        assertThat(dashboard.sourceScopedPercentiles().status()).isEqualTo("missing");
        assertThat(dashboard.sourceScopedPercentiles().reason())
                .isEqualTo("no_percentile_points_in_current_window");
        assertThat(dashboard.sourceScopedPercentiles().items()).isEmpty();
        assertThat(dashboard.histogramDistribution().source()).isEqualTo("histogram_bucket_distribution");
        assertThat(dashboard.histogramDistribution().scope()).isEqualTo("application");
        assertThat(dashboard.histogramDistribution().current().status()).isEqualTo("missing");
        assertThat(dashboard.histogramDistribution().current().reason())
                .isEqualTo("no_histogram_buckets_in_current_window");
        assertThat(dashboard.histogramDistribution().current().buckets()).isEmpty();
        assertThat(dashboard.histogramDistribution().baseline().status()).isEqualTo("missing");
        assertThat(dashboard.histogramDistribution().baseline().reason())
                .isEqualTo("no_histogram_buckets_in_baseline_window");
        assertThat(dashboard.histogramDistribution().baseline().buckets()).isEmpty();
        assertThat(dashboard.triageCards()).isEmpty();
        assertThat(dashboard.endpointPriority()).isEmpty();
        assertThat(dashboard.instances()).isEmpty();
        assertThat(dashboard.snapshot()).isNull();
        assertThat(metricRecordComponentNames()).containsExactly("requestCount", "errorCount", "errorRate");
        verify(metricBucketRepository)
                .findLatestBucketEndUtcByApplicationIdAtOrBefore(APPLICATION_ID, EVALUATION_AT);
        verify(heartbeatRepository, never()).findLatestByProjectId(PROJECT_ID);
    }

    @Test
    void exposesLatestValidStarterLocalPercentilePointPerInstanceOnly() {
        when(applicationRepository.findByIdAndProjectId(APPLICATION_ID, PROJECT_ID))
                .thenReturn(Optional.of(application()));
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationIdAtOrBefore(APPLICATION_ID, EVALUATION_AT))
                .thenReturn(Optional.of(offset("2026-05-25T10:32:00Z")));
        when(metricBucketRepository.findWindowAggregateByApplicationId(APPLICATION_ID, CURRENT_START, EVALUATION_AT))
                .thenReturn(new WindowBucketAggregate(2400L, 0L));
        when(heartbeatRepository.findLatestByApplicationScope(PROJECT_ID, "orders-api", "prod"))
                .thenReturn(Optional.of(heartbeat("2026-05-25T10:32:20Z")));
        when(metricBucketRepository.findLocalPercentileEvidenceRowsByApplicationId(
                APPLICATION_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(List.of(
                        percentileRow("00000000-0000-0000-0000-000000005221",
                                "pod-a",
                                "2026-05-25T10:20:00Z",
                                700L,
                                410L,
                                900L),
                        percentileRow("00000000-0000-0000-0000-000000005221",
                                "pod-a",
                                "2026-05-25T10:31:30Z",
                                1200L,
                                480L,
                                960L),
                        percentileRow("00000000-0000-0000-0000-000000005222",
                                "pod-b",
                                "2026-05-25T10:30:30Z",
                                900L,
                                520L,
                                1100L),
                        percentileRowWithJsonBoundary(
                                "00000000-0000-0000-0000-000000005223",
                                "pod-c",
                                "2026-05-25T10:29:30Z",
                                "2026-05-25T10:28:30Z",
                                400L)));

        ApplicationDashboardReadModel dashboard = service.getDashboard(PROJECT_ID, APPLICATION_ID).orElseThrow();

        assertThat(dashboard.sourceScopedPercentiles().status()).isEqualTo("available");
        assertThat(dashboard.sourceScopedPercentiles().reason()).isNull();
        assertThat(dashboard.sourceScopedPercentiles().items())
                .extracting(
                        ApplicationDashboardReadModel.PercentileItem::instance,
                        ApplicationDashboardReadModel.PercentileItem::bucketEndUtc,
                        ApplicationDashboardReadModel.PercentileItem::requestCount,
                        ApplicationDashboardReadModel.PercentileItem::p95Ms,
                        ApplicationDashboardReadModel.PercentileItem::p99Ms)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "pod-a",
                                offset("2026-05-25T10:32:00Z"),
                                1200L,
                                480L,
                                960L),
                        org.assertj.core.groups.Tuple.tuple(
                                "pod-b",
                                offset("2026-05-25T10:31:00Z"),
                                900L,
                                520L,
                                1100L));
        assertThat(dashboard.sourceScopedPercentiles().items())
                .allSatisfy(item -> {
                    assertThat(item.source()).isEqualTo("starter_local");
                    assertThat(item.application()).isEqualTo("orders-api");
                    assertThat(item.environment()).isEqualTo("prod");
                });
        verify(metricBucketRepository).findLocalPercentileEvidenceRowsByApplicationId(
                APPLICATION_ID,
                CURRENT_START,
                EVALUATION_AT);
    }

    @Test
    void marksPercentileEvidenceInsufficientWhenRowsExistButNoValidPoint() {
        when(applicationRepository.findByIdAndProjectId(APPLICATION_ID, PROJECT_ID))
                .thenReturn(Optional.of(application()));
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationIdAtOrBefore(APPLICATION_ID, EVALUATION_AT))
                .thenReturn(Optional.of(offset("2026-05-25T10:32:00Z")));
        when(metricBucketRepository.findWindowAggregateByApplicationId(APPLICATION_ID, CURRENT_START, EVALUATION_AT))
                .thenReturn(new WindowBucketAggregate(0L, 0L));
        when(heartbeatRepository.findLatestByApplicationScope(PROJECT_ID, "orders-api", "prod"))
                .thenReturn(Optional.of(heartbeat("2026-05-25T10:32:20Z")));
        when(metricBucketRepository.findLocalPercentileEvidenceRowsByApplicationId(
                APPLICATION_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(List.of(percentileRow(
                        "00000000-0000-0000-0000-000000005221",
                        "pod-a",
                        "2026-05-25T10:31:30Z",
                        0L,
                        0L,
                        0L)));

        ApplicationDashboardReadModel dashboard = service.getDashboard(PROJECT_ID, APPLICATION_ID).orElseThrow();

        assertThat(dashboard.sourceScopedPercentiles().items()).isEmpty();
        assertThat(dashboard.sourceScopedPercentiles().status()).isEqualTo("insufficient");
        assertThat(dashboard.sourceScopedPercentiles().reason())
                .isEqualTo("no_valid_percentile_points_in_current_window");
    }

    @Test
    void excludesPercentileRowsWithMissingBoundaryTimestampWithoutThrowing() {
        when(applicationRepository.findByIdAndProjectId(APPLICATION_ID, PROJECT_ID))
                .thenReturn(Optional.of(application()));
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationIdAtOrBefore(APPLICATION_ID, EVALUATION_AT))
                .thenReturn(Optional.of(offset("2026-05-25T10:32:00Z")));
        when(metricBucketRepository.findWindowAggregateByApplicationId(APPLICATION_ID, CURRENT_START, EVALUATION_AT))
                .thenReturn(new WindowBucketAggregate(1200L, 0L));
        when(heartbeatRepository.findLatestByApplicationScope(PROJECT_ID, "orders-api", "prod"))
                .thenReturn(Optional.of(heartbeat("2026-05-25T10:32:20Z")));
        when(metricBucketRepository.findLocalPercentileEvidenceRowsByApplicationId(
                APPLICATION_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(List.of(
                        percentileRowWithRawJson(
                                "00000000-0000-0000-0000-000000005221",
                                "pod-a",
                                "2026-05-25T10:31:30Z",
                                """
                                {
                                  "scope": "instance_bucket",
                                  "source": "starter_local",
                                  "bucketEndUtc": "2026-05-25T10:32:00Z",
                                  "requestCount": 1200,
                                  "p95Ms": 480,
                                  "p99Ms": 960,
                                  "mergeable": false
                                }
                                """),
                        percentileRowWithRawJson(
                                "00000000-0000-0000-0000-000000005222",
                                "pod-b",
                                "2026-05-25T10:30:30Z",
                                """
                                {
                                  "scope": "instance_bucket",
                                  "source": "starter_local",
                                  "bucketStartUtc": "2026-05-25T10:30:30Z",
                                  "bucketEndUtc": null,
                                  "requestCount": 900,
                                  "p95Ms": 520,
                                  "p99Ms": 1100,
                                  "mergeable": false
                                }
                                """)));

        ApplicationDashboardReadModel dashboard = service.getDashboard(PROJECT_ID, APPLICATION_ID).orElseThrow();

        assertThat(dashboard.sourceScopedPercentiles().items()).isEmpty();
        assertThat(dashboard.sourceScopedPercentiles().status()).isEqualTo("insufficient");
        assertThat(dashboard.sourceScopedPercentiles().reason())
                .isEqualTo("no_valid_percentile_points_in_current_window");
    }

    @Test
    void mergesCurrentAndBaselineHistogramDistributionWhenBoundarySetsMatch() {
        when(applicationRepository.findByIdAndProjectId(APPLICATION_ID, PROJECT_ID))
                .thenReturn(Optional.of(application()));
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationIdAtOrBefore(APPLICATION_ID, EVALUATION_AT))
                .thenReturn(Optional.of(offset("2026-05-25T10:32:00Z")));
        when(metricBucketRepository.findWindowAggregateByApplicationId(APPLICATION_ID, CURRENT_START, EVALUATION_AT))
                .thenReturn(new WindowBucketAggregate(30L, 0L));
        when(heartbeatRepository.findLatestByApplicationScope(PROJECT_ID, "orders-api", "prod"))
                .thenReturn(Optional.of(heartbeat("2026-05-25T10:32:20Z")));
        when(metricBucketRepository.findSummaryDurationBucketEvidenceRowsByApplicationId(
                APPLICATION_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(List.of(
                        histogramRow("2026-05-25T10:30:00Z", 50L, 10L, 100L, 18L),
                        histogramRow("2026-05-25T10:31:30Z", 50L, 12L, 100L, 24L)));
        when(metricBucketRepository.findSummaryDurationBucketEvidenceRowsByApplicationId(
                APPLICATION_ID,
                BASELINE_START,
                CURRENT_START))
                .thenReturn(List.of(histogramRow("2026-05-25T10:16:30Z", 50L, 5L, 100L, 9L)));

        ApplicationDashboardReadModel dashboard = service.getDashboard(PROJECT_ID, APPLICATION_ID).orElseThrow();

        assertThat(dashboard.histogramDistribution().current().status()).isEqualTo("available");
        assertThat(dashboard.histogramDistribution().current().reason()).isNull();
        assertThat(dashboard.histogramDistribution().current().totalCount()).isEqualTo(42L);
        assertThat(dashboard.histogramDistribution().current().buckets())
                .extracting(
                        ApplicationDashboardReadModel.HistogramBucket::leMs,
                        ApplicationDashboardReadModel.HistogramBucket::count)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(50L, 22L),
                        org.assertj.core.groups.Tuple.tuple(100L, 42L));
        assertThat(dashboard.histogramDistribution().baseline().status()).isEqualTo("available");
        assertThat(dashboard.histogramDistribution().baseline().totalCount()).isEqualTo(9L);
    }

    @Test
    void marksOnlyMismatchedHistogramWindowUnavailable() {
        when(applicationRepository.findByIdAndProjectId(APPLICATION_ID, PROJECT_ID))
                .thenReturn(Optional.of(application()));
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationIdAtOrBefore(APPLICATION_ID, EVALUATION_AT))
                .thenReturn(Optional.of(offset("2026-05-25T10:32:00Z")));
        when(metricBucketRepository.findWindowAggregateByApplicationId(APPLICATION_ID, CURRENT_START, EVALUATION_AT))
                .thenReturn(new WindowBucketAggregate(30L, 0L));
        when(heartbeatRepository.findLatestByApplicationScope(PROJECT_ID, "orders-api", "prod"))
                .thenReturn(Optional.of(heartbeat("2026-05-25T10:32:20Z")));
        when(metricBucketRepository.findSummaryDurationBucketEvidenceRowsByApplicationId(
                APPLICATION_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(List.of(
                        histogramRow("2026-05-25T10:30:00Z", 50L, 10L, 100L, 18L),
                        histogramRow("2026-05-25T10:31:30Z", 50L, 12L, 250L, 24L)));
        when(metricBucketRepository.findSummaryDurationBucketEvidenceRowsByApplicationId(
                APPLICATION_ID,
                BASELINE_START,
                CURRENT_START))
                .thenReturn(List.of(histogramRow("2026-05-25T10:16:30Z", 50L, 5L, 100L, 9L)));

        ApplicationDashboardReadModel dashboard = service.getDashboard(PROJECT_ID, APPLICATION_ID).orElseThrow();

        assertThat(dashboard.histogramDistribution().current().status()).isEqualTo("unavailable");
        assertThat(dashboard.histogramDistribution().current().reason()).isEqualTo("histogram_boundary_mismatch");
        assertThat(dashboard.histogramDistribution().current().totalCount()).isZero();
        assertThat(dashboard.histogramDistribution().current().buckets()).isEmpty();
        assertThat(dashboard.histogramDistribution().baseline().status()).isEqualTo("available");
        assertThat(dashboard.histogramDistribution().baseline().buckets()).isNotEmpty();
    }

    @Test
    void keepsTinyNonZeroErrorRateFromSerializingAsZero() {
        ApplicationDashboardReadModel dashboard = dashboard(
                "2026-05-25T10:32:00Z",
                "2026-05-25T10:32:20Z",
                3_000_000L,
                1L);

        assertThat(dashboard.metrics().errorRate()).isNotNull();
        assertThat(dashboard.metrics().errorRate()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void keepsCommonErrorRateMeaning() {
        ApplicationDashboardReadModel dashboard = dashboard(
                "2026-05-25T10:32:00Z",
                "2026-05-25T10:32:20Z",
                100L,
                3L);

        assertThat(dashboard.metrics().errorRate()).isEqualByComparingTo("0.03");
    }

    @Test
    void returnsNullErrorRateWhenRequestCountIsZero() {
        ApplicationDashboardReadModel dashboard = dashboard(
                "2026-05-25T10:32:00Z",
                "2026-05-25T10:32:20Z",
                0L,
                0L);

        assertThat(dashboard.metrics().errorRate()).isNull();
    }

    @Test
    void futureAcceptedBucketDoesNotChangeCurrentWindowOrFreshness() {
        when(applicationRepository.findByIdAndProjectId(APPLICATION_ID, PROJECT_ID))
                .thenReturn(Optional.of(application()));
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationIdAtOrBefore(APPLICATION_ID, EVALUATION_AT))
                .thenReturn(Optional.empty());
        when(metricBucketRepository.findWindowAggregateByApplicationId(APPLICATION_ID, CURRENT_START, EVALUATION_AT))
                .thenReturn(WindowBucketAggregate.zero());
        when(heartbeatRepository.findLatestByApplicationScope(PROJECT_ID, "orders-api", "prod"))
                .thenReturn(Optional.of(heartbeat("2026-05-25T10:32:20Z")));

        ApplicationDashboardReadModel dashboard = service.getDashboard(PROJECT_ID, APPLICATION_ID).orElseThrow();

        assertThat(dashboard.generatedAt()).isEqualTo(offset(QUERY_AT));
        assertThat(dashboard.application().sourceWindow().current().endUtc()).isEqualTo(offset(EVALUATION_AT));
        assertThat(dashboard.application().lastAcceptedBucketAt()).isNull();
        assertThat(dashboard.application().freshness().lastObservedAt()).isNull();
        assertThat(dashboard.zeroInsight().reasonCode()).isEqualTo("waiting_first_data");
        verify(metricBucketRepository)
                .findLatestBucketEndUtcByApplicationIdAtOrBefore(APPLICATION_ID, EVALUATION_AT);
        verify(metricBucketRepository, never()).findLatestBucketEndUtcByApplicationId(APPLICATION_ID);
    }

    @Test
    void returnsEmptyWhenApplicationDoesNotBelongToProject() {
        when(applicationRepository.findByIdAndProjectId(APPLICATION_ID, PROJECT_ID)).thenReturn(Optional.empty());

        assertThat(service.getDashboard(PROJECT_ID, APPLICATION_ID)).isEmpty();

        verifyNoInteractions(applicationInstanceRepository, metricBucketRepository, heartbeatRepository);
    }

    @Test
    void exposesBoundedInstanceEvidenceEntriesWithUuidLinks() {
        when(applicationRepository.findByIdAndProjectId(APPLICATION_ID, PROJECT_ID))
                .thenReturn(Optional.of(application()));
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationIdAtOrBefore(APPLICATION_ID, EVALUATION_AT))
                .thenReturn(Optional.of(offset("2026-05-25T10:32:00Z")));
        when(metricBucketRepository.findWindowAggregateByApplicationId(APPLICATION_ID, CURRENT_START, EVALUATION_AT))
                .thenReturn(new WindowBucketAggregate(100L, 0L));
        when(heartbeatRepository.findLatestByApplicationScope(PROJECT_ID, "orders-api", "prod"))
                .thenReturn(Optional.of(heartbeat("2026-05-25T10:32:20Z")));
        when(applicationInstanceRepository.findByApplicationIdOrderByLastSeenAtDescInstanceNameAsc(
                APPLICATION_ID,
                PageRequest.of(0, 50)))
                .thenReturn(instanceRows(51));

        ApplicationDashboardReadModel dashboard = service.getDashboard(PROJECT_ID, APPLICATION_ID).orElseThrow();

        assertThat(dashboard.instances()).hasSize(50);
        assertThat(dashboard.instances().get(0)).satisfies(entry -> {
            assertThat(entry.instanceId()).isEqualTo(INSTANCE_ID);
            assertThat(entry.instanceName()).isEqualTo("pod-a");
            assertThat(entry.lastSeenAt()).isEqualTo(offset("2026-05-25T10:31:30Z"));
            assertThat(entry.links().evidence())
                    .isEqualTo("/api/projects/%s/applications/%s/instances/%s/evidence"
                            .formatted(PROJECT_ID, APPLICATION_ID, INSTANCE_ID));
        });
        verify(applicationInstanceRepository).findByApplicationIdOrderByLastSeenAtDescInstanceNameAsc(
                APPLICATION_ID,
                PageRequest.of(0, 50));
    }

    @Test
    void mapsNoAcceptedBucketWithRecentHeartbeatToWaitingFirstData() {
        assertZeroInsight(null, "2026-05-25T10:32:20Z", 0L, "waiting_first_data");
    }

    @Test
    void mapsNoAcceptedBucketWithMissingHeartbeatToTelemetryUnreachable() {
        assertZeroInsight(null, null, 0L, "telemetry_unreachable");
    }

    @Test
    void mapsStaleAcceptedBucketWithRecentHeartbeatToMetricDataIdle() {
        assertZeroInsight("2026-05-25T10:30:30Z", "2026-05-25T10:32:20Z", 0L, "metric_data_idle");
    }

    @Test
    void mapsStaleAcceptedBucketWithStaleHeartbeatToTelemetryUnreachable() {
        assertZeroInsight("2026-05-25T10:30:30Z", "2026-05-25T10:29:00Z", 0L, "telemetry_unreachable");
    }

    @Test
    void mapsCurrentAcceptedBucketWithInsufficientSample() {
        assertZeroInsight("2026-05-25T10:32:00Z", "2026-05-25T10:32:20Z", 3L, "insufficient_sample");
    }

    @Test
    void mapsCurrentAcceptedBucketWithIdleTraffic() {
        assertZeroInsight("2026-05-25T10:32:00Z", "2026-05-25T10:32:20Z", 0L, "metric_data_idle");
    }

    @Test
    void mapsCurrentAcceptedBucketWithSufficientActiveTraffic() {
        assertZeroInsight("2026-05-25T10:32:00Z", null, 50L, "no_action_needed");
    }

    @Test
    void mapsPreviousDownGapAndCurrentInsufficientSampleToObservingRecovery() {
        when(applicationRepository.findByIdAndProjectId(APPLICATION_ID, PROJECT_ID))
                .thenReturn(Optional.of(application()));
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationIdAtOrBefore(APPLICATION_ID, EVALUATION_AT))
                .thenReturn(Optional.of(offset("2026-05-25T10:32:00Z")));
        when(metricBucketRepository.findAcceptedBucketGapEvidenceByApplicationIdAtOrBefore(
                APPLICATION_ID,
                EVALUATION_AT))
                .thenReturn(Optional.of(new AcceptedBucketGapEvidence(
                        offset("2026-05-25T10:32:00Z"),
                        Optional.of(offset("2026-05-25T10:28:30Z")))));
        when(metricBucketRepository.findWindowAggregateByApplicationId(APPLICATION_ID, CURRENT_START, EVALUATION_AT))
                .thenReturn(new WindowBucketAggregate(3L, 0L));
        when(heartbeatRepository.findLatestByApplicationScope(PROJECT_ID, "orders-api", "prod"))
                .thenReturn(Optional.empty());

        ApplicationDashboardReadModel dashboard = service.getDashboard(PROJECT_ID, APPLICATION_ID).orElseThrow();

        assertThat(dashboard.state().code()).isEqualTo("unknown");
        assertThat(dashboard.recovery().isRecovering()).isTrue();
        assertThat(dashboard.recovery().lastHealthyAt()).isNull();
        assertThat(dashboard.recovery().retryAfterSeconds()).isEqualTo(30);
        assertThat(dashboard.zeroInsight()).isNotNull();
        assertThat(dashboard.zeroInsight().reasonCode()).isEqualTo("observing_recovery");
    }

    @Test
    void doesNotInferRecoveryFromCurrentBucketWithoutPreviousGapEvidence() {
        when(applicationRepository.findByIdAndProjectId(APPLICATION_ID, PROJECT_ID))
                .thenReturn(Optional.of(application()));
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationIdAtOrBefore(APPLICATION_ID, EVALUATION_AT))
                .thenReturn(Optional.of(offset("2026-05-25T10:32:00Z")));
        when(metricBucketRepository.findAcceptedBucketGapEvidenceByApplicationIdAtOrBefore(
                APPLICATION_ID,
                EVALUATION_AT))
                .thenReturn(Optional.of(new AcceptedBucketGapEvidence(
                        offset("2026-05-25T10:32:00Z"),
                        Optional.empty())));
        when(metricBucketRepository.findWindowAggregateByApplicationId(APPLICATION_ID, CURRENT_START, EVALUATION_AT))
                .thenReturn(new WindowBucketAggregate(3L, 0L));
        when(heartbeatRepository.findLatestByApplicationScope(PROJECT_ID, "orders-api", "prod"))
                .thenReturn(Optional.of(heartbeat("2026-05-25T10:32:20Z")));

        ApplicationDashboardReadModel dashboard = service.getDashboard(PROJECT_ID, APPLICATION_ID).orElseThrow();

        assertThat(dashboard.recovery().isRecovering()).isFalse();
        assertThat(dashboard.zeroInsight().reasonCode()).isEqualTo("insufficient_sample");
    }

    @Test
    void exposesTriageCardWhileStateCanRemainActiveWhenConfidenceBelowDegradedEnter() {
        when(applicationRepository.findByIdAndProjectId(APPLICATION_ID, PROJECT_ID))
                .thenReturn(Optional.of(application()));
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationIdAtOrBefore(APPLICATION_ID, EVALUATION_AT))
                .thenReturn(Optional.of(offset("2026-05-25T10:32:00Z")));
        when(metricBucketRepository.findWindowAggregateByApplicationId(APPLICATION_ID, CURRENT_START, EVALUATION_AT))
                .thenReturn(new WindowBucketAggregate(100L, 8L));
        when(metricBucketRepository.findWindowAggregateByApplicationId(APPLICATION_ID, BASELINE_START, CURRENT_START))
                .thenReturn(new WindowBucketAggregate(100L, 2L));
        when(metricBucketRepository.findRecentFiveBucketEvidenceRowsByApplicationIdAtOrBefore(
                APPLICATION_ID,
                EVALUATION_AT))
                .thenReturn(List.of(
                        recentBucket("2026-05-25T10:31:30Z", 10L, 1L),
                        recentBucket("2026-05-25T10:31:00Z", 10L, 1L),
                        recentBucket("2026-05-25T10:30:30Z", 10L, 1L)));
        when(heartbeatRepository.findLatestByApplicationScope(PROJECT_ID, "orders-api", "prod"))
                .thenReturn(Optional.of(heartbeat("2026-05-25T10:32:20Z")));

        ApplicationDashboardReadModel dashboard = service.getDashboard(PROJECT_ID, APPLICATION_ID).orElseThrow();

        assertThat(dashboard.triageCards())
                .extracting(ApplicationDashboardReadModel.TriageCard::ruleId)
                .contains("global_error_spike");
        assertThat(dashboard.triageCards().get(0).confidence()).isLessThan(0.75d);
        assertThat(dashboard.state().code()).isEqualTo("active");
        assertThat(dashboard.zeroInsight()).isNull();
        assertThat(dashboard.endpointPriority()).isEmpty();
    }

    @Test
    void assemblesEndpointPriorityFromCurrentAndBaselineEndpointEvidence() {
        when(applicationRepository.findByIdAndProjectId(APPLICATION_ID, PROJECT_ID))
                .thenReturn(Optional.of(application()));
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationIdAtOrBefore(APPLICATION_ID, EVALUATION_AT))
                .thenReturn(Optional.of(offset("2026-05-25T10:32:00Z")));
        when(metricBucketRepository.findWindowAggregateByApplicationId(APPLICATION_ID, CURRENT_START, EVALUATION_AT))
                .thenReturn(new WindowBucketAggregate(200L, 0L));
        when(metricBucketRepository.findWindowAggregateByApplicationId(APPLICATION_ID, BASELINE_START, CURRENT_START))
                .thenReturn(new WindowBucketAggregate(200L, 0L));
        when(metricBucketRepository.findEndpointEvidenceRowsByApplicationId(
                APPLICATION_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(List.of(endpointEvidenceRow(
                        "2026-05-25T10:31:30Z",
                        endpointJson("POST", "/orders", 120L, 12L, 70L, 120L))));
        when(metricBucketRepository.findEndpointEvidenceRowsByApplicationId(
                APPLICATION_ID,
                BASELINE_START,
                CURRENT_START))
                .thenReturn(List.of(endpointEvidenceRow(
                        "2026-05-25T10:16:30Z",
                        endpointJson("POST", "/orders", 120L, 1L, 114L, 120L))));
        when(heartbeatRepository.findLatestByApplicationScope(PROJECT_ID, "orders-api", "prod"))
                .thenReturn(Optional.of(heartbeat("2026-05-25T10:32:20Z")));

        ApplicationDashboardReadModel dashboard = service.getDashboard(PROJECT_ID, APPLICATION_ID).orElseThrow();

        assertThat(dashboard.endpointPriority()).singleElement().satisfies(item -> {
            assertThat(item.rank()).isEqualTo(1);
            assertThat(item.endpointKey()).isEqualTo("POST /orders");
            assertThat(item.reason())
                    .isEqualTo(ApplicationDashboardReadModel.EndpointPriorityReason.ERROR_AND_LATENCY);
            assertThat(item.ruleIds()).containsExactly("endpoint_error_spike", "endpoint_latency_spike");
            assertThat(item.freshness().lastObservedAt()).isEqualTo(offset("2026-05-25T10:32:00Z"));
            assertThat(item.evidence().requestCount()).isEqualTo(120L);
            assertThat(item.evidence().baselineRequestCount()).isEqualTo(120L);
            assertThat(item.evidence().bucketDistributionSource()).isEqualTo("histogram_bucket_distribution");
            assertThat(item.recommendedAction()).contains("먼저 확인");
        });
        verify(metricBucketRepository).findEndpointEvidenceRowsByApplicationId(
                APPLICATION_ID,
                CURRENT_START,
                EVALUATION_AT);
        verify(metricBucketRepository).findEndpointEvidenceRowsByApplicationId(
                APPLICATION_ID,
                BASELINE_START,
                CURRENT_START);
    }

    @Test
    void suppressesEndpointPriorityWhenCurrentAggregateSampleIsInsufficient() {
        when(applicationRepository.findByIdAndProjectId(APPLICATION_ID, PROJECT_ID))
                .thenReturn(Optional.of(application()));
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationIdAtOrBefore(APPLICATION_ID, EVALUATION_AT))
                .thenReturn(Optional.of(offset("2026-05-25T10:32:00Z")));
        when(metricBucketRepository.findWindowAggregateByApplicationId(APPLICATION_ID, CURRENT_START, EVALUATION_AT))
                .thenReturn(new WindowBucketAggregate(3L, 0L));
        when(metricBucketRepository.findWindowAggregateByApplicationId(APPLICATION_ID, BASELINE_START, CURRENT_START))
                .thenReturn(new WindowBucketAggregate(200L, 0L));
        when(metricBucketRepository.findEndpointEvidenceRowsByApplicationId(
                APPLICATION_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(List.of(endpointEvidenceRow(
                        "2026-05-25T10:31:30Z",
                        endpointJson("POST", "/orders", 120L, 12L, 70L, 120L))));
        when(metricBucketRepository.findEndpointEvidenceRowsByApplicationId(
                APPLICATION_ID,
                BASELINE_START,
                CURRENT_START))
                .thenReturn(List.of(endpointEvidenceRow(
                        "2026-05-25T10:16:30Z",
                        endpointJson("POST", "/orders", 120L, 1L, 114L, 120L))));
        when(heartbeatRepository.findLatestByApplicationScope(PROJECT_ID, "orders-api", "prod"))
                .thenReturn(Optional.of(heartbeat("2026-05-25T10:32:20Z")));

        ApplicationDashboardReadModel dashboard = service.getDashboard(PROJECT_ID, APPLICATION_ID).orElseThrow();

        assertThat(dashboard.application().lastAcceptedBucketAt()).isEqualTo(offset("2026-05-25T10:32:00Z"));
        assertThat(dashboard.zeroInsight().reasonCode()).isEqualTo("insufficient_sample");
        assertThat(dashboard.endpointPriority()).isEmpty();
    }

    @Test
    void suppressesEndpointPriorityWhenApplicationFreshnessIsStaleEvenWithEndpointEvidence() {
        when(applicationRepository.findByIdAndProjectId(APPLICATION_ID, PROJECT_ID))
                .thenReturn(Optional.of(application()));
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationIdAtOrBefore(APPLICATION_ID, EVALUATION_AT))
                .thenReturn(Optional.of(offset("2026-05-25T10:30:30Z")));
        when(metricBucketRepository.findWindowAggregateByApplicationId(APPLICATION_ID, CURRENT_START, EVALUATION_AT))
                .thenReturn(new WindowBucketAggregate(200L, 0L));
        when(metricBucketRepository.findWindowAggregateByApplicationId(APPLICATION_ID, BASELINE_START, CURRENT_START))
                .thenReturn(new WindowBucketAggregate(200L, 0L));
        when(metricBucketRepository.findEndpointEvidenceRowsByApplicationId(
                APPLICATION_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(List.of(endpointEvidenceRow(
                        "2026-05-25T10:30:00Z",
                        endpointJson("POST", "/orders", 120L, 12L, 70L, 120L))));
        when(metricBucketRepository.findEndpointEvidenceRowsByApplicationId(
                APPLICATION_ID,
                BASELINE_START,
                CURRENT_START))
                .thenReturn(List.of(endpointEvidenceRow(
                        "2026-05-25T10:16:30Z",
                        endpointJson("POST", "/orders", 120L, 1L, 114L, 120L))));
        when(heartbeatRepository.findLatestByApplicationScope(PROJECT_ID, "orders-api", "prod"))
                .thenReturn(Optional.of(heartbeat("2026-05-25T10:32:20Z")));

        ApplicationDashboardReadModel dashboard = service.getDashboard(PROJECT_ID, APPLICATION_ID).orElseThrow();

        assertThat(dashboard.application().lastAcceptedBucketAt()).isEqualTo(offset("2026-05-25T10:30:30Z"));
        assertThat(dashboard.endpointPriority()).isEmpty();
    }

    @Test
    void keepsStateActiveWhenHighConfidenceConcernHasOnlyTwoRecentBadBuckets() {
        when(applicationRepository.findByIdAndProjectId(APPLICATION_ID, PROJECT_ID))
                .thenReturn(Optional.of(application()));
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationIdAtOrBefore(APPLICATION_ID, EVALUATION_AT))
                .thenReturn(Optional.of(offset("2026-05-25T10:32:00Z")));
        when(metricBucketRepository.findWindowAggregateByApplicationId(APPLICATION_ID, CURRENT_START, EVALUATION_AT))
                .thenReturn(new WindowBucketAggregate(100L, 9L));
        when(metricBucketRepository.findWindowAggregateByApplicationId(APPLICATION_ID, BASELINE_START, CURRENT_START))
                .thenReturn(new WindowBucketAggregate(100L, 2L));
        when(metricBucketRepository.findRecentFiveBucketEvidenceRowsByApplicationIdAtOrBefore(
                APPLICATION_ID,
                EVALUATION_AT))
                .thenReturn(List.of(
                        recentBucket("2026-05-25T10:31:30Z", 10L, 1L),
                        recentBucket("2026-05-25T10:31:00Z", 10L, 1L),
                        recentBucket("2026-05-25T10:30:30Z", 10L, 0L)));
        when(heartbeatRepository.findLatestByApplicationScope(PROJECT_ID, "orders-api", "prod"))
                .thenReturn(Optional.of(heartbeat("2026-05-25T10:32:20Z")));

        ApplicationDashboardReadModel dashboard = service.getDashboard(PROJECT_ID, APPLICATION_ID).orElseThrow();

        assertThat(dashboard.triageCards()).isNotEmpty();
        assertThat(dashboard.triageCards().get(0).confidence()).isGreaterThanOrEqualTo(0.75d);
        assertThat(dashboard.state().code()).isEqualTo("active");
    }

    @Test
    void entersDegradedOnlyWhenConfidenceAndThreeRecentBadBucketsPass() {
        when(applicationRepository.findByIdAndProjectId(APPLICATION_ID, PROJECT_ID))
                .thenReturn(Optional.of(application()));
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationIdAtOrBefore(APPLICATION_ID, EVALUATION_AT))
                .thenReturn(Optional.of(offset("2026-05-25T10:32:00Z")));
        when(metricBucketRepository.findWindowAggregateByApplicationId(APPLICATION_ID, CURRENT_START, EVALUATION_AT))
                .thenReturn(new WindowBucketAggregate(100L, 9L));
        when(metricBucketRepository.findWindowAggregateByApplicationId(APPLICATION_ID, BASELINE_START, CURRENT_START))
                .thenReturn(new WindowBucketAggregate(100L, 2L));
        when(metricBucketRepository.findRecentFiveBucketEvidenceRowsByApplicationIdAtOrBefore(
                APPLICATION_ID,
                EVALUATION_AT))
                .thenReturn(List.of(
                        recentBucket("2026-05-25T10:31:30Z", 10L, 1L),
                        recentBucket("2026-05-25T10:31:00Z", 10L, 1L),
                        recentBucket("2026-05-25T10:30:30Z", 10L, 1L)));
        when(heartbeatRepository.findLatestByApplicationScope(PROJECT_ID, "orders-api", "prod"))
                .thenReturn(Optional.of(heartbeat("2026-05-25T10:32:20Z")));

        ApplicationDashboardReadModel dashboard = service.getDashboard(PROJECT_ID, APPLICATION_ID).orElseThrow();

        assertThat(dashboard.triageCards()).isNotEmpty();
        assertThat(dashboard.state().code()).isEqualTo("degraded");
        assertThat(dashboard.zeroInsight()).isNull();
    }

    @Test
    void currentMetricDataAndStaleHeartbeatRemainSeparateAxes() {
        ApplicationDashboardReadModel dashboard = dashboard(
                "2026-05-25T10:32:00Z",
                "2026-05-25T10:29:00Z",
                50L);

        assertThat(dashboard.state().code()).isEqualTo("active");
        assertThat(dashboard.starterConnection().connectionMeaning()).isEqualTo("starter_disconnected");
        assertThat(dashboard.starterConnection().stateImpact()).isEqualTo("none");
        assertThat(dashboard.zeroInsight().reasonCode()).isEqualTo("no_action_needed");
    }

    @Test
    void demoReadModelStatesExposeSourceAwareReasonsWithoutUnsafeOutcomeCopy() {
        ApplicationDashboardReadModel heartbeatOnly = dashboard(null, "2026-05-25T10:32:20Z", 0L);

        assertThat(heartbeatOnly.state().code()).isEqualTo("waiting_first_data");
        assertThat(heartbeatOnly.application().lastAcceptedBucketAt()).isNull();
        assertThat(heartbeatOnly.starterConnection().statusSource()).isEqualTo("starter_heartbeat");
        assertThat(heartbeatOnly.starterConnection().connectionMeaning()).isEqualTo("starter_connected");
        assertThat(heartbeatOnly.starterConnection().stateImpact()).isEqualTo("none");
        assertThat(heartbeatOnly.triageCards()).isEmpty();
        assertThat(heartbeatOnly.zeroInsight().reasonCode()).isEqualTo("waiting_first_data");
        assertThat(heartbeatOnly.zeroInsight().message()).contains("accepted metric bucket");
        assertThat(heartbeatOnly.zeroInsight().recommendedAction()).contains("bucket");
        assertThat(heartbeatOnly.recovery().isRecovering()).isFalse();
        assertServerCopyDoesNotDeclareUnsafeOutcome(heartbeatOnly);

        ApplicationDashboardReadModel firstAcceptedBucket = dashboard(
                "2026-05-25T10:32:00Z",
                "2026-05-25T10:32:20Z",
                3L);

        assertThat(firstAcceptedBucket.state().code()).isEqualTo("unknown");
        assertThat(firstAcceptedBucket.application().lastAcceptedBucketAt())
                .isEqualTo(offset("2026-05-25T10:32:00Z"));
        assertThat(firstAcceptedBucket.metrics().requestCount()).isEqualTo(3L);
        assertThat(firstAcceptedBucket.triageCards()).isEmpty();
        assertThat(firstAcceptedBucket.zeroInsight().reasonCode()).isEqualTo("insufficient_sample");
        assertThat(firstAcceptedBucket.zeroInsight().message()).contains("sample");
        assertThat(firstAcceptedBucket.zeroInsight().recommendedAction()).contains("요청 sample");
        assertThat(firstAcceptedBucket.endpointPriority()).isEmpty();
        assertThat(firstAcceptedBucket.recovery().isRecovering()).isFalse();
        assertServerCopyDoesNotDeclareUnsafeOutcome(firstAcceptedBucket);

        ApplicationDashboardReadModel lowRiskBaseline = dashboard(
                "2026-05-25T10:32:00Z",
                "2026-05-25T10:32:20Z",
                50L);

        assertThat(lowRiskBaseline.state().code()).isEqualTo("active");
        assertThat(lowRiskBaseline.metrics().requestCount()).isEqualTo(50L);
        assertThat(lowRiskBaseline.triageCards()).isEmpty();
        assertThat(lowRiskBaseline.zeroInsight().reasonCode()).isEqualTo("no_action_needed");
        assertThat(lowRiskBaseline.endpointPriority()).isEmpty();
        assertThat(lowRiskBaseline.recovery().isRecovering()).isFalse();
        assertServerCopyDoesNotDeclareUnsafeOutcome(lowRiskBaseline);
    }

    private void assertZeroInsight(
            String latestBucketEndUtc,
            String latestHeartbeatAt,
            long requestCount,
            String expectedReasonCode) {
        ApplicationDashboardReadModel dashboard = dashboard(latestBucketEndUtc, latestHeartbeatAt, requestCount);

        assertThat(dashboard.zeroInsight()).isNotNull();
        assertThat(dashboard.zeroInsight().reasonCode()).isEqualTo(expectedReasonCode);
        assertThat(dashboard.zeroInsight().reasonCode()).isNotEqualTo("observing_recovery");
        assertThat(dashboard.triageCards()).isEmpty();
    }

    /**
     * Story 6.8 demo 상태 copy가 현재 read model 증거보다 강한 운영 결론을 단정하지 않는지 확인한다.
     */
    private static void assertServerCopyDoesNotDeclareUnsafeOutcome(ApplicationDashboardReadModel dashboard) {
        assertThat(String.join("\n",
                dashboard.state().label(),
                dashboard.state().rationale(),
                dashboard.state().recommendedAction(),
                nullableText(dashboard.zeroInsight() == null ? null : dashboard.zeroInsight().message()),
                nullableText(dashboard.zeroInsight() == null ? null : dashboard.zeroInsight().recommendedAction()),
                nullableText(dashboard.recovery().recommendedAction())))
                .doesNotContain(UNSAFE_OPERATIONAL_OUTCOME_COPY.toArray(String[]::new));
    }

    private static String nullableText(String value) {
        return value == null ? "" : value;
    }

    private ApplicationDashboardReadModel dashboard(
            String latestBucketEndUtc,
            String latestHeartbeatAt,
            long requestCount,
            long errorCount) {
        when(applicationRepository.findByIdAndProjectId(APPLICATION_ID, PROJECT_ID))
                .thenReturn(Optional.of(application()));
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationIdAtOrBefore(APPLICATION_ID, EVALUATION_AT))
                .thenReturn(latestBucketEndUtc == null
                        ? Optional.empty()
                        : Optional.of(offset(latestBucketEndUtc)));
        when(metricBucketRepository.findWindowAggregateByApplicationId(APPLICATION_ID, CURRENT_START, EVALUATION_AT))
                .thenReturn(new WindowBucketAggregate(requestCount, errorCount));
        when(heartbeatRepository.findLatestByApplicationScope(PROJECT_ID, "orders-api", "prod"))
                .thenReturn(latestHeartbeatAt == null
                        ? Optional.empty()
                        : Optional.of(heartbeat(latestHeartbeatAt)));

        return service.getDashboard(PROJECT_ID, APPLICATION_ID).orElseThrow();
    }

    private ApplicationDashboardReadModel dashboard(
            String latestBucketEndUtc,
            String latestHeartbeatAt,
            long requestCount) {
        return dashboard(latestBucketEndUtc, latestHeartbeatAt, requestCount, 0L);
    }

    private static List<String> metricRecordComponentNames() {
        return Arrays.stream(ApplicationDashboardReadModel.Metrics.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }

    private static LocalPercentileEvidenceRow percentileRow(
            String applicationInstanceId,
            String instanceName,
            String bucketStartUtc,
            long requestCount,
            long p95Ms,
            long p99Ms) {
        return percentileRowWithJsonBoundary(
                applicationInstanceId,
                instanceName,
                bucketStartUtc,
                bucketStartUtc,
                requestCount,
                p95Ms,
                p99Ms);
    }

    private static LocalPercentileEvidenceRow percentileRowWithJsonBoundary(
            String applicationInstanceId,
            String instanceName,
            String rowBucketStartUtc,
            String jsonBucketStartUtc,
            long requestCount) {
        return percentileRowWithJsonBoundary(
                applicationInstanceId,
                instanceName,
                rowBucketStartUtc,
                jsonBucketStartUtc,
                requestCount,
                100L,
                200L);
    }

    private static LocalPercentileEvidenceRow percentileRowWithJsonBoundary(
            String applicationInstanceId,
            String instanceName,
            String rowBucketStartUtc,
            String jsonBucketStartUtc,
            long requestCount,
            long p95Ms,
            long p99Ms) {
        OffsetDateTime rowStart = offset(rowBucketStartUtc);
        OffsetDateTime jsonStart = offset(jsonBucketStartUtc);
        OffsetDateTime jsonEnd = jsonStart.plusSeconds(30);
        return new LocalPercentileEvidenceRow(
                APPLICATION_ID,
                UUID.fromString(applicationInstanceId),
                instanceName,
                rowStart,
                rowStart.plusSeconds(30),
                """
                {
                  "scope": "instance_bucket",
                  "source": "starter_local",
                  "bucketStartUtc": "%s",
                  "bucketEndUtc": "%s",
                  "requestCount": %d,
                  "p95Ms": %d,
                  "p99Ms": %d,
                  "mergeable": false
                }
                """.formatted(jsonStart, jsonEnd, requestCount, p95Ms, p99Ms));
    }

    private static LocalPercentileEvidenceRow percentileRowWithRawJson(
            String applicationInstanceId,
            String instanceName,
            String rowBucketStartUtc,
            String localPercentilesJson) {
        OffsetDateTime rowStart = offset(rowBucketStartUtc);
        return new LocalPercentileEvidenceRow(
                APPLICATION_ID,
                UUID.fromString(applicationInstanceId),
                instanceName,
                rowStart,
                rowStart.plusSeconds(30),
                localPercentilesJson);
    }

    private static HistogramBucketEvidenceRow histogramRow(
            String bucketStartUtc,
            long firstLeMs,
            long firstCount,
            long secondLeMs,
            long secondCount) {
        OffsetDateTime start = offset(bucketStartUtc);
        return new HistogramBucketEvidenceRow(
                APPLICATION_ID,
                start,
                start.plusSeconds(30),
                """
                [
                  {"leMs": %d, "count": %d},
                  {"leMs": %d, "count": %d}
                ]
                """.formatted(firstLeMs, firstCount, secondLeMs, secondCount));
    }

    private static RecentBucketEvidenceRow recentBucket(String bucketStartUtc, long requestCount, long errorCount) {
        OffsetDateTime start = offset(bucketStartUtc);
        return new RecentBucketEvidenceRow(
                APPLICATION_ID,
                start,
                start.plusSeconds(30),
                requestCount,
                errorCount,
                """
                [
                  {"leMs": 500, "count": %d},
                  {"leMs": 1000, "count": %d}
                ]
                """.formatted(Math.max(0L, requestCount - 1L), requestCount));
    }

    private static EndpointEvidenceRow endpointEvidenceRow(String bucketStartUtc, String endpointJson) {
        OffsetDateTime start = offset(bucketStartUtc);
        return new EndpointEvidenceRow(APPLICATION_ID, start, start.plusSeconds(30), "[" + endpointJson + "]");
    }

    private static String endpointJson(
            String method,
            String route,
            long requestCount,
            long errorCount,
            long countAt500,
            long totalCount) {
        return """
                {
                  "method": "%s",
                  "route": "%s",
                  "requestCount": %d,
                  "errorCount": %d,
                  "durationBuckets": [
                    {"leMs": 500, "count": %d},
                    {"leMs": 1000, "count": %d}
                  ]
                }
                """.formatted(method, route, requestCount, errorCount, countAt500, totalCount);
    }

    private static ApplicationEntity application() {
        return new ApplicationEntity(
                APPLICATION_ID,
                PROJECT_ID,
                "orders-api",
                "prod",
                "active",
                offset("2026-05-25T10:00:00Z"),
                offset("2026-05-25T10:31:30Z"),
                offset("2026-05-25T10:00:00Z"),
                offset("2026-05-25T10:31:30Z"));
    }

    private static List<ApplicationInstanceEntity> instanceRows(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> new ApplicationInstanceEntity(
                        index == 0
                                ? INSTANCE_ID
                                : UUID.fromString("00000000-0000-0000-0000-%012d".formatted(5221 + index)),
                        APPLICATION_ID,
                        index == 0 ? "pod-a" : "pod-%02d".formatted(index),
                        offset("2026-05-25T10:00:00Z"),
                        index == 0
                                ? offset("2026-05-25T10:31:30Z")
                                : offset("2026-05-25T10:30:00Z"),
                        offset("2026-05-25T10:00:00Z"),
                        offset("2026-05-25T10:31:30Z")))
                .toList();
    }

    private static StarterHeartbeatTelemetryRecord heartbeat(String lastReceivedAtUtc) {
        OffsetDateTime receivedAt = offset(lastReceivedAtUtc);
        return new StarterHeartbeatTelemetryRecord(
                UUID.randomUUID(),
                PROJECT_ID,
                "orders-api",
                "prod",
                "pod-a",
                "0.1.0",
                receivedAt.minusSeconds(1),
                receivedAt,
                1L,
                30,
                "valid",
                "received",
                receivedAt,
                receivedAt);
    }

    private static OffsetDateTime offset(String instant) {
        return offset(Instant.parse(instant));
    }

    private static OffsetDateTime offset(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
