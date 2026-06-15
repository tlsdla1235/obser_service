package com.observation.portal.domain.snapshot.controller;

import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel.LastHealthyAt;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel.PreviousState;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel.SnapshotEndpointEvidence;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel.SnapshotLinks;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel.SnapshotMetadata;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel.SnapshotReadSemantics;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel.StoredReadModel;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel.Window;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotMarkerItem;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotMarkerReadModel;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotMarkerSeverity;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotMarkerType;
import com.observation.portal.domain.snapshot.service.DashboardSnapshotDetailService;
import com.observation.portal.domain.snapshot.service.DashboardSnapshotMarkerService;
import com.observation.portal.domain.snapshot.service.DashboardSnapshotProjectionException;
import com.observation.portal.domain.snapshot.service.InvalidSnapshotMarkerQueryException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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

class DashboardSnapshotControllerTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000005801");
    private static final UUID APPLICATION_ID = UUID.fromString("00000000-0000-0000-0000-000000005811");
    private static final UUID SNAPSHOT_ID = UUID.fromString("00000000-0000-0000-0000-000000005821");

    private final DashboardSnapshotDetailService detailService = mock(DashboardSnapshotDetailService.class);
    private final DashboardSnapshotMarkerService markerService = mock(DashboardSnapshotMarkerService.class);
    private final DashboardSnapshotController controller = new DashboardSnapshotController(detailService, markerService);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

    @Test
    void getSnapshotDetailSerializesStoredWrapperAndDoesNotExposeRawJsonEscapeHatch() throws Exception {
        when(detailService.getDetail(PROJECT_ID, APPLICATION_ID, SNAPSHOT_ID))
                .thenReturn(Optional.of(detail()));

        mockMvc.perform(get(
                        "/api/projects/{projectId}/applications/{applicationId}/dashboard/snapshots/{snapshotId}",
                        PROJECT_ID,
                        APPLICATION_ID,
                        SNAPSHOT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("dashboard_snapshots"))
                .andExpect(jsonPath("$.readSemantics.mode").value("stored_snapshot_detail"))
                .andExpect(jsonPath("$.readSemantics.source").value("dashboard_snapshots.read_model_json"))
                .andExpect(jsonPath("$.readSemantics.snapshotDetailRecalculates").value(false))
                .andExpect(jsonPath("$.readSemantics.currentStateRecalculated").value(false))
                .andExpect(jsonPath("$.readSemantics.liveSourcesJoined").isEmpty())
                .andExpect(jsonPath("$.readSemantics.rawReadModelJsonExposed").value(false))
                .andExpect(jsonPath("$.readSemantics.markerIsStateSource").value(false))
                .andExpect(jsonPath("$.readSemantics.baselineComparisonUsedForMvpDecision").value(false))
                .andExpect(jsonPath("$.snapshot.snapshotId").value(SNAPSHOT_ID.toString()))
                .andExpect(jsonPath("$.snapshot.captureReason").value("hourly_scheduled"))
                .andExpect(jsonPath("$.marker.type").value("scheduled_snapshot"))
                .andExpect(jsonPath("$.snapshotEndpointEvidence.source")
                        .value("dashboard_snapshots.read_model_json.endpointPriority"))
                .andExpect(jsonPath("$.snapshotEndpointEvidence.selectionPolicy").value("stored_read_model"))
                .andExpect(jsonPath("$.instanceSummary.schemaVersion").value("dashboard_read_model.v1"))
                .andExpect(jsonPath("$.instanceSummary.source")
                        .value("dashboard_snapshots.read_model_json.instanceSummary.items"))
                .andExpect(jsonPath("$.instanceSummary.selectionPolicy").value("stored_read_model"))
                .andExpect(jsonPath("$.readModel.schemaVersion").value("dashboard_read_model.v1"))
                .andExpect(jsonPath("$.readModel.mode").value("snapshot"))
                .andExpect(jsonPath("$.readModel.window.type").value("recent_30_minutes"))
                .andExpect(jsonPath("$.readModel.thresholds.minimumRequestCount").value(30))
                .andExpect(jsonPath("$.readModel.attentionEvidence[0].kind").value("endpoint"))
                .andExpect(jsonPath("$.readModel.readSemantics.source")
                        .value("dashboard_snapshots.read_model_json"))
                .andExpect(jsonPath("$.previousState.source").value("dashboard_snapshots"))
                .andExpect(jsonPath("$.lastHealthyAt.source").value("dashboard_snapshots"))
                .andExpect(jsonPath("$.links.self").value("/api/projects/%s/applications/%s/dashboard/snapshots/%s"
                        .formatted(PROJECT_ID, APPLICATION_ID, SNAPSHOT_ID)))
                .andExpect(content().string(not(containsString("rawReadModelJson\":"))))
                .andExpect(content().string(not(containsString("nodeType"))))
                .andExpect(content().string(not(containsString("containerNode"))))
                .andExpect(content().string(not(containsString("operationalEvents"))))
                .andExpect(content().string(not(containsString("resolvedAt"))));
    }

    @Test
    void detailInvalidUuidMissingAndProjectionFailureMapToContractStatusBodies() throws Exception {
        when(detailService.getDetail(PROJECT_ID, APPLICATION_ID, SNAPSHOT_ID)).thenReturn(Optional.empty());
        UUID projectionFailureSnapshotId = UUID.fromString("00000000-0000-0000-0000-000000005822");
        when(detailService.getDetail(PROJECT_ID, APPLICATION_ID, projectionFailureSnapshotId))
                .thenThrow(new DashboardSnapshotProjectionException("boom"));

        mockMvc.perform(get(
                        "/api/projects/{projectId}/applications/{applicationId}/dashboard/snapshots/{snapshotId}",
                        PROJECT_ID,
                        APPLICATION_ID,
                        "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_snapshot_id"));
        mockMvc.perform(get(
                        "/api/projects/{projectId}/applications/{applicationId}/dashboard/snapshots/{snapshotId}",
                        PROJECT_ID,
                        APPLICATION_ID,
                        SNAPSHOT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("snapshot_not_found_or_expired"))
                .andExpect(jsonPath("$.error.message")
                        .value("저장된 snapshot detail이 없거나 보관 기간이 지나 더 이상 없습니다."))
                .andExpect(jsonPath("$.error.recommendedAction")
                        .value("현재 상태는 application dashboard에서 다시 확인하세요."));
        mockMvc.perform(get(
                        "/api/projects/{projectId}/applications/{applicationId}/dashboard/snapshots/{snapshotId}",
                        PROJECT_ID,
                        APPLICATION_ID,
                        projectionFailureSnapshotId))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("snapshot_projection_failed"));
    }

    @Test
    void markerEndpointDelegatesQueryAndMapsInvalidQueryTo400() throws Exception {
        when(markerService.getMarkers(PROJECT_ID, APPLICATION_ID, "14d", "999"))
                .thenReturn(Optional.of(markers()));
        when(markerService.getMarkers(PROJECT_ID, APPLICATION_ID, "30d", "50"))
                .thenThrow(new InvalidSnapshotMarkerQueryException("bad since"));

        mockMvc.perform(get(
                        "/api/projects/{projectId}/applications/{applicationId}/dashboard/snapshot-markers",
                        PROJECT_ID,
                        APPLICATION_ID)
                        .param("since", "14d")
                        .param("limit", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("dashboard_snapshots"))
                .andExpect(jsonPath("$.horizon.requestedSince").value("14d"))
                .andExpect(jsonPath("$.markers[0].readMeaning").value("stored_read_model_point"))
                .andExpect(jsonPath("$.markers[0].links.snapshot")
                        .value("/api/projects/%s/applications/%s/dashboard/snapshots/%s"
                                .formatted(PROJECT_ID, APPLICATION_ID, SNAPSHOT_ID)))
                .andExpect(content().string(not(containsString("eventId"))))
                .andExpect(content().string(not(containsString("resolvedAt"))));
        verify(markerService).getMarkers(PROJECT_ID, APPLICATION_ID, "14d", "999");

        mockMvc.perform(get(
                        "/api/projects/{projectId}/applications/{applicationId}/dashboard/snapshot-markers",
                        PROJECT_ID,
                        APPLICATION_ID)
                        .param("since", "30d")
                        .param("limit", "50"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_snapshot_marker_query"));
    }

    @Test
    void markerRouteUsesProjectApplicationDashboardPathAndOptionalQueryParameters() {
        Method method = List.of(DashboardSnapshotController.class.getDeclaredMethods()).stream()
                .filter(candidate -> candidate.isAnnotationPresent(GetMapping.class))
                .filter(candidate -> candidate.getName().equals("getSnapshotMarkers"))
                .findFirst()
                .orElseThrow();

        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly("/snapshot-markers");
        assertThat(method.getParameters())
                .extracting(Parameter::getName)
                .containsExactly("projectId", "applicationId", "since", "limit");
        assertThat(method.getParameters()[2].getAnnotation(RequestParam.class).required()).isFalse();
        assertThat(method.getParameters()[3].getAnnotation(RequestParam.class).required()).isFalse();
    }

    private static DashboardSnapshotDetailReadModel detail() {
        return new DashboardSnapshotDetailReadModel(
                offset("2026-05-26T08:00:00Z"),
                "dashboard_snapshots",
                SnapshotReadSemantics.storedSnapshotDetail(),
                new SnapshotMetadata(
                        SNAPSHOT_ID,
                        offset("2026-05-26T08:00:00Z"),
                        offset("2026-05-26T08:00:00Z"),
                        new Window(offset("2026-05-26T07:45:00Z"), offset("2026-05-26T08:00:00Z")),
                        new Window(offset("2026-05-26T07:30:00Z"), offset("2026-05-26T07:45:00Z")),
                        "hourly_scheduled",
                        "active",
                        null,
                        null,
                        null),
                marker(),
                PreviousState.none(),
                LastHealthyAt.none(),
                null,
                new StoredReadModel(
                        "dashboard_read_model.v1",
                        "snapshot",
                        Map.of("type", "recent_30_minutes"),
                        Map.of("minimumRequestCount", 30),
                        Map.of("headline", "저장된 요약"),
                        Map.of("limitations", List.of("baseline_comparison_not_used_for_mvp")),
                        Map.of("red", Map.of("requestCount", 120)),
                        List.of(),
                        List.of(Map.of("kind", "endpoint")),
                        List.of(),
                        Map.of("source", "dashboard_snapshots.read_model_json"),
                        Map.of("applicationId", APPLICATION_ID.toString()),
                        Map.of("code", "active"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        List.of()),
                new SnapshotEndpointEvidence(
                        "dashboard_snapshots.read_model_json.endpointPriority",
                        10,
                        "stored_read_model",
                        null,
                        List.of()),
                new DashboardSnapshotDetailReadModel.InstanceSummary(
                        "dashboard_read_model.v1",
                        "dashboard_snapshots.read_model_json.instanceSummary.items",
                        50,
                        "stored_read_model",
                        null,
                        List.of()),
                new SnapshotLinks(
                        "/api/projects/%s/applications/%s/dashboard/snapshots/%s"
                                .formatted(PROJECT_ID, APPLICATION_ID, SNAPSHOT_ID),
                        "/api/projects/%s/applications/%s/dashboard/snapshot-markers?since=24h"
                                .formatted(PROJECT_ID, APPLICATION_ID)));
    }

    private static DashboardSnapshotMarkerReadModel markers() {
        return new DashboardSnapshotMarkerReadModel(
                offset("2026-05-26T08:10:35Z"),
                APPLICATION_ID,
                "dashboard_snapshots",
                new DashboardSnapshotMarkerReadModel.Horizon(
                        offset("2026-05-12T08:10:35Z"),
                        offset("2026-05-26T08:10:35Z"),
                        "14d",
                        "24h",
                        "14d",
                        336,
                        672,
                        "currentWindowEndUtc_asc"),
                null,
                List.of(marker()));
    }

    private static DashboardSnapshotMarkerItem marker() {
        return new DashboardSnapshotMarkerItem(
                "snapshot:%s:scheduled_snapshot".formatted(SNAPSHOT_ID),
                SNAPSHOT_ID,
                offset("2026-05-26T08:00:00Z"),
                offset("2026-05-26T08:00:00Z"),
                DashboardSnapshotMarkerType.SCHEDULED_SNAPSHOT,
                DashboardSnapshotMarkerSeverity.INFO,
                "stored_read_model_point",
                "hourly_scheduled",
                "active",
                PreviousState.none(),
                "정기 snapshot",
                "저장된 dashboard read model입니다.",
                "필요하면 snapshot detail에서 저장된 근거를 확인하세요.",
                null,
                null,
                null,
                new DashboardSnapshotMarkerItem.Links(
                        "/api/projects/%s/applications/%s/dashboard/snapshots/%s"
                                .formatted(PROJECT_ID, APPLICATION_ID, SNAPSHOT_ID)));
    }

    private static OffsetDateTime offset(String instant) {
        return OffsetDateTime.parse(instant);
    }
}
