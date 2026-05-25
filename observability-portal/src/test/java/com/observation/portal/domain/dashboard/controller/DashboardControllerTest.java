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
                .andExpect(jsonPath("$.zeroInsight.reasonCode").value("no_action_needed"))
                .andExpect(jsonPath("$.recovery.isRecovering").value(false))
                .andExpect(jsonPath("$.metrics.requestCount").value(100))
                .andExpect(jsonPath("$.metrics.errorCount").value(3))
                .andExpect(jsonPath("$.metrics.errorRate").value(0.03))
                .andExpect(jsonPath("$.metrics.p95").doesNotExist())
                .andExpect(jsonPath("$.metrics.p99").doesNotExist())
                .andExpect(jsonPath("$.sourceScopedPercentiles.items").isEmpty())
                .andExpect(jsonPath("$.triageCards").isEmpty())
                .andExpect(jsonPath("$.endpointPriority").isEmpty())
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
                new ApplicationDashboardReadModel.ZeroInsight(
                        "no_action_needed",
                        "현재 우선 조치가 필요한 신호는 없습니다.",
                        "트래픽이 유지되는지 다음 bucket까지 관찰하세요."),
                new ApplicationDashboardReadModel.Recovery(false, null, null, null),
                new ApplicationDashboardReadModel.Metrics(100L, 3L, java.math.BigDecimal.valueOf(0.03)),
                ApplicationDashboardReadModel.SourceScopedPercentiles.empty(),
                List.of(),
                List.of(),
                null);
    }
}
