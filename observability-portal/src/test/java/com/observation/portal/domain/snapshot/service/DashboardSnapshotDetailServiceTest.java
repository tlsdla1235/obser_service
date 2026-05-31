package com.observation.portal.domain.snapshot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.portal.domain.catalog.entity.ApplicationEntity;
import com.observation.portal.domain.catalog.repository.ApplicationRepository;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailRow;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotSourceRow;
import com.observation.portal.domain.snapshot.repository.DashboardSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DashboardSnapshotDetailServiceTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000005801");
    private static final UUID APPLICATION_ID = UUID.fromString("00000000-0000-0000-0000-000000005811");
    private static final UUID SNAPSHOT_ID = UUID.fromString("00000000-0000-0000-0000-000000005821");
    private static final UUID PREVIOUS_SNAPSHOT_ID = UUID.fromString("00000000-0000-0000-0000-000000005822");
    private static final UUID HEALTHY_SNAPSHOT_ID = UUID.fromString("00000000-0000-0000-0000-000000005823");

    private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
    private final DashboardSnapshotRepository snapshotRepository = mock(DashboardSnapshotRepository.class);
    private final DashboardSnapshotDetailProjectionParser parser = new DashboardSnapshotDetailProjectionParser(
            new ObjectMapper().findAndRegisterModules(),
            new SnapshotEndpointEvidenceAnchorResolver());
    private final DashboardSnapshotMarkerClassifier classifier = new DashboardSnapshotMarkerClassifier();

    private DashboardSnapshotDetailService service;

    @BeforeEach
    void setUp() {
        service = new DashboardSnapshotDetailService(
                applicationRepository,
                snapshotRepository,
                parser,
                classifier);
        when(applicationRepository.findByIdAndProjectId(APPLICATION_ID, PROJECT_ID))
                .thenReturn(Optional.of(application()));
    }

    @Test
    void returnsStoredSnapshotDetailWithoutCurrentRecalculation() {
        DashboardSnapshotDetailRow row = row("degraded", "hourly_scheduled", storedJson());
        when(snapshotRepository.findDetailRow(PROJECT_ID, APPLICATION_ID, SNAPSHOT_ID)).thenReturn(Optional.of(row));
        when(snapshotRepository.findPreviousSnapshot(APPLICATION_ID, row.currentWindowEndUtc()))
                .thenReturn(Optional.of(sourceRow(PREVIOUS_SNAPSHOT_ID, "active", "2026-05-26T07:00:00Z")));
        when(snapshotRepository.findPreviousActiveSnapshot(APPLICATION_ID, row.currentWindowEndUtc()))
                .thenReturn(Optional.of(sourceRow(HEALTHY_SNAPSHOT_ID, "active", "2026-05-26T06:00:00Z")));

        DashboardSnapshotDetailReadModel detail = service.getDetail(PROJECT_ID, APPLICATION_ID, SNAPSHOT_ID)
                .orElseThrow();

        assertThat(detail.source()).isEqualTo("dashboard_snapshots");
        assertThat(detail.readSemantics().mode()).isEqualTo("stored_snapshot_detail");
        assertThat(detail.readSemantics().currentStateRecalculated()).isFalse();
        assertThat(detail.readSemantics().liveSourcesJoined()).isEmpty();
        assertThat(detail.readSemantics().rawReadModelJsonExposed()).isFalse();
        assertThat(detail.snapshot().captureReason()).isEqualTo("hourly_scheduled");
        assertThat(detail.snapshot().storedApplicationStateCode()).isEqualTo("degraded");
        assertThat(detail.marker().type().value()).isEqualTo("state_change");
        assertThat(detail.previousState().stateCode()).isEqualTo("active");
        assertThat(detail.previousState().source()).isEqualTo("previous_dashboard_snapshot");
        assertThat(detail.lastHealthyAt().value()).isEqualTo(offset("2026-05-26T06:00:00Z"));
        assertThat(detail.lastHealthyAt().source()).isEqualTo("previous_active_dashboard_snapshot");
        assertThat(detail.snapshotEndpointEvidence().items().get(0).anchorId()).isEqualTo("endpoint-evidence-1");
        assertThat(detail.instanceSummary().items().get(0).endpointEvidenceRefs().get(0).anchorStatus())
                .isEqualTo("resolved");
        assertThat(detail.instanceSummary().items().get(0).endpointEvidenceRefs().get(0).snapshotDetailAnchor())
                .isEqualTo("endpoint-evidence-1");
        assertThat(detail.links().self()).endsWith("/dashboard/snapshots/" + SNAPSHOT_ID);
    }

    @Test
    void recoveryObservedSnapshotDetailKeepsStoredObservationCopyWithoutCurrentFallback() {
        DashboardSnapshotDetailRow row = row("unknown", "state_change", recoveryJson());
        when(snapshotRepository.findDetailRow(PROJECT_ID, APPLICATION_ID, SNAPSHOT_ID)).thenReturn(Optional.of(row));
        when(snapshotRepository.findPreviousSnapshot(APPLICATION_ID, row.currentWindowEndUtc()))
                .thenReturn(Optional.of(sourceRow(PREVIOUS_SNAPSHOT_ID, "down", "2026-05-26T07:30:00Z")));
        when(snapshotRepository.findPreviousActiveSnapshot(APPLICATION_ID, row.currentWindowEndUtc()))
                .thenReturn(Optional.of(sourceRow(HEALTHY_SNAPSHOT_ID, "active", "2026-05-26T06:00:00Z")));

        DashboardSnapshotDetailReadModel detail = service.getDetail(PROJECT_ID, APPLICATION_ID, SNAPSHOT_ID)
                .orElseThrow();

        assertThat(detail.readSemantics().mode()).isEqualTo("stored_snapshot_detail");
        assertThat(detail.readSemantics().currentStateRecalculated()).isFalse();
        assertThat(detail.readSemantics().liveSourcesJoined()).isEmpty();
        assertThat(detail.marker().type().value()).isEqualTo("recovery_observed");
        assertThat(detail.marker().title()).isEqualTo("회복 관찰 중");
        assertThat(detail.recoveryMarker()).isNotNull();
        assertThat(detail.recoveryMarker().type().value()).isEqualTo("recovery_observed");
        assertThat(detail.recoveryMarker().title()).isEqualTo("회복 관찰 중");
        assertThat(detail.recoveryMarker().summary()).contains("판단 sample이 아직 부족");
        assertThat(detail.lastHealthyAt().source()).isEqualTo("previous_active_dashboard_snapshot");

        String userFacingCopy = detail.marker().title() + " " + detail.marker().summary() + " "
                + detail.marker().recommendedAction() + " " + detail.recoveryMarker().title() + " "
                + detail.recoveryMarker().summary() + " " + detail.recoveryMarker().recommendedAction();
        assertThat(userFacingCopy)
                .doesNotContain("복구 완료", "장애 해결 완료", "앱 정상 확정", "문제 없음", "현재 정상");
    }

    @Test
    void missingCatalogPathOrSnapshotReturnsEmptyWithoutFallbackProjection() {
        when(applicationRepository.findByIdAndProjectId(APPLICATION_ID, PROJECT_ID)).thenReturn(Optional.empty());

        assertThat(service.getDetail(PROJECT_ID, APPLICATION_ID, SNAPSHOT_ID)).isEmpty();
        verifyNoInteractions(snapshotRepository);

        when(applicationRepository.findByIdAndProjectId(APPLICATION_ID, PROJECT_ID))
                .thenReturn(Optional.of(application()));
        when(snapshotRepository.findDetailRow(PROJECT_ID, APPLICATION_ID, SNAPSHOT_ID)).thenReturn(Optional.empty());

        assertThat(service.getDetail(PROJECT_ID, APPLICATION_ID, SNAPSHOT_ID)).isEmpty();
        verify(snapshotRepository, never()).findPreviousSnapshot(APPLICATION_ID, offset("2026-05-26T08:00:00Z"));
    }

    @Test
    void storedJsonProjectionFailureSurfacesAs500CandidateWithoutFallback() {
        DashboardSnapshotDetailRow row = row("active", "hourly_scheduled", "{");
        when(snapshotRepository.findDetailRow(PROJECT_ID, APPLICATION_ID, SNAPSHOT_ID)).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.getDetail(PROJECT_ID, APPLICATION_ID, SNAPSHOT_ID))
                .isInstanceOf(DashboardSnapshotProjectionException.class);
        verify(snapshotRepository, never()).findPreviousSnapshot(APPLICATION_ID, row.currentWindowEndUtc());
    }

    @Test
    void constructorDoesNotAcceptForbiddenCurrentRecalculationDependencies() {
        List<String> constructorParameterTypeNames = Arrays.stream(DashboardSnapshotDetailService.class
                        .getConstructors())
                .map(Constructor::getParameterTypes)
                .flatMap(Arrays::stream)
                .map(Class::getSimpleName)
                .toList();

        assertThat(constructorParameterTypeNames).doesNotContain(
                "MetricBucketRepository",
                "StarterHeartbeatTelemetryRepository",
                "LifecycleStateService",
                "TriageSummaryService",
                "EndpointPriorityService",
                "DashboardReadModelService",
                "InstanceEvidenceReadModelService");
    }

    private static DashboardSnapshotDetailRow row(String stateCode, String captureReason, String readModelJson) {
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
                "global_error_spike",
                "POST /orders",
                new BigDecimal("0.840"),
                readModelJson);
    }

    private static DashboardSnapshotSourceRow sourceRow(UUID snapshotId, String stateCode, String generatedAt) {
        return new DashboardSnapshotSourceRow(
                snapshotId,
                offset(generatedAt),
                offset(generatedAt),
                stateCode);
    }

    private static ApplicationEntity application() {
        return new ApplicationEntity(
                APPLICATION_ID,
                PROJECT_ID,
                "orders-api",
                "prod",
                "active",
                offset("2026-05-26T05:00:00Z"),
                offset("2026-05-26T08:00:00Z"),
                offset("2026-05-26T05:00:00Z"),
                offset("2026-05-26T08:00:00Z"));
    }

    private static String storedJson() {
        return """
                {
                  "application": {"name": "orders-api"},
                  "state": {"code": "degraded"},
                  "recovery": {"isRecovering": false},
                  "triageCards": [{"severity": "warning", "confidence": 0.84}],
                  "snapshotEndpointEvidence": {
                    "source": "bounded_endpoint_evidence",
                    "maxItems": 10,
                    "items": [
                      {"method": "POST", "route": "/orders", "endpointKey": "POST /orders", "rank": 1}
                    ]
                  },
                  "instanceSummary": {
                    "schemaVersion": "1.0",
                    "source": "bounded_instance_summary",
                    "maxItems": 50,
                    "items": [
                      {
                        "instanceId": "00000000-0000-0000-0000-000000005831",
                        "instanceName": "pod-a",
                        "observationStatus": "observed",
                        "endpointEvidenceRefs": [
                          {"endpointKey": "POST /orders", "method": "POST", "route": "/orders"}
                        ]
                      }
                    ]
                  }
                }
                """;
    }

    private static String recoveryJson() {
        return """
                {
                  "application": {"name": "orders-api"},
                  "state": {"code": "unknown"},
                  "recovery": {"isRecovering": true, "recommendedAction": "다음 bucket까지 관찰하세요."},
                  "zeroInsight": {"reasonCode": "observing_recovery"},
                  "triageCards": [],
                  "snapshotEndpointEvidence": {
                    "source": "bounded_endpoint_evidence",
                    "maxItems": 10,
                    "items": []
                  },
                  "instanceSummary": {
                    "schemaVersion": "1.0",
                    "source": "bounded_instance_summary",
                    "maxItems": 50,
                    "items": []
                  }
                }
                """;
    }

    private static OffsetDateTime offset(String instant) {
        return OffsetDateTime.parse(instant);
    }
}
