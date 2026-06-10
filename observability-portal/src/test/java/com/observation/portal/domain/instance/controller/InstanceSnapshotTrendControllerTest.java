package com.observation.portal.domain.instance.controller;

import com.observation.portal.domain.instance.model.InstanceSnapshotTrendReadModel;
import com.observation.portal.domain.instance.service.InstanceSnapshotTrendService;
import com.observation.portal.domain.instance.service.InvalidSnapshotTrendQueryException;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InstanceSnapshotTrendControllerTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000005201");
    private static final UUID APPLICATION_ID = UUID.fromString("00000000-0000-0000-0000-000000005211");
    private static final UUID INSTANCE_ID = UUID.fromString("00000000-0000-0000-0000-000000005221");

    private final InstanceSnapshotTrendService service = mock(InstanceSnapshotTrendService.class);
    private final InstanceSnapshotTrendController controller = new InstanceSnapshotTrendController(service);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

    @Test
    void getSnapshotTrendSerializesReadModelAndDelegatesQueryParametersToService() throws Exception {
        when(service.getTrend(PROJECT_ID, APPLICATION_ID, INSTANCE_ID, "14d", "400"))
                .thenReturn(Optional.of(readModel()));

        mockMvc.perform(get(
                        "/api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/snapshot-trend",
                        PROJECT_ID,
                        APPLICATION_ID,
                        INSTANCE_ID)
                        .param("since", "14d")
                        .param("limit", "400"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedAt").value("2026-05-26T08:10:35Z"))
                .andExpect(jsonPath("$.application.projectId").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$.application.applicationId").value(APPLICATION_ID.toString()))
                .andExpect(jsonPath("$.application.links.dashboard")
                        .value("/api/projects/%s/applications/%s/dashboard"
                                .formatted(PROJECT_ID, APPLICATION_ID)))
                .andExpect(jsonPath("$.instance.instanceId").value(INSTANCE_ID.toString()))
                .andExpect(jsonPath("$.instance.instanceName").value("pod-a"))
                .andExpect(jsonPath("$.instance.links.evidence")
                        .value("/api/projects/%s/applications/%s/instances/%s/evidence"
                                .formatted(PROJECT_ID, APPLICATION_ID, INSTANCE_ID)))
                .andExpect(jsonPath("$.source")
                        .value("dashboard_snapshots.read_model_json.instanceSummary.items"))
                .andExpect(jsonPath("$.horizon.requestedSince").value("14d"))
                .andExpect(jsonPath("$.horizon.limit").value(400))
                .andExpect(jsonPath("$.horizon.order").value("currentWindowEndUtc_asc"))
                .andExpect(jsonPath("$.points[0].snapshotId")
                        .value("00000000-0000-0000-0000-000000005731"))
                .andExpect(jsonPath("$.points[0].capturedAt").value("2026-05-26T08:00:00Z"))
                .andExpect(jsonPath("$.points[0].currentWindowEndUtc").value("2026-05-26T08:00:00Z"))
                .andExpect(jsonPath("$.points[0].storedApplicationStateCode").value("active"))
                .andExpect(jsonPath("$.points[0].captureReason").value("opaque_future_reason"))
                .andExpect(jsonPath("$.points[0].metricData.statusSource").value("accepted_bucket"))
                .andExpect(jsonPath("$.points[0].starterConnection.statusSource").value("starter_heartbeat"))
                .andExpect(jsonPath("$.points[0].starterConnection.stateImpact").value("none"))
                .andExpect(jsonPath("$.points[0].endpointEvidenceRefs[0].endpointKey").value("POST /orders"));
        verify(service).getTrend(PROJECT_ID, APPLICATION_ID, INSTANCE_ID, "14d", "400");
    }

    @Test
    void omittedQueryParametersReachServiceAsNullAndEmptyTrendReturns200() throws Exception {
        when(service.getTrend(PROJECT_ID, APPLICATION_ID, INSTANCE_ID, null, null))
                .thenReturn(Optional.of(emptyReadModel()));

        mockMvc.perform(get(
                        "/api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/snapshot-trend",
                        PROJECT_ID,
                        APPLICATION_ID,
                        INSTANCE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").isArray())
                .andExpect(jsonPath("$.points").isEmpty());
        verify(service).getTrend(PROJECT_ID, APPLICATION_ID, INSTANCE_ID, null, null);
    }

    @Test
    void membershipFailureMapsTo404AndInvalidQueryMapsTo400() throws Exception {
        when(service.getTrend(PROJECT_ID, APPLICATION_ID, INSTANCE_ID, "7d", "168"))
                .thenReturn(Optional.empty());
        when(service.getTrend(PROJECT_ID, APPLICATION_ID, INSTANCE_ID, "24h", "168"))
                .thenThrow(new InvalidSnapshotTrendQueryException("since must be 7d or 14d"));

        mockMvc.perform(get(
                        "/api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/snapshot-trend",
                        PROJECT_ID,
                        APPLICATION_ID,
                        INSTANCE_ID)
                        .param("since", "7d")
                        .param("limit", "168"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(
                        "/api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/snapshot-trend",
                        PROJECT_ID,
                        APPLICATION_ID,
                        INSTANCE_ID)
                        .param("since", "24h")
                        .param("limit", "168"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void responseDoesNotExposeRawTimeseriesOrRecoveryFields() throws Exception {
        when(service.getTrend(PROJECT_ID, APPLICATION_ID, INSTANCE_ID, "14d", "400"))
                .thenReturn(Optional.of(readModel()));

        mockMvc.perform(get(
                        "/api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/snapshot-trend",
                        PROJECT_ID,
                        APPLICATION_ID,
                        INSTANCE_ID)
                        .param("since", "14d")
                        .param("limit", "400"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("rawBucketJson"))))
                .andExpect(content().string(not(containsString("rawSnapshotJson"))))
                .andExpect(content().string(not(containsString("endpointsJson"))))
                .andExpect(content().string(not(containsString("endpointTimeseries"))))
                .andExpect(content().string(not(containsString("previousState"))))
                .andExpect(content().string(not(containsString("lastHealthyAt"))))
                .andExpect(content().string(not(containsString("recoveryMarker"))))
                .andExpect(content().string(not(containsString("lastRecoveredAt"))))
                .andExpect(content().string(not(containsString("healthScore"))))
                .andExpect(content().string(not(containsString("recommendedAction"))));
    }

    @Test
    void snapshotTrendRouteUsesProjectApplicationInstancePathAndOptionalQueryParameters() {
        Method method = trendMethod();

        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly("");
        assertThat(method.getParameters())
                .extracting(Parameter::getName)
                .containsExactly("projectId", "applicationId", "instanceId", "since", "limit");
        assertThat(method.getParameters()[3].getAnnotation(RequestParam.class).required()).isFalse();
        assertThat(method.getParameters()[4].getAnnotation(RequestParam.class).required()).isFalse();
    }

    private static Method trendMethod() {
        return List.of(InstanceSnapshotTrendController.class.getDeclaredMethods()).stream()
                .filter(method -> method.isAnnotationPresent(GetMapping.class))
                .findFirst()
                .orElseThrow();
    }

    private static InstanceSnapshotTrendReadModel emptyReadModel() {
        return readModel(List.of());
    }

    private static InstanceSnapshotTrendReadModel readModel() {
        return readModel(List.of(point()));
    }

    private static InstanceSnapshotTrendReadModel readModel(List<InstanceSnapshotTrendReadModel.Point> points) {
        return new InstanceSnapshotTrendReadModel(
                OffsetDateTime.parse("2026-05-26T08:10:35Z"),
                new InstanceSnapshotTrendReadModel.Application(
                        PROJECT_ID,
                        APPLICATION_ID,
                        "orders-api",
                        "prod",
                        new InstanceSnapshotTrendReadModel.ApplicationLinks(
                                "/api/projects/%s/applications/%s/dashboard"
                                        .formatted(PROJECT_ID, APPLICATION_ID))),
                new InstanceSnapshotTrendReadModel.Instance(
                        INSTANCE_ID,
                        "pod-a",
                        OffsetDateTime.parse("2026-05-26T05:00:05Z"),
                        OffsetDateTime.parse("2026-05-26T08:00:05Z"),
                        new InstanceSnapshotTrendReadModel.InstanceLinks(
                                "/api/projects/%s/applications/%s/instances/%s/evidence"
                                        .formatted(PROJECT_ID, APPLICATION_ID, INSTANCE_ID))),
                InstanceSnapshotTrendReadModel.SOURCE,
                new InstanceSnapshotTrendReadModel.Horizon(
                        OffsetDateTime.parse("2026-05-12T08:10:35Z"),
                        OffsetDateTime.parse("2026-05-26T08:10:35Z"),
                        "14d",
                        "7d",
                        "14d",
                        400,
                        672,
                        "currentWindowEndUtc_asc"),
                points);
    }

    private static InstanceSnapshotTrendReadModel.Point point() {
        return new InstanceSnapshotTrendReadModel.Point(
                UUID.fromString("00000000-0000-0000-0000-000000005731"),
                OffsetDateTime.parse("2026-05-26T08:00:00Z"),
                OffsetDateTime.parse("2026-05-26T08:00:00Z"),
                "active",
                "opaque_future_reason",
                "pod-a",
                "observed",
                new InstanceSnapshotTrendReadModel.MetricData(
                        "accepted_bucket",
                        OffsetDateTime.parse("2026-05-26T07:59:30Z"),
                        "current"),
                new InstanceSnapshotTrendReadModel.StarterConnection(
                        "starter_heartbeat",
                        OffsetDateTime.parse("2026-05-26T07:59:45Z"),
                        "received",
                        "starter_connected",
                        "none"),
                null,
                null,
                new InstanceSnapshotTrendReadModel.ApplicationTriageContribution(
                        "available",
                        false,
                        List.of(),
                        "no_action_needed"),
                List.of(new InstanceSnapshotTrendReadModel.EndpointEvidenceRef(
                        "POST /orders",
                        "POST",
                        "/orders",
                        1,
                        List.of(),
                        "snapshot:endpoint-1")));
    }
}
