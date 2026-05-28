package com.observation.portal.domain.account.controller;

import com.observation.portal.domain.account.model.GithubOAuthCallbackCommand;
import com.observation.portal.domain.account.service.AccountAuthException;
import com.observation.portal.domain.account.service.AccountAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthSecretExposureGuardTest {

    private final AccountAuthService authService = mock(AccountAuthService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AccountAuthController(authService))
            .build();

    @Test
    void oauthFailureResponseUsesGeneralizedCopyWithoutTokenRawPayloadOrSecret() throws Exception {
        when(authService.completeGithubCallback(new GithubOAuthCallbackCommand(
                "oauth-code-should-not-leak",
                "browser-state",
                "access_denied")))
                .thenThrow(new AccountAuthException(
                        "github_oauth_failed",
                        "GitHub OAuth를 완료할 수 없습니다."));

        mockMvc.perform(get("/api/auth/github/callback")
                        .param("code", "oauth-code-should-not-leak")
                        .param("state", "browser-state")
                        .param("error", "access_denied")
                        .param("providerAccessToken", "gho_raw_provider_token")
                        .param("client_secret", "raw-client-secret"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(content().string(containsString("GitHub OAuth를 완료할 수 없습니다.")))
                .andExpect(content().string(not(containsString("oauth-code-should-not-leak"))))
                .andExpect(content().string(not(containsString("gho_raw_provider_token"))))
                .andExpect(content().string(not(containsString("raw-client-secret"))))
                .andExpect(content().string(not(containsString("access_denied"))));
    }
}
