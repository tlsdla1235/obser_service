package com.observation.portal.domain.instance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.portal.domain.catalog.entity.ApplicationEntity;
import com.observation.portal.domain.catalog.entity.ApplicationInstanceEntity;
import com.observation.portal.domain.catalog.repository.ApplicationInstanceRepository;
import com.observation.portal.domain.catalog.repository.ApplicationRepository;
import com.observation.portal.domain.instance.model.InstanceSnapshotTrendReadModel;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotTrendRow;
import com.observation.portal.domain.snapshot.repository.DashboardSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
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

class InstanceSnapshotTrendServiceTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000005201");
    private static final UUID OTHER_PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000009201");
    private static final UUID APPLICATION_ID = UUID.fromString("00000000-0000-0000-0000-000000005211");
    private static final UUID INSTANCE_ID = UUID.fromString("00000000-0000-0000-0000-000000005221");
    private static final Instant QUERY_AT = Instant.parse("2026-05-26T08:10:35Z");
    private static final Clock CLOCK = Clock.fixed(QUERY_AT, ZoneOffset.UTC);

    private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
    private final ApplicationInstanceRepository applicationInstanceRepository =
            mock(ApplicationInstanceRepository.class);
    private final DashboardSnapshotRepository dashboardSnapshotRepository = mock(DashboardSnapshotRepository.class);
    private final InstanceSnapshotTrendParser parser = new InstanceSnapshotTrendParser(new ObjectMapper());

    private InstanceSnapshotTrendService service;

    @BeforeEach
    void setUp() {
        service = new InstanceSnapshotTrendService(
                applicationRepository,
                applicationInstanceRepository,
                dashboardSnapshotRepository,
                parser,
                CLOCK,
                14);
        stubCatalogPathConsistency();
    }

    @Test
    void returnsEmptyPointsWhenSnapshotRowsAreMissingWithDefaultQuery() {
        when(dashboardSnapshotRepository.findTrendRowsNewestFirst(
                PROJECT_ID,
                APPLICATION_ID,
                OffsetDateTime.parse("2026-05-19T08:10:35Z"),
                OffsetDateTime.parse("2026-05-26T08:10:35Z"),
                336))
                .thenReturn(List.of());

        InstanceSnapshotTrendReadModel trend = service.getTrend(
                        PROJECT_ID,
                        APPLICATION_ID,
                        INSTANCE_ID,
                        null,
                        null)
                .orElseThrow();

        assertThat(trend.generatedAt()).isEqualTo(OffsetDateTime.parse("2026-05-26T08:10:35Z"));
        assertThat(trend.application().projectId()).isEqualTo(PROJECT_ID);
        assertThat(trend.application().applicationId()).isEqualTo(APPLICATION_ID);
        assertThat(trend.application().links().dashboard())
                .isEqualTo("/api/projects/%s/applications/%s/dashboard".formatted(PROJECT_ID, APPLICATION_ID));
        assertThat(trend.instance().instanceId()).isEqualTo(INSTANCE_ID);
        assertThat(trend.instance().links().evidence())
                .isEqualTo("/api/projects/%s/applications/%s/instances/%s/evidence"
                        .formatted(PROJECT_ID, APPLICATION_ID, INSTANCE_ID));
        assertThat(trend.source()).isEqualTo(InstanceSnapshotTrendReadModel.SOURCE);
        assertThat(trend.horizon().requestedSince()).isEqualTo("7d");
        assertThat(trend.horizon().since()).isEqualTo(OffsetDateTime.parse("2026-05-19T08:10:35Z"));
        assertThat(trend.horizon().until()).isEqualTo(OffsetDateTime.parse("2026-05-26T08:10:35Z"));
        assertThat(trend.horizon().limit()).isEqualTo(336);
        assertThat(trend.points()).isEmpty();
    }

    @Test
    void clampsSinceByRetentionAndLimitByMax() {
        service = new InstanceSnapshotTrendService(
                applicationRepository,
                applicationInstanceRepository,
                dashboardSnapshotRepository,
                parser,
                CLOCK,
                10);
        when(dashboardSnapshotRepository.findTrendRowsNewestFirst(
                PROJECT_ID,
                APPLICATION_ID,
                OffsetDateTime.parse("2026-05-16T08:10:35Z"),
                OffsetDateTime.parse("2026-05-26T08:10:35Z"),
                672))
                .thenReturn(List.of());

        InstanceSnapshotTrendReadModel trend = service.getTrend(
                        PROJECT_ID,
                        APPLICATION_ID,
                        INSTANCE_ID,
                        "14d",
                        "999")
                .orElseThrow();

        assertThat(trend.horizon().requestedSince()).isEqualTo("14d");
        assertThat(trend.horizon().since()).isEqualTo(OffsetDateTime.parse("2026-05-16T08:10:35Z"));
        assertThat(trend.horizon().limit()).isEqualTo(672);
        verify(dashboardSnapshotRepository).findTrendRowsNewestFirst(
                PROJECT_ID,
                APPLICATION_ID,
                OffsetDateTime.parse("2026-05-16T08:10:35Z"),
                OffsetDateTime.parse("2026-05-26T08:10:35Z"),
                672);
    }

    @Test
    void rejectsNonPositiveRetentionConfiguration() {
        assertThatThrownBy(() -> new InstanceSnapshotTrendService(
                applicationRepository,
                applicationInstanceRepository,
                dashboardSnapshotRepository,
                parser,
                CLOCK,
                0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("retentionDays must be positive");
        assertThatThrownBy(() -> new InstanceSnapshotTrendService(
                applicationRepository,
                applicationInstanceRepository,
                dashboardSnapshotRepository,
                parser,
                CLOCK,
                -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("retentionDays must be positive");
    }

    @Test
    void rejectsInvalidSinceAndLimitBeforeRepositoryLookup() {
        assertThatThrownBy(() -> service.getTrend(PROJECT_ID, APPLICATION_ID, INSTANCE_ID, "24h", "168"))
                .isInstanceOf(InvalidSnapshotTrendQueryException.class);
        assertThatThrownBy(() -> service.getTrend(PROJECT_ID, APPLICATION_ID, INSTANCE_ID, "7d", "0"))
                .isInstanceOf(InvalidSnapshotTrendQueryException.class);
        assertThatThrownBy(() -> service.getTrend(PROJECT_ID, APPLICATION_ID, INSTANCE_ID, "7d", "abc"))
                .isInstanceOf(InvalidSnapshotTrendQueryException.class);

        verifyNoInteractions(dashboardSnapshotRepository);
    }

    @Test
    void returnsEmptyWhenApplicationOrInstanceCatalogPathMismatch() {
        when(applicationRepository.findByIdAndProjectId(APPLICATION_ID, OTHER_PROJECT_ID))
                .thenReturn(Optional.empty());

        assertThat(service.getTrend(OTHER_PROJECT_ID, APPLICATION_ID, INSTANCE_ID, "7d", "168")).isEmpty();
        verify(applicationRepository).findByIdAndProjectId(APPLICATION_ID, OTHER_PROJECT_ID);
        verifyNoInteractions(dashboardSnapshotRepository);

        when(applicationInstanceRepository.findByIdAndApplicationId(INSTANCE_ID, APPLICATION_ID))
                .thenReturn(Optional.empty());

        assertThat(service.getTrend(PROJECT_ID, APPLICATION_ID, INSTANCE_ID, "7d", "168")).isEmpty();
        verify(applicationInstanceRepository).findByIdAndApplicationId(INSTANCE_ID, APPLICATION_ID);
        verify(dashboardSnapshotRepository, never()).findTrendRowsNewestFirst(
                PROJECT_ID,
                APPLICATION_ID,
                OffsetDateTime.parse("2026-05-19T08:10:35Z"),
                OffsetDateTime.parse("2026-05-26T08:10:35Z"),
                336);
    }

    @Test
    void projectsSnapshotRowsAndReturnsCurrentWindowAscendingWithGeneratedAtTieBreaker() {
        UUID earlierSnapshotId = UUID.fromString("00000000-0000-0000-0000-000000005731");
        UUID tiedLowerSnapshotId = UUID.fromString("00000000-0000-0000-0000-000000005732");
        UUID tiedHigherSnapshotId = UUID.fromString("00000000-0000-0000-0000-000000005733");
        when(dashboardSnapshotRepository.findTrendRowsNewestFirst(
                PROJECT_ID,
                APPLICATION_ID,
                OffsetDateTime.parse("2026-05-19T08:10:35Z"),
                OffsetDateTime.parse("2026-05-26T08:10:35Z"),
                168))
                .thenReturn(List.of(
                        row(tiedHigherSnapshotId, "2026-05-26T08:40:00Z", "2026-05-26T08:00:00Z", "opaque_future_reason"),
                        row(tiedLowerSnapshotId, "2026-05-26T08:30:00Z", "2026-05-26T08:00:00Z", (String) null),
                        row(earlierSnapshotId, "2026-05-26T09:00:00Z", "2026-05-26T07:30:00Z", "  hourly_scheduled  "),
                        row(UUID.fromString("00000000-0000-0000-0000-000000005734"),
                                "2026-05-26T06:00:00Z",
                                "missing_item",
                                OTHER_PROJECT_ID)));

        InstanceSnapshotTrendReadModel trend = service.getTrend(
                        PROJECT_ID,
                        APPLICATION_ID,
                        INSTANCE_ID,
                        "7d",
                        "168")
                .orElseThrow();

        assertThat(trend.points())
                .extracting(InstanceSnapshotTrendReadModel.Point::snapshotId)
                .containsExactly(earlierSnapshotId, tiedLowerSnapshotId, tiedHigherSnapshotId);
        assertThat(trend.points())
                .extracting(InstanceSnapshotTrendReadModel.Point::currentWindowEndUtc)
                .containsExactly(
                        offset("2026-05-26T07:30:00Z"),
                        offset("2026-05-26T08:00:00Z"),
                        offset("2026-05-26T08:00:00Z"));
        assertThat(trend.points())
                .extracting(InstanceSnapshotTrendReadModel.Point::captureReason)
                .containsExactly("  hourly_scheduled  ", null, "opaque_future_reason");
        assertThat(trend.points().get(0).storedApplicationStateCode()).isEqualTo("active");
        assertThat(trend.points().get(0).metricData().statusSource()).isEqualTo("accepted_bucket");
        assertThat(trend.points().get(0).starterConnection().statusSource()).isEqualTo("starter_heartbeat");
    }

    @Test
    void constructorDoesNotAcceptForbiddenCurrentRecalculationDependencies() {
        List<String> constructorParameterTypeNames = Arrays.stream(InstanceSnapshotTrendService.class
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

    private void stubCatalogPathConsistency() {
        when(applicationRepository.findByIdAndProjectId(APPLICATION_ID, PROJECT_ID))
                .thenReturn(Optional.of(application()));
        when(applicationInstanceRepository.findByIdAndApplicationId(INSTANCE_ID, APPLICATION_ID))
                .thenReturn(Optional.of(instance()));
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

    private static ApplicationInstanceEntity instance() {
        return new ApplicationInstanceEntity(
                INSTANCE_ID,
                APPLICATION_ID,
                "pod-a",
                offset("2026-05-26T05:00:05Z"),
                offset("2026-05-26T08:00:05Z"),
                offset("2026-05-26T05:00:05Z"),
                offset("2026-05-26T08:00:05Z"));
    }

    private static DashboardSnapshotTrendRow row(UUID snapshotId, String generatedAt, String captureReason) {
        return row(snapshotId, generatedAt, generatedAt, captureReason, INSTANCE_ID);
    }

    private static DashboardSnapshotTrendRow row(
            UUID snapshotId,
            String generatedAt,
            String currentWindowEndUtc,
            String captureReason) {
        return row(snapshotId, generatedAt, currentWindowEndUtc, captureReason, INSTANCE_ID);
    }

    private static DashboardSnapshotTrendRow row(
            UUID snapshotId,
            String generatedAt,
            String captureReason,
            UUID storedInstanceId) {
        return row(snapshotId, generatedAt, generatedAt, captureReason, storedInstanceId);
    }

    private static DashboardSnapshotTrendRow row(
            UUID snapshotId,
            String generatedAt,
            String currentWindowEndUtc,
            String captureReason,
            UUID storedInstanceId) {
        return new DashboardSnapshotTrendRow(
                snapshotId,
                offset(generatedAt),
                offset(currentWindowEndUtc),
                "active",
                captureReason,
                """
                {
                  "instanceSummary": {
                    "schemaVersion": "1.0",
                    "items": [
                      {
                        "instanceId": "%s",
                        "instanceName": "pod-a",
                        "observationStatus": "observed",
                        "metricData": {
                          "statusSource": "accepted_bucket",
                          "lastAcceptedBucketAt": "2026-05-26T07:59:30Z",
                          "freshnessLabel": "current"
                        },
                        "starterConnection": {
                          "statusSource": "starter_heartbeat",
                          "lastHeartbeatAt": "2026-05-26T07:59:45Z",
                          "lastHeartbeatStatus": "received",
                          "connectionMeaning": "starter_connected",
                          "stateImpact": "none"
                        },
                        "applicationTriageContribution": {
                          "status": "available",
                          "contributed": false,
                          "relatedRuleIds": [],
                          "reason": "no_action_needed"
                        },
                        "endpointEvidenceRefs": []
                      }
                    ]
                  }
                }
                """.formatted(storedInstanceId));
    }

    private static OffsetDateTime offset(String instant) {
        return OffsetDateTime.parse(instant);
    }
}
