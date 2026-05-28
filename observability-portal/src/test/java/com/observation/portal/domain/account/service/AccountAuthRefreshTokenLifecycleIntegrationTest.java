package com.observation.portal.domain.account.service;

import com.observation.portal.domain.account.model.AccountAuthResult;
import com.observation.portal.domain.account.model.GithubAuthorizationStart;
import com.observation.portal.domain.account.model.GithubOAuthCallbackCommand;
import com.observation.portal.domain.account.model.VerifiedGithubIdentity;
import com.observation.portal.domain.account.repository.AccountEntity;
import com.observation.portal.domain.account.repository.AccountJpaRepository;
import com.observation.portal.domain.account.repository.OAuthStateNonceJpaRepository;
import com.observation.portal.domain.account.repository.RefreshTokenEntity;
import com.observation.portal.domain.account.repository.RefreshTokenFamilyEntity;
import com.observation.portal.domain.account.repository.RefreshTokenFamilyJpaRepository;
import com.observation.portal.domain.account.repository.RefreshTokenJpaRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * refresh token family 상태, 동시 rotation, schema 무결성을 실제 PostgreSQL/JPA 경로로 검증한다.
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "portal.auth.service-token.signing-key=refresh-token-lifecycle-test-signing-key",
        "portal.auth.oauth-state.signing-key=oauth-state-lifecycle-test-signing-key"
})
class AccountAuthRefreshTokenLifecycleIntegrationTest {

    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-05-28T10:03:00Z");
    private static final OffsetDateTime EXPIRES_AT = OffsetDateTime.parse("2099-01-01T00:00:00Z");
    private static final String FOREIGN_KEY_CONSTRAINT = "fk_refresh_tokens_family_account";

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private AccountAuthService authService;

    @Autowired
    private AccountJpaRepository accountRepository;

    @Autowired
    private RefreshTokenFamilyJpaRepository familyRepository;

    @Autowired
    private RefreshTokenJpaRepository tokenRepository;

    @Autowired
    private OAuthStateNonceJpaRepository oauthStateNonceRepository;

    @Autowired
    private ServiceTokenIssuer tokenIssuer;

    @Autowired
    private OAuthStateSigner oauthStateSigner;

    @Autowired
    private FakeGithubOAuthClient githubOAuthClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void migrateSchema() {
        cleanAndMigrate();
    }

    @ParameterizedTest
    @ValueSource(strings = {"revoked", "reuse_detected"})
    void inactiveFamilyStatusBlocksDescendantActiveTokenRefresh(String familyStatus) {
        SeededToken token = seedActiveRefreshToken();
        updateFamilyStatus(token.familyId(), familyStatus);

        assertThatThrownBy(() -> authService.refresh(token.rawRefreshToken()))
                .isInstanceOf(AccountAuthException.class)
                .hasMessage("Refresh token을 사용할 수 없습니다.");

        assertThat(countTokensInFamily(token.familyId())).isEqualTo(1);
    }

    @Test
    void concurrentRotationOfSameRefreshTokenCreatesOnlyOneSuccessor() throws Exception {
        SeededToken token = seedActiveRefreshToken();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<RefreshAttempt> refreshAttempt = () -> {
            ready.countDown();
            assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
            try {
                return RefreshAttempt.success(authService.refresh(token.rawRefreshToken()));
            } catch (AccountAuthException ignored) {
                return RefreshAttempt.failure();
            }
        };

        try {
            var first = executor.submit(refreshAttempt);
            var second = executor.submit(refreshAttempt);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<RefreshAttempt> attempts = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS));
            List<RefreshAttempt> successes = attempts.stream()
                    .filter(RefreshAttempt::succeeded)
                    .toList();
            List<RefreshAttempt> failures = attempts.stream()
                    .filter(attempt -> !attempt.succeeded())
                    .toList();

