package com.observation.portal.domain.catalog.controller;

import com.observation.portal.domain.account.controller.AccountAuthController;
import com.observation.portal.domain.account.controller.AccountProjectMembershipResourceApiInterceptor;
import com.observation.portal.domain.account.controller.BearerResourceApiInterceptor;
import com.observation.portal.domain.account.model.GithubAuthorizationStart;
import com.observation.portal.domain.account.model.GithubOAuthCallbackCommand;
import com.observation.portal.domain.account.service.AccountAuthException;
import com.observation.portal.domain.account.service.AccountAuthService;
import com.observation.portal.domain.account.service.AccountProjectMembershipService;
import com.observation.portal.domain.account.service.ServiceTokenIssuer;
import com.observation.portal.domain.dashboard.controller.DashboardController;
import com.observation.portal.domain.dashboard.model.ApplicationDashboardReadModel;
import com.observation.portal.domain.dashboard.service.DashboardReadModelService;
import com.observation.portal.domain.instance.controller.InstanceSnapshotTrendController;
import com.observation.portal.domain.instance.controller.InstanceEvidenceController;
import com.observation.portal.domain.instance.model.InstanceEvidenceReadModel;
import com.observation.portal.domain.instance.service.InstanceEvidenceReadModelService;
import com.observation.portal.domain.instance.service.InstanceSnapshotTrendService;
import com.observation.portal.domain.instance.service.InvalidSnapshotTrendQueryException;
import com.observation.portal.domain.catalog.model.ProjectNavigationReadModel;
import com.observation.portal.domain.catalog.model.ProjectApplicationNavigationReadModel;
import com.observation.portal.domain.catalog.service.ProjectApplicationNavigationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectNavigationResourceAuthorizationTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000006101");
    private static final UUID UNLINKED_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000006102");
    private static final UUID DEMO_PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000006801");
    private static final UUID DEMO_APPLICATION_ID = UUID.fromString("00000000-0000-0000-0000-000000006811");
    private static final UUID DEMO_INSTANCE_ID = UUID.fromString("00000000-0000-0000-0000-000000006821");
    private static final Instant NOW = Instant.parse("2026-05-28T10:00:00Z");

    private final MutableClock clock = new MutableClock(NOW, ZoneOffset.UTC);
    private final ServiceTokenIssuer tokenIssuer = new ServiceTokenIssuer(
            clock,
            "resource-api-boundary-test-signing-key",
            Duration.ofMinutes(15),
            Duration.ofDays(30));
    private final ProjectApplicationNavigationService navigationService = mock(ProjectApplicationNavigationService.class);
    private final DashboardReadModelService dashboardService = mock(DashboardReadModelService.class);
    private final InstanceEvidenceReadModelService evidenceService = mock(InstanceEvidenceReadModelService.class);
    private final InstanceSnapshotTrendService trendService = mock(InstanceSnapshotTrendService.class);
    private final AccountProjectMembershipService membershipService = mock(AccountProjectMembershipService.class);
    private final AccountAuthService authService = mock(AccountAuthService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(
                    new ProjectNavigationController(navigationService),
                    new DashboardController(dashboardService),
                    new InstanceEvidenceController(evidenceService),
                    new InstanceSnapshotTrendController(trendService),
                    new AccountAuthController(authService))
            .addMappedInterceptors(
                    new String[] {"/api/projects", "/api/projects/**"},
                    new BearerResourceApiInterceptor(tokenIssuer))
            .addMappedInterceptors(
                    new String[] {"/api/projects/*/applications", "/api/projects/*/applications/**"},
                    new AccountProjectMembershipResourceApiInterceptor(membershipService))
            .build();

    @Test
    void getProjectsWithoutAuthorizationHeaderReturnsUnauthorizedWithoutCookieSession() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().string(containsString("Bearer access token이 필요합니다.")));

        verifyNoInteractions(navigationService, membershipService);
    }

    @Test
    void validServiceAccessTokenAllowsProjectResourceApi() throws Exception {
        ProjectNavigationReadModel readModel = new ProjectNavigationReadModel(
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC),
                List.of());
        when(navigationService.listProjects(ACCOUNT_ID)).thenReturn(readModel);

        mockMvc.perform(get("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validAccessToken()))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.generatedAt").value("2026-05-28T10:00:00Z"))
                .andExpect(jsonPath("$.projects").isArray());

        verify(navigationService).listProjects(ACCOUNT_ID);
    }

    @Test
    void activeMembershipDemoAccountFollowsProjectApplicationDashboardAndEvidencePath() throws Exception {
        String accessToken = accessTokenFor(ACCOUNT_ID);
        when(navigationService.listProjects(ACCOUNT_ID)).thenReturn(demoProjectListReadModel());
        when(membershipService.hasActiveMembership(ACCOUNT_ID, DEMO_PROJECT_ID)).thenReturn(true);
        when(navigationService.listApplications(DEMO_PROJECT_ID)).thenReturn(Optional.of(demoApplicationListReadModel()));
        when(dashboardService.getDashboard(DEMO_PROJECT_ID, DEMO_APPLICATION_ID))
                .thenReturn(Optional.of(demoDashboardReadModel()));
        when(evidenceService.getEvidence(DEMO_PROJECT_ID, DEMO_APPLICATION_ID, DEMO_INSTANCE_ID))
                .thenReturn(Optional.of(demoEvidenceReadModel()));

        mockMvc.perform(get("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projects[0].projectId").value(DEMO_PROJECT_ID.toString()))
                .andExpect(jsonPath("$.projects[0].links.applications").value(demoApplicationsPath()));

        mockMvc.perform(get(demoApplicationsPath())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applications[0].applicationId").value(DEMO_APPLICATION_ID.toString()))
                .andExpect(jsonPath("$.applications[0].metricData.statusSource").value("accepted_bucket"))
                .andExpect(jsonPath("$.applications[0].starterConnection.statusSource").value("starter_heartbeat"))
                .andExpect(jsonPath("$.applications[0].starterConnection.stateImpact").value("none"))
                .andExpect(jsonPath("$.applications[0].links.dashboard").value(demoDashboardPath()));

        mockMvc.perform(get(demoDashboardPath())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state.code").value("active"))
                .andExpect(jsonPath("$.zeroInsight.reasonCode").value("no_action_needed"))
                .andExpect(jsonPath("$.triageCards.length()").value(0))
                .andExpect(jsonPath("$.instances[0].instanceId").value(DEMO_INSTANCE_ID.toString()))
                .andExpect(jsonPath("$.instances[0].links.evidence").value(demoEvidencePath()));

        mockMvc.perform(get(demoEvidencePath())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metricData.statusSource").value("accepted_bucket"))
                .andExpect(jsonPath("$.metricData.freshnessLabel").value("current"))
                .andExpect(jsonPath("$.starterConnection.statusSource").value("starter_heartbeat"))
                .andExpect(jsonPath("$.starterConnection.stateImpact").value("none"));

        verify(navigationService).listProjects(ACCOUNT_ID);
        verify(navigationService).listApplications(DEMO_PROJECT_ID);
        verify(dashboardService).getDashboard(DEMO_PROJECT_ID, DEMO_APPLICATION_ID);
        verify(evidenceService).getEvidence(DEMO_PROJECT_ID, DEMO_APPLICATION_ID, DEMO_INSTANCE_ID);
        verify(membershipService, times(3)).hasActiveMembership(ACCOUNT_ID, DEMO_PROJECT_ID);
    }

    @Test
    void accountWithoutDemoMembershipSeesEmptyProjectListAndProjectScopedApisFailClosed() throws Exception {
        String accessToken = accessTokenFor(UNLINKED_ACCOUNT_ID);
        when(navigationService.listProjects(UNLINKED_ACCOUNT_ID))
                .thenReturn(new ProjectNavigationReadModel(
                        OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC),
                        List.of()));
        when(membershipService.hasActiveMembership(UNLINKED_ACCOUNT_ID, DEMO_PROJECT_ID)).thenReturn(false);

        mockMvc.perform(get("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projects.length()").value(0));

        mockMvc.perform(get(demoApplicationsPath())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
        mockMvc.perform(get(demoDashboardPath())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
        mockMvc.perform(get(demoEvidencePath())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));

        verify(navigationService).listProjects(UNLINKED_ACCOUNT_ID);
        verify(navigationService, never()).listApplications(DEMO_PROJECT_ID);
        verifyNoInteractions(dashboardService, evidenceService);
        verify(membershipService, times(3)).hasActiveMembership(UNLINKED_ACCOUNT_ID, DEMO_PROJECT_ID);
    }

    @Test
    void projectScopedApiWithoutActiveMembershipReturnsNotFoundWithoutCallingResourceService() throws Exception {
        UUID projectId = UUID.fromString("00000000-0000-0000-0000-000000006201");
        when(membershipService.hasActiveMembership(ACCOUNT_ID, projectId)).thenReturn(false);

        mockMvc.perform(get("/api/projects/{projectId}/applications", projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validAccessToken()))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));

        verify(membershipService).hasActiveMembership(ACCOUNT_ID, projectId);
        verifyNoInteractions(navigationService);
    }

    @Test
    void projectScopedApiAfterMembershipUsesExistingCatalogNotFoundMapping() throws Exception {
        UUID projectId = UUID.fromString("00000000-0000-0000-0000-000000006202");
        when(membershipService.hasActiveMembership(ACCOUNT_ID, projectId)).thenReturn(true);
        when(navigationService.listApplications(projectId)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/projects/{projectId}/applications", projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validAccessToken()))
                .andExpect(status().isNotFound());

        verify(membershipService).hasActiveMembership(ACCOUNT_ID, projectId);
        verify(navigationService).listApplications(projectId);
    }

    @Test
    void nestedResourceApiMembershipMismatchFailsClosedBeforeDashboardLookup() throws Exception {
        UUID projectId = UUID.fromString("00000000-0000-0000-0000-000000006203");
        UUID applicationId = UUID.fromString("00000000-0000-0000-0000-000000006204");
        when(membershipService.hasActiveMembership(ACCOUNT_ID, projectId)).thenReturn(false);

        mockMvc.perform(get("/api/projects/{projectId}/applications/{applicationId}/dashboard", projectId, applicationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validAccessToken()))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));

        verify(membershipService).hasActiveMembership(ACCOUNT_ID, projectId);
        verifyNoInteractions(dashboardService);
    }

    @Test
    void nestedEvidenceResourceApiMembershipMismatchFailsClosedBeforeEvidenceLookup() throws Exception {
        UUID projectId = UUID.fromString("00000000-0000-0000-0000-000000006208");
        UUID applicationId = UUID.fromString("00000000-0000-0000-0000-000000006209");
        UUID instanceId = UUID.fromString("00000000-0000-0000-0000-000000006210");
        when(membershipService.hasActiveMembership(ACCOUNT_ID, projectId)).thenReturn(false);

        mockMvc.perform(get(
                        "/api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/evidence",
                        projectId,
                        applicationId,
                        instanceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validAccessToken()))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));

        verify(membershipService).hasActiveMembership(ACCOUNT_ID, projectId);
        verifyNoInteractions(evidenceService);
    }

    @Test
    void membershipMismatchTakesPriorityOverNestedQueryValidation() throws Exception {
        UUID projectId = UUID.fromString("00000000-0000-0000-0000-000000006205");
        UUID applicationId = UUID.fromString("00000000-0000-0000-0000-000000006206");
        UUID instanceId = UUID.fromString("00000000-0000-0000-0000-000000006207");
        when(membershipService.hasActiveMembership(ACCOUNT_ID, projectId)).thenReturn(false);
        when(trendService.getTrend(projectId, applicationId, instanceId, "24h", "bad"))
                .thenThrow(new InvalidSnapshotTrendQueryException("limit must be positive"));

        mockMvc.perform(get(
                        "/api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/snapshot-trend",
                        projectId,
                        applicationId,
                        instanceId)
                        .param("since", "24h")
                        .param("limit", "bad")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validAccessToken()))
                .andExpect(status().isNotFound());

        verify(membershipService).hasActiveMembership(ACCOUNT_ID, projectId);
        verifyNoInteractions(trendService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Bearer not-a-service-jwt",
            "Bearer gho_provider_token_value",
            "Basic abc.def.ghi",
            "Bearer "
    })
    void malformedBearerTokenReturnsUnauthorized(String authorization) throws Exception {
        mockMvc.perform(get("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Set-Cookie"));

        verifyNoInteractions(navigationService, membershipService);
    }

    @Test
    void invalidSignatureBearerTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validAccessToken() + "x"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Set-Cookie"));

        verifyNoInteractions(navigationService, membershipService);
    }

    @Test
    void expiredBearerTokenReturnsUnauthorized() throws Exception {
        String accessToken = validAccessToken();
        clock.set(Instant.parse("2026-05-28T10:16:00Z"));

        mockMvc.perform(get("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Set-Cookie"));

        verifyNoInteractions(navigationService, membershipService);
    }

    @Test
    void authEndpointsRemainPublicWithoutBearerHeader() throws Exception {
        when(authService.startGithubAuthorization())
                .thenReturn(new GithubAuthorizationStart(
                        "github",
                        "https://github.com/login/oauth/authorize?client_id=public-client&state=signed-state",
                        true));
        when(authService.completeGithubCallback(new GithubOAuthCallbackCommand(
                "oauth-code",
                "browser-state",
                null)))
                .thenThrow(new AccountAuthException(
                        "github_oauth_failed",
                        "GitHub OAuth를 완료할 수 없습니다."));

        mockMvc.perform(get("/api/auth/github/authorize"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));
        mockMvc.perform(get("/api/auth/github/callback")
                        .param("code", "oauth-code")
                        .param("state", "browser-state"))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist("Set-Cookie"));
        mockMvc.perform(post("/api/auth/token/refresh")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist("Set-Cookie"));
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    private String validAccessToken() {
        return accessTokenFor(ACCOUNT_ID);
    }

    private String accessTokenFor(UUID accountId) {
        return tokenIssuer.issue(accountId, tokenIssuer.generateRefreshToken()).accessToken();
    }

    private static ProjectNavigationReadModel demoProjectListReadModel() {
        return new ProjectNavigationReadModel(
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC),
                List.of(new ProjectNavigationReadModel.ProjectItem(
                        DEMO_PROJECT_ID,
                        "demo-green-path",
                        1,
                        0,
                        null,
                        new ProjectNavigationReadModel.ProjectLinks(demoApplicationsPath()))));
    }

    private static ProjectApplicationNavigationReadModel demoApplicationListReadModel() {
        return new ProjectApplicationNavigationReadModel(
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC),
                new ProjectApplicationNavigationReadModel.ProjectSummary(DEMO_PROJECT_ID, "demo-green-path"),
                List.of(new ProjectApplicationNavigationReadModel.ApplicationItem(
                        DEMO_APPLICATION_ID,
                        "orders-api",
                        "prod",
                        new ProjectApplicationNavigationReadModel.MetricDataSummary(
                                "accepted_bucket",
                                demoBucketEnd(),
                                "current"),
                        new ProjectApplicationNavigationReadModel.StarterConnectionSummary(
                                "starter_heartbeat",
                                OffsetDateTime.ofInstant(NOW.minusSeconds(15), ZoneOffset.UTC),
                                "received",
                                "recent",
                                "starter_connected",
                                "none"),
                        new ProjectApplicationNavigationReadModel.LifecycleBadge(
                                "server_light_navigation_read_model",
                                "unknown",
                                "Metric data unknown"),
                        null,
                        new ProjectApplicationNavigationReadModel.ApplicationLinks(demoDashboardPath()))));
    }

    /**
     * Story 6.8 demo green path에서 server read model이 제공하는 active/no-triage baseline fixture다.
     */
    private static ApplicationDashboardReadModel demoDashboardReadModel() {
        OffsetDateTime generatedAt = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        OffsetDateTime bucketEnd = demoBucketEnd();
        OffsetDateTime bucketStart = bucketEnd.minusSeconds(30);
        return new ApplicationDashboardReadModel(
                generatedAt,
                new ApplicationDashboardReadModel.Application(
                        DEMO_PROJECT_ID,
                        DEMO_APPLICATION_ID,
                        "orders-api",
                        "prod",
                        bucketEnd,
                        null,
                        new ApplicationDashboardReadModel.SourceWindow(
                                new ApplicationDashboardReadModel.Window(bucketEnd.minusMinutes(15), bucketEnd),
                                new ApplicationDashboardReadModel.Window(
                                        bucketEnd.minusMinutes(30),
                                        bucketEnd.minusMinutes(15))),
                        new ApplicationDashboardReadModel.Freshness(
                                bucketEnd,
                                bucketEnd.plusSeconds(90),
                                bucketEnd.plusSeconds(180))),
                new ApplicationDashboardReadModel.State(
                        "active",
                        "Metric data active",
                        "Freshness와 sample이 충분합니다.",
                        "현재 우선 노출할 triage는 없습니다.",
                        "application"),
                new ApplicationDashboardReadModel.StarterConnection(
                        "starter_heartbeat",
                        OffsetDateTime.ofInstant(NOW.minusSeconds(15), ZoneOffset.UTC),
                        "received",
                        "starter_connected",
                        "none"),
                new ApplicationDashboardReadModel.ZeroInsight(
                        "no_action_needed",
                        "현재 우선 노출할 triage는 없습니다.",
                        "새 accepted bucket을 계속 관찰합니다."),
                new ApplicationDashboardReadModel.Recovery(false, null, null, null),
                new ApplicationDashboardReadModel.Metrics(60L, 0L, BigDecimal.ZERO),
                ApplicationDashboardReadModel.SourceScopedPercentiles.available(List.of(
                        new ApplicationDashboardReadModel.PercentileItem(
                                "starter_local",
                                "orders-api",
                                "prod",
                                "pod-a",
                                bucketStart,
                                bucketEnd,
                                60L,
                                120L,
                                240L))),
                ApplicationDashboardReadModel.HistogramDistribution.empty(),
                List.of(),
                List.of(),
                List.of(new ApplicationDashboardReadModel.InstanceEntry(
                        DEMO_INSTANCE_ID,
                        "pod-a",
                        bucketEnd,
                        new ApplicationDashboardReadModel.InstanceEntryLinks(demoEvidencePath()))),
                null);
    }

    /**
     * Instance Evidence가 accepted bucket axis와 starter heartbeat axis를 합치지 않는지 확인하는 bounded fixture다.
     */
    private static InstanceEvidenceReadModel demoEvidenceReadModel() {
        OffsetDateTime generatedAt = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        OffsetDateTime bucketEnd = demoBucketEnd();
        return new InstanceEvidenceReadModel(
                generatedAt,
                new InstanceEvidenceReadModel.Application(
                        DEMO_PROJECT_ID,
                        DEMO_APPLICATION_ID,
                        "orders-api",
                        "prod",
                        new InstanceEvidenceReadModel.ApplicationLinks(demoDashboardPath())),
                new InstanceEvidenceReadModel.Instance(
                        DEMO_INSTANCE_ID,
                        "pod-a",
                        bucketEnd.minusMinutes(10),
                        bucketEnd),
                new InstanceEvidenceReadModel.MetricData(
                        "accepted_bucket",
                        new InstanceEvidenceReadModel.MetricWindow(
                                "recent_30_minutes",
                                bucketEnd.minusMinutes(30),
                                bucketEnd,
                                30),
                        bucketEnd,
                        "current",
                        "ready",
                        60L,
                        0L,
                        BigDecimal.ZERO,
                        "sample_guard_passed"),
                new InstanceEvidenceReadModel.StarterConnection(
                        "starter_heartbeat",
                        OffsetDateTime.ofInstant(NOW.minusSeconds(15), ZoneOffset.UTC),
                        "received",
                        "recent",
                        "starter_connected",
                        "none"),
                InstanceEvidenceReadModel.StarterPercentiles.missing(),
                InstanceEvidenceReadModel.HistogramDistribution.missing(),
                InstanceEvidenceReadModel.ResourceHints.missing(),
                InstanceEvidenceReadModel.ApplicationTriageContribution.missing(),
                InstanceEvidenceReadModel.EndpointEvidence.missing(),
                new InstanceEvidenceReadModel.Links(demoEvidencePath(), demoDashboardPath(), demoSnapshotTrendPath()));
    }

    private static OffsetDateTime demoBucketEnd() {
        return OffsetDateTime.ofInstant(NOW.minusSeconds(30), ZoneOffset.UTC);
    }

    private static String demoApplicationsPath() {
        return "/api/projects/%s/applications".formatted(DEMO_PROJECT_ID);
    }

    private static String demoDashboardPath() {
        return "/api/projects/%s/applications/%s/dashboard".formatted(DEMO_PROJECT_ID, DEMO_APPLICATION_ID);
    }

    private static String demoEvidencePath() {
        return "/api/projects/%s/applications/%s/instances/%s/evidence"
                .formatted(DEMO_PROJECT_ID, DEMO_APPLICATION_ID, DEMO_INSTANCE_ID);
    }

    private static String demoSnapshotTrendPath() {
        return "/api/projects/%s/applications/%s/instances/%s/snapshot-trend"
                .formatted(DEMO_PROJECT_ID, DEMO_APPLICATION_ID, DEMO_INSTANCE_ID);
    }

    private static final class MutableClock extends Clock {

        private final AtomicReference<Instant> instant;
        private final ZoneId zone;

        private MutableClock(Instant instant, ZoneId zone) {
            this(new AtomicReference<>(instant), zone);
        }

        private MutableClock(AtomicReference<Instant> instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        private void set(Instant newInstant) {
            instant.set(newInstant);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
