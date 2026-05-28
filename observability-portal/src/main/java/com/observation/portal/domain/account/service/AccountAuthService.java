package com.observation.portal.domain.account.service;

import com.observation.portal.domain.account.model.AccountAuthResult;
import com.observation.portal.domain.account.model.GithubAuthorizationStart;
import com.observation.portal.domain.account.model.GithubOAuthCallbackCommand;
import com.observation.portal.domain.account.model.ServiceTokenPair;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

/**
 * GitHub OAuth only account entry와 service token lifecycle을 담당하는 service다.
 *
 * <p>provider subject 검증 전에는 account/external identity row를 만들지 않고, refresh token은 hash만 저장한다.</p>
 */
@Service
public class AccountAuthService {

    private static final String PROVIDER_GITHUB = "github";
    private static final String GENERIC_OAUTH_FAILURE = "GitHub OAuth를 완료할 수 없습니다.";
    private static final String GENERIC_TOKEN_FAILURE = "Refresh token을 사용할 수 없습니다.";

    private final GithubOAuthClient githubOAuthClient;
    private final AccountJpaRepository accountRepository;
    private final ExternalIdentityJpaRepository identityRepository;
    private final RefreshTokenFamilyJpaRepository tokenFamilyRepository;
    private final RefreshTokenJpaRepository refreshTokenRepository;
    private final OAuthStateNonceJpaRepository oauthStateNonceRepository;
    private final ServiceTokenIssuer tokenIssuer;
    private final OAuthStateSigner oauthStateSigner;
    private final Clock clock;

    /**
     * OAuth client, account repository, token store 후보, token issuer를 주입한다.
     */
    public AccountAuthService(
            GithubOAuthClient githubOAuthClient,
            AccountJpaRepository accountRepository,
            ExternalIdentityJpaRepository identityRepository,
            RefreshTokenFamilyJpaRepository tokenFamilyRepository,
            RefreshTokenJpaRepository refreshTokenRepository,
            OAuthStateNonceJpaRepository oauthStateNonceRepository,
            ServiceTokenIssuer tokenIssuer,
            OAuthStateSigner oauthStateSigner,
            Clock clock) {
        this.githubOAuthClient = Objects.requireNonNull(githubOAuthClient, "githubOAuthClient must not be null");
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository must not be null");
        this.identityRepository = Objects.requireNonNull(identityRepository, "identityRepository must not be null");
        this.tokenFamilyRepository = Objects.requireNonNull(
                tokenFamilyRepository,
                "tokenFamilyRepository must not be null");
        this.refreshTokenRepository = Objects.requireNonNull(
                refreshTokenRepository,
                "refreshTokenRepository must not be null");
        this.oauthStateNonceRepository = Objects.requireNonNull(
                oauthStateNonceRepository,
                "oauthStateNonceRepository must not be null");
        this.tokenIssuer = Objects.requireNonNull(tokenIssuer, "tokenIssuer must not be null");
        this.oauthStateSigner = Objects.requireNonNull(oauthStateSigner, "oauthStateSigner must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null").withZone(ZoneOffset.UTC);
    }

    /**
     * GitHub OAuth authorization 시작 정보를 반환한다.
     *
     * <p>server signing key로 새 expiring state를 만들어 전달한다.</p>
     */
    @Transactional
    public GithubAuthorizationStart startGithubAuthorization() {
        OAuthStateSigner.SignedState signedState = oauthStateSigner.createSignedState();
        oauthStateNonceRepository.save(OAuthStateNonceEntity.active(
                UUID.randomUUID(),
                signedState.nonceHash(),
                signedState.expiresAt(),
                nowUtc()));
        return githubOAuthClient.startAuthorization(signedState.value());
    }

    /**
     * GitHub callback을 완료하고 내부 account/external identity와 service token을 발급한다.
     */
    @Transactional(noRollbackFor = AccountAuthException.class)
    public AccountAuthResult completeGithubCallback(GithubOAuthCallbackCommand command) {
        GithubOAuthCallbackCommand requiredCommand = Objects.requireNonNull(command, "command must not be null");
        OAuthStateSigner.VerifiedState verifiedState = oauthStateSigner.verify(requiredCommand.state())
                .orElseThrow(() -> new AccountAuthException("github_oauth_failed", GENERIC_OAUTH_FAILURE));
        consumeOAuthState(verifiedState, nowUtc());
        if (requiredCommand.hasProviderFailure() || !requiredCommand.hasAuthorizationCode()) {
            throw new AccountAuthException("github_oauth_failed", GENERIC_OAUTH_FAILURE);
        }
        VerifiedGithubIdentity identity = githubOAuthClient.exchangeCode(requiredCommand.normalizedCode());
        UUID accountId = accountIdFor(identity, nowUtc());
        ServiceTokenPair tokens = issueNewTokenFamily(accountId, nowUtc());
        return new AccountAuthResult(accountId, PROVIDER_GITHUB, tokens);
    }