            assertThat(successes).hasSize(1);
            assertThat(failures).hasSize(1);
            assertThat(countTokensInFamily(token.familyId())).isEqualTo(2);
            assertThat(familyStatus(token.familyId())).isEqualTo("reuse_detected");

            String successorRefreshToken = successes.get(0).result().tokens().refreshToken();
            assertThatThrownBy(() -> authService.refresh(successorRefreshToken))
                    .isInstanceOf(AccountAuthException.class)
                    .hasMessage("Refresh token을 사용할 수 없습니다.");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void compositeFamilyAccountForeignKeyRejectsMismatchedRefreshTokenAccount() {
        UUID ownerAccountId = UUID.randomUUID();
        UUID otherAccountId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        accountRepository.saveAndFlush(AccountEntity.active(ownerAccountId, CREATED_AT));
        accountRepository.saveAndFlush(AccountEntity.active(otherAccountId, CREATED_AT));
        familyRepository.saveAndFlush(RefreshTokenFamilyEntity.active(familyId, ownerAccountId, CREATED_AT));

        assertThat(constraintExists("refresh_tokens", FOREIGN_KEY_CONSTRAINT)).isTrue();
        assertThatThrownBy(() -> jdbcTemplate.update("""
                        insert into refresh_tokens (
                          id, family_id, account_id, token_hash, status, expires_at, created_at
                        )
                        values (?, ?, ?, ?, 'active', ?, ?)
                        """,
                UUID.randomUUID(),
                familyId,
                otherAccountId,
                "a".repeat(64),
                EXPIRES_AT,
                CREATED_AT))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(FOREIGN_KEY_CONSTRAINT);
    }

