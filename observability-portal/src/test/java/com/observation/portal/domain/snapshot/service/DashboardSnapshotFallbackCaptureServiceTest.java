package com.observation.portal.domain.snapshot.service;

import com.observation.portal.domain.dashboard.model.ApplicationDashboardReadModel;
import com.observation.portal.domain.ingest.model.StarterHeartbeatTelemetryRecord;
import com.observation.portal.domain.ingest.repository.StarterHeartbeatTelemetryRepository;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotCaptureReason;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotLatestRow;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotWriteCommand;
import com.observation.portal.domain.snapshot.repository.DashboardSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardSnapshotFallbackCaptureServiceTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000005801");
    private static final UUID APPLICATION_ID = UUID.fromString("00000000-0000-0000-0000-000000005811");
    private static final OffsetDateTime QUERY_AT = OffsetDateTime.parse("2026-05-27T13:05:00Z");

    private final DashboardSnapshotRepository snapshotRepository = mock(DashboardSnapshotRepository.class);
    private final DashboardSnapshotWriterService writerService = mock(DashboardSnapshotWriterService.class);
    private final DashboardSnapshotProperties snapshotProperties = new DashboardSnapshotProperties();
    private final StarterHeartbeatTelemetryRepository heartbeatTelemetryRepository =
            mock(StarterHeartbeatTelemetryRepository.class);
    private final DashboardSnapshotFallbackCaptureService service =
            new DashboardSnapshotFallbackCaptureService(
                    snapshotRepository,
                    writerService,
                    snapshotProperties,
                    heartbeatTelemetryRepository);

    @Test
    void capturesWhenLatestSnapshotIsMissingUsingAlreadyBuiltReadModel() {
        ApplicationDashboardReadModel readModel = readModel();
        givenRecentHeartbeat();
        when(snapshotRepository.findLatestByApplicationId(APPLICATION_ID)).thenReturn(Optional.empty());

        service.captureIfNeeded(readModel, QUERY_AT);

        ArgumentCaptor<DashboardSnapshotWriteCommand> captor =
                ArgumentCaptor.forClass(DashboardSnapshotWriteCommand.class);
        verify(writerService).write(captor.capture());
        assertThat(captor.getValue().readModel()).isSameAs(readModel);
        assertThat(captor.getValue().captureReason()).isEqualTo(DashboardSnapshotCaptureReason.QUERY_FALLBACK);
        assertThat(captor.getValue().currentWindowEndUtc()).isEqualTo(OffsetDateTime.parse("2026-05-27T13:00:00Z"));
        assertThat(captor.getValue().snapshotCutoffAt()).isEqualTo(QUERY_AT);
    }

    @Test
    void skipsWhenLatestSnapshotIsInsideDelayAwareGraceWindow() {
        givenRecentHeartbeat();
        when(snapshotRepository.findLatestByApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(latest("2026-05-27T12:29:00Z")));

        service.captureIfNeeded(readModel(), QUERY_AT);

        verify(writerService, never()).write(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void capturesWhenLatestSnapshotIsExactlyAtStalenessThreshold() {
        snapshotProperties.setCaptureDelay(Duration.ofSeconds(120));
        snapshotProperties.setFallbackGrace(Duration.ofMinutes(5));
        givenRecentHeartbeat();
        when(snapshotRepository.findLatestByApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(latest("2026-05-27T12:28:00Z")));

        service.captureIfNeeded(readModel(), QUERY_AT);

        verify(writerService).write(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void failsOpenWhenWriterThrows() {
        givenRecentHeartbeat();
        when(snapshotRepository.findLatestByApplicationId(APPLICATION_ID)).thenReturn(Optional.empty());
        doThrow(new DashboardSnapshotWriteException("boom", "persistence", new RuntimeException("boom")))
                .when(writerService)
                .write(org.mockito.ArgumentMatchers.any());

        assertThatNoException().isThrownBy(() -> service.captureIfNeeded(readModel(), QUERY_AT));
    }

    @Test
    void failsOpenWhenLatestSnapshotLookupThrows() {
        givenRecentHeartbeat();
        when(snapshotRepository.findLatestByApplicationId(APPLICATION_ID))
                .thenThrow(new RuntimeException("repository down"));

        assertThatNoException().isThrownBy(() -> service.captureIfNeeded(readModel(), QUERY_AT));

        verify(writerService, never()).write(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doesNotAddSecondFallbackRetryWhenWriterAlreadyClassifiesDuplicateConflict() {
        givenRecentHeartbeat();
        when(snapshotRepository.findLatestByApplicationId(APPLICATION_ID)).thenReturn(Optional.empty());
        doThrow(new DashboardSnapshotWriteException("conflict", "duplicate_conflict", new RuntimeException("boom")))
                .when(writerService)
                .write(org.mockito.ArgumentMatchers.any());

        assertThatNoException().isThrownBy(() -> service.captureIfNeeded(readModel(), QUERY_AT));

        verify(writerService, times(1)).write(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void skipsWhenStarterHeartbeatIsMissingAndKeepsDashboardFailOpen() {
        when(heartbeatTelemetryRepository.findLatestByApplicationScope(PROJECT_ID, "orders-api", "prod"))
                .thenReturn(Optional.empty());

        assertThatNoException().isThrownBy(() -> service.captureIfNeeded(readModel(), QUERY_AT));

        verify(snapshotRepository, never()).findLatestByApplicationId(APPLICATION_ID);
        verify(writerService, never()).write(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void skipsWhenStarterHeartbeatIsStaleByReportedInterval() {
        when(heartbeatTelemetryRepository.findLatestByApplicationScope(PROJECT_ID, "orders-api", "prod"))
                .thenReturn(Optional.of(heartbeat("2026-05-27T13:03:29Z", 30)));

        service.captureIfNeeded(readModel(), QUERY_AT);

        verify(snapshotRepository, never()).findLatestByApplicationId(APPLICATION_ID);
        verify(writerService, never()).write(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void capturesWhenHeartbeatIsFreshByLongerReportedInterval() {
        when(heartbeatTelemetryRepository.findLatestByApplicationScope(PROJECT_ID, "orders-api", "prod"))
                .thenReturn(Optional.of(heartbeat("2026-05-27T13:02:01Z", 60)));
        when(snapshotRepository.findLatestByApplicationId(APPLICATION_ID)).thenReturn(Optional.empty());

        service.captureIfNeeded(readModel(), QUERY_AT);

        verify(writerService).write(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void failsOpenWhenHeartbeatLookupThrows() {
        when(heartbeatTelemetryRepository.findLatestByApplicationScope(PROJECT_ID, "orders-api", "prod"))
                .thenThrow(new RuntimeException("heartbeat repository down"));

        assertThatNoException().isThrownBy(() -> service.captureIfNeeded(readModel(), QUERY_AT));

        verify(snapshotRepository, never()).findLatestByApplicationId(APPLICATION_ID);
        verify(writerService, never()).write(org.mockito.ArgumentMatchers.any());
    }

    private static DashboardSnapshotLatestRow latest(String generatedAt) {
        return new DashboardSnapshotLatestRow(
                UUID.fromString("00000000-0000-0000-0000-000000005899"),
                OffsetDateTime.parse(generatedAt),
                OffsetDateTime.parse(generatedAt),
                "active",
                "hourly_scheduled");
    }

    private void givenRecentHeartbeat() {
        when(heartbeatTelemetryRepository.findLatestByApplicationScope(PROJECT_ID, "orders-api", "prod"))
                .thenReturn(Optional.of(heartbeat("2026-05-27T13:04:00Z", 30)));
    }

    private static StarterHeartbeatTelemetryRecord heartbeat(String lastReceivedAtUtc, int intervalSeconds) {
        OffsetDateTime receivedAt = OffsetDateTime.parse(lastReceivedAtUtc);
        return new StarterHeartbeatTelemetryRecord(
                UUID.fromString("00000000-0000-0000-0000-000000005888"),
                PROJECT_ID,
                "orders-api",
                "prod",
                "pod-a",
                "1.0.0",
                receivedAt.minusSeconds(1),
                receivedAt,
                1,
                intervalSeconds,
                "valid",
                "received",
                receivedAt,
                receivedAt);
    }

    static ApplicationDashboardReadModel readModel() {
        OffsetDateTime windowEnd = OffsetDateTime.parse("2026-05-27T13:00:00Z");
        return new ApplicationDashboardReadModel(
                QUERY_AT,
                new ApplicationDashboardReadModel.Application(
                        PROJECT_ID,
                        APPLICATION_ID,
                        "orders-api",
                        "prod",
                        windowEnd.minusSeconds(30),
                        null,
                        new ApplicationDashboardReadModel.SourceWindow(
                                new ApplicationDashboardReadModel.Window(windowEnd.minusMinutes(15), windowEnd),
                                new ApplicationDashboardReadModel.Window(windowEnd.minusMinutes(30), windowEnd.minusMinutes(15))),
                        new ApplicationDashboardReadModel.Freshness(
                                windowEnd.minusSeconds(30),
                                windowEnd.plusSeconds(60),
                                windowEnd.plusSeconds(150))),
                new ApplicationDashboardReadModel.State(
                        "active",
                        "정상",
                        "현재 우선 조치가 필요한 신호는 없습니다.",
                        "다음 bucket까지 관찰하세요.",
                        "application"),
                new ApplicationDashboardReadModel.StarterConnection(
                        "starter_heartbeat",
                        windowEnd.minusSeconds(20),
                        "received",
                        "starter_connected",
                        "none"),
                new ApplicationDashboardReadModel.ZeroInsight(
                        "no_action_needed",
                        "현재 우선 조치가 필요한 신호는 없습니다.",
                        "트래픽이 유지되는지 다음 bucket까지 관찰하세요."),
                new ApplicationDashboardReadModel.Recovery(false, null, null, null),
                new ApplicationDashboardReadModel.Metrics(100L, 0L, java.math.BigDecimal.ZERO),
                ApplicationDashboardReadModel.SourceScopedPercentiles.empty(),
                ApplicationDashboardReadModel.HistogramDistribution.empty(),
                List.of(),
                List.of(),
                List.of(),
                null);
    }
}