    /**
     * refresh token을 rotation하고 새 access/refresh token pair를 JSON body용 result로 반환한다.
     *
     * <p>reuse detection 표시는 실패 응답과 함께 commit되어야 하므로 safe auth exception은 rollback하지 않는다.</p>
     */
    @Transactional(noRollbackFor = AccountAuthException.class)
    public AccountAuthResult refresh(String rawRefreshToken) {
        OffsetDateTime now = nowUtc();
        String normalizedRefreshToken = requireRefreshToken(rawRefreshToken);
        RefreshTokenEntity currentToken = refreshTokenRepository
                .findByTokenHashForUpdate(tokenIssuer.hashRefreshToken(normalizedRefreshToken))
                .orElseThrow(() -> new AccountAuthException("refresh_token_invalid", GENERIC_TOKEN_FAILURE));
        RefreshTokenFamilyEntity family = lockedFamily(currentToken);

        if (!family.isActive() || !family.accountId().equals(currentToken.accountId())) {
            throw new AccountAuthException("refresh_token_invalid", GENERIC_TOKEN_FAILURE);
        }

        if (!currentToken.isActiveAt(now)) {
            currentToken.markReuseDetected(now);
            refreshTokenRepository.save(currentToken);
            family.markReuseDetected(now);
            tokenFamilyRepository.save(family);
            throw new AccountAuthException("refresh_token_invalid", GENERIC_TOKEN_FAILURE);
        }

        currentToken.markConsumed(now);
        refreshTokenRepository.save(currentToken);
        ServiceTokenPair tokens = issueTokenInFamily(currentToken.accountId(), currentToken.familyId(), now);
        return new AccountAuthResult(currentToken.accountId(), PROVIDER_GITHUB, tokens);
    }

    /**
     * 전달된 refresh token hash를 찾아 revoke한다.
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        OffsetDateTime now = nowUtc();
        String normalizedRefreshToken = requireRefreshToken(rawRefreshToken);
        RefreshTokenEntity token = refreshTokenRepository
                .findByTokenHashForUpdate(tokenIssuer.hashRefreshToken(normalizedRefreshToken))
                .orElseThrow(() -> new AccountAuthException("refresh_token_invalid", GENERIC_TOKEN_FAILURE));
        RefreshTokenFamilyEntity family = lockedFamily(token);
        token.markRevoked(now);
        refreshTokenRepository.save(token);
        family.markRevoked(now);
        tokenFamilyRepository.save(family);
    }

    private UUID accountIdFor(VerifiedGithubIdentity identity, OffsetDateTime now) {
        return identityRepository.findByProviderAndProviderSubject(PROVIDER_GITHUB, identity.providerSubject())
                .map(existingIdentity -> existingAccountId(existingIdentity, identity, now))
                .orElseGet(() -> createAccountAndIdentity(identity, now));
    }

    private UUID createAccountAndIdentity(VerifiedGithubIdentity identity, OffsetDateTime now) {
        AccountEntity account = accountRepository.saveAndFlush(AccountEntity.active(UUID.randomUUID(), now));
        return identityRepository.insertIfAbsentReturningAccountId(
                UUID.randomUUID(),
                account.id(),
                PROVIDER_GITHUB,
                identity.providerSubject(),
                identity.email(),
                identity.displayName(),
                identity.avatarUrl(),
                now)
                .orElseGet(() -> {
                    accountRepository.delete(account);
                    accountRepository.flush();
                    return identityRepository.findByProviderAndProviderSubject(PROVIDER_GITHUB, identity.providerSubject())
                            .map(existingIdentity -> existingAccountId(existingIdentity, identity, now))
                            .orElseThrow(() -> new AccountAuthException("github_oauth_failed", GENERIC_OAUTH_FAILURE));
                });
    }

    private UUID existingAccountId(
            ExternalIdentityEntity existingIdentity,
            VerifiedGithubIdentity identity,
            OffsetDateTime now) {
        existingIdentity.updateProfile(identity.email(), identity.displayName(), identity.avatarUrl(), now);
        return existingIdentity.accountId();
    }

    private void consumeOAuthState(OAuthStateSigner.VerifiedState verifiedState, OffsetDateTime now) {
        OAuthStateNonceEntity nonce = oauthStateNonceRepository.findByNonceHashForUpdate(verifiedState.nonceHash())
                .orElseThrow(() -> new AccountAuthException("github_oauth_failed", GENERIC_OAUTH_FAILURE));
        if (!nonce.isActiveAt(now)) {
            throw new AccountAuthException("github_oauth_failed", GENERIC_OAUTH_FAILURE);
        }
        nonce.markConsumed(now);
        oauthStateNonceRepository.save(nonce);
    }

    private ServiceTokenPair issueNewTokenFamily(UUID accountId, OffsetDateTime now) {
        RefreshTokenFamilyEntity family = tokenFamilyRepository.save(RefreshTokenFamilyEntity.active(
                UUID.randomUUID(),
                accountId,
                now));
        return issueTokenInFamily(accountId, family.id(), now);
    }

    private ServiceTokenPair issueTokenInFamily(UUID accountId, UUID familyId, OffsetDateTime now) {
        String rawRefreshToken = tokenIssuer.generateRefreshToken();
        ServiceTokenPair pair = tokenIssuer.issue(accountId, rawRefreshToken);
        refreshTokenRepository.save(RefreshTokenEntity.active(
                UUID.randomUUID(),
                familyId,
                accountId,
                tokenIssuer.hashRefreshToken(rawRefreshToken),
                pair.refreshTokenExpiresAt(),
                now));
        return pair;
    }

    /**
     * token row와 같은 transaction에서 family row를 잠가 revoke/reuse 상태를 일관되게 본다.
     */
    private RefreshTokenFamilyEntity lockedFamily(RefreshTokenEntity token) {
        return tokenFamilyRepository.findByIdForUpdate(token.familyId())
                .orElseThrow(() -> new AccountAuthException("refresh_token_invalid", GENERIC_TOKEN_FAILURE));
    }

    private OffsetDateTime nowUtc() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static String requireRefreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new AccountAuthException("refresh_token_invalid", GENERIC_TOKEN_FAILURE);
        }
        return rawRefreshToken.trim();
    }
}