    @Test
    void oauthStateNonceIsStoredAsHashAndConsumedOnCallback() {
        githubOAuthClient.useIdentity("state-subject-1");
        String state = startedState();
        OAuthStateSigner.VerifiedState verifiedState = oauthStateSigner.verify(state).orElseThrow();

        assertThat(oauthStateNonceRepository.findByNonceHash(verifiedState.nonceHash()))
                .hasValueSatisfying(nonce -> assertThat(nonce.nonceHash()).isEqualTo(verifiedState.nonceHash()));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from oauth_state_nonces where nonce_hash = ? and status = 'active'",
                Number.class,
                verifiedState.nonceHash()).longValue()).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForList(
                "select 1 from oauth_state_nonces where nonce_hash = ?",
                state)).isEmpty();

        authService.completeGithubCallback(new GithubOAuthCallbackCommand("oauth-code", state, null));

        assertThat(jdbcTemplate.queryForObject(
                "select status from oauth_state_nonces where nonce_hash = ?",
                String.class,
                verifiedState.nonceHash())).isEqualTo("consumed");
        assertThat(jdbcTemplate.queryForObject(
                "select consumed_at is not null from oauth_state_nonces where nonce_hash = ?",
                Boolean.class,
                verifiedState.nonceHash())).isTrue();
    }

    @Test
    void reusedOAuthStateFailsBeforeSecondCodeExchangeOrAccountCreation() {
        githubOAuthClient.useIdentity("state-subject-2");
        String state = startedState();
        authService.completeGithubCallback(new GithubOAuthCallbackCommand("oauth-code", state, null));
        int exchangeCountAfterSuccess = githubOAuthClient.exchangeCount();
        long accountCountAfterSuccess = tableCount("accounts");

        assertThatThrownBy(() -> authService.completeGithubCallback(new GithubOAuthCallbackCommand(
                "oauth-code-reused",
                state,
                null)))
                .isInstanceOf(AccountAuthException.class)
                .hasMessage("GitHub OAuth를 완료할 수 없습니다.");

        assertThat(githubOAuthClient.exchangeCount()).isEqualTo(exchangeCountAfterSuccess);
        assertThat(tableCount("accounts")).isEqualTo(accountCountAfterSuccess);
    }

    @Test
    void validSignedStateMissingFromDatabaseFailsBeforeCodeExchange() {
        githubOAuthClient.useIdentity("state-subject-3");
        String state = oauthStateSigner.createState();

        assertThatThrownBy(() -> authService.completeGithubCallback(new GithubOAuthCallbackCommand(
                "oauth-code",
                state,
                null)))
                .isInstanceOf(AccountAuthException.class)
                .hasMessage("GitHub OAuth를 완료할 수 없습니다.");

        assertThat(githubOAuthClient.exchangeCount()).isZero();
        assertThat(tableCount("accounts")).isZero();
        assertThat(tableCount("external_identities")).isZero();
    }

    @Test
    void concurrentFirstGithubCallbacksForSameSubjectConvergeToSingleAccount() throws Exception {
        githubOAuthClient.useIdentity("race-subject");
        githubOAuthClient.synchronizeNextExchanges(2);
        String firstState = startedState();
        String secondState = startedState();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            var first = executor.submit(() -> authService.completeGithubCallback(new GithubOAuthCallbackCommand(
                    "oauth-code-1",
                    firstState,
                    null)));
            var second = executor.submit(() -> authService.completeGithubCallback(new GithubOAuthCallbackCommand(
                    "oauth-code-2",
                    secondState,
                    null)));

            assertThat(githubOAuthClient.awaitSynchronizedExchanges()).isTrue();
            githubOAuthClient.releaseSynchronizedExchanges();
            AccountAuthResult firstResult = first.get(10, TimeUnit.SECONDS);
            AccountAuthResult secondResult = second.get(10, TimeUnit.SECONDS);

            assertThat(firstResult.accountId()).isEqualTo(secondResult.accountId());
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from external_identities where provider = 'github' and provider_subject = 'race-subject'",
                    Number.class).longValue()).isEqualTo(1L);
            assertThat(tableCount("accounts")).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
            githubOAuthClient.releaseSynchronizedExchanges();
        }
    }

    private SeededToken seedActiveRefreshToken() {
        UUID accountId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        String rawRefreshToken = tokenIssuer.generateRefreshToken();
        accountRepository.saveAndFlush(AccountEntity.active(accountId, CREATED_AT));
        familyRepository.saveAndFlush(RefreshTokenFamilyEntity.active(familyId, accountId, CREATED_AT));
        tokenRepository.saveAndFlush(RefreshTokenEntity.active(
                UUID.randomUUID(),
                familyId,
                accountId,
                tokenIssuer.hashRefreshToken(rawRefreshToken),
                EXPIRES_AT,
                CREATED_AT));
        return new SeededToken(familyId, rawRefreshToken);
    }

    private void updateFamilyStatus(UUID familyId, String status) {
        jdbcTemplate.update("""
                        update refresh_token_families
                        set status = ?, updated_at = ?
                        where id = ?
                        """,
                status,
                CREATED_AT.plusMinutes(1),
                familyId);
    }

    private long countTokensInFamily(UUID familyId) {
        Number count = jdbcTemplate.queryForObject(
                "select count(*) from refresh_tokens where family_id = ?",
                Number.class,
                familyId);
        return count.longValue();
    }

    private String familyStatus(UUID familyId) {
        return jdbcTemplate.queryForObject(
                "select status from refresh_token_families where id = ?",
                String.class,
                familyId);
    }

    private String startedState() {
        GithubAuthorizationStart start = authService.startGithubAuthorization();
        String marker = "state=";
        int stateStart = start.authorizationUrl().indexOf(marker);
        assertThat(stateStart).isGreaterThanOrEqualTo(0);
        return start.authorizationUrl().substring(stateStart + marker.length());
    }

    private long tableCount(String tableName) {
        Number count = jdbcTemplate.queryForObject("select count(*) from " + tableName, Number.class);
        return count.longValue();
    }

    private boolean constraintExists(String tableName, String constraintName) {
        Boolean exists = jdbcTemplate.queryForObject("""
                        select exists (
                          select 1
                          from information_schema.table_constraints
                          where table_schema = 'public'
                            and table_name = ?
                            and constraint_name = ?
                        )
                        """,
                Boolean.class,
                tableName,
                constraintName);
        return Boolean.TRUE.equals(exists);
    }

    private static void cleanAndMigrate() {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();

        flyway.clean();
        flyway.migrate();
    }

    private record SeededToken(UUID familyId, String rawRefreshToken) {
    }

    private record RefreshAttempt(AccountAuthResult result) {

        private static RefreshAttempt success(AccountAuthResult result) {
            return new RefreshAttempt(result);
        }

        private static RefreshAttempt failure() {
            return new RefreshAttempt(null);
        }

        private boolean succeeded() {
            return result != null;
        }
    }

    @TestConfiguration
    static class GithubOAuthClientTestConfig {

        /**
         * 실제 GitHub provider 호출 없이 OAuth callback 후속 저장소 흐름만 검증하도록 fake client를 우선 주입한다.
         */
        @Bean
        @Primary
        FakeGithubOAuthClient fakeGithubOAuthClient() {
            return new FakeGithubOAuthClient();
        }
    }

    static final class FakeGithubOAuthClient implements GithubOAuthClient {

        private final AtomicInteger exchangeCount = new AtomicInteger();
        private final AtomicReference<VerifiedGithubIdentity> identity = new AtomicReference<>(
                new VerifiedGithubIdentity("default-subject", "user@example.com", "octocat", null));
        private volatile CountDownLatch exchangeReady;
        private volatile CountDownLatch exchangeRelease;

        /**
         * 다음 OAuth code exchange 결과로 돌려줄 GitHub subject를 지정한다.
         */
        void useIdentity(String providerSubject) {
            exchangeCount.set(0);
            identity.set(new VerifiedGithubIdentity(providerSubject, "user@example.com", "octocat", null));
            exchangeReady = null;
            exchangeRelease = null;
        }

        /**
         * 지정된 수의 code exchange가 동시에 account 생성 경계로 들어가도록 대기점을 건다.
         */
        void synchronizeNextExchanges(int expectedExchanges) {
            exchangeReady = new CountDownLatch(expectedExchanges);
            exchangeRelease = new CountDownLatch(1);
        }

        /**
         * 동시 code exchange 대기점에 모든 요청이 도착했는지 확인한다.
         */
        boolean awaitSynchronizedExchanges() throws InterruptedException {
            CountDownLatch ready = exchangeReady;
            return ready == null || ready.await(5, TimeUnit.SECONDS);
        }

        /**
         * 동시 code exchange 대기점을 풀어 account identity 저장 경합을 진행시킨다.
         */
        void releaseSynchronizedExchanges() {
            CountDownLatch release = exchangeRelease;
            if (release != null) {
                release.countDown();
            }
        }

        /**
         * provider token 저장 없이 GitHub authorization URL에 state만 반영한다.
         */
        @Override
        public GithubAuthorizationStart startAuthorization(String state) {
            return new GithubAuthorizationStart(
                    "github",
                    "https://github.test/login/oauth/authorize?state=" + state,
                    true);
        }

        /**
         * provider raw payload 없이 stable subject metadata만 반환한다.
         */
        @Override
        public VerifiedGithubIdentity exchangeCode(String code) {
            exchangeCount.incrementAndGet();
            awaitReleaseIfConfigured();
            return identity.get();
        }

        /**
         * code exchange 호출 횟수를 반환한다.
         */
        int exchangeCount() {
            return exchangeCount.get();
        }

        private void awaitReleaseIfConfigured() {
            CountDownLatch ready = exchangeReady;
            CountDownLatch release = exchangeRelease;
            if (ready == null || release == null) {
                return;
            }
            ready.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new AccountAuthException("github_oauth_failed", "GitHub OAuth를 완료할 수 없습니다.");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AccountAuthException("github_oauth_failed", "GitHub OAuth를 완료할 수 없습니다.");
            }
        }
    }
}
