package com.observation.portal.domain.dashboard.controller;

import com.observation.portal.domain.dashboard.model.ApplicationDashboardReadModel;
import com.observation.portal.domain.dashboard.service.DashboardReadModelService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardControllerTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000005201");
    private static final UUID APPLICATION_ID = UUID.fromString("00000000-0000-0000-0000-000000005211");
    private static final UUID INSTANCE_ID = UUID.fromString("00000000-0000-0000-0000-000000005221");
    private static final OffsetDateTime GENERATED_AT = OffsetDateTime.parse("2026-05-25T10:32:38.421Z");
    private static final OffsetDateTime CURRENT_END = OffsetDateTime.parse("2026-05-25T10:32:30Z");

    private final DashboardReadModelService service = mock(DashboardReadModelService.class);
    private final DashboardController controller = new DashboardController(service);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

    @Test
    void getDashboardSerializesReadModelShapeAndDelegatesToService() throws Exception {
        when(service.getDashboard(PROJECT_ID, APPLICATION_ID)).thenReturn(Optional.of(readModel()));

        mockMvc.perform(get("/api/projects/{projectId}/applications/{applicationId}/dashboard",
                        PROJECT_ID,
                APPLICATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value("dashboard_read_model.v1"))
                .andExpect(jsonPath("$.mode").value("live"))
                .andExpect(jsonPath("$.generatedAt").value("2026-05-25T10:32:38.421Z"))
                .andExpect(jsonPath("$.application.projectId").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$.application.applicationId").value(APPLICATION_ID.toString()))
                .andExpect(jsonPath("$.application.name").value("orders-api"))
                .andExpect(jsonPath("$.application.environment").value("prod"))
                .andExpect(jsonPath("$.application.sourceWindow.baseline").value(nullValue()))
                .andExpect(jsonPath("$.application.sourceWindow.recent_30_minutes.endUtc").value("2026-05-25T10:32:30Z"))
                .andExpect(jsonPath("$.application.sourceWindow.current.endUtc").value("2026-05-25T10:32:30Z"))
                .andExpect(jsonPath("$.window.type").value("recent_30_minutes"))
                .andExpect(jsonPath("$.window.startUtc").value("2026-05-25T10:02:30Z"))
                .andExpect(jsonPath("$.window.endUtc").value("2026-05-25T10:32:30Z"))
                .andExpect(jsonPath("$.thresholds.minimumRequestCount").value(30))
                .andExpect(jsonPath("$.thresholds.errorRate").value(0.05))
                .andExpect(jsonPath("$.operatorSummary.headline").value("Freshness와 sample이 충분합니다."))
                .andExpect(jsonPath("$.operatorSummary.primaryProblemCode").value("application_error_rate_high"))
                .andExpect(jsonPath("$.operatorSummary.firstLookText").isNotEmpty())
                .andExpect(jsonPath("$.dataQuality.state").value("sufficient"))
                .andExpect(jsonPath("$.dataQuality.requestCount").value(100))
                .andExpect(jsonPath("$.dataQuality.limitations[0]").value("baseline_comparison_not_used_for_mvp"))
                .andExpect(jsonPath("$.dataQuality.limitations[1]").value("sourceWindow.baseline_null_public_read_model"))
                .andExpect(jsonPath("$.state.code").value("active"))
                .andExpect(jsonPath("$.starterConnection.statusSource").value("starter_heartbeat"))
                .andExpect(jsonPath("$.starterConnection.connectionMeaning").value("starter_connected"))
                .andExpect(jsonPath("$.starterConnection.stateImpact").value("none"))
                .andExpect(jsonPath("$.signals.red.requestCount").value(100))
                .andExpect(jsonPath("$.signals.red.errorSemantic").value("server_error_5xx"))
                .andExpect(jsonPath("$.signals.red.latencyEvidenceStatus").value("unavailable"))
                .andExpect(jsonPath("$.signals.use.cpuUsage.status").value("missing"))
                .andExpect(jsonPath("$.stateReasons").isEmpty())
                .andExpect(jsonPath("$.attentionEvidence[0].scope").value("application"))
                .andExpect(jsonPath("$.attentionEvidence[0].affectsLifecycleState").value(false))
                .andExpect(jsonPath("$.firstLookCandidates[0].source").value("endpointPriority"))
                .andExpect(jsonPath("$.readSemantics.source").value("accepted_metric_buckets"))
                .andExpect(jsonPath("$.readSemantics.snapshotDetailRecalculates").value(false))
                .andExpect(jsonPath("$.readSemantics.markerIsStateSource").value(false))
                .andExpect(jsonPath("$.readSemantics.baselineComparisonUsedForMvpDecision").value(false))
                .andExpect(jsonPath("$.readSemantics.helperColumnsAreStateSource").value(false))
                .andExpect(jsonPath("$.readSemantics.histogramBucketsUsedForPercentiles").value(false))
                .andExpect(jsonPath("$.readSemantics.bucketDistributionSource").value("accepted_bucket"))
                .andExpect(jsonPath("$.readSemantics.bucketEndBoundary")
                        .value("bucket_end_utc > window.startUtc and bucket_end_utc <= window.endUtc"))
                .andExpect(jsonPath("$.zeroInsight").value(nullValue()))
                .andExpect(jsonPath("$.recovery.isRecovering").value(false))
                .andExpect(jsonPath("$.metrics.requestCount").value(100))
                .andExpect(jsonPath("$.metrics.errorCount").value(3))
                .andExpect(jsonPath("$.metrics.errorRate").value(0.03))
                .andExpect(jsonPath("$.metrics.p95").doesNotExist())
                .andExpect(jsonPath("$.metrics.p99").doesNotExist())
                .andExpect(jsonPath("$.metrics.p95Ms").doesNotExist())
                .andExpect(jsonPath("$.metrics.p99Ms").doesNotExist())
                .andExpect(jsonPath("$.metrics.avgMs").doesNotExist())
                .andExpect(jsonPath("$.metrics.maxMs").doesNotExist())
                .andExpect(jsonPath("$.sourceScopedPercentiles.source").value("starter_canonical_percentile"))
                .andExpect(jsonPath("$.sourceScopedPercentiles.scope").value("instance_bucket"))
                .andExpect(jsonPath("$.sourceScopedPercentiles.status").value("available"))
                .andExpect(jsonPath("$.sourceScopedPercentiles.reason").value(nullValue()))
                .andExpect(jsonPath("$.sourceScopedPercentiles.applicationScopeFallback").doesNotExist())
                .andExpect(jsonPath("$.sourceScopedPercentiles.items[0].source").value("starter_canonical_percentile"))
                .andExpect(jsonPath("$.sourceScopedPercentiles.items[0].application").value("orders-api"))
                .andExpect(jsonPath("$.sourceScopedPercentiles.items[0].environment").value("prod"))
                .andExpect(jsonPath("$.sourceScopedPercentiles.items[0].instance").value("pod-a"))
                .andExpect(jsonPath("$.sourceScopedPercentiles.items[0].p95Ms").value(480))
                .andExpect(jsonPath("$.sourceScopedPercentiles.items[0].p99Ms").value(960))
                .andExpect(jsonPath("$.histogramDistribution.source").value("accepted_bucket"))
                .andExpect(jsonPath("$.histogramDistribution.scope").value("application"))
                .andExpect(jsonPath("$.histogramDistribution.current.status").value("available"))
                .andExpect(jsonPath("$.histogramDistribution.current.totalCount").value(42))
                .andExpect(jsonPath("$.histogramDistribution.current.buckets[0].leMs").value(50))
                .andExpect(jsonPath("$.histogramDistribution.current.buckets[0].count").value(22))
                .andExpect(jsonPath("$.histogramDistribution.current.p95Ms").doesNotExist())
                .andExpect(jsonPath("$.histogramDistribution.current.p99Ms").doesNotExist())
                .andExpect(jsonPath("$.histogramDistribution.current.avgMs").doesNotExist())
                .andExpect(jsonPath("$.histogramDistribution.current.maxMs").doesNotExist())
                .andExpect(jsonPath("$.histogramDistribution.current.delta").doesNotExist())
                .andExpect(jsonPath("$.histogramDistribution.current.regression").doesNotExist())
                .andExpect(jsonPath("$.histogramDistribution.current.confidence").doesNotExist())
                .andExpect(jsonPath("$.histogramDistribution.current.ruleId").doesNotExist())
                .andExpect(jsonPath("$.triageCards[0].ruleId").value("application_error_rate_high"))
                .andExpect(jsonPath("$.triageCards[0].severity").value("warning"))
                .andExpect(jsonPath("$.triageCards[0].confidence").value(0.74))
                .andExpect(jsonPath("$.triageCards[0].score").value(74))
                .andExpect(jsonPath("$.triageCards[0].affectedEndpoint").value("GET /different"))
                .andExpect(jsonPath("$.triageCards[0].evidence.requestCount").value(100))
                .andExpect(jsonPath("$.triageCards[0].evidence.currentErrorRate").value(0.08))
                .andExpect(jsonPath("$.triageCards[0].evidence.rawPath").doesNotExist())
                .andExpect(jsonPath("$.triageCards[0].evidence.queryString").doesNotExist())
                .andExpect(jsonPath("$.triageCards[0].evidence.traceId").doesNotExist())
                .andExpect(jsonPath("$.triageCards[0].evidence.endpointP95Ms").doesNotExist())
                .andExpect(jsonPath("$.endpointPriority[0].rank").value(1))
                .andExpect(jsonPath("$.endpointPriority[0].method").value("POST"))
                .andExpect(jsonPath("$.endpointPriority[0].route").value("/orders"))
                .andExpect(jsonPath("$.endpointPriority[0].endpointKey").value("POST /orders"))
                .andExpect(jsonPath("$.endpointPriority[0].reason").value("error_and_latency"))
                .andExpect(jsonPath("$.endpointPriority[0].ruleIds[0]").value("endpoint_error_rate_high"))
                .andExpect(jsonPath("$.endpointPriority[0].ruleIds[1]").value("endpoint_slow_share_high"))
                .andExpect(jsonPath("$.endpointPriority[0].confidence").value(0.84))
                .andExpect(jsonPath("$.endpointPriority[0].score").value(84))
                .andExpect(jsonPath("$.endpointPriority[0].freshness.status").value("current"))
                .andExpect(jsonPath("$.endpointPriority[0].freshness.lastObservedAt")
                        .value("2026-05-25T10:32:00Z"))
                .andExpect(jsonPath("$.endpointPriority[0].freshness.sourceWindow").value("current"))
                .andExpect(jsonPath("$.endpointPriority[0].freshness.reason").value(nullValue()))
                .andExpect(jsonPath("$.endpointPriority[0].evidence.requestCount").value(120))
                .andExpect(jsonPath("$.endpointPriority[0].evidence.errorCount").value(12))
                .andExpect(jsonPath("$.endpointPriority[0].evidence.errorRate").value(0.1))
                .andExpect(jsonPath("$.endpointPriority[0].evidence.baselineRequestCount").value(nullValue()))
                .andExpect(jsonPath("$.endpointPriority[0].evidence.baselineErrorCount").value(nullValue()))
                .andExpect(jsonPath("$.endpointPriority[0].evidence.bucketDistributionSource")
                        .value("accepted_bucket"))
                .andExpect(jsonPath("$.endpointPriority[0].evidence.errorEvidenceStatus").value("available"))
                .andExpect(jsonPath("$.endpointPriority[0].evidence.latencyEvidenceStatus").value("available"))
                .andExpect(jsonPath("$.endpointPriority[0].evidence.durationBuckets[0].leMs").value(500))
                .andExpect(jsonPath("$.endpointPriority[0].recommendedAction").isNotEmpty())
                .andExpect(jsonPath("$.endpointPriority[0].evidence.rawJson").doesNotExist())
                .andExpect(jsonPath("$.endpointPriority[0].evidence.rawPath").doesNotExist())
                .andExpect(jsonPath("$.endpointPriority[0].evidence.queryString").doesNotExist())
                .andExpect(jsonPath("$.endpointPriority[0].evidence.traceId").doesNotExist())
                .andExpect(jsonPath("$.endpointPriority[0].evidence.endpointP95Ms").doesNotExist())
                .andExpect(jsonPath("$.endpointPriority[0].evidence.endpointP99Ms").doesNotExist())
                .andExpect(jsonPath("$.instances[0].instanceId").value(INSTANCE_ID.toString()))
                .andExpect(jsonPath("$.instances[0].instanceName").value("pod-a"))
                .andExpect(jsonPath("$.instances[0].lastSeenAt").value("2026-05-25T10:31:30Z"))
                .andExpect(jsonPath("$.instances[0].links.evidence")
                        .value("/api/projects/%s/applications/%s/instances/%s/evidence"
                                .formatted(PROJECT_ID, APPLICATION_ID, INSTANCE_ID)))
                .andExpect(jsonPath("$.instances[0].state").doesNotExist())
                .andExpect(jsonPath("$.instances[0].healthScore").doesNotExist())
                .andExpect(jsonPath("$.instances[0].endpointEvidence").doesNotExist())
                .andExpect(jsonPath("$.instances[0].p95Ms").doesNotExist())
                .andExpect(jsonPath("$.instances[0].p99Ms").doesNotExist())
                .andExpect(jsonPath("$.snapshot").value(nullValue()));
        verify(service).getDashboard(PROJECT_ID, APPLICATION_ID);
    }

    @Test
    void getDashboardMapsProjectApplicationMissingOrMismatchTo404() throws Exception {
        when(service.getDashboard(PROJECT_ID, APPLICATION_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/projects/{projectId}/applications/{applicationId}/dashboard",
                        PROJECT_ID,
                        APPLICATION_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void dashboardRouteUsesOnlyProjectAndApplicationPathVariables() {
        Method method = dashboardMethod();

        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly("");
        assertThat(method.getParameters())
                .extracting(Parameter::getName)
                .containsExactly("projectId", "applicationId");
        assertThat(method.getParameters())
                .noneMatch(parameter -> parameter.isAnnotationPresent(RequestParam.class));
    }

    private static Method dashboardMethod() {
        return List.of(DashboardController.class.getDeclaredMethods()).stream()
                .filter(method -> method.isAnnotationPresent(GetMapping.class))
                .findFirst()
                .orElseThrow();
    }

    private static ApplicationDashboardReadModel readModel() {
        return new ApplicationDashboardReadModel(
                GENERATED_AT,
                new ApplicationDashboardReadModel.Application(
                        PROJECT_ID,
                        APPLICATION_ID,
                        "orders-api",
                        "prod",
                        OffsetDateTime.parse("2026-05-25T10:31:30Z"),
                        null,
                        new ApplicationDashboardReadModel.SourceWindow(
                                new ApplicationDashboardReadModel.Window(
                                        OffsetDateTime.parse("2026-05-25T10:02:30Z"),
                                        CURRENT_END),
                                null),
                        new ApplicationDashboardReadModel.Freshness(
                                OffsetDateTime.parse("2026-05-25T10:31:30Z"),
                                OffsetDateTime.parse("2026-05-25T10:33:00Z"),
                                OffsetDateTime.parse("2026-05-25T10:34:30Z"))),
                new ApplicationDashboardReadModel.State(
                        "active",
                        "Metric data active",
                        "Freshness와 sample이 충분합니다.",
                        "현재 metric data state 관련 우선 조치는 없습니다.",
                        "application"),
                new ApplicationDashboardReadModel.StarterConnection(
                        "starter_heartbeat",
                        OffsetDateTime.parse("2026-05-25T10:32:15Z"),
                        "received",
                        "starter_connected",
                        "none"),
                null,
                new ApplicationDashboardReadModel.Recovery(false, null, null, null),
                new ApplicationDashboardReadModel.Metrics(100L, 3L, java.math.BigDecimal.valueOf(0.03)),
                new ApplicationDashboardReadModel.SourceScopedPercentiles(
                        "starter_canonical_percentile",
                        "instance_bucket",
                        "source_scoped_points",
                        "no_average_no_max_no_merge_no_histogram_recalculation",
                        "available",
                        null,
                        List.of(new ApplicationDashboardReadModel.PercentileItem(
                                "starter_canonical_percentile",
                                "orders-api",
                                "prod",
                                "pod-a",
                                OffsetDateTime.parse("2026-05-25T10:31:30Z"),
                                OffsetDateTime.parse("2026-05-25T10:32:00Z"),
                                1200L,
                                480L,
                                960L))),
                new ApplicationDashboardReadModel.HistogramDistribution(
                        "accepted_bucket",
                        "application",
                        "cumulative_bucket_distribution",
                        "display_bucket_only_no_percentile_recalculation",
                        new ApplicationDashboardReadModel.HistogramWindow(
                                "available",
                                null,
                                42L,
                                List.of(
                                        new ApplicationDashboardReadModel.HistogramBucket(50L, 22L),
                                        new ApplicationDashboardReadModel.HistogramBucket(100L, 42L))),
                        new ApplicationDashboardReadModel.HistogramWindow(
                                "unavailable",
                                "baseline_comparison_not_used_for_mvp",
                                0L,
                                List.of())),
                List.of(new ApplicationDashboardReadModel.TriageCard(
                        "application_error_rate_high",
                        ApplicationDashboardReadModel.TriageSeverity.WARNING,
                        "Application 오류율 높음",
                        "recent 30 minutes window의 오류율이 절대 기준 이상입니다.",
                        "최근 배포와 외부 의존성 오류 로그를 먼저 확인해보세요.",
                        0.74d,
                        74,
                        "GET /different",
                        new ApplicationDashboardReadModel.TriageEvidence(
                                100L,
                                8L,
                                BigDecimal.valueOf(0.08d),
                                100L,
                                2L,
                                BigDecimal.valueOf(0.02d),
                                BigDecimal.valueOf(0.06d),
                                null,
                                null,
                                null,
                                null,
                                null,
                                "current",
                                null))),
                List.of(new ApplicationDashboardReadModel.EndpointPriorityItem(
                        1,
                        "POST",
                        "/orders",
                        "POST /orders",
                        ApplicationDashboardReadModel.EndpointPriorityReason.ERROR_AND_LATENCY,
                        List.of("endpoint_error_rate_high", "endpoint_slow_share_high"),
                        0.84d,
                        84,
                        new ApplicationDashboardReadModel.EndpointPriorityFreshness(
                                "current",
                                OffsetDateTime.parse("2026-05-25T10:32:00Z"),
                                "current",
                                null),
                        new ApplicationDashboardReadModel.EndpointPriorityEvidence(
                                120L,
                                12L,
                                BigDecimal.valueOf(0.10d),
                                null,
                                null,
                                null,
                                null,
                                List.of(
                                        new ApplicationDashboardReadModel.HistogramBucket(500L, 70L),
                                        new ApplicationDashboardReadModel.HistogramBucket(1000L, 120L)),
                                null,
                                BigDecimal.valueOf(0.416667d),
                                null,
                                null,
                                "accepted_bucket",
                                ApplicationDashboardReadModel.EndpointEvidenceStatus.AVAILABLE,
                                ApplicationDashboardReadModel.EndpointEvidenceStatus.AVAILABLE),
                        "최근 30분 동안 이 endpoint의 오류와 느린 응답 근거를 함께 확인하세요.")),
                List.of(new ApplicationDashboardReadModel.InstanceEntry(
                        INSTANCE_ID,
                        "pod-a",
                        OffsetDateTime.parse("2026-05-25T10:31:30Z"),
                        new ApplicationDashboardReadModel.InstanceEntryLinks(
                                "/api/projects/%s/applications/%s/instances/%s/evidence"
                                        .formatted(PROJECT_ID, APPLICATION_ID, INSTANCE_ID)))),
                null);
    }
}
