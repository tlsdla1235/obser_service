package com.observation.portal.domain.snapshot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel.LastHealthyAt;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel.PreviousState;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel.RecoveryMarker;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailRow;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotMarkerItem;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotMarkerSeverity;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotMarkerType;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotStoredReadModelProjection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardSnapshotMarkerClassifierTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000005801");
    private static final UUID APPLICATION_ID = UUID.fromString("00000000-0000-0000-0000-000000005811");
    private static final UUID SNAPSHOT_ID = UUID.fromString("00000000-0000-0000-0000-000000005821");
    private static final PreviousState NO_PREVIOUS = PreviousState.none();

    private final DashboardSnapshotDetailProjectionParser parser = new DashboardSnapshotDetailProjectionParser(
            new ObjectMapper().findAndRegisterModules(),
            new SnapshotEndpointEvidenceAnchorResolver());
    private final DashboardSnapshotMarkerClassifier classifier = new DashboardSnapshotMarkerClassifier();

    @Test
    void recoveryObservedOutranksCaptureReasonAndUsesObservingCopy() {
        DashboardSnapshotMarkerItem marker = marker(
                row("unknown", "high_confidence_concern", null),
                new PreviousState("down", "previous_dashboard_snapshot", UUID.randomUUID(), offset("2026-05-26T07:00:00Z")),
                projection("""
                        {
                          "recovery": {"isRecovering": true, "recommendedAction": "sample 확인"},
                          "zeroInsight": {"reasonCode": "observing_recovery"},
                          "triageCards": []
                        }
                        """));

        assertThat(marker.type()).isEqualTo(DashboardSnapshotMarkerType.RECOVERY_OBSERVED);
        assertThat(marker.severity()).isEqualTo(DashboardSnapshotMarkerSeverity.WARNING);
        assertThat(marker.title()).isEqualTo("회복 관찰 중");
        assertThat(marker.summary()).contains("sample");
        assertThat(marker.summary()).doesNotContain("복구 완료", "장애 해결", "앱이 다시 정상");

        RecoveryMarker recoveryMarker = classifier.recoveryMarker(
                marker,
                marker.previousState(),
                new LastHealthyAt(offset("2026-05-26T06:00:00Z"), "previous_active_dashboard_snapshot", UUID.randomUUID()));
        assertThat(recoveryMarker).isNotNull();
        assertThat(recoveryMarker.type()).isEqualTo(DashboardSnapshotMarkerType.RECOVERY_OBSERVED);
    }

    @Test
    void captureReasonIsSeedOnlyAndDoesNotForceSeverity() {
        DashboardSnapshotMarkerItem queryFallback = marker(row("active", "query_fallback", null), NO_PREVIOUS, emptyProjection());
        DashboardSnapshotMarkerItem scheduled = marker(row("active", "hourly_scheduled", null), NO_PREVIOUS, emptyProjection());

        assertThat(queryFallback.type()).isEqualTo(DashboardSnapshotMarkerType.QUERY_FALLBACK_SNAPSHOT);
        assertThat(queryFallback.severity()).isEqualTo(DashboardSnapshotMarkerSeverity.INFO);
        assertThat(scheduled.type()).isEqualTo(DashboardSnapshotMarkerType.SCHEDULED_SNAPSHOT);
        assertThat(scheduled.severity()).isEqualTo(DashboardSnapshotMarkerSeverity.INFO);
    }

    @Test
    void concernCaptureReasonsDoNotForceWarningWithoutStoredSignals() {
        DashboardSnapshotMarkerItem highConfidenceReason = marker(
                row("active", "high_confidence_concern", null),
                NO_PREVIOUS,
                emptyProjection());
        DashboardSnapshotMarkerItem shortStrongReason = marker(
                row("active", "short_strong_spike", null),
                NO_PREVIOUS,
                emptyProjection());

        assertThat(highConfidenceReason.type()).isEqualTo(DashboardSnapshotMarkerType.HIGH_CONFIDENCE_CONCERN);
        assertThat(highConfidenceReason.severity()).isEqualTo(DashboardSnapshotMarkerSeverity.INFO);
        assertThat(shortStrongReason.type()).isEqualTo(DashboardSnapshotMarkerType.SHORT_STRONG_SPIKE);
        assertThat(shortStrongReason.severity()).isEqualTo(DashboardSnapshotMarkerSeverity.INFO);
    }

    @Test
    void markerTypePriorityUsesPreviousStateHighConfidenceAndLegacyFallback() {
        DashboardSnapshotMarkerItem stateChange = marker(
                row("degraded", "hourly_scheduled", null),
                new PreviousState("active", "previous_dashboard_snapshot", UUID.randomUUID(), offset("2026-05-26T07:00:00Z")),
                emptyProjection());
        DashboardSnapshotMarkerItem highConfidence = marker(
                row("active", null, new BigDecimal("0.820")),
                NO_PREVIOUS,
                emptyProjection());
        DashboardSnapshotMarkerItem legacy = marker(row("active", "scheduled", null), NO_PREVIOUS, emptyProjection());

        assertThat(stateChange.type()).isEqualTo(DashboardSnapshotMarkerType.STATE_CHANGE);
        assertThat(highConfidence.type()).isEqualTo(DashboardSnapshotMarkerType.HIGH_CONFIDENCE_CONCERN);
        assertThat(highConfidence.severity()).isEqualTo(DashboardSnapshotMarkerSeverity.WARNING);
        assertThat(legacy.type()).isEqualTo(DashboardSnapshotMarkerType.STORED_SNAPSHOT);
        assertThat(legacy.severity()).isEqualTo(DashboardSnapshotMarkerSeverity.INFO);
    }

    @Test
    void markerTypeDoesNotForceSeverityWhenStoredStateIsNeutral() {
        DashboardSnapshotMarkerItem captureReasonStateChange = marker(
                row("active", "state_change", null),
                NO_PREVIOUS,
                emptyProjection());
        DashboardSnapshotMarkerItem previousStateChange = marker(
                row("active", "hourly_scheduled", null),
                new PreviousState("degraded", "previous_dashboard_snapshot", UUID.randomUUID(), offset("2026-05-26T07:00:00Z")),
                emptyProjection());

        assertThat(captureReasonStateChange.type()).isEqualTo(DashboardSnapshotMarkerType.STATE_CHANGE);
        assertThat(captureReasonStateChange.severity()).isEqualTo(DashboardSnapshotMarkerSeverity.INFO);
        assertThat(previousStateChange.type()).isEqualTo(DashboardSnapshotMarkerType.STATE_CHANGE);
        assertThat(previousStateChange.severity()).isEqualTo(DashboardSnapshotMarkerSeverity.INFO);
    }

    @Test
    void severityUsesStoredStateAndTriageSeverity() {
        DashboardSnapshotMarkerItem down = marker(row("down", "hourly_scheduled", null), NO_PREVIOUS, emptyProjection());
        DashboardSnapshotMarkerItem degraded = marker(row("degraded", null, null), NO_PREVIOUS, emptyProjection());
        DashboardSnapshotMarkerItem stale = marker(row("stale", null, null), NO_PREVIOUS, emptyProjection());
        DashboardSnapshotMarkerItem criticalTriage = marker(
                row("active", "hourly_scheduled", null),
                NO_PREVIOUS,
                projection("{\"triageCards\":[{\"severity\":\"critical\",\"confidence\":0.40}]}"));
        DashboardSnapshotMarkerItem warningTriage = marker(
                row("active", "hourly_scheduled", null),
                NO_PREVIOUS,
                projection("{\"triageCards\":[{\"severity\":\"warning\",\"confidence\":0.40}]}"));
        DashboardSnapshotMarkerItem highConfidenceTriage = marker(
                row("active", null, null),
                NO_PREVIOUS,
                projection("{\"triageCards\":[{\"severity\":\"info\",\"confidence\":0.82}]}"));
        DashboardSnapshotMarkerItem unknownState = marker(row("unknown", null, null), NO_PREVIOUS, emptyProjection());
        DashboardSnapshotMarkerItem legacyState = marker(row("mystery", null, null), NO_PREVIOUS, emptyProjection());

        assertThat(down.severity()).isEqualTo(DashboardSnapshotMarkerSeverity.CRITICAL);
        assertThat(degraded.severity()).isEqualTo(DashboardSnapshotMarkerSeverity.WARNING);
        assertThat(stale.severity()).isEqualTo(DashboardSnapshotMarkerSeverity.WARNING);
        assertThat(criticalTriage.severity()).isEqualTo(DashboardSnapshotMarkerSeverity.CRITICAL);
        assertThat(warningTriage.severity()).isEqualTo(DashboardSnapshotMarkerSeverity.WARNING);
        assertThat(highConfidenceTriage.type()).isEqualTo(DashboardSnapshotMarkerType.HIGH_CONFIDENCE_CONCERN);
        assertThat(highConfidenceTriage.severity()).isEqualTo(DashboardSnapshotMarkerSeverity.WARNING);
        assertThat(unknownState.severity()).isEqualTo(DashboardSnapshotMarkerSeverity.WARNING);
        assertThat(unknownState.summary()).isEqualTo("저장된 상태를 완전히 해석하지 못했습니다.");
        assertThat(legacyState.severity()).isEqualTo(DashboardSnapshotMarkerSeverity.WARNING);
        assertThat(legacyState.summary()).isEqualTo("저장된 상태를 완전히 해석하지 못했습니다.");
    }

    private DashboardSnapshotMarkerItem marker(
            DashboardSnapshotDetailRow row,
            PreviousState previousState,
            DashboardSnapshotStoredReadModelProjection projection) {
        return classifier.marker(row, previousState, projection, "/snapshot/" + row.snapshotId());
    }

    private DashboardSnapshotStoredReadModelProjection emptyProjection() {
        return projection("{\"triageCards\":[]}");
    }

    private DashboardSnapshotStoredReadModelProjection projection(String json) {
        return parser.project(json);
    }

    private static DashboardSnapshotDetailRow row(
            String stateCode,
            String captureReason,
            BigDecimal maxConfidence) {
        return new DashboardSnapshotDetailRow(
                SNAPSHOT_ID,
                PROJECT_ID,
                APPLICATION_ID,
                offset("2026-05-26T08:00:00Z"),
                offset("2026-05-26T07:45:00Z"),
                offset("2026-05-26T08:00:00Z"),
                offset("2026-05-26T07:30:00Z"),
                offset("2026-05-26T07:45:00Z"),
                stateCode,
                captureReason,
                "rule-1",
                "POST /orders",
                maxConfidence,
                "{\"triageCards\":[]}");
    }

    private static OffsetDateTime offset(String instant) {
        return OffsetDateTime.parse(instant);
    }
}
