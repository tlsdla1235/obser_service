package com.observation.portal.domain.account.service;

import com.observation.portal.domain.account.model.AccountAuthResult;
import com.observation.portal.domain.account.model.GithubAuthorizationStart;
import com.observation.portal.domain.account.model.GithubOAuthCallbackCommand;
import com.observation.portal.domain.account.model.VerifiedGithubIdentity;
import com.observation.portal.domain.account.repository.AccountEntity;
import com.observation.portal.domain.account.repository.AccountJpaRepository;
import com.observation.portal.domain.account.repository.ExternalIdentityEntity;
import com.observation.portal.domain.account.repository.ExternalIdentityJpaRepository;
import com.observation.portal.domain.account.repository.OAuthStateNonceEntity;
import com.observation.portal.domain.account.repository.OAuthStateNonceJpaRepository;
import com.observation.portal.domain.account.repository.RefreshTokenEntity;
import com.observation.portal.domain.account.repository.RefreshTokenFamilyEntity;
import com.observation.portal.domain.account.repository.RefreshTokenFamilyJpaRepository;
import com.observation.portal.domain.account.repository.RefreshTokenJpaRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GithubOAuthFailureTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-28T10:03:00Z"), ZoneOffset.UTC);
    private static final String STATE_SIGNING_KEY = "oauth-state-signing-key-for-story-6-1";

    private final GithubOAuthClient githubOAuthClient = mock(GithubOAuthClient.class);
    private final AccountJpaRepository accountRepository = mock(AccountJpaRepository.class);
    private final ExternalIdentityJpaRepository identityRepository = mock(ExternalIdentityJpaRepository.class);
    private final RefreshTokenFamilyJpaRepository tokenFamilyRepository = mock(RefreshTokenFamilyJpaRepository.class);
    private final RefreshTokenJpaRepository refreshTokenRepository = mock(RefreshTokenJpaRepository.class);
    private final OAuthStateNonceJpaRepository oauthStateNonceRepository = mock(OAuthStateNonceJpaRepository.class);
    private final ServiceTokenIssuer tokenIssuer = new ServiceTokenIssuer(
            CLOCK,
            "test-signing-key-for-story-6-1",
            Duration.ofMinutes(15),
            Duration.ofDays(30));
    private final OAuthStateSigner oauthStateSigner = new OAuthStateSigner(
            CLOCK,
            STATE_SIGNING_KEY,
            Duration.ofMinutes(5));
    private final AccountAuthService authService = new AccountAuthService(
            githubOAuthClient,
            accountRepository,
            identityRepository,
            tokenFamilyRepository,
            refreshTokenRepository,
            oauthStateNonceRepository,
            tokenIssuer,
            oauthStateSigner,
            CLOCK);

    @Test
    void githubAuthorizationStartPassesServerSignedStateToProviderClient() {
        when(oauthStateNonceRepository.save(any(OAuthStateNonceEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(githubOAuthClient.startAuthorization(argThat(oauthStateSigner::isValid)))
                .thenReturn(new GithubAuthorizationStart(
                        "github",
                        "https://github.com/login/oauth/authorize?state=signed-state",
                        true));

        var start = authService.startGithubAuthorization();

        assertThat(start.authorizationUrl()).contains("state=signed-state");
        verify(githubOAuthClient).startAuthorization(argThat(oauthStateSigner::isValid));
    }

    @Test
    void cancelledOrFailedGithubOAuthDoesNotCreateAccountOrExternalIdentity() {
        OAuthStateSigner.SignedState state = storedState();
        GithubOAuthCallbackCommand cancelled = new GithubOAuthCallbackCommand(
                null,
                state.value(),
                "access_denied");

        assertThatThrownBy(() -> authService.completeGithubCallback(cancelled))
                .isInstanceOf(AccountAuthException.class)
                .hasMessage("GitHub OAuth를 완료할 수 없습니다.");

        verifyNoInteractions(githubOAuthClient, accountRepository, identityRepository);
    }

    @Test
    void missingTamperedOrExpiredStateStopsBeforeCodeExchangeAndDoesNotCreateAccountOrExternalIdentity() {
        OAuthStateSigner expiredStateSigner = new OAuthStateSigner(
                Clock.offset(CLOCK, Duration.ofMinutes(-10)),
                STATE_SIGNING_KEY,
                Duration.ofMinutes(5));
        List<String> invalidStates = List.of(
                "",
                oauthStateSigner.createState() + "x",
                expiredStateSigner.createState());

        for (String invalidState : invalidStates) {
            assertThatThrownBy(() -> authService.completeGithubCallback(
                    new GithubOAuthCallbackCommand("oauth-code", invalidState, null)))
                    .isInstanceOf(AccountAuthException.class)
                    .hasMessage("GitHub OAuth를 완료할 수 없습니다.");
        }
        assertThatThrownBy(() -> authService.completeGithubCallback(
                new GithubOAuthCallbackCommand("oauth-code", null, null)))
                .isInstanceOf(AccountAuthException.class)
                .hasMessage("GitHub OAuth를 완료할 수 없습니다.");

        verifyNoInteractions(githubOAuthClient, accountRepository, identityRepository);
    }

    @Test
    void successfulGithubOAuthUsesProviderSubjectAsStableIdentityAndStoresOnlyRefreshTokenHash() {
        OAuthStateSigner.SignedState state = storedState();
        when(githubOAuthClient.exchangeCode("oauth-code"))
                .thenReturn(new VerifiedGithubIdentity(
                        "1234567",
                        "user@example.com",
                        "octocat",
                        "https://avatars.githubusercontent.com/u/1234567"));
        when(identityRepository.findByProviderAndProviderSubject("github", "1234567"))
                .thenReturn(Optional.empty());
        when(accountRepository.saveAndFlush(any(AccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(identityRepository.insertIfAbsentReturningAccountId(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()))
                .thenAnswer(invocation -> {
                    java.util.UUID accountId = invocation.getArgument(1);
                    return Optional.of(accountId);
                });
        when(tokenFamilyRepository.save(any(RefreshTokenFamilyEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(refreshTokenRepository.save(any(RefreshTokenEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountAuthResult result = authService.completeGithubCallback(
                new GithubOAuthCallbackCommand("oauth-code", state.value(), null));

        assertThat(result.provider()).isEqualTo("github");
        assertThat(result.tokens().accessToken()).contains(".");
        assertThat(result.tokens().refreshToken()).isNotBlank();
        verify(identityRepository).findByProviderAndProviderSubject("github", "1234567");
        verify(identityRepository).insertIfAbsentReturningAccountId(
                any(),
                any(),
                argThat(provider -> provider.equals("github")),
                argThat(subject -> subject.equals("1234567")),
                argThat(email -> email.equals("user@example.com")),
                any(),
                any(),
                any());
        verify(refreshTokenRepository).save(argThat(refreshToken ->
                refreshToken.tokenHash().length() == 64
                        && !refreshToken.tokenHash().contains(result.tokens().refreshToken())));
    }

    private OAuthStateSigner.SignedState storedState() {
        OAuthStateSigner.SignedState state = oauthStateSigner.createSignedState();
        OAuthStateNonceEntity nonce = OAuthStateNonceEntity.active(
                java.util.UUID.randomUUID(),
                state.nonceHash(),
                state.expiresAt(),
                java.time.OffsetDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC));
        when(oauthStateNonceRepository.findByNonceHashForUpdate(state.nonceHash()))
                .thenReturn(Optional.of(nonce));
        when(oauthStateNonceRepository.save(any(OAuthStateNonceEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        return state;
    }
}
