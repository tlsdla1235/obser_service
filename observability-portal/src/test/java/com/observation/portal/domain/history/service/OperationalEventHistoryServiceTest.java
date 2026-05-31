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
        service = new OperationalEventHistoryService(applicationRepository, snapshotRepository, projector, CLOCK);
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

    private static DashboardSnapshotDetailRow row() {
        return new DashboardSnapshotDetailRow(
                SNAPSHOT_ID,
                PROJECT_ID,
                APPLICATION_ID,
                offset("2026-05-27T12:00:00Z"),
                offset("2026-05-27T11:45:00Z"),
                offset("2026-05-27T12:00:00Z"),
                offset("2026-05-27T11:30:00Z"),
                offset("2026-05-27T11:45:00Z"),
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
