package com.observation.portal.domain.catalog.controller;

import com.observation.portal.domain.account.controller.BearerResourceApiInterceptor;
import com.observation.portal.domain.account.service.ServiceTokenIssuer;
import com.observation.portal.domain.catalog.dto.ProjectRegistrationResponse;
import com.observation.portal.domain.catalog.model.ProjectRegistrationCommand;
import com.observation.portal.domain.catalog.model.ProjectRegistrationResult;
import com.observation.portal.domain.catalog.model.StarterCredentialDisplay;
import com.observation.portal.domain.catalog.service.ProjectRegistrationException;
import com.observation.portal.domain.catalog.service.ProjectRegistrationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectRegistrationControllerTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000009201");
    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000009202");
    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");
    private static final String DISPLAY_VALUE = "obs_live_stage1abc.<shown-once-placeholder>";
    private static final String KEY_PREFIX = "obs_live_stage1abc";

    private final MutableClock clock = new MutableClock(NOW, ZoneOffset.UTC);
    private final ServiceTokenIssuer tokenIssuer = new ServiceTokenIssuer(
            clock,
            "registration-controller-test-signing-key",
            Duration.ofMinutes(15),
            Duration.ofDays(30));
    private final ProjectRegistrationService registrationService = mock(ProjectRegistrationService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ProjectRegistrationController(registrationService))
            .addMappedInterceptors(
                    new String[] {"/api/projects", "/api/projects/**"},
                    new BearerResourceApiInterceptor(tokenIssuer))
            .build();

    @Test
    void authenticatedAccountRegistersProjectWithOneTimeStarterCredentialAndNoStore() throws Exception {
        when(registrationService.register(new ProjectRegistrationCommand(ACCOUNT_ID, "orders-prod")))
                .thenReturn(registrationResult("orders-prod"));

        mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokenFor(ACCOUNT_ID))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "orders-prod",
                                  "accountId": "00000000-0000-0000-0000-000000009299",
                                  "role": "owner",
                                  "status": "disabled",
                                  "keyPrefix": "client-prefix",
                                  "projectKeyHash": "client-hash",
                                  "starterCredential": {"displayValue": "<client-supplied-placeholder>"}
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(jsonPath("$.project.projectId").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$.project.name").value("orders-prod"))
                .andExpect(jsonPath("$.project.links.applications")
                        .value("/api/projects/%s/applications".formatted(PROJECT_ID)))
                .andExpect(jsonPath("$.starterCredential.displayValue").value(DISPLAY_VALUE))
                .andExpect(jsonPath("$.starterCredential.keyPrefix").value(KEY_PREFIX))
                .andExpect(jsonPath("$.starterCredential.visibleOnce").value(true))
                .andExpect(jsonPath("$.starterCredential.issuedAt").value("2026-06-01T10:00:00Z"))
                .andExpect(content().string(not(containsString("<client-supplied-placeholder>"))))
                .andExpect(content().string(not(containsString("client-hash"))));

        ArgumentCaptor<ProjectRegistrationCommand> commandCaptor =
                ArgumentCaptor.forClass(ProjectRegistrationCommand.class);
        verify(registrationService).register(commandCaptor.capture());
        assertThat(commandCaptor.getValue())
                .isEqualTo(new ProjectRegistrationCommand(ACCOUNT_ID, "orders-prod"));
    }

    @Test
    void noTokenInvalidTokenAndExpiredTokenReturnBearerUnauthorized() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"orders-prod\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().string(containsString("Bearer access token이 필요합니다.")));

        mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-service-token")
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"orders-prod\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));

        String expiringToken = accessTokenFor(ACCOUNT_ID);
        clock.set(Instant.parse("2026-06-01T10:16:00Z"));
        mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiringToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"orders-prod\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));

        verifyNoInteractions(registrationService);
    }

    @Test
    void registrationFailureResponseDoesNotExposeSubmittedSecretsOrTokens() throws Exception {
        when(registrationService.register(new ProjectRegistrationCommand(ACCOUNT_ID, "bad-name")))
                .thenThrow(ProjectRegistrationException.invalidName());

        mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokenFor(ACCOUNT_ID))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "bad-name",
                                  "accessToken": "service-access-token-raw",
                                  "refreshToken": "service-refresh-token-raw",
                                  "providerAccessToken": "github-provider-token-raw",
                                  "starterCredential": {"displayValue": "obs_live_bad.<shown-once-placeholder>"}
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.error").value("invalid_project_name"))
                .andExpect(content().string(not(containsString("service-access-token-raw"))))
                .andExpect(content().string(not(containsString("service-refresh-token-raw"))))
                .andExpect(content().string(not(containsString("github-provider-token-raw"))))
                .andExpect(content().string(not(containsString("obs_live_bad.<shown-once-placeholder>"))));
    }

    @Test
    void malformedJsonMapsToSanitizedNoStoreFailureWithoutBodySecretEcho() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokenFor(ACCOUNT_ID))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"orders-prod","starterCredential":{"displayValue":"obs_live_bad.<shown-once-placeholder>"
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(jsonPath("$.error").value("invalid_project_name"))
                .andExpect(content().string(not(containsString("obs_live_bad.<shown-once-placeholder>"))));

        verifyNoInteractions(registrationService);
    }

    @Test
    void duplicateProjectNameMapsToSanitizedConflict() throws Exception {
        when(registrationService.register(new ProjectRegistrationCommand(ACCOUNT_ID, "orders-prod")))
                .thenThrow(ProjectRegistrationException.duplicateName());

        mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokenFor(ACCOUNT_ID))
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"orders-prod\"}"))
                .andExpect(status().isConflict())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.error").value("duplicate_project_name"))
                .andExpect(content().string(not(containsString(DISPLAY_VALUE))));
    }

    @Test
    void registrationResponseToStringRedactsOneTimeStarterCredential() {
        ProjectRegistrationResponse response = ProjectRegistrationResponse.from(registrationResult("orders-prod"));

        assertThat(response.toString()).doesNotContain(DISPLAY_VALUE);
        assertThat(response.starterCredential().toString()).doesNotContain(DISPLAY_VALUE);
    }

    private String accessTokenFor(UUID accountId) {
        return tokenIssuer.issue(accountId, "refresh-token-for-test").accessToken();
    }

    private static ProjectRegistrationResult registrationResult(String projectName) {
        OffsetDateTime issuedAt = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        return new ProjectRegistrationResult(
                PROJECT_ID,
                projectName,
                new StarterCredentialDisplay(DISPLAY_VALUE, KEY_PREFIX, true, issuedAt));
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

        private void set(Instant instant) {
            this.instant.set(instant);
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
