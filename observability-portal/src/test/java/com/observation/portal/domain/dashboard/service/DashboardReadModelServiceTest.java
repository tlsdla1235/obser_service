package com.observation.portal.domain.dashboard.service;

import com.observation.portal.common.time.AcceptedBucketFreshnessEvaluator;
import com.observation.portal.common.time.TimeBucketWindowCalculator;
import com.observation.portal.domain.bucket.model.WindowBucketAggregate;
import com.observation.portal.domain.bucket.repository.MetricBucketRepository;
import com.observation.portal.domain.catalog.entity.ApplicationEntity;
import com.observation.portal.domain.catalog.repository.ApplicationRepository;
import com.observation.portal.domain.dashboard.model.ApplicationDashboardReadModel;
import com.observation.portal.domain.ingest.model.StarterHeartbeatTelemetryRecord;
import com.observation.portal.domain.ingest.repository.StarterHeartbeatTelemetryRepository;
import com.observation.portal.domain.state.service.LifecycleStateService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DashboardReadModelServiceTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000005201");
    private static final UUID APPLICATION_ID = UUID.fromString("00000000-0000-0000-0000-000000005211");
    private static final Instant QUERY_AT = Instant.parse("2026-05-25T10:32:38.421Z");
    private static final Instant EVALUATION_AT = Instant.parse("2026-05-25T10:32:30Z");
    private static final Instant CURRENT_START = Instant.parse("2026-05-25T10:17:30Z");
    private static final Instant BASELINE_START = Instant.parse("2026-05-25T10:02:30Z");
    private static final Clock CLOCK = Clock.fixed(QUERY_AT, ZoneOffset.UTC);

    private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
    private final MetricBucketRepository metricBucketRepository = mock(MetricBucketRepository.class);
    private final StarterHeartbeatTelemetryRepository heartbeatRepository =
            mock(StarterHeartbeatTelemetryRepository.class);
    private final DashboardReadModelService service = new DashboardReadModelService(
            applicationRepository,
            metricBucketRepository,
            heartbeatRepository,
            new AcceptedBucketFreshnessEvaluator(CLOCK),
            new TimeBucketWindowCalculator(CLOCK),
            new LifecycleStateService(),
            CLOCK);

    @Test
    void assemblesDashboardWithFlooredWindowMetricsAndPlaceholderFields() {
        ApplicationEntity application = application();
        when(applicationRepository.findByIdAndProjectId(APPLICATION_ID, PROJECT_ID))
                .thenReturn(Optional.of(application));
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationIdAtOrBefore(APPLICATION_ID, EVALUATION_AT))
                .thenReturn(Optional.of(offset("2026-05-25T10:31:30Z")));
        when(metricBucketRepository.findWindowAggregateByApplicationId(APPLICATION_ID, CURRENT_START, EVALUATION_AT))
                .thenReturn(new WindowBucketAggregate(100L, 3L));
        when(heartbeatRepository.findLatestByApplicationScope(PROJECT_ID, "orders-api", "prod"))
                .thenReturn(Optional.of(heartbeat("2026-05-25T10:32:15Z")));

        ApplicationDashboardReadModel dashboard = service.getDashboard(PROJECT_ID, APPLICATION_ID).orElseThrow();

        assertThat(dashboard.generatedAt()).isEqualTo(offset(QUERY_AT));
        assertThat(dashboard.application().projectId()).isEqualTo(PROJECT_ID);
        assertThat(dashboard.application().applicationId()).isEqualTo(APPLICATION_ID);
        assertThat(dashboard.application().name()).isEqualTo("orders-api");
        assertThat(dashboard.application().environment()).isEqualTo("prod");
        assertThat(dashboard.application().lastAcceptedBucketAt()).isEqualTo(offset("2026-05-25T10:31:30Z"));
        assertThat(dashboard.application().lastHealthyAt()).isNull();
        assertThat(dashboard.application().sourceWindow().current().startUtc()).isEqualTo(offset(CURRENT_START));
        assertThat(dashboard.application().sourceWindow().current().endUtc()).isEqualTo(offset(EVALUATION_AT));
        assertThat(dashboard.application().sourceWindow().baseline().startUtc()).isEqualTo(offset(BASELINE_START));
        assertThat(dashboard.application().sourceWindow().baseline().endUtc()).isEqualTo(offset(CURRENT_START));
        assertThat(dashboard.application().freshness().lastObservedAt()).isEqualTo(offset("2026-05-25T10:31:30Z"));
        assertThat(dashboard.application().freshness().staleAt()).isEqualTo(offset("2026-05-25T10:33:00Z"));
        assertThat(dashboard.application().freshness().downAt()).isEqualTo(offset("2026-05-25T10:34:30Z"));
        assertThat(dashboard.state().code()).isEqualTo("active");
        assertThat(dashboard.starterConnection().statusSource()).isEqualTo("starter_heartbeat");
        assertThat(dashboard.starterConnection().lastHeartbeatAt()).isEqualTo(offset("2026-05-25T10:32:15Z"));
        assertThat(dashboard.starterConnection().lastHeartbeatStatus()).isEqualTo("received");
        assertThat(dashboard.starterConnection().connectionMeaning()).isEqualTo("starter_connected");
        assertThat(dashboard.starterConnection().stateImpact()).isEqualTo("none");
        assertThat(dashboard.zeroInsight().reasonCode()).isEqualTo("no_action_needed");
        assertThat(dashboard.zeroInsight().reasonCode()).isNotEqualTo("observing_recovery");
        assertThat(dashboard.recovery().isRecovering()).isFalse();
        assertThat(dashboard.metrics().requestCount()).isEqualTo(100L);
        assertThat(dashboard.metrics().errorCount()).isEqualTo(3L);
        assertThat(dashboard.metrics().errorRate()).isEqualByComparingTo("0.03");
        assertThat(dashboard.sourceScopedPercentiles().items()).isEmpty();
        assertThat(dashboard.triageCards()).isEmpty();
        assertThat(dashboard.endpointPriority()).isEmpty();
        assertThat(dashboard.snapshot()).isNull();
        assertThat(metricRecordComponentNames()).containsExactly("requestCount", "errorCount", "errorRate");
        verify(metricBucketRepository)
                .findLatestBucketEndUtcByApplicationIdAtOrBefore(APPLICATION_ID, EVALUATION_AT);
        verify(heartbeatRepository, never()).findLatestByProjectId(PROJECT_ID);
    }

    @Test
    void keepsTinyNonZeroErrorRateFromSerializingAsZero() {
        ApplicationDashboardReadModel dashboard = dashboard(
                "2026-05-25T10:32:00Z",
                "2026-05-25T10:32:20Z",
                3_000_000L,
                1L);

        assertThat(dashboard.metrics().errorRate()).isNotNull();
        assertThat(dashboard.metrics().errorRate()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void keepsCommonErrorRateMeaning() {
        ApplicationDashboardReadModel dashboard = dashboard(
                "2026-05-25T10:32:00Z",
                "2026-05-25T10:32:20Z",
                100L,
                3L);

        assertThat(dashboard.metrics().errorRate()).isEqualByComparingTo("0.03");
    }

    @Test
    void returnsNullErrorRateWhenRequestCountIsZero() {
        ApplicationDashboardReadModel dashboard = dashboard(
                "2026-05-25T10:32:00Z",
                "2026-05-25T10:32:20Z",
                0L,
                0L);

        assertThat(dashboard.metrics().errorRate()).isNull();
    }

    @Test
    void futureAcceptedBucketDoesNotChangeCurrentWindowOrFreshness() {
        when(applicationRepository.findByIdAndProjectId(APPLICATION_ID, PROJECT_ID))
                .thenReturn(Optional.of(application()));
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationIdAtOrBefore(APPLICATION_ID, EVALUATION_AT))
                .thenReturn(Optional.empty());
        when(metricBucketRepository.findWindowAggregateByApplicationId(APPLICATION_ID, CURRENT_START, EVALUATION_AT))
                .thenReturn(WindowBucketAggregate.zero());
        when(heartbeatRepository.findLatestByApplicationScope(PROJECT_ID, "orders-api", "prod"))
                .thenReturn(Optional.of(heartbeat("2026-05-25T10:32:20Z")));

        ApplicationDashboardReadModel dashboard = service.getDashboard(PROJECT_ID, APPLICATION_ID).orElseThrow();

        assertThat(dashboard.generatedAt()).isEqualTo(offset(QUERY_AT));
        assertThat(dashboard.application().sourceWindow().current().endUtc()).isEqualTo(offset(EVALUATION_AT));
        assertThat(dashboard.application().lastAcceptedBucketAt()).isNull();
        assertThat(dashboard.application().freshness().lastObservedAt()).isNull();
        assertThat(dashboard.zeroInsight().reasonCode()).isEqualTo("waiting_first_data");
        verify(metricBucketRepository)
                .findLatestBucketEndUtcByApplicationIdAtOrBefore(APPLICATION_ID, EVALUATION_AT);
        verify(metricBucketRepository, never()).findLatestBucketEndUtcByApplicationId(APPLICATION_ID);
    }

    @Test
    void returnsEmptyWhenApplicationDoesNotBelongToProject() {
        when(applicationRepository.findByIdAndProjectId(APPLICATION_ID, PROJECT_ID)).thenReturn(Optional.empty());

        assertThat(service.getDashboard(PROJECT_ID, APPLICATION_ID)).isEmpty();

        verifyNoInteractions(metricBucketRepository, heartbeatRepository);
    }

    @Test
    void mapsNoAcceptedBucketWithRecentHeartbeatToWaitingFirstData() {
        assertZeroInsight(null, "2026-05-25T10:32:20Z", 0L, "waiting_first_data");
    }

    @Test
    void mapsNoAcceptedBucketWithMissingHeartbeatToTelemetryUnreachable() {
        assertZeroInsight(null, null, 0L, "telemetry_unreachable");
    }

    @Test
    void mapsStaleAcceptedBucketWithRecentHeartbeatToMetricDataIdle() {
        assertZeroInsight("2026-05-25T10:30:30Z", "2026-05-25T10:32:20Z", 0L, "metric_data_idle");
    }

    @Test
    void mapsStaleAcceptedBucketWithStaleHeartbeatToTelemetryUnreachable() {
        assertZeroInsight("2026-05-25T10:30:30Z", "2026-05-25T10:29:00Z", 0L, "telemetry_unreachable");
    }

    @Test
    void mapsCurrentAcceptedBucketWithInsufficientSample() {
        assertZeroInsight("2026-05-25T10:32:00Z", "2026-05-25T10:32:20Z", 3L, "insufficient_sample");
    }

    @Test
    void mapsCurrentAcceptedBucketWithIdleTraffic() {
        assertZeroInsight("2026-05-25T10:32:00Z", "2026-05-25T10:32:20Z", 0L, "metric_data_idle");
    }

    @Test
    void mapsCurrentAcceptedBucketWithSufficientActiveTraffic() {
        assertZeroInsight("2026-05-25T10:32:00Z", null, 50L, "no_action_needed");
    }

    @Test
    void currentMetricDataAndStaleHeartbeatRemainSeparateAxes() {
        ApplicationDashboardReadModel dashboard = dashboard(
                "2026-05-25T10:32:00Z",
                "2026-05-25T10:29:00Z",
                50L);

        assertThat(dashboard.state().code()).isEqualTo("active");
        assertThat(dashboard.starterConnection().connectionMeaning()).isEqualTo("starter_disconnected");
        assertThat(dashboard.starterConnection().stateImpact()).isEqualTo("none");
        assertThat(dashboard.zeroInsight().reasonCode()).isEqualTo("no_action_needed");
    }

    private void assertZeroInsight(
            String latestBucketEndUtc,
            String latestHeartbeatAt,
            long requestCount,
            String expectedReasonCode) {
        ApplicationDashboardReadModel dashboard = dashboard(latestBucketEndUtc, latestHeartbeatAt, requestCount);

        assertThat(dashboard.zeroInsight()).isNotNull();
        assertThat(dashboard.zeroInsight().reasonCode()).isEqualTo(expectedReasonCode);
        assertThat(dashboard.zeroInsight().reasonCode()).isNotEqualTo("observing_recovery");
        assertThat(dashboard.triageCards()).isEmpty();
    }

    private ApplicationDashboardReadModel dashboard(
            String latestBucketEndUtc,
            String latestHeartbeatAt,
            long requestCount,
            long errorCount) {
        when(applicationRepository.findByIdAndProjectId(APPLICATION_ID, PROJECT_ID))
                .thenReturn(Optional.of(application()));
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationIdAtOrBefore(APPLICATION_ID, EVALUATION_AT))
                .thenReturn(latestBucketEndUtc == null
                        ? Optional.empty()
                        : Optional.of(offset(latestBucketEndUtc)));
        when(metricBucketRepository.findWindowAggregateByApplicationId(APPLICATION_ID, CURRENT_START, EVALUATION_AT))
                .thenReturn(new WindowBucketAggregate(requestCount, errorCount));
        when(heartbeatRepository.findLatestByApplicationScope(PROJECT_ID, "orders-api", "prod"))
                .thenReturn(latestHeartbeatAt == null
                        ? Optional.empty()
                        : Optional.of(heartbeat(latestHeartbeatAt)));

        return service.getDashboard(PROJECT_ID, APPLICATION_ID).orElseThrow();
    }

    private ApplicationDashboardReadModel dashboard(
            String latestBucketEndUtc,
            String latestHeartbeatAt,
            long requestCount) {
        return dashboard(latestBucketEndUtc, latestHeartbeatAt, requestCount, 0L);
    }

    private static List<String> metricRecordComponentNames() {
        return Arrays.stream(ApplicationDashboardReadModel.Metrics.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }

    private static ApplicationEntity application() {
        return new ApplicationEntity(
                APPLICATION_ID,
                PROJECT_ID,
                "orders-api",
                "prod",
                "active",
                offset("2026-05-25T10:00:00Z"),
                offset("2026-05-25T10:31:30Z"),
                offset("2026-05-25T10:00:00Z"),
                offset("2026-05-25T10:31:30Z"));
    }

    private static StarterHeartbeatTelemetryRecord heartbeat(String lastReceivedAtUtc) {
        OffsetDateTime receivedAt = offset(lastReceivedAtUtc);
        return new StarterHeartbeatTelemetryRecord(
                UUID.randomUUID(),
                PROJECT_ID,
                "orders-api",
                "prod",
                "pod-a",
                "0.1.0",
                receivedAt.minusSeconds(1),
                receivedAt,
                1L,
                30,
                "valid",
                "received",
                receivedAt,
                receivedAt);
    }

    private static OffsetDateTime offset(String instant) {
        return offset(Instant.parse(instant));
    }

    private static OffsetDateTime offset(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
