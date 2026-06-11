package com.observation.portal.domain.instance.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstanceDashboardReadModelShapeTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000013801");
    private static final UUID APPLICATION_ID = UUID.fromString("00000000-0000-0000-0000-000000013811");
    private static final UUID INSTANCE_ID = UUID.fromString("00000000-0000-0000-0000-000000013821");

    @Test
    @DisplayName("Instance Dashboard top-level shape에는 instance lifecycle state 계열 필드가 없다")
    void topLevelShapeDoesNotExposeIndependentInstanceStateOrHealthFields() {
        List<String> fieldNames = componentNames(InstanceDashboardReadModel.class);

        assertThat(fieldNames).containsExactly(
                "schemaVersion",
                "mode",
                "generatedAt",
                "application",
                "instance",
                "window",
                "thresholds",
                "applicationStateRef",
                "observationStatus",
                "applicationContribution",
                "dataQuality",
                "starterConnection",
                "signals",
                "endpointEvidence",
                "resourceEvidence",
                "patterns",
                "snapshot",
                "readSemantics",
                "links",
                "excludedCapabilities");
        assertThat(fieldNames).doesNotContain(
                "state",
                "stateCode",
                "health",
                "healthScore",
                "rootCause",
                "recoveryProof");
    }

    @Test
    @DisplayName("snapshot readSemantics는 accepted_at cutoff 금지와 late metric 포함 가능성을 강제한다")
    void snapshotReadSemanticsRejectsCutoffAndRecalculationFlags() {
        assertThatThrownBy(() -> new InstanceDashboardReadModel.ReadSemantics(
                "accepted_metric_buckets",
                "selected_application_snapshot",
                "dashboard_snapshots",
                true,
                true,
                true,
                false,
                true,
                false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InstanceDashboardReadModel.ReadSemantics(
                "accepted_metric_buckets",
                "selected_application_snapshot",
                "dashboard_snapshots",
                false,
                true,
                true,
                true,
                true,
                false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InstanceDashboardReadModel.ReadSemantics(
                "accepted_metric_buckets",
                "selected_application_snapshot",
                "dashboard_snapshots",
                false,
                false,
                true,
                false,
                true,
                false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("live readSemantics는 recent 30 minutes source와 snapshot 미사용을 유지한다")
    void liveReadSemanticsRejectsSnapshotOnlyFlags() {
        assertThatThrownBy(() -> new InstanceDashboardReadModel.ReadSemantics(
                "accepted_metric_buckets",
                "live_recent_30_minutes",
                "dashboard_snapshots",
                false,
                false,
                false,
                false,
                true,
                false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InstanceDashboardReadModel.ReadSemantics(
                "dashboard_snapshots",
                "live_recent_30_minutes",
                null,
                false,
                false,
                false,
                false,
                true,
                false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("mode와 window/readSemantics/applicationStateRef는 서로 다른 source 조합을 거부한다")
    void readModelRejectsCrossModeSourceMismatch() {
        InstanceDashboardReadModel.Snapshot snapshot = snapshot();

        assertThatThrownBy(() -> readModel(
                "snapshot",
                new InstanceDashboardReadModel.Window(
                        "recent_30_minutes",
                        OffsetDateTime.parse("2026-06-10T07:30:00Z"),
                        OffsetDateTime.parse("2026-06-10T08:00:00Z"),
                        30,
                        "live_recent_30_minutes"),
                snapshot,
                new InstanceDashboardReadModel.ApplicationStateRef(
                        "application",
                        "selected_application_snapshot",
                        "degraded",
                        snapshot.snapshotId()),
                new InstanceDashboardReadModel.ReadSemantics(
                        "accepted_metric_buckets",
                        "live_recent_30_minutes",
                        null,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> readModel(
                "snapshot",
                snapshotWindow(),
                snapshot,
                new InstanceDashboardReadModel.ApplicationStateRef(
                        "application",
                        "selected_application_snapshot",
                        "degraded",
                        UUID.fromString("00000000-0000-0000-0000-000000013899")),
                snapshotReadSemantics()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("bounded fixture는 snapshot/source semantics와 제외 capability를 함께 담는다")
    void boundedFixtureCarriesSourceSemanticsAndExcludedCapabilities() {
        InstanceDashboardReadModel readModel = readModel();

        assertThat(readModel.mode()).isEqualTo("snapshot");
        assertThat(readModel.window().name()).isEqualTo("recent_30_minutes");
        assertThat(readModel.window().windowSource()).isEqualTo("selected_application_snapshot");
        assertThat(readModel.applicationStateRef().lifecycleOwner()).isEqualTo("application");
        assertThat(readModel.observationStatus().code()).isEqualTo("observed");
        assertThat(readModel.readSemantics().acceptedAtCutoffApplied()).isFalse();
        assertThat(readModel.readSemantics().includesLateAcceptedMetrics()).isTrue();
        assertThat(readModel.readSemantics().mayDifferFromStoredApplicationSnapshot()).isTrue();
        assertThat(readModel.readSemantics().applicationSnapshotRecalculated()).isFalse();
        assertThat(readModel.readSemantics().markerIsStateSource()).isFalse();
        assertThat(readModel.excludedCapabilities()).contains(
                "instance_lifecycle_state",
                "instance_health_score",
                "root_cause",
                "recovery_proof");
    }

    private static InstanceDashboardReadModel readModel() {
        UUID snapshotId = UUID.fromString("00000000-0000-0000-0000-000000013831");
        InstanceDashboardReadModel.Snapshot snapshot = snapshot();
        return readModel(
                "snapshot",
                snapshotWindow(),
                snapshot,
                new InstanceDashboardReadModel.ApplicationStateRef(
                        "application",
                        "selected_application_snapshot",
                        "degraded",
                        snapshotId),
                snapshotReadSemantics());
    }

    private static InstanceDashboardReadModel readModel(
            String mode,
            InstanceDashboardReadModel.Window window,
            InstanceDashboardReadModel.Snapshot snapshot,
            InstanceDashboardReadModel.ApplicationStateRef applicationStateRef,
            InstanceDashboardReadModel.ReadSemantics readSemantics) {
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
                window,
                thresholds(),
                applicationStateRef,
                new InstanceDashboardReadModel.ObservationStatus(
                        "observed",
                        "selected_instance_metric_bucket_observed",
                        OffsetDateTime.parse("2026-06-10T07:59:30Z")),
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
                readSemantics,
                new InstanceDashboardReadModel.Links(
                        "/api/projects/%s/applications/%s/snapshots/%s/instances/%s/dashboard"
                                .formatted(PROJECT_ID, APPLICATION_ID, snapshot.snapshotId(), INSTANCE_ID),
                        "/api/projects/%s/applications/%s/dashboard".formatted(PROJECT_ID, APPLICATION_ID),
                        "/api/projects/%s/applications/%s/instances/%s/evidence"
                                .formatted(PROJECT_ID, APPLICATION_ID, INSTANCE_ID),
                        "/api/projects/%s/applications/%s/instances/%s/snapshot-trend"
                                .formatted(PROJECT_ID, APPLICATION_ID, INSTANCE_ID),
                        "/api/projects/%s/applications/%s/dashboard/snapshots/%s"
                                .formatted(PROJECT_ID, APPLICATION_ID, snapshot.snapshotId())),
                List.of(
                        "instance_lifecycle_state",
                        "instance_health_score",
                        "root_cause",
                        "recovery_proof",
                        "marker_bucket_as_state_source"));
    }

    private static InstanceDashboardReadModel.Window snapshotWindow() {
        return new InstanceDashboardReadModel.Window(
                "recent_30_minutes",
                OffsetDateTime.parse("2026-06-10T07:30:00Z"),
                OffsetDateTime.parse("2026-06-10T08:00:00Z"),
                30,
                "selected_application_snapshot");
    }

    private static InstanceDashboardReadModel.ReadSemantics snapshotReadSemantics() {
        return new InstanceDashboardReadModel.ReadSemantics(
                "accepted_metric_buckets",
                "selected_application_snapshot",
                "dashboard_snapshots",
                false,
                true,
                true,
                false,
                true,
                false);
    }

    private static InstanceDashboardReadModel.Snapshot snapshot() {
        return new InstanceDashboardReadModel.Snapshot(
                UUID.fromString("00000000-0000-0000-0000-000000013831"),
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

    private static List<String> componentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }
}
