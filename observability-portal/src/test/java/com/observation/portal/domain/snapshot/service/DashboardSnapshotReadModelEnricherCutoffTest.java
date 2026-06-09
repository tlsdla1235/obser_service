package com.observation.portal.domain.snapshot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.portal.common.time.AcceptedBucketFreshnessEvaluator;
import com.observation.portal.domain.bucket.model.EndpointEvidenceRow;
import com.observation.portal.domain.bucket.model.WindowBucketAggregate;
import com.observation.portal.domain.bucket.repository.MetricBucketRepository;
import com.observation.portal.domain.catalog.entity.ApplicationInstanceEntity;
import com.observation.portal.domain.catalog.repository.ApplicationInstanceRepository;
import com.observation.portal.domain.dashboard.model.ApplicationDashboardReadModel;
import com.observation.portal.domain.dashboard.service.EndpointEvidenceAggregationService;
import com.observation.portal.domain.ingest.repository.StarterHeartbeatTelemetryRepository;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotCaptureReason;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotWriteCommand;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardSnapshotReadModelEnricherCutoffTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000005801");
    private static final UUID APPLICATION_ID = UUID.fromString("00000000-0000-0000-0000-000000005811");
    private static final UUID INSTANCE_ID = UUID.fromString("00000000-0000-0000-0000-000000005821");
    private static final OffsetDateTime WINDOW_END = OffsetDateTime.parse("2026-05-27T13:00:00Z");
    private static final OffsetDateTime WINDOW_START = WINDOW_END.minusMinutes(15);
    private static final OffsetDateTime SNAPSHOT_CUTOFF_AT = OffsetDateTime.parse("2026-05-27T13:02:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final MetricBucketRepository metricBucketRepository = mock(MetricBucketRepository.class);
    private final ApplicationInstanceRepository applicationInstanceRepository =
            mock(ApplicationInstanceRepository.class);
    private final StarterHeartbeatTelemetryRepository heartbeatRepository =
            mock(StarterHeartbeatTelemetryRepository.class);
    private final DashboardSnapshotReadModelEnricher enricher = new DashboardSnapshotReadModelEnricher(
            objectMapper,
            metricBucketRepository,
            applicationInstanceRepository,
            heartbeatRepository,
            new EndpointEvidenceAggregationService(objectMapper),
            new AcceptedBucketFreshnessEvaluator(Clock.fixed(WINDOW_END.toInstant(), ZoneOffset.UTC)));

    @Test
    void instanceSummaryUsesCommandSnapshotCutoffForInstanceBucketEvidence() throws Exception {
        when(applicationInstanceRepository.findByApplicationId(APPLICATION_ID))
                .thenReturn(List.of(new ApplicationInstanceEntity(
                        INSTANCE_ID,
                        APPLICATION_ID,
                        "pod-a",
                        WINDOW_START,
                        WINDOW_END,
                        WINDOW_START,
                        WINDOW_END)));
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationInstanceIdAtOrBeforeAcceptedAt(
                INSTANCE_ID,
                WINDOW_END.toInstant(),
                SNAPSHOT_CUTOFF_AT))
                .thenReturn(Optional.of(WINDOW_END.minusSeconds(30)));
        when(metricBucketRepository.findWindowAggregateByApplicationInstanceIdAcceptedAtOrBefore(
                INSTANCE_ID,
                WINDOW_START.toInstant(),
                WINDOW_END.toInstant(),
                SNAPSHOT_CUTOFF_AT))
                .thenReturn(new WindowBucketAggregate(100L, 0L));
        when(metricBucketRepository.findLatestRuntimeRatioEvidenceRowByApplicationInstanceIdAcceptedAtOrBefore(
                INSTANCE_ID,
                WINDOW_START.toInstant(),
                WINDOW_END.toInstant(),
                SNAPSHOT_CUTOFF_AT))
                .thenReturn(Optional.empty());
        when(metricBucketRepository.findLocalPercentileEvidenceRowsByApplicationInstanceIdAcceptedAtOrBefore(
                INSTANCE_ID,
                WINDOW_START.toInstant(),
                WINDOW_END.toInstant(),
                SNAPSHOT_CUTOFF_AT))
                .thenReturn(List.of());
        when(metricBucketRepository.findEndpointEvidenceRowsByApplicationInstanceIdAcceptedAtOrBefore(
                INSTANCE_ID,
                WINDOW_START.toInstant(),
                WINDOW_END.toInstant(),
                SNAPSHOT_CUTOFF_AT))
                .thenReturn(List.of(endpointEvidenceRow()));
        when(heartbeatRepository.findByIdentityAtOrBeforeReceivedAt(
                PROJECT_ID,
                "orders-api",
                "prod",
                "pod-a",
                WINDOW_END))
                .thenReturn(Optional.empty());

        enricher.enrich(command());

        verify(metricBucketRepository).findLatestBucketEndUtcByApplicationInstanceIdAtOrBeforeAcceptedAt(
                INSTANCE_ID,
                WINDOW_END.toInstant(),
                SNAPSHOT_CUTOFF_AT);
        verify(metricBucketRepository).findWindowAggregateByApplicationInstanceIdAcceptedAtOrBefore(
                INSTANCE_ID,
                WINDOW_START.toInstant(),
                WINDOW_END.toInstant(),
                SNAPSHOT_CUTOFF_AT);
        verify(metricBucketRepository).findEndpointEvidenceRowsByApplicationInstanceIdAcceptedAtOrBefore(
                INSTANCE_ID,
                WINDOW_START.toInstant(),
                WINDOW_END.toInstant(),
                SNAPSHOT_CUTOFF_AT);
        verify(heartbeatRepository).findByIdentityAtOrBeforeReceivedAt(
                PROJECT_ID,
                "orders-api",
                "prod",
                "pod-a",
                WINDOW_END);
        verify(metricBucketRepository, never()).findWindowAggregateByApplicationInstanceId(
                INSTANCE_ID,
                WINDOW_START.toInstant(),
                WINDOW_END.toInstant());
    }

    private static DashboardSnapshotWriteCommand command() {
        return new DashboardSnapshotWriteCommand(
                PROJECT_ID,
                APPLICATION_ID,
                readModel(),
                DashboardSnapshotCaptureReason.HOURLY_SCHEDULED,
                WINDOW_END,
                SNAPSHOT_CUTOFF_AT,
                SNAPSHOT_CUTOFF_AT,
                "test");
    }

    private static ApplicationDashboardReadModel readModel() {
        return new ApplicationDashboardReadModel(
                WINDOW_END,
                new ApplicationDashboardReadModel.Application(
                        PROJECT_ID,
                        APPLICATION_ID,
                        "orders-api",
                        "prod",
                        WINDOW_END.minusSeconds(30),
                        null,
                        new ApplicationDashboardReadModel.SourceWindow(
                                new ApplicationDashboardReadModel.Window(WINDOW_START, WINDOW_END),
                                new ApplicationDashboardReadModel.Window(
                                        WINDOW_START.minusMinutes(15),
                                        WINDOW_START)),
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
                        "트래픽이 유지되는지 다음 bucket까지 관찰하세요."),
                new ApplicationDashboardReadModel.Recovery(false, null, null, null),
                new ApplicationDashboardReadModel.Metrics(100L, 0L, BigDecimal.ZERO),
                ApplicationDashboardReadModel.SourceScopedPercentiles.empty(),
                ApplicationDashboardReadModel.HistogramDistribution.empty(),
                List.of(),
                List.of(endpointPriority()),
                List.of(),
                null);
    }

    private static ApplicationDashboardReadModel.EndpointPriorityItem endpointPriority() {
        return new ApplicationDashboardReadModel.EndpointPriorityItem(
                1,
                "POST",
                "/orders",
                "POST /orders",
                ApplicationDashboardReadModel.EndpointPriorityReason.ERROR_SPIKE,
                List.of("endpoint_error_spike"),
                0.91d,
                91,
                new ApplicationDashboardReadModel.EndpointPriorityFreshness(
                        "current",
                        WINDOW_END.minusSeconds(30),
                        "current",
                        null),
                new ApplicationDashboardReadModel.EndpointPriorityEvidence(
                        100L,
                        5L,
                        new BigDecimal("0.05"),
                        100L,
                        0L,
                        BigDecimal.ZERO,
                        new BigDecimal("0.05"),
                        List.of(new ApplicationDashboardReadModel.HistogramBucket(500L, 100L)),
                        List.of(new ApplicationDashboardReadModel.HistogramBucket(500L, 100L)),
                        null,
                        null,
                        null,
                        "accepted_bucket",
                        ApplicationDashboardReadModel.EndpointEvidenceStatus.AVAILABLE,
                        ApplicationDashboardReadModel.EndpointEvidenceStatus.AVAILABLE),
                "이 endpoint를 먼저 확인하세요.");
    }

    private static EndpointEvidenceRow endpointEvidenceRow() {
        return new EndpointEvidenceRow(
                APPLICATION_ID,
                WINDOW_END.minusSeconds(30),
                WINDOW_END,
                """
                [{"method":"POST","route":"/orders","requestCount":100,"errorCount":5,
                  "durationBuckets":[{"leMs":500,"count":100}]}]
                """);
    }
}
