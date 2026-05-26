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
                .andExpect(jsonPath("$.generatedAt").value("2026-05-25T10:32:38.421Z"))
                .andExpect(jsonPath("$.application.projectId").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$.application.applicationId").value(APPLICATION_ID.toString()))
                .andExpect(jsonPath("$.application.name").value("orders-api"))
                .andExpect(jsonPath("$.application.environment").value("prod"))
                .andExpect(jsonPath("$.application.sourceWindow.current.endUtc").value("2026-05-25T10:32:30Z"))
                .andExpect(jsonPath("$.state.code").value("active"))
                .andExpect(jsonPath("$.starterConnection.statusSource").value("starter_heartbeat"))
                .andExpect(jsonPath("$.starterConnection.connectionMeaning").value("starter_connected"))
                .andExpect(jsonPath("$.starterConnection.stateImpact").value("none"))
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
                .andExpect(jsonPath("$.sourceScopedPercentiles.source").value("starter_local"))
                .andExpect(jsonPath("$.sourceScopedPercentiles.scope").value("instance_bucket"))
                .andExpect(jsonPath("$.sourceScopedPercentiles.status").value("available"))
                .andExpect(jsonPath("$.sourceScopedPercentiles.reason").value(nullValue()))
                .andExpect(jsonPath("$.sourceScopedPercentiles.applicationScopeFallback").doesNotExist())
                .andExpect(jsonPath("$.sourceScopedPercentiles.items[0].source").value("starter_local"))
                .andExpect(jsonPath("$.sourceScopedPercentiles.items[0].application").value("orders-api"))
                .andExpect(jsonPath("$.sourceScopedPercentiles.items[0].environment").value("prod"))
                .andExpect(jsonPath("$.sourceScopedPercentiles.items[0].instance").value("pod-a"))
                .andExpect(jsonPath("$.sourceScopedPercentiles.items[0].p95Ms").value(480))
                .andExpect(jsonPath("$.sourceScopedPercentiles.items[0].p99Ms").value(960))
                .andExpect(jsonPath("$.histogramDistribution.source").value("histogram_bucket_distribution"))
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
                .andExpect(jsonPath("$.triageCards[0].ruleId").value("global_error_spike"))
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
                .andExpect(jsonPath("$.endpointPriority[0].ruleIds[0]").value("endpoint_error_spike"))
                .andExpect(jsonPath("$.endpointPriority[0].ruleIds[1]").value("endpoint_latency_spike"))
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
                .andExpect(jsonPath("$.endpointPriority[0].evidence.baselineRequestCount").value(100))
                .andExpect(jsonPath("$.endpointPriority[0].evidence.baselineErrorCount").value(1))
                .andExpect(jsonPath("$.endpointPriority[0].evidence.bucketDistributionSource")
                        .value("histogram_bucket_distribution"))
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
                                        OffsetDateTime.parse("2026-05-25T10:17:30Z"),
                                        CURRENT_END),
                                new ApplicationDashboardReadModel.Window(
                                        OffsetDateTime.parse("2026-05-25T10:02:30Z"),
                                        OffsetDateTime.parse("2026-05-25T10:17:30Z"))),
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
                        "starter_local",
                        "instance_bucket",
                        "latest_starter_point_per_instance_in_current_window",
                        "no_average_no_max_no_merge_no_histogram_recalculation",
                        "available",
                        null,
                        List.of(new ApplicationDashboardReadModel.PercentileItem(
                                "starter_local",
                                "orders-api",
                                "prod",
                                "pod-a",
                                OffsetDateTime.parse("2026-05-25T10:31:30Z"),
                                OffsetDateTime.parse("2026-05-25T10:32:00Z"),
                                1200L,
                                480L,
                                960L))),
                new ApplicationDashboardReadModel.HistogramDistribution(
                        "histogram_bucket_distribution",
                        "application",
                        "bucket_distribution_evidence",
                        "sum_cumulative_counts_only_when_boundary_set_matches",
                        new ApplicationDashboardReadModel.HistogramWindow(
                                "available",
                                null,
                                42L,
                                List.of(
                                        new ApplicationDashboardReadModel.HistogramBucket(50L, 22L),
                                        new ApplicationDashboardReadModel.HistogramBucket(100L, 42L))),
                        new ApplicationDashboardReadModel.HistogramWindow(
                                "missing",
                                "no_histogram_buckets_in_baseline_window",
                                0L,
                                List.of())),
                List.of(new ApplicationDashboardReadModel.TriageCard(
                        "global_error_spike",
                        ApplicationDashboardReadModel.TriageSeverity.WARNING,
                        "Application 오류율 증가",
                        "current window의 오류율이 baseline보다 의미 있게 증가했습니다.",
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
                        List.of("endpoint_error_spike", "endpoint_latency_spike"),
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
                                100L,
                                1L,
                                BigDecimal.valueOf(0.01d),
                                BigDecimal.valueOf(0.09d),
                                List.of(
                                        new ApplicationDashboardReadModel.HistogramBucket(500L, 70L),
                                        new ApplicationDashboardReadModel.HistogramBucket(1000L, 120L)),
                                List.of(
                                        new ApplicationDashboardReadModel.HistogramBucket(500L, 95L),
                                        new ApplicationDashboardReadModel.HistogramBucket(1000L, 100L)),
                                BigDecimal.valueOf(0.416667d),
                                BigDecimal.valueOf(0.05d),
                                BigDecimal.valueOf(0.366667d),
                                "histogram_bucket_distribution",
                                ApplicationDashboardReadModel.EndpointEvidenceStatus.AVAILABLE,
                                ApplicationDashboardReadModel.EndpointEvidenceStatus.AVAILABLE),
                        "이 endpoint의 오류 로그와 외부 의존성 지연 가능성을 먼저 확인해보세요.")),
                null);
    }
}
