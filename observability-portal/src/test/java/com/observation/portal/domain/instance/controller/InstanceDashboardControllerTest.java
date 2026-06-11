package com.observation.portal.domain.instance.controller;

import com.observation.portal.domain.instance.model.InstanceDashboardReadModel;
import com.observation.portal.domain.instance.service.InstanceDashboardReadModelService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InstanceDashboardControllerTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000013801");
    private static final UUID APPLICATION_ID = UUID.fromString("00000000-0000-0000-0000-000000013811");
    private static final UUID INSTANCE_ID = UUID.fromString("00000000-0000-0000-0000-000000013821");
    private static final UUID SNAPSHOT_ID = UUID.fromString("00000000-0000-0000-0000-000000013831");

    private final InstanceDashboardReadModelService service = mock(InstanceDashboardReadModelService.class);
    private final InstanceDashboardController controller = new InstanceDashboardController(service);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

    @Test
    @DisplayName("live dashboard route는 mode/source/window semantics를 직렬화한다")
    void liveDashboardRouteSerializesModeSourceAndWindowSemantics() throws Exception {
        when(service.getLiveDashboard(PROJECT_ID, APPLICATION_ID, INSTANCE_ID))
                .thenReturn(Optional.of(readModel("live", null)));

        mockMvc.perform(get(
                        "/api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/dashboard",
                        PROJECT_ID,
                        APPLICATION_ID,
                        INSTANCE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value("instance_dashboard_read_model.v1"))
                .andExpect(jsonPath("$.mode").value("live"))
                .andExpect(jsonPath("$.window.name").value("recent_30_minutes"))
                .andExpect(jsonPath("$.window.windowSource").value("live_recent_30_minutes"))
                .andExpect(jsonPath("$.thresholds.minimumRequestCount").value(30))
                .andExpect(jsonPath("$.readSemantics.source").value("accepted_metric_buckets"))
                .andExpect(jsonPath("$.readSemantics.acceptedAtCutoffApplied").value(false))
                .andExpect(jsonPath("$.readSemantics.includesLateAcceptedMetrics").value(false))
                .andExpect(jsonPath("$.readSemantics.instanceEvidenceReconstructedFromMetrics").value(false))
                .andExpect(jsonPath("$.readSemantics.applicationSnapshotRecalculated").value(false))
                .andExpect(jsonPath("$.readSemantics.markerIsStateSource").value(false))
                .andExpect(jsonPath("$.state").doesNotExist())
                .andExpect(jsonPath("$.stateCode").doesNotExist())
                .andExpect(content().string(not(containsString("current_15m"))))
                .andExpect(content().string(not(containsString("healthScore"))))
                .andExpect(content().string(not(containsString("rootCause"))))
                .andExpect(content().string(not(containsString("recoveryProof"))));
        verify(service).getLiveDashboard(PROJECT_ID, APPLICATION_ID, INSTANCE_ID);
    }

    @Test
    @DisplayName("snapshot dashboard route는 selected Application Snapshot metadata와 late metric semantics를 노출한다")
    void snapshotDashboardRouteSerializesSnapshotSemantics() throws Exception {
        when(service.getSnapshotDashboard(PROJECT_ID, APPLICATION_ID, SNAPSHOT_ID, INSTANCE_ID))
                .thenReturn(Optional.of(readModel("snapshot", snapshot())));

        mockMvc.perform(get(
                        "/api/projects/{projectId}/applications/{applicationId}/snapshots/{snapshotId}/instances/{instanceId}/dashboard",
                        PROJECT_ID,
                        APPLICATION_ID,
                        SNAPSHOT_ID,
                        INSTANCE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("snapshot"))
                .andExpect(jsonPath("$.window.windowSource").value("selected_application_snapshot"))
                .andExpect(jsonPath("$.snapshot.snapshotId").value(SNAPSHOT_ID.toString()))
                .andExpect(jsonPath("$.snapshot.snapshotRowSource").value("dashboard_snapshots"))
                .andExpect(jsonPath("$.readSemantics.snapshotRowSource").value("dashboard_snapshots"))
                .andExpect(jsonPath("$.readSemantics.acceptedAtCutoffApplied").value(false))
                .andExpect(jsonPath("$.readSemantics.includesLateAcceptedMetrics").value(true))
                .andExpect(jsonPath("$.readSemantics.mayDifferFromStoredApplicationSnapshot").value(true))
                .andExpect(jsonPath("$.readSemantics.instanceEvidenceReconstructedFromMetrics").value(true))
                .andExpect(jsonPath("$.readSemantics.applicationSnapshotRecalculated").value(false))
                .andExpect(jsonPath("$.state").doesNotExist())
                .andExpect(jsonPath("$.stateCode").doesNotExist());
        verify(service).getSnapshotDashboard(PROJECT_ID, APPLICATION_ID, SNAPSHOT_ID, INSTANCE_ID);
    }

    @Test
    @DisplayName("catalog 또는 snapshot mismatch는 404로 매핑한다")
    void missingCatalogOrSnapshotRowsMapTo404() throws Exception {
        when(service.getLiveDashboard(PROJECT_ID, APPLICATION_ID, INSTANCE_ID)).thenReturn(Optional.empty());
        when(service.getSnapshotDashboard(PROJECT_ID, APPLICATION_ID, SNAPSHOT_ID, INSTANCE_ID))
                .thenReturn(Optional.empty());

        mockMvc.perform(get(
                        "/api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/dashboard",
                        PROJECT_ID,
                        APPLICATION_ID,
                        INSTANCE_ID))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(
                        "/api/projects/{projectId}/applications/{applicationId}/snapshots/{snapshotId}/instances/{instanceId}/dashboard",
                        PROJECT_ID,
                        APPLICATION_ID,
                        SNAPSHOT_ID,
                        INSTANCE_ID))
                .andExpect(status().isNotFound());
    }

    private static InstanceDashboardReadModel readModel(
            String mode,
            InstanceDashboardReadModel.Snapshot snapshot) {
        String windowSource = "snapshot".equals(mode) ? "selected_application_snapshot" : "live_recent_30_minutes";
        return new InstanceDashboardReadModel(
                "instance_dashboard_read_model.v1",
                mode,
                OffsetDateTime.parse("2026-06-10T08:10:35Z"),
                new InstanceDashboardReadModel.Application(
                        PROJECT_ID,
                        APPLICATION_ID,
                        "orders-api",
                        "prod",
                        new InstanceDashboardReadModel.ApplicationLinks(
                                "/api/projects/%s/applications/%s/dashboard".formatted(PROJECT_ID, APPLICATION_ID))),
                new InstanceDashboardReadModel.Instance(
                        INSTANCE_ID,
                        "pod-a",
                        OffsetDateTime.parse("2026-06-10T07:00:30Z"),
                        OffsetDateTime.parse("2026-06-10T08:10:00Z")),
                new InstanceDashboardReadModel.Window(
                        "recent_30_minutes",
                        OffsetDateTime.parse("2026-06-10T07:40:30Z"),
                        OffsetDateTime.parse("2026-06-10T08:10:30Z"),
                        30,
                        windowSource),
                thresholds(),
                new InstanceDashboardReadModel.ApplicationStateRef(
                        "application",
                        "snapshot".equals(mode) ? "selected_application_snapshot" : "application_dashboard_live",
                        snapshot == null ? null : snapshot.storedApplicationStateCode(),
                        snapshot == null ? null : snapshot.snapshotId()),
                new InstanceDashboardReadModel.ObservationStatus(
                        "observed",
                        "selected_instance_metric_bucket_observed",
                        OffsetDateTime.parse("2026-06-10T08:10:00Z")),
                new InstanceDashboardReadModel.ApplicationContribution(
                        "attention",
                        "request_symptom_observed_without_root_cause_claim",
                        List.of("server_error_5xx")),
                new InstanceDashboardReadModel.DataQuality(
                        "sufficient",
                        List.of(),
                        "accepted_metric_buckets"),
                InstanceDashboardReadModel.StarterConnection.missing(),
                new InstanceDashboardReadModel.Signals(
                        new InstanceDashboardReadModel.RedSignals(
                                42L,
                                3L,
                                BigDecimal.valueOf(0.071428d),
                                12L,
                                BigDecimal.valueOf(0.285714d),
                                true)),
                new InstanceDashboardReadModel.EndpointEvidence(
                        "accepted_metric_buckets.endpoints_json",
                        "instance_recent_30_minutes",
                        "selected_instance_metric_evidence",
                        "server_order",
                        "available",
                        null,
                        List.of()),
                new InstanceDashboardReadModel.ResourceEvidence(
                        "accepted_metric_buckets",
                        "available",
                        List.of()),
                List.of(),
                snapshot,
                new InstanceDashboardReadModel.ReadSemantics(
                        "accepted_metric_buckets",
                        windowSource,
                        snapshot == null ? null : "dashboard_snapshots",
                        false,
                        snapshot != null,
                        snapshot != null,
                        false,
                        snapshot != null,
                        false),
                new InstanceDashboardReadModel.Links(
                        "snapshot".equals(mode)
                                ? "/api/projects/%s/applications/%s/snapshots/%s/instances/%s/dashboard"
                                        .formatted(PROJECT_ID, APPLICATION_ID, SNAPSHOT_ID, INSTANCE_ID)
                                : "/api/projects/%s/applications/%s/instances/%s/dashboard"
                                        .formatted(PROJECT_ID, APPLICATION_ID, INSTANCE_ID),
                        "/api/projects/%s/applications/%s/dashboard".formatted(PROJECT_ID, APPLICATION_ID),
                        "/api/projects/%s/applications/%s/instances/%s/evidence"
                                .formatted(PROJECT_ID, APPLICATION_ID, INSTANCE_ID),
                        "/api/projects/%s/applications/%s/instances/%s/snapshot-trend"
                                .formatted(PROJECT_ID, APPLICATION_ID, INSTANCE_ID),
                        snapshot == null ? null
                                : "/api/projects/%s/applications/%s/dashboard/snapshots/%s"
                                        .formatted(PROJECT_ID, APPLICATION_ID, SNAPSHOT_ID)),
                List.of(
                        "instance_lifecycle_state",
                        "instance_health_score",
                        "root_cause",
                        "recovery_proof",
                        "marker_bucket_as_state_source"));
    }

    private static InstanceDashboardReadModel.Snapshot snapshot() {
        return new InstanceDashboardReadModel.Snapshot(
                SNAPSHOT_ID,
                "dashboard_snapshots",
                OffsetDateTime.parse("2026-06-10T08:00:05Z"),
                OffsetDateTime.parse("2026-06-10T07:30:00Z"),
                OffsetDateTime.parse("2026-06-10T08:00:00Z"),
                "hourly_scheduled",
                "degraded");
    }

    private static InstanceDashboardReadModel.Thresholds thresholds() {
        return new InstanceDashboardReadModel.Thresholds(
                30L,
                BigDecimal.valueOf(0.05d),
                BigDecimal.valueOf(0.20d),
                BigDecimal.valueOf(0.85d),
                BigDecimal.valueOf(0.85d),
                BigDecimal.valueOf(0.90d));
    }
}
