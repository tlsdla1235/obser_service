package com.observation.portal.domain.catalog.controller;

import com.observation.portal.domain.account.controller.AccountAuthController;
import com.observation.portal.domain.account.controller.BearerResourceApiInterceptor;
import com.observation.portal.domain.account.model.GithubAuthorizationStart;
import com.observation.portal.domain.account.model.GithubOAuthCallbackCommand;
import com.observation.portal.domain.account.service.AccountAuthException;
import com.observation.portal.domain.account.service.AccountAuthService;
import com.observation.portal.domain.account.service.ServiceTokenIssuer;
import com.observation.portal.domain.catalog.model.ProjectNavigationReadModel;
import com.observation.portal.domain.catalog.service.ProjectApplicationNavigationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
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
    private static final Instant NOW = Instant.parse("2026-05-28T10:00:00Z");

    private final MutableClock clock = new MutableClock(NOW, ZoneOffset.UTC);
    private final ServiceTokenIssuer tokenIssuer = new ServiceTokenIssuer(
            clock,
            "resource-api-boundary-test-signing-key",
            Duration.ofMinutes(15),
            Duration.ofDays(30));
    private final ProjectApplicationNavigationService navigationService = mock(ProjectApplicationNavigationService.class);
    private final AccountAuthService authService = mock(AccountAuthService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(
                    new ProjectNavigationController(navigationService),
                    new AccountAuthController(authService))
            .addMappedInterceptors(
                    new String[] {"/api/projects", "/api/projects/**"},
                    new BearerResourceApiInterceptor(tokenIssuer))
            .build();

    @Test
    void getProjectsWithoutAuthorizationHeaderReturnsUnauthorizedWithoutCookieSession() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().string(containsString("Bearer access token이 필요합니다.")));

        verifyNoInteractions(navigationService);
    }

    @Test
    void validServiceAccessTokenAllowsProjectResourceApi() throws Exception {
        ProjectNavigationReadModel readModel = new ProjectNavigationReadModel(
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC),
                List.of());
        when(navigationService.listProjects()).thenReturn(readModel);

        mockMvc.perform(get("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validAccessToken()))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.generatedAt").value("2026-05-28T10:00:00Z"))
                .andExpect(jsonPath("$.projects").isArray());

        verify(navigationService).listProjects();
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

        verifyNoInteractions(navigationService);
    }

    @Test
    void invalidSignatureBearerTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validAccessToken() + "x"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Set-Cookie"));

        verifyNoInteractions(navigationService);
    }

    @Test
    void expiredBearerTokenReturnsUnauthorized() throws Exception {
        String accessToken = validAccessToken();
        clock.set(Instant.parse("2026-05-28T10:16:00Z"));

        mockMvc.perform(get("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Set-Cookie"));

        verifyNoInteractions(navigationService);
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
        return tokenIssuer.issue(ACCOUNT_ID, tokenIssuer.generateRefreshToken()).accessToken();
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
