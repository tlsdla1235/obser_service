package com.observation.portal.domain.catalog.service;

import com.observation.portal.common.time.AcceptedBucketFreshnessEvaluator;
import com.observation.portal.domain.account.service.AccountProjectMembershipService;
import com.observation.portal.domain.bucket.repository.MetricBucketRepository;
import com.observation.portal.domain.catalog.entity.ApplicationEntity;
import com.observation.portal.domain.catalog.entity.ProjectEntity;
import com.observation.portal.domain.catalog.model.ProjectApplicationNavigationReadModel;
import com.observation.portal.domain.catalog.model.ProjectNavigationReadModel;
import com.observation.portal.domain.catalog.repository.ApplicationRepository;
import com.observation.portal.domain.catalog.repository.ProjectRepository;
import com.observation.portal.domain.ingest.model.StarterHeartbeatTelemetryRecord;
import com.observation.portal.domain.ingest.repository.StarterHeartbeatTelemetryRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Clock;
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

class ProjectApplicationNavigationServiceTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000005101");
    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000006101");
    private static final UUID ORDERS_ID = UUID.fromString("00000000-0000-0000-0000-000000005111");
    private static final UUID BILLING_ID = UUID.fromString("00000000-0000-0000-0000-000000005112");
    private static final OffsetDateTime QUERY_AT = OffsetDateTime.parse("2026-05-25T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(QUERY_AT.toInstant(), ZoneOffset.UTC);

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final AccountProjectMembershipService membershipService = mock(AccountProjectMembershipService.class);
    private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
    private final MetricBucketRepository metricBucketRepository = mock(MetricBucketRepository.class);
    private final StarterHeartbeatTelemetryRepository heartbeatRepository =
            mock(StarterHeartbeatTelemetryRepository.class);
    private final ProjectApplicationNavigationService service = new ProjectApplicationNavigationService(
            projectRepository,
            membershipService,
            applicationRepository,
            metricBucketRepository,
            heartbeatRepository,
            new AcceptedBucketFreshnessEvaluator(CLOCK),
            CLOCK);

    @Test
    void projectListSummarizesIdentityCountAndLightIssueCandidatesWithoutTriage() {
        ApplicationEntity orders = application(ORDERS_ID, "orders-api", "prod");
        ApplicationEntity billing = application(BILLING_ID, "billing-api", "prod");
        when(membershipService.listActiveProjects(ACCOUNT_ID))
                .thenReturn(List.of(project().toCandidate()));
        when(applicationRepository.findByProjectIdOrderByNameAscEnvironmentAsc(PROJECT_ID))
                .thenReturn(List.of(billing, orders));
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationId(BILLING_ID))
                .thenReturn(Optional.of(QUERY_AT.minusSeconds(45)));
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationId(ORDERS_ID))
                .thenReturn(Optional.empty());
        when(heartbeatRepository.findLatestByApplicationScope(PROJECT_ID, "billing-api", "prod"))
                .thenReturn(Optional.of(heartbeat("billing-api", "prod", "pod-a", QUERY_AT.minusSeconds(30))));
        when(heartbeatRepository.findLatestByApplicationScope(PROJECT_ID, "orders-api", "prod"))
                .thenReturn(Optional.empty());

        ProjectNavigationReadModel model = service.listProjects(ACCOUNT_ID);

        assertThat(model.generatedAt()).isEqualTo(QUERY_AT);
        assertThat(model.projects()).singleElement().satisfies(project -> {
            assertThat(project.projectId()).isEqualTo(PROJECT_ID);
            assertThat(project.name()).isEqualTo("local-demo");
            assertThat(project.applicationCount()).isEqualTo(2);
            assertThat(project.setupConnectionIssueCount()).isEqualTo(1);
            assertThat(project.recentConcern()).isNull();
            assertThat(project.links().applications()).isEqualTo("/api/projects/" + PROJECT_ID + "/applications");
        });
        verify(heartbeatRepository, never()).findLatestByProjectId(PROJECT_ID);
    }

    @Test
    void projectListReturnsEmptyWhenAccountHasNoActiveMembership() {
        when(membershipService.listActiveProjects(ACCOUNT_ID)).thenReturn(List.of());

        ProjectNavigationReadModel model = service.listProjects(ACCOUNT_ID);

        assertThat(model.generatedAt()).isEqualTo(QUERY_AT);
        assertThat(model.projects()).isEmpty();
        verifyNoInteractions(applicationRepository, metricBucketRepository, heartbeatRepository);
    }

    @Test
    void applicationListKeepsMetricFreshnessAndStarterConnectionSeparate() {
        ApplicationEntity orders = application(ORDERS_ID, "orders-api", "prod");
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project()));
        when(applicationRepository.findByProjectIdOrderByNameAscEnvironmentAsc(PROJECT_ID))
                .thenReturn(List.of(orders));
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationId(ORDERS_ID))
                .thenReturn(Optional.empty());
        when(heartbeatRepository.findLatestByApplicationScope(PROJECT_ID, "orders-api", "prod"))
                .thenReturn(Optional.of(heartbeat("orders-api", "prod", "pod-a", QUERY_AT.minusSeconds(20))));

        Optional<ProjectApplicationNavigationReadModel> maybeModel = service.listApplications(PROJECT_ID);

        assertThat(maybeModel).hasValueSatisfying(model -> {
            assertThat(model.generatedAt()).isEqualTo(QUERY_AT);
            assertThat(model.project().projectId()).isEqualTo(PROJECT_ID);
            assertThat(model.project().name()).isEqualTo("local-demo");
            assertThat(model.applications()).singleElement().satisfies(application -> {
                assertThat(application.applicationId()).isEqualTo(ORDERS_ID);
                assertThat(application.name()).isEqualTo("orders-api");
                assertThat(application.environment()).isEqualTo("prod");
                assertThat(application.metricData().statusSource()).isEqualTo("accepted_bucket");
                assertThat(application.metricData().lastAcceptedBucketAt()).isNull();
                assertThat(application.metricData().freshnessLabel()).isEqualTo("waiting_first_data");
                assertThat(application.starterConnection().statusSource()).isEqualTo("starter_heartbeat");
                assertThat(application.starterConnection().lastHeartbeatAt()).isEqualTo(QUERY_AT.minusSeconds(20));
                assertThat(application.starterConnection().heartbeatStatus()).isEqualTo("received");
                assertThat(application.starterConnection().freshnessLabel()).isEqualTo("recent");
                assertThat(application.starterConnection().connectionMeaning()).isEqualTo("starter_connected");
                assertThat(application.starterConnection().stateImpact()).isEqualTo("none");
                assertThat(application.lifecycleBadge().code()).isEqualTo("unknown");
                assertThat(application.topConcern()).isNull();
                assertThat(application.links().dashboard())
                        .isEqualTo("/api/projects/" + PROJECT_ID + "/applications/" + ORDERS_ID + "/dashboard");
            });
        });
    }

    @Test
    void heartbeatRecencyIsScopedToProjectApplicationAndEnvironment() {
        ApplicationEntity orders = application(ORDERS_ID, "orders-api", "prod");
        ApplicationEntity billing = application(BILLING_ID, "billing-api", "prod");
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project()));
        when(applicationRepository.findByProjectIdOrderByNameAscEnvironmentAsc(PROJECT_ID))
                .thenReturn(List.of(billing, orders));
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationId(BILLING_ID))
                .thenReturn(Optional.of(QUERY_AT.minusSeconds(30)));
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationId(ORDERS_ID))
                .thenReturn(Optional.of(QUERY_AT.minusSeconds(30)));
        when(heartbeatRepository.findLatestByApplicationScope(PROJECT_ID, "billing-api", "prod"))
                .thenReturn(Optional.of(heartbeat("billing-api", "prod", "pod-b", QUERY_AT.minusSeconds(91))));
        when(heartbeatRepository.findLatestByApplicationScope(PROJECT_ID, "orders-api", "prod"))
                .thenReturn(Optional.empty());

        ProjectApplicationNavigationReadModel model = service.listApplications(PROJECT_ID).orElseThrow();

        assertThat(model.applications()).extracting(application -> application.starterConnection().freshnessLabel())
                .containsExactly("stale", "unknown");
        assertThat(model.applications()).extracting(application -> application.starterConnection().heartbeatStatus())
                .containsExactly("received", "missing");
        assertThat(model.applications()).extracting(application -> application.starterConnection().connectionMeaning())
                .containsExactly("starter_telemetry_stale", "starter_telemetry_missing");
        verify(heartbeatRepository).findLatestByApplicationScope(PROJECT_ID, "billing-api", "prod");
        verify(heartbeatRepository).findLatestByApplicationScope(PROJECT_ID, "orders-api", "prod");
        verify(heartbeatRepository, never()).findLatestByProjectId(PROJECT_ID);
    }

    @Test
    void staleMetricDataAndRecentHeartbeatDoNotDeclareHostApplicationDown() {
        ApplicationEntity orders = application(ORDERS_ID, "orders-api", "prod");
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project()));
        when(applicationRepository.findByProjectIdOrderByNameAscEnvironmentAsc(PROJECT_ID))
                .thenReturn(List.of(orders));
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationId(ORDERS_ID))
                .thenReturn(Optional.of(QUERY_AT.minusSeconds(181)));
        when(heartbeatRepository.findLatestByApplicationScope(PROJECT_ID, "orders-api", "prod"))
                .thenReturn(Optional.of(heartbeat("orders-api", "prod", "pod-a", QUERY_AT.minusSeconds(10))));

        ProjectApplicationNavigationReadModel.ApplicationItem application =
                service.listApplications(PROJECT_ID).orElseThrow().applications().get(0);

        assertThat(application.metricData().freshnessLabel()).isEqualTo("down_candidate");
        assertThat(application.starterConnection().connectionMeaning()).isEqualTo("starter_connected");
        assertThat(application.starterConnection().stateImpact()).isEqualTo("none");
        assertThat(application.lifecycleBadge().code()).isEqualTo("unknown");
        assertThat(application.toString()).doesNotContainIgnoringCase("host down");
    }

    @Test
    void readModelDoesNotExposeDashboardHealthOrEndpointPercentileFields() {
        List<String> forbiddenNames = List.of(
                "health",
                "hostHealth",
                "applicationHealth",
                "endpointPriority",
                "p95",
                "p99");

        assertThat(recordComponentNames(ProjectApplicationNavigationReadModel.ApplicationItem.class))
                .doesNotContainAnyElementsOf(forbiddenNames);
        assertThat(recordComponentNames(ProjectApplicationNavigationReadModel.MetricDataSummary.class))
                .doesNotContainAnyElementsOf(forbiddenNames);
        assertThat(recordComponentNames(ProjectApplicationNavigationReadModel.StarterConnectionSummary.class))
                .doesNotContainAnyElementsOf(forbiddenNames);
    }

    private static List<String> recordComponentNames(Class<? extends Record> recordType) {
        return Arrays.stream(recordType.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }

    private static ProjectEntity project() {
        return new ProjectEntity(
                PROJECT_ID,
                "local-demo",
                "pk_local_demo",
                "$2a$10$navigationhashnavigationhashnavigationhashnavigationha",
                "active",
                QUERY_AT,
                QUERY_AT);
    }

    private static ApplicationEntity application(UUID applicationId, String name, String environment) {
        return new ApplicationEntity(
                applicationId,
                PROJECT_ID,
                name,
                environment,
                "active",
                QUERY_AT.minusMinutes(5),
                QUERY_AT.minusSeconds(30),
                QUERY_AT.minusMinutes(5),
                QUERY_AT.minusSeconds(30));
    }

    private static StarterHeartbeatTelemetryRecord heartbeat(
            String applicationName,
            String environment,
            String instanceName,
            OffsetDateTime lastReceivedAtUtc) {
        return new StarterHeartbeatTelemetryRecord(
                UUID.randomUUID(),
                PROJECT_ID,
                applicationName,
                environment,
                instanceName,
                "0.1.0",
                lastReceivedAtUtc.minusSeconds(1),
                lastReceivedAtUtc,
                1L,
                30,
                "valid",
                "received",
                lastReceivedAtUtc,
                lastReceivedAtUtc);
    }
}
