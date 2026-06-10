package com.observation.portal.domain.snapshot.service;

import com.observation.portal.domain.dashboard.model.ApplicationDashboardReadModel;
import com.observation.portal.domain.snapshot.entity.DashboardSnapshotEntity;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotCaptureReason;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotWriteCommand;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotWriteResult;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotWriteValues;
import com.observation.portal.domain.snapshot.repository.DashboardSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardSnapshotWriterServiceTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000005801");
    private static final UUID APPLICATION_ID = UUID.fromString("00000000-0000-0000-0000-000000005811");
    private static final OffsetDateTime WINDOW_END = OffsetDateTime.parse("2026-05-27T13:00:00Z");

    private final DashboardSnapshotRepository snapshotRepository = mock(DashboardSnapshotRepository.class);
    private final DashboardSnapshotReadModelEnricher enricher = mock(DashboardSnapshotReadModelEnricher.class);
    private final DashboardSnapshotCapturePolicy capturePolicy = mock(DashboardSnapshotCapturePolicy.class);
    private final DashboardSnapshotWriteMetrics metrics = mock(DashboardSnapshotWriteMetrics.class);
    private final DashboardSnapshotWriterService writerService = new DashboardSnapshotWriterService(
            snapshotRepository,
            enricher,
            capturePolicy,
            metrics,
            new NoopTransactionManager());

    @Test
    void scheduledWriteUsesCurrentWindowIdentityAndKeepsLegacyHourlyScheduledToken() throws Exception {
        stubCommonWriteInputs();
        when(snapshotRepository.insert(any())).thenAnswer(invocation ->
                new DashboardSnapshotEntity(invocation.getArgument(0)));

        writerService.write(command());

        ArgumentCaptor<DashboardSnapshotWriteValues> valuesCaptor =
                ArgumentCaptor.forClass(DashboardSnapshotWriteValues.class);
        verify(snapshotRepository).findByIdentityForUpdate(APPLICATION_ID, WINDOW_END);
        verify(snapshotRepository).insert(valuesCaptor.capture());
        DashboardSnapshotWriteValues values = valuesCaptor.getValue();
        assertThat(values.currentWindowEndUtc()).isEqualTo(WINDOW_END);
        assertThat(values.generatedAt()).isEqualTo(WINDOW_END.plusSeconds(5));
        assertThat(values.captureReason()).isEqualTo("hourly_scheduled");
    }

    @Test
    void retriesOnceOnlyForDashboardSnapshotIdentityUniqueConflict() throws Exception {
        stubCommonWriteInputs();
        AtomicInteger attempts = new AtomicInteger();
        when(snapshotRepository.insert(any())).thenAnswer(invocation -> {
            if (attempts.getAndIncrement() == 0) {
                throw duplicateIdentityConflict();
            }
            return new DashboardSnapshotEntity(invocation.getArgument(0));
        });

        DashboardSnapshotWriteResult result = writerService.write(command());

        assertThat(result.operation()).isEqualTo(DashboardSnapshotWriteResult.Operation.INSERT);
        verify(snapshotRepository, times(2)).insert(any());
        verify(metrics, never()).recordFailure(any(), any(), any());
        verify(metrics).recordSuccess(DashboardSnapshotCaptureReason.HOURLY_SCHEDULED.token(), "insert");
    }

    @Test
    void stopsAfterOneRetryAndClassifiesRepeatedIdentityConflictAsDuplicateConflict() throws Exception {
        stubCommonWriteInputs();
        when(snapshotRepository.insert(any())).thenThrow(duplicateIdentityConflict());

        assertThatThrownBy(() -> writerService.write(command()))
                .isInstanceOf(DashboardSnapshotWriteException.class)
                .satisfies(throwable -> assertThat(((DashboardSnapshotWriteException) throwable).failureType())
                        .isEqualTo("duplicate_conflict"));

        verify(snapshotRepository, times(2)).insert(any());
        verify(metrics).recordFailure(
                DashboardSnapshotCaptureReason.HOURLY_SCHEDULED.token(),
                "upsert",
                "duplicate_conflict");
    }

    @Test
    void classifiesOtherIntegrityFailuresAsPersistenceWithoutRetry() throws Exception {
        stubCommonWriteInputs();
        when(snapshotRepository.insert(any())).thenThrow(new DataIntegrityViolationException(
                "not null violation",
                new SQLException("not null violation", "23502")));

        assertThatThrownBy(() -> writerService.write(command()))
                .isInstanceOf(DashboardSnapshotWriteException.class)
                .satisfies(throwable -> assertThat(((DashboardSnapshotWriteException) throwable).failureType())
                        .isEqualTo("persistence"));

        verify(snapshotRepository, times(1)).insert(any());
        verify(metrics).recordFailure(
                DashboardSnapshotCaptureReason.HOURLY_SCHEDULED.token(),
                "upsert",
                "persistence");
    }

    @Test
    void fillsLegacyBaselineHelperWhenPublicReadModelBaselineIsNull() throws Exception {
        stubCommonWriteInputs();
        when(enricher.enrich(any())).thenReturn(new DashboardSnapshotReadModelEnricher.EnrichedSnapshotReadModel(
                """
                {
                  "schemaVersion": "dashboard_read_model.v1",
                  "mode": "snapshot",
                  "readSemantics": {
                    "source": "dashboard_snapshots.read_model_json",
                    "snapshotDetailRecalculates": false,
                    "markerIsStateSource": false,
                    "baselineComparisonUsedForMvpDecision": false,
                    "helperColumnsAreStateSource": false
                  }
                }
                """,
                null,
                null,
                null));
        when(snapshotRepository.insert(any())).thenAnswer(invocation ->
                new DashboardSnapshotEntity(invocation.getArgument(0)));

        writerService.write(command(readModelWithNullBaseline()));

        ArgumentCaptor<DashboardSnapshotWriteValues> valuesCaptor =
                ArgumentCaptor.forClass(DashboardSnapshotWriteValues.class);
        verify(snapshotRepository).insert(valuesCaptor.capture());
        DashboardSnapshotWriteValues values = valuesCaptor.getValue();
        assertThat(values.currentWindowStartUtc()).isEqualTo(WINDOW_END.minusMinutes(15));
        assertThat(values.currentWindowEndUtc()).isEqualTo(WINDOW_END);
        assertThat(values.baselineWindowStartUtc()).isEqualTo(WINDOW_END.minusMinutes(30));
        assertThat(values.baselineWindowEndUtc()).isEqualTo(WINDOW_END.minusMinutes(15));
        assertThat(values.readModelJson()).contains(
                "\"source\": \"dashboard_snapshots.read_model_json\"",
                "\"markerIsStateSource\": false",
                "\"baselineComparisonUsedForMvpDecision\": false",
                "\"helperColumnsAreStateSource\": false");
    }

    private void stubCommonWriteInputs() throws Exception {
        when(capturePolicy.representativeReason(any()))
                .thenReturn(DashboardSnapshotCaptureReason.HOURLY_SCHEDULED);
        when(enricher.enrich(any())).thenReturn(new DashboardSnapshotReadModelEnricher.EnrichedSnapshotReadModel(
                "{\"instanceSummary\":{\"schemaVersion\":\"1.0\",\"items\":[]}}",
                null,
                null,
                null));
        when(snapshotRepository.findByIdentityForUpdate(APPLICATION_ID, WINDOW_END))
                .thenReturn(Optional.empty());
    }

    private static DataIntegrityViolationException duplicateIdentityConflict() {
        return new DataIntegrityViolationException(
                "duplicate key value violates unique constraint "
                        + "\"uk_dashboard_snapshots_application_current_window_end\"",
                new SQLException(
                        "duplicate key value violates unique constraint "
                                + "\"uk_dashboard_snapshots_application_current_window_end\"",
                        "23505"));
    }

    private static DashboardSnapshotWriteCommand command() {
        return command(readModel());
    }

    private static DashboardSnapshotWriteCommand command(ApplicationDashboardReadModel readModel) {
        return new DashboardSnapshotWriteCommand(
                PROJECT_ID,
                APPLICATION_ID,
                readModel,
                DashboardSnapshotCaptureReason.HOURLY_SCHEDULED,
                WINDOW_END,
                WINDOW_END.plusSeconds(5),
                WINDOW_END.plusSeconds(5),
                "test");
    }

    private static ApplicationDashboardReadModel readModel() {
        return new ApplicationDashboardReadModel(
                WINDOW_END.plusSeconds(5),
                new ApplicationDashboardReadModel.Application(
                        PROJECT_ID,
                        APPLICATION_ID,
                        "orders-api",
                        "prod",
                        WINDOW_END.minusSeconds(30),
                        null,
                        new ApplicationDashboardReadModel.SourceWindow(
                                new ApplicationDashboardReadModel.Window(WINDOW_END.minusMinutes(15), WINDOW_END),
                                new ApplicationDashboardReadModel.Window(
                                        WINDOW_END.minusMinutes(30),
                                        WINDOW_END.minusMinutes(15))),
                        new ApplicationDashboardReadModel.Freshness(
                                WINDOW_END.minusSeconds(30),
                                WINDOW_END.plusSeconds(60),
                                WINDOW_END.plusSeconds(150))),
                new ApplicationDashboardReadModel.State(
                        "active",
                        "정상",
                        "현재 우선 조치가 필요한 신호는 없습니다.",
                        "다음 bucket까지 관찰하세요.",
                        "application"),
                new ApplicationDashboardReadModel.StarterConnection(
                        "starter_heartbeat",
                        WINDOW_END.minusSeconds(20),
                        "received",
                        "starter_connected",
                        "none"),
                new ApplicationDashboardReadModel.ZeroInsight(
                        "no_action_needed",
                        "현재 우선 조치가 필요한 신호는 없습니다.",
                        "트래픽이 유지되는지 관찰하세요."),
                new ApplicationDashboardReadModel.Recovery(false, null, null, null),
                new ApplicationDashboardReadModel.Metrics(100L, 0L, BigDecimal.ZERO),
                ApplicationDashboardReadModel.SourceScopedPercentiles.empty(),
                ApplicationDashboardReadModel.HistogramDistribution.empty(),
                List.of(),
                List.of(),
                List.of(),
                null);
    }

    private static ApplicationDashboardReadModel readModelWithNullBaseline() {
        ApplicationDashboardReadModel readModel = readModel();
        return new ApplicationDashboardReadModel(
                readModel.generatedAt(),
                new ApplicationDashboardReadModel.Application(
                        readModel.application().projectId(),
                        readModel.application().applicationId(),
                        readModel.application().name(),
                        readModel.application().environment(),
                        readModel.application().lastAcceptedBucketAt(),
                        readModel.application().lastHealthyAt(),
                        new ApplicationDashboardReadModel.SourceWindow(
                                readModel.application().sourceWindow().current(),
                                null),
                        readModel.application().freshness()),
                readModel.state(),
                readModel.starterConnection(),
                readModel.zeroInsight(),
                readModel.recovery(),
                readModel.metrics(),
                readModel.sourceScopedPercentiles(),
                readModel.histogramDistribution(),
                readModel.triageCards(),
                readModel.endpointPriority(),
                readModel.instances(),
                readModel.snapshot());
    }

    private static class NoopTransactionManager implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
