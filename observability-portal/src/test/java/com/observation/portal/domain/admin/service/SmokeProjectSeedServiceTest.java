package com.observation.portal.domain.admin.service;

import com.observation.portal.domain.account.repository.AccountEntity;
import com.observation.portal.domain.account.repository.AccountJpaRepository;
import com.observation.portal.domain.account.repository.AccountProjectMembershipEntity;
import com.observation.portal.domain.account.repository.AccountProjectMembershipRepository;
import com.observation.portal.domain.account.repository.ExternalIdentityEntity;
import com.observation.portal.domain.account.repository.ExternalIdentityJpaRepository;
import com.observation.portal.domain.catalog.entity.ProjectEntity;
import com.observation.portal.domain.catalog.repository.ProjectRepository;
import com.observation.portal.security.ProjectKeyHashVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCrypt;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmokeProjectSeedServiceTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000007101");
    private static final UUID IDENTITY_ID = UUID.fromString("00000000-0000-0000-0000-000000007102");
    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000007201");
    private static final UUID MEMBERSHIP_ID = UUID.fromString("00000000-0000-0000-0000-000000007301");
    private static final String PROVIDER_SUBJECT = "12345678";
    private static final String PROJECT_NAME = "local-smoke";
    private static final String PROJECT_KEY_PREFIX = "smoke_local";
    private static final Instant NOW = Instant.parse("2026-06-01T00:30:00Z");

    private final AccountJpaRepository accountRepository = mock(AccountJpaRepository.class);
    private final ExternalIdentityJpaRepository identityRepository = mock(ExternalIdentityJpaRepository.class);
    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final AccountProjectMembershipRepository membershipRepository =
            mock(AccountProjectMembershipRepository.class);
    private final SmokeProjectSeedService service = new SmokeProjectSeedService(
            accountRepository,
            identityRepository,
            projectRepository,
            membershipRepository,
            new ProjectKeyHashVerifier(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void providerSubjectSeedCreatesActiveProjectAndMembershipWithoutPersistingRawKey() {
        String rawProjectKey = rawProjectKey();
        when(identityRepository.findByProviderAndProviderSubject("github", PROVIDER_SUBJECT))
                .thenReturn(Optional.of(identity("github", PROVIDER_SUBJECT, "octocat", ACCOUNT_ID)));
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(AccountEntity.active(ACCOUNT_ID, now())));
        when(projectRepository.findByName(PROJECT_NAME)).thenReturn(Optional.empty());
        when(projectRepository.findByKeyPrefix(PROJECT_KEY_PREFIX)).thenReturn(Optional.empty());
        when(membershipRepository.findByAccountIdAndProjectId(ACCOUNT_ID, PROJECT_ID)).thenReturn(Optional.empty());
        when(projectRepository.save(any(ProjectEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(membershipRepository.save(any(AccountProjectMembershipEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SmokeProjectSeedResult result = service.seed(properties(PROJECT_ID, PROVIDER_SUBJECT, null, rawProjectKey));

        assertThat(result.projectId()).isEqualTo(PROJECT_ID);
        assertThat(result.projectName()).isEqualTo(PROJECT_NAME);
        assertThat(result.projectCreated()).isTrue();
        assertThat(result.membershipCreated()).isTrue();
        assertThat(result.toString()).doesNotContain(rawProjectKey);
        verify(projectRepository).save(argThat(project -> {
            var candidate = project.toCandidate();
            return candidate.projectId().equals(PROJECT_ID)
                    && candidate.projectName().equals(PROJECT_NAME)
                    && candidate.keyPrefix().equals(PROJECT_KEY_PREFIX)
                    && !candidate.projectKeyHash().contains(rawProjectKey)
                    && BCrypt.checkpw(rawProjectKey, candidate.projectKeyHash());
        }));
        verify(membershipRepository).save(argThat(membership ->
                membership.accountId().equals(ACCOUNT_ID)
                        && membership.projectId().equals(PROJECT_ID)
                        && membership.isActive()));
    }

    @Test
    void missingIdentitySelectorsFailClosedWithoutRows() {
        when(identityRepository.findByProviderAndProviderSubject("github", PROVIDER_SUBJECT))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.seed(properties(PROJECT_ID, PROVIDER_SUBJECT, null)))
                .isInstanceOf(SmokeProjectSeedException.class)
                .hasMessageContaining("portal.smoke.seed.account-provider-subject");

        when(identityRepository.findByProviderAndDisplayName("github", "missing"))
                .thenReturn(List.of());
        assertThatThrownBy(() -> service.seed(properties(PROJECT_ID, null, "missing")))
                .isInstanceOf(SmokeProjectSeedException.class)
                .hasMessageContaining("portal.smoke.seed.account-display-name");

        assertThatThrownBy(() -> service.seed(properties(PROJECT_ID, null, null)))
                .isInstanceOf(SmokeProjectSeedException.class)
                .hasMessageContaining("portal.smoke.seed.account-provider-subject");

        verify(projectRepository, never()).save(any());
        verify(membershipRepository, never()).save(any());
    }

    @Test
    void displayNameSelectorRequiresExactlyOneGithubIdentity() {
        when(identityRepository.findByProviderAndDisplayName("github", "octocat"))
                .thenReturn(List.of(
                        identity("github", "101", "octocat", ACCOUNT_ID),
                        identity("github", "102", "octocat", UUID.fromString("00000000-0000-0000-0000-000000007103"))));

        assertThatThrownBy(() -> service.seed(properties(PROJECT_ID, null, "octocat")))
                .isInstanceOf(SmokeProjectSeedException.class)
                .hasMessageContaining("portal.smoke.seed.account-display-name");

        verify(projectRepository, never()).save(any());
        verify(membershipRepository, never()).save(any());
    }

    @Test
    void disabledAccountDisabledProjectAndDisabledMembershipFailClosed() {
        String rawProjectKey = rawProjectKey();
        ExternalIdentityEntity identity = identity("github", PROVIDER_SUBJECT, "octocat", ACCOUNT_ID);
        SmokeProjectSeedProperties properties = properties(PROJECT_ID, PROVIDER_SUBJECT, null, rawProjectKey);

        when(identityRepository.findByProviderAndProviderSubject("github", PROVIDER_SUBJECT))
                .thenReturn(Optional.of(identity));
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(disabledAccount(ACCOUNT_ID)));
        assertThatThrownBy(() -> service.seed(properties))
                .isInstanceOf(SmokeProjectSeedException.class)
                .hasMessageContaining("active account");

        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(AccountEntity.active(ACCOUNT_ID, now())));
        when(projectRepository.findByName(PROJECT_NAME)).thenReturn(Optional.of(disabledProject(rawProjectKey)));
        when(projectRepository.findByKeyPrefix(PROJECT_KEY_PREFIX)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.seed(properties))
                .isInstanceOf(SmokeProjectSeedException.class)
                .hasMessageContaining("active project");

        ProjectEntity project = existingActiveProject(rawProjectKey);
        when(projectRepository.findByName(PROJECT_NAME)).thenReturn(Optional.of(project));
        when(projectRepository.findByKeyPrefix(PROJECT_KEY_PREFIX)).thenReturn(Optional.of(project));
        when(membershipRepository.findByAccountIdAndProjectId(ACCOUNT_ID, PROJECT_ID))
                .thenReturn(Optional.of(disabledMembership()));
        assertThatThrownBy(() -> service.seed(properties))
                .isInstanceOf(SmokeProjectSeedException.class)
                .hasMessageContaining("disabled membership");

        verify(membershipRepository, never()).save(any());
    }

    @Test
    void invalidRawProjectKeyAndProductionProfilesFailBeforeRowsAreWritten() {
        assertThatThrownBy(() -> service.seed(properties(PROJECT_ID, PROVIDER_SUBJECT, null, "missing-dot")))
                .isInstanceOf(SmokeProjectSeedException.class)
                .hasMessageContaining("portal.smoke.seed.raw-project-key");

        String tooLongKey = PROJECT_KEY_PREFIX + "." + "x".repeat(80);
        assertThatThrownBy(() -> service.seed(properties(PROJECT_ID, PROVIDER_SUBJECT, null, tooLongKey)))
                .isInstanceOf(SmokeProjectSeedException.class)
                .hasMessageContaining("72 byte");

        SmokeProjectSeedProperties productionProperties = new SmokeProjectSeedProperties(
                true,
                Set.of("prod"),
                PROVIDER_SUBJECT,
                null,
                PROJECT_NAME,
                rawProjectKey(),
                PROJECT_ID);
        assertThatThrownBy(() -> service.seed(productionProperties))
                .isInstanceOf(SmokeProjectSeedException.class)
                .hasMessageContaining("local-smoke");

        verify(projectRepository, never()).save(any());
        verify(membershipRepository, never()).save(any());
    }

    @Test
    void existingActiveMembershipIsIdempotentSuccess() {
        String rawProjectKey = rawProjectKey();
        ProjectEntity project = existingActiveProject(rawProjectKey);
        when(identityRepository.findByProviderAndProviderSubject("github", PROVIDER_SUBJECT))
                .thenReturn(Optional.of(identity("github", PROVIDER_SUBJECT, "octocat", ACCOUNT_ID)));
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(AccountEntity.active(ACCOUNT_ID, now())));
        when(projectRepository.findByName(PROJECT_NAME)).thenReturn(Optional.of(project));
        when(projectRepository.findByKeyPrefix(PROJECT_KEY_PREFIX)).thenReturn(Optional.of(project));
        when(membershipRepository.findByAccountIdAndProjectId(ACCOUNT_ID, PROJECT_ID))
                .thenReturn(Optional.of(activeMembership()));

        SmokeProjectSeedResult result = service.seed(properties(PROJECT_ID, PROVIDER_SUBJECT, null, rawProjectKey));

        assertThat(result.projectCreated()).isFalse();
        assertThat(result.membershipCreated()).isFalse();
        assertThat(result.membershipStatus()).isEqualTo("already_active");
        verify(projectRepository, never()).save(any());
        verify(membershipRepository, never()).save(any());
    }

    private static SmokeProjectSeedProperties properties(UUID projectId, String providerSubject, String displayName) {
        return properties(projectId, providerSubject, displayName, rawProjectKey());
    }

    private static SmokeProjectSeedProperties properties(
            UUID projectId,
            String providerSubject,
            String displayName,
            String rawProjectKey) {
        return new SmokeProjectSeedProperties(
                true,
                Set.of("local-smoke"),
                providerSubject,
                displayName,
                PROJECT_NAME,
                rawProjectKey,
                projectId);
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
    }

    /**
     * raw project key fixture가 repository에 고정 문자열로 남지 않도록 테스트 실행 중에만 값을 만든다.
     */
    private static String rawProjectKey() {
        return PROJECT_KEY_PREFIX + "." + UUID.randomUUID().toString().replace("-", "");
    }

    private static ExternalIdentityEntity identity(
            String provider,
            String providerSubject,
            String displayName,
            UUID accountId) {
        return ExternalIdentityEntity.create(
                IDENTITY_ID,
                accountId,
                provider,
                providerSubject,
                null,
                displayName,
                null,
                now());
    }

    private static AccountEntity disabledAccount(UUID accountId) {
        return AccountEntity.disabled(accountId, now());
    }

    private static ProjectEntity existingActiveProject(String rawProjectKey) {
        return new ProjectEntity(
                PROJECT_ID,
                PROJECT_NAME,
                PROJECT_KEY_PREFIX,
                BCrypt.hashpw(rawProjectKey, BCrypt.gensalt()),
                "active",
                now(),
                now());
    }

    private static ProjectEntity disabledProject(String rawProjectKey) {
        return new ProjectEntity(
                PROJECT_ID,
                PROJECT_NAME,
                PROJECT_KEY_PREFIX,
                BCrypt.hashpw(rawProjectKey, BCrypt.gensalt()),
                "disabled",
                now(),
                now());
    }

    private static AccountProjectMembershipEntity activeMembership() {
        return membership("active");
    }

    private static AccountProjectMembershipEntity disabledMembership() {
        return membership("disabled");
    }

    private static AccountProjectMembershipEntity membership(String status) {
        return new AccountProjectMembershipEntity(
                MEMBERSHIP_ID,
                ACCOUNT_ID,
                PROJECT_ID,
                "member",
                status,
                now(),
                now());
    }
}
