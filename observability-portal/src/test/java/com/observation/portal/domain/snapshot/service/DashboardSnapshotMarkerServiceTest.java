package com.observation.portal.domain.snapshot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.portal.domain.catalog.entity.ApplicationEntity;
import com.observation.portal.domain.catalog.repository.ApplicationRepository;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailRow;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotMarkerReadModel;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotSourceRow;
import com.observation.portal.domain.snapshot.repository.DashboardSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DashboardSnapshotMarkerServiceTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000005801");
    private static final UUID APPLICATION_ID = UUID.fromString("00000000-0000-0000-0000-000000005811");
    private static final UUID SNAPSHOT_EARLIER = UUID.fromString("00000000-0000-0000-0000-000000005821");
    private static final UUID SNAPSHOT_TIED_LOW = UUID.fromString("00000000-0000-0000-0000-000000005822");
    private static final UUID SNAPSHOT_TIED_HIGH = UUID.fromString("00000000-0000-0000-0000-000000005823");
    private static final UUID SNAPSHOT_STALE = UUID.fromString("00000000-0000-0000-0000-000000005824");
    private static final UUID SNAPSHOT_DOWN = UUID.fromString("00000000-0000-0000-0000-000000005825");
    private static final UUID SNAPSHOT_RECOVERY = UUID.fromString("00000000-0000-0000-0000-000000005826");
    private static final Instant QUERY_AT = Instant.parse("2026-05-26T08:10:35Z");
    private static final Clock CLOCK = Clock.fixed(QUERY_AT, ZoneOffset.UTC);
    private static final OffsetDateTime SNAPSHOT_CUTOFF = offset("2026-05-16T08:10:35Z");

    private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
    private final DashboardSnapshotRepository snapshotRepository = mock(DashboardSnapshotRepository.class);
    private final DashboardSnapshotDetailProjectionParser parser = new DashboardSnapshotDetailProjectionParser(
            new ObjectMapper().findAndRegisterModules(),
            new SnapshotEndpointEvidenceAnchorResolver());
    private final DashboardSnapshotMarkerClassifier classifier = new DashboardSnapshotMarkerClassifier();

    private DashboardSnapshotMarkerService service;

    @BeforeEach
    void setUp() {
        service = new DashboardSnapshotMarkerService(
                applicationRepository,
                snapshotRepository,
                parser,
                classifier,
                CLOCK,
                10);
        when(applicationRepository.findByIdAndProjectId(APPLICATION_ID, PROJECT_ID))
                .thenReturn(Optional.of(application()));
    }

    @Test
    void defaultQueryUses24hAndReturnsEmptyStateWhenNoSnapshotsExist() {
        when(snapshotRepository.findMarkerRows(
                PROJECT_ID,
                APPLICATION_ID,
                offset("2026-05-25T08:10:35Z"),
                offset("2026-05-26T08:10:35Z"),
                50))
                .thenReturn(List.of());

        DashboardSnapshotMarkerReadModel markers = service.getMarkers(PROJECT_ID, APPLICATION_ID, null, null)
                .orElseThrow();

        assertThat(markers.generatedAt()).isEqualTo(offset("2026-05-26T08:10:35Z"));
        assertThat(markers.horizon().requestedSince()).isEqualTo("24h");
        assertThat(markers.horizon().limit()).isEqualTo(50);
        assertThat(markers.emptyState().reasonCode()).isEqualTo("no_snapshots_in_retention");
        assertThat(markers.emptyState().message()).isEqualTo("보관 기간 안에 표시할 snapshot marker가 없습니다.");
        assertThat(markers.emptyState().message())
                .doesNotContain("문제 없음", "현재 정상", "복구 완료", "장애 해결 완료");
        assertThat(markers.markers()).isEmpty();
    }

    @Test
    void failureRecoveryMarkersUseStoredSnapshotCopyWithoutRecoveryCompletionLanguage() {
        when(snapshotRepository.findMarkerRows(
                PROJECT_ID,
                APPLICATION_ID,
                offset("2026-05-25T08:10:35Z"),
                offset("2026-05-26T08:10:35Z"),
                50))
                .thenReturn(List.of(
                        row(SNAPSHOT_RECOVERY, "2026-05-26T08:00:00Z", "unknown", "state_change", null, recoveryJson()),
                        row(SNAPSHOT_DOWN, "2026-05-26T07:50:00Z", "down", "state_change", null),
                        row(SNAPSHOT_STALE, "2026-05-26T07:40:00Z", "stale", "state_change", null)));
        when(snapshotRepository.findPreviousSnapshot(APPLICATION_ID, offset("2026-05-26T07:40:00Z"), SNAPSHOT_CUTOFF))
                .thenReturn(Optional.of(sourceRow("active", "2026-05-26T07:00:00Z")));
        when(snapshotRepository.findPreviousSnapshot(APPLICATION_ID, offset("2026-05-26T07:50:00Z"), SNAPSHOT_CUTOFF))
                .thenReturn(Optional.of(sourceRow("stale", "2026-05-26T07:40:00Z")));
        when(snapshotRepository.findPreviousSnapshot(APPLICATION_ID, offset("2026-05-26T08:00:00Z"), SNAPSHOT_CUTOFF))
                .thenReturn(Optional.of(sourceRow("down", "2026-05-26T07:50:00Z")));

        DashboardSnapshotMarkerReadModel markers = service.getMarkers(PROJECT_ID, APPLICATION_ID, "24h", "50")
                .orElseThrow();

        assertThat(markers.markers())
                .extracting(marker -> marker.type().value())
                .containsExactly("state_change", "state_change", "recovery_observed");
        assertThat(markers.markers().get(0).summary())
                .contains("application state가 관찰")
                .doesNotContain("host application down", "host process down");
        assertThat(markers.markers().get(1).summary())
                .contains("application state가 관찰")
                .doesNotContain("host application down", "host process down");
        assertThat(markers.markers().get(2).title()).isEqualTo("회복 관찰 중");
        assertThat(markers.markers().get(2).summary()).contains("판단 sample이 아직 부족");
        assertThat(markers.markers())
                .allSatisfy(marker -> assertThat(marker.title() + " " + marker.summary() + " " + marker.recommendedAction())
                        .doesNotContain("복구 완료", "장애 해결 완료", "앱 정상 확정", "문제 없음", "현재 정상"));
    }

    @Test
    void clampsSinceByRetentionAndLimitByMaxThenOrdersCurrentWindowAscending() {
        when(snapshotRepository.findMarkerRows(
                PROJECT_ID,
                APPLICATION_ID,
                offset("2026-05-16T08:10:35Z"),
                offset("2026-05-26T08:10:35Z"),
                672))
                .thenReturn(List.of(
                        row(SNAPSHOT_TIED_HIGH, "2026-05-26T07:05:00Z", "2026-05-26T07:00:00Z", "active", null, (BigDecimal) null),
                        row(SNAPSHOT_TIED_LOW, "2026-05-26T07:00:00Z", "2026-05-26T07:00:00Z", "active", "hourly_scheduled", (BigDecimal) null),
                        row(SNAPSHOT_EARLIER, "2026-05-26T08:40:00Z", "2026-05-26T06:00:00Z", "degraded", "query_fallback", (BigDecimal) null)));
        when(snapshotRepository.findPreviousSnapshot(APPLICATION_ID, offset("2026-05-26T06:00:00Z"), SNAPSHOT_CUTOFF))
                .thenReturn(Optional.empty());
        when(snapshotRepository.findPreviousSnapshot(APPLICATION_ID, offset("2026-05-26T07:00:00Z"), SNAPSHOT_CUTOFF))
                .thenReturn(Optional.of(sourceRow("active", "2026-05-26T06:00:00Z")));

        DashboardSnapshotMarkerReadModel markers = service.getMarkers(PROJECT_ID, APPLICATION_ID, "14d", "999")
                .orElseThrow();

        assertThat(markers.horizon().since()).isEqualTo(offset("2026-05-16T08:10:35Z"));
        assertThat(markers.horizon().limit()).isEqualTo(672);
        assertThat(markers.markers())
                .extracting(marker -> marker.snapshotId())
                .containsExactly(SNAPSHOT_EARLIER, SNAPSHOT_TIED_LOW, SNAPSHOT_TIED_HIGH);
        assertThat(markers.markers().get(0).type().value()).isEqualTo("state_observation");
        assertThat(markers.markers().get(0).severity().value()).isEqualTo("warning");
        verify(snapshotRepository).findMarkerRows(
                PROJECT_ID,
                APPLICATION_ID,
                offset("2026-05-16T08:10:35Z"),
                offset("2026-05-26T08:10:35Z"),
                672);
    }

    @Test
    void filtersRepositoryRowsOutsideEffectiveRetentionHorizonBeforeMarkerProjection() {
        when(snapshotRepository.findMarkerRows(
                PROJECT_ID,
                APPLICATION_ID,
                offset("2026-05-16T08:10:35Z"),
                offset("2026-05-26T08:10:35Z"),
                50))
                .thenReturn(List.of(
                        row(SNAPSHOT_STALE, "2026-05-15T08:40:00Z", "2026-05-15T08:00:00Z", "stale", null, (BigDecimal) null),
                        row(SNAPSHOT_EARLIER, "2026-05-16T08:40:00Z", "2026-05-16T08:10:35Z", "active", null, (BigDecimal) null)));
        when(snapshotRepository.findPreviousSnapshot(APPLICATION_ID, offset("2026-05-16T08:10:35Z"), SNAPSHOT_CUTOFF))
                .thenReturn(Optional.empty());

        DashboardSnapshotMarkerReadModel markers = service.getMarkers(PROJECT_ID, APPLICATION_ID, "14d", "50")
                .orElseThrow();

        assertThat(markers.markers())
                .extracting(marker -> marker.snapshotId())
                .containsExactly(SNAPSHOT_EARLIER);
        verify(snapshotRepository, never()).findPreviousSnapshot(APPLICATION_ID, offset("2026-05-15T08:00:00Z"), SNAPSHOT_CUTOFF);
    }

    @Test
    void invalidQueryFailsBeforeCatalogPathLookup() {
        assertThatThrownBy(() -> service.getMarkers(PROJECT_ID, APPLICATION_ID, "30d", "50"))
                .isInstanceOf(InvalidSnapshotMarkerQueryException.class);
        assertThatThrownBy(() -> service.getMarkers(PROJECT_ID, APPLICATION_ID, "24h", "0"))
                .isInstanceOf(InvalidSnapshotMarkerQueryException.class);
        assertThatThrownBy(() -> service.getMarkers(PROJECT_ID, APPLICATION_ID, "24h", "abc"))
                .isInstanceOf(InvalidSnapshotMarkerQueryException.class);

        verifyNoInteractions(snapshotRepository);
    }

    @Test
    void catalogPathMismatchReturnsEmptyWithoutSnapshotLookup() {
        when(applicationRepository.findByIdAndProjectId(APPLICATION_ID, PROJECT_ID)).thenReturn(Optional.empty());

        assertThat(service.getMarkers(PROJECT_ID, APPLICATION_ID, "24h", "50")).isEmpty();
        verifyNoInteractions(snapshotRepository);
    }

    @Test
    void constructorDoesNotAcceptForbiddenCurrentRecalculationDependencies() {
        List<String> constructorParameterTypeNames = Arrays.stream(DashboardSnapshotMarkerService.class
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

    private static DashboardSnapshotDetailRow row(
            UUID snapshotId,
            String generatedAt,
            String stateCode,
            String captureReason,
            BigDecimal maxConfidence) {
        return row(snapshotId, generatedAt, generatedAt, stateCode, captureReason, maxConfidence, "{\"triageCards\":[]}");
    }

    private static DashboardSnapshotDetailRow row(
            UUID snapshotId,
            String generatedAt,
            String stateCode,
            String captureReason,
            BigDecimal maxConfidence,
            String readModelJson) {
        return row(snapshotId, generatedAt, generatedAt, stateCode, captureReason, maxConfidence, readModelJson);
    }

    private static DashboardSnapshotDetailRow row(
            UUID snapshotId,
            String generatedAt,
            String currentWindowEndUtc,
            String stateCode,
            String captureReason,
            BigDecimal maxConfidence) {
        return row(
                snapshotId,
                generatedAt,
                currentWindowEndUtc,
                stateCode,
                captureReason,
                maxConfidence,
                "{\"triageCards\":[]}");
    }

    private static DashboardSnapshotDetailRow row(
            UUID snapshotId,
            String generatedAt,
            String currentWindowEndUtc,
            String stateCode,
            String captureReason,
            BigDecimal maxConfidence,
            String readModelJson) {
        OffsetDateTime currentWindowEnd = offset(currentWindowEndUtc);
        return new DashboardSnapshotDetailRow(
                snapshotId,
                PROJECT_ID,
                APPLICATION_ID,
                offset(generatedAt),
                currentWindowEnd.minusMinutes(30),
                currentWindowEnd,
                currentWindowEnd.minusMinutes(60),
                currentWindowEnd.minusMinutes(30),
                stateCode,
                captureReason,
                null,
                null,
                maxConfidence,
                readModelJson);
    }

    private static DashboardSnapshotSourceRow sourceRow(String stateCode, String generatedAt) {
        return new DashboardSnapshotSourceRow(
                UUID.randomUUID(),
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

    private static OffsetDateTime offset(String instant) {
        return OffsetDateTime.parse(instant);
    }

    private static String recoveryJson() {
        return """
                {
                  "triageCards": [],
                  "snapshotEndpointEvidence": {"items": []},
                  "recovery": {"isRecovering": true, "recommendedAction": "다음 bucket까지 관찰하세요."},
                  "zeroInsight": {"reasonCode": "observing_recovery"}
                }
                """;
    }
}
