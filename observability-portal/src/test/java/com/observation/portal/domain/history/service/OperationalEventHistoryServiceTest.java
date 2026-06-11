package com.observation.portal.domain.history.service;

import com.observation.portal.domain.catalog.entity.ApplicationEntity;
import com.observation.portal.domain.catalog.repository.ApplicationRepository;
import com.observation.portal.domain.history.model.OperationalEventHistoryReadModel;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailRow;
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

class OperationalEventHistoryServiceTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000005901");
    private static final UUID APPLICATION_ID = UUID.fromString("00000000-0000-0000-0000-000000005911");
    private static final UUID SNAPSHOT_ID = UUID.fromString("00000000-0000-0000-0000-000000005921");
    private static final Instant QUERY_AT = Instant.parse("2026-05-27T13:10:35Z");
    private static final Clock CLOCK = Clock.fixed(QUERY_AT, ZoneOffset.UTC);

    private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
    private final DashboardSnapshotRepository snapshotRepository = mock(DashboardSnapshotRepository.class);
    private final OperationalEventHistoryProjector projector = mock(OperationalEventHistoryProjector.class);
    private OperationalEventHistoryService service;

    @BeforeEach
    void setUp() {
        service = serviceWithRetentionDays(14);
        when(applicationRepository.findByIdAndProjectId(APPLICATION_ID, PROJECT_ID))
                .thenReturn(Optional.of(application()));
    }

    @Test
    void validRequestUsesSnapshotRepositorySourceCapAndKeepsResponseLimit() {
        List<DashboardSnapshotDetailRow> sourceRows = List.of(row());
        when(snapshotRepository.findOperationalHistoryRows(
                PROJECT_ID,
                APPLICATION_ID,
                offset("2026-05-26T13:10:35Z"),
                offset("2026-05-27T13:10:35Z"),
                336))
                .thenReturn(sourceRows);
        when(projector.project(PROJECT_ID, APPLICATION_ID, sourceRows)).thenReturn(List.of());

        OperationalEventHistoryReadModel readModel = service.getHistory(PROJECT_ID, APPLICATION_ID, null, null)
                .orElseThrow();

        assertThat(readModel.source()).isEqualTo("dashboard_snapshots");
        assertThat(readModel.generatedAt()).isEqualTo(offset("2026-05-27T13:10:35Z"));
        assertThat(readModel.horizon().requestedSince()).isEqualTo("24h");
        assertThat(readModel.horizon().limit()).isEqualTo(50);
        assertThat(readModel.events()).isEmpty();
        verify(snapshotRepository).findOperationalHistoryRows(
                PROJECT_ID,
                APPLICATION_ID,
                offset("2026-05-26T13:10:35Z"),
                offset("2026-05-27T13:10:35Z"),
                336);
        verify(projector).project(PROJECT_ID, APPLICATION_ID, sourceRows);
    }

    @Test
    void sourceFetchCapScalesWithResponseLimitButStaysBounded() {
        when(snapshotRepository.findOperationalHistoryRows(
                PROJECT_ID,
                APPLICATION_ID,
                offset("2026-05-26T13:10:35Z"),
                offset("2026-05-27T13:10:35Z"),
                400))
                .thenReturn(List.of());
        when(projector.project(PROJECT_ID, APPLICATION_ID, List.of())).thenReturn(List.of());

        OperationalEventHistoryReadModel readModel = service.getHistory(PROJECT_ID, APPLICATION_ID, "24h", "100")
                .orElseThrow();

        assertThat(readModel.horizon().limit()).isEqualTo(100);
        verify(snapshotRepository).findOperationalHistoryRows(
                PROJECT_ID,
                APPLICATION_ID,
                offset("2026-05-26T13:10:35Z"),
                offset("2026-05-27T13:10:35Z"),
                400);
    }

    @Test
    void filtersRepositoryRowsOutsideQueryHorizonBeforeProjection() {
        DashboardSnapshotDetailRow expired = rowAt("2026-05-26T13:10:34Z");
        DashboardSnapshotDetailRow retained = rowAt("2026-05-26T13:10:35Z");
        when(snapshotRepository.findOperationalHistoryRows(
                PROJECT_ID,
                APPLICATION_ID,
                offset("2026-05-26T13:10:35Z"),
                offset("2026-05-27T13:10:35Z"),
                336))
                .thenReturn(List.of(expired, retained));
        when(projector.project(PROJECT_ID, APPLICATION_ID, List.of(retained))).thenReturn(List.of());

        OperationalEventHistoryReadModel readModel = service.getHistory(PROJECT_ID, APPLICATION_ID, "24h", "50")
                .orElseThrow();

        assertThat(readModel.events()).isEmpty();
        verify(projector).project(PROJECT_ID, APPLICATION_ID, List.of(retained));
        verify(projector, never()).project(PROJECT_ID, APPLICATION_ID, List.of(expired, retained));
    }

    @Test
    void clampsHistoryHorizonToConfiguredRetentionDays() {
        service = serviceWithRetentionDays(7);
        when(snapshotRepository.findOperationalHistoryRows(
                PROJECT_ID,
                APPLICATION_ID,
                offset("2026-05-20T13:10:35Z"),
                offset("2026-05-27T13:10:35Z"),
                336))
                .thenReturn(List.of());
        when(projector.project(PROJECT_ID, APPLICATION_ID, List.of())).thenReturn(List.of());

        OperationalEventHistoryReadModel readModel = service.getHistory(PROJECT_ID, APPLICATION_ID, "14d", "50")
                .orElseThrow();

        assertThat(readModel.horizon().requestedSince()).isEqualTo("14d");
        assertThat(readModel.horizon().since()).isEqualTo(offset("2026-05-20T13:10:35Z"));
        verify(snapshotRepository).findOperationalHistoryRows(
                PROJECT_ID,
                APPLICATION_ID,
                offset("2026-05-20T13:10:35Z"),
                offset("2026-05-27T13:10:35Z"),
                336);
    }

    @Test
    void catalogPathMismatchReturnsEmptyWithoutSnapshotLookup() {
        when(applicationRepository.findByIdAndProjectId(APPLICATION_ID, PROJECT_ID)).thenReturn(Optional.empty());

        assertThat(service.getHistory(PROJECT_ID, APPLICATION_ID, "24h", "50")).isEmpty();
        verifyNoInteractions(snapshotRepository);
    }

    @Test
    void snapshotLookupFailureMapsToProjectionException() {
        when(snapshotRepository.findOperationalHistoryRows(
                PROJECT_ID,
                APPLICATION_ID,
                offset("2026-05-26T13:10:35Z"),
                offset("2026-05-27T13:10:35Z"),
                336))
                .thenThrow(new IllegalStateException("database down"));

        assertThatThrownBy(() -> service.getHistory(PROJECT_ID, APPLICATION_ID, "24h", "50"))
                .isInstanceOf(OperationalEventHistoryProjectionException.class);
    }

    @Test
    void constructorDoesNotAcceptForbiddenLiveSourceDependencies() {
        List<String> constructorParameterTypeNames = Arrays.stream(OperationalEventHistoryService.class
                        .getConstructors())
                .map(Constructor::getParameterTypes)
                .flatMap(Arrays::stream)
                .map(Class::getSimpleName)
                .toList();

        assertThat(constructorParameterTypeNames).doesNotContain(
                "MetricBucketRepository",
                "StarterHeartbeatTelemetryRepository",
                "DashboardReadModelService",
                "LifecycleStateService",
                "TriageSummaryService",
                "EndpointPriorityService",
                "InstanceEvidenceReadModelService");
    }

    private OperationalEventHistoryService serviceWithRetentionDays(int retentionDays) {
        return new OperationalEventHistoryService(
                applicationRepository,
                snapshotRepository,
                projector,
                CLOCK,
                retentionDays);
    }

    private static DashboardSnapshotDetailRow row() {
        return rowAt("2026-05-27T12:00:00Z");
    }

    private static DashboardSnapshotDetailRow rowAt(String currentWindowEndUtc) {
        OffsetDateTime currentWindowEnd = offset(currentWindowEndUtc);
        return new DashboardSnapshotDetailRow(
                SNAPSHOT_ID,
                PROJECT_ID,
                APPLICATION_ID,
                currentWindowEnd,
                currentWindowEnd.minusMinutes(15),
                currentWindowEnd,
                currentWindowEnd.minusMinutes(30),
                currentWindowEnd.minusMinutes(15),
                "degraded",
                "high_confidence_concern",
                "endpoint_latency_spike",
                "POST /orders",
                new BigDecimal("0.84"),
                "{\"snapshotEndpointEvidence\":{\"items\":[]},\"triageCards\":[]}");
    }

    private static ApplicationEntity application() {
        return new ApplicationEntity(
                APPLICATION_ID,
                PROJECT_ID,
                "orders-api",
                "prod",
                "active",
                offset("2026-05-27T05:00:00Z"),
                offset("2026-05-27T12:00:00Z"),
                offset("2026-05-27T05:00:00Z"),
                offset("2026-05-27T12:00:00Z"));
    }

    private static OffsetDateTime offset(String instant) {
        return OffsetDateTime.parse(instant);
    }
}
