package com.observation.portal.domain.account.controller;

import com.observation.portal.domain.account.model.AccountAuthResult;
import com.observation.portal.domain.account.model.GithubAuthorizationStart;
import com.observation.portal.domain.account.model.GithubOAuthCallbackCommand;
import com.observation.portal.domain.account.model.ServiceTokenPair;
import com.observation.portal.domain.account.service.AccountAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountAuthControllerTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000006101");
    private static final OffsetDateTime ACCESS_EXPIRES_AT = OffsetDateTime.parse("2026-05-28T10:18:00Z");
    private static final OffsetDateTime REFRESH_EXPIRES_AT = OffsetDateTime.parse("2026-06-27T10:03:00Z");

    private final AccountAuthService authService = mock(AccountAuthService.class);
    private final AccountAuthController controller = new AccountAuthController(authService);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

    @Test
    void githubAuthorizeReturnsJsonEntryWithoutSecretOrCookieSession() throws Exception {
        when(authService.startGithubAuthorization())
                .thenReturn(new GithubAuthorizationStart(
                        "github",
                        "https://github.com/login/oauth/authorize?client_id=public-client&state=signed-state",
                        true));

        mockMvc.perform(get("/api/auth/github/authorize"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(jsonPath("$.provider").value("github"))
                .andExpect(jsonPath("$.authorizationUrl")
                        .value(containsString("github.com/login/oauth/authorize")))
                .andExpect(jsonPath("$.stateRequired").value(true))
                .andExpect(content().string(not(containsString("client_secret"))))
                .andExpect(content().string(not(containsString("providerAccessToken"))));
    }

    @Test
    void githubCallbackReturnsServiceTokensAsJsonBodyWithoutRedirectFragmentOrCookie() throws Exception {
        ServiceTokenPair tokens = new ServiceTokenPair(
                "Bearer",
                "access.jwt.value",
                ACCESS_EXPIRES_AT,
                "refresh-token-value",
                REFRESH_EXPIRES_AT);
        when(authService.completeGithubCallback(new GithubOAuthCallbackCommand("oauth-code", "browser-state", null)))
                .thenReturn(new AccountAuthResult(ACCOUNT_ID, "github", tokens));

        mockMvc.perform(get("/api/auth/github/callback")
                        .param("code", "oauth-code")
                        .param("state", "browser-state"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(header().doesNotExist("Location"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(jsonPath("$.accountId").value(ACCOUNT_ID.toString()))
                .andExpect(jsonPath("$.provider").value("github"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").value("access.jwt.value"))
                .andExpect(jsonPath("$.accessTokenExpiresAt").value("2026-05-28T10:18:00Z"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token-value"))
                .andExpect(jsonPath("$.refreshTokenExpiresAt").value("2026-06-27T10:03:00Z"));

        verify(authService).completeGithubCallback(new GithubOAuthCallbackCommand("oauth-code", "browser-state", null));
    }

    @Test
    void refreshReturnsServiceTokensAsJsonBodyWithNoStoreHeaders() throws Exception {
        ServiceTokenPair tokens = new ServiceTokenPair(
                "Bearer",
                "rotated.access.jwt",
                ACCESS_EXPIRES_AT,
                "rotated-refresh-token",
                REFRESH_EXPIRES_AT);
        when(authService.refresh("refresh-token-value"))
                .thenReturn(new AccountAuthResult(ACCOUNT_ID, "github", tokens));

        mockMvc.perform(post("/api/auth/token/refresh")
                        .contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\" refresh-token-value \"}"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(jsonPath("$.accessToken").value("rotated.access.jwt"))
                .andExpect(jsonPath("$.refreshToken").value("rotated-refresh-token"));

        verify(authService).refresh("refresh-token-value");
    }

    @Test
    void blankOrNullRefreshTokenBodyReturnsSafeBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/token/refresh")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(jsonPath("$.error").value("refresh_token_invalid"))
                .andExpect(jsonPath("$.message").value("Refresh token을 사용할 수 없습니다."));

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("refresh_token_invalid"))
                .andExpect(jsonPath("$.message").value("Refresh token을 사용할 수 없습니다."));

        verify(authService, never()).refresh(anyString());
        verify(authService, never()).logout(anyString());
    }

    @Test
    void nonexistentLogoutRefreshTokenReturnsSafeBadRequestWithoutLeakingInput() throws Exception {
        doThrow(new com.observation.portal.domain.account.service.AccountAuthException(
                "refresh_token_invalid",
                "Refresh token을 사용할 수 없습니다."))
                .when(authService).logout("missing-refresh-token");

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"missing-refresh-token\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(jsonPath("$.error").value("refresh_token_invalid"))
                .andExpect(jsonPath("$.message").value("Refresh token을 사용할 수 없습니다."))
                .andExpect(content().string(not(containsString("missing-refresh-token"))));

        verify(authService).logout("missing-refresh-token");
    }
}
