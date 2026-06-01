package com.observation.portal.domain.admin.service;

import com.observation.portal.domain.account.repository.AccountEntity;
import com.observation.portal.domain.account.repository.AccountJpaRepository;
import com.observation.portal.domain.account.repository.AccountProjectMembershipEntity;
import com.observation.portal.domain.account.repository.AccountProjectMembershipRepository;
import com.observation.portal.domain.account.repository.ExternalIdentityEntity;
import com.observation.portal.domain.account.repository.ExternalIdentityJpaRepository;
import com.observation.portal.domain.catalog.entity.ProjectEntity;
import com.observation.portal.domain.catalog.model.ProjectKeyCandidate;
import com.observation.portal.domain.catalog.repository.ProjectRepository;
import com.observation.portal.security.ProjectKeyHashVerifier;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 실제 GitHub OAuth로 생성된 local account를 smoke project와 active membership으로 연결한다.
 *
 * <p>이 service는 public HTTP API가 아니라 local/operator seed command에서만 호출된다. raw project key는
 * BCrypt hash 생성과 기존 hash 검증에만 쓰고, exception/result/log surface에 싣지 않는다.</p>
 */
@Service
public class SmokeProjectSeedService {

    private static final String PROVIDER_GITHUB = "github";
    private static final String ROLE_MEMBER = "member";
    private static final String STATUS_ACTIVE = "active";
    private static final int MAX_KEY_PREFIX_LENGTH = 32;
    private static final int MAX_BCRYPT_INPUT_BYTES = 72;
    private static final char PREFIX_SEPARATOR = '.';
    private static final Set<String> PRODUCTION_LIKE_PROFILES = Set.of("prod", "production", "staging");
    private static final String VERIFICATION_COMMAND = "scripts/smoke/verify-smoke-projects.sh";

    private final AccountJpaRepository accountRepository;
    private final ExternalIdentityJpaRepository identityRepository;
    private final ProjectRepository projectRepository;
    private final AccountProjectMembershipRepository membershipRepository;
    private final ProjectKeyHashVerifier keyHashVerifier;
    private final Clock clock;

    /**
     * smoke seed에 필요한 account, identity, project, membership repository와 BCrypt 검증기를 주입한다.
     */
    public SmokeProjectSeedService(
            AccountJpaRepository accountRepository,
            ExternalIdentityJpaRepository identityRepository,
            ProjectRepository projectRepository,
            AccountProjectMembershipRepository membershipRepository,
            ProjectKeyHashVerifier keyHashVerifier,
            Clock clock) {
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository must not be null");
        this.identityRepository = Objects.requireNonNull(identityRepository, "identityRepository must not be null");
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository must not be null");
        this.membershipRepository = Objects.requireNonNull(
                membershipRepository,
                "membershipRepository must not be null");
        this.keyHashVerifier = Objects.requireNonNull(keyHashVerifier, "keyHashVerifier must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null").withZone(ZoneOffset.UTC);
    }

    /**
     * local-only guard를 통과한 뒤 smoke project와 active account-project membership을 idempotent하게 준비한다.
     */
    @Transactional
    public SmokeProjectSeedResult seed(SmokeProjectSeedProperties properties) {
        SmokeProjectSeedProperties requiredProperties =
                Objects.requireNonNull(properties, "properties must not be null");
        validateLocalOnlyGuard(requiredProperties);
        ProjectKeyInput projectKey = ProjectKeyInput.from(requiredProperties.rawProjectKey());
        String projectName = requireText(requiredProperties.projectName(), "portal.smoke.seed.project-name");

        ExternalIdentityEntity identity = resolveIdentity(requiredProperties);
        AccountEntity account = accountRepository.findById(identity.accountId())
                .orElseThrow(() -> new SmokeProjectSeedException("GitHub identity is not linked to an account"));
        if (!account.isActive()) {
            throw new SmokeProjectSeedException("Smoke seed requires an active account");
        }

        ProjectUpsert projectUpsert = upsertProject(projectName, projectKey, requiredProperties.projectId());
        MembershipUpsert membershipUpsert = upsertMembership(account.id(), projectUpsert.project().projectId());
        return new SmokeProjectSeedResult(
                projectUpsert.project().projectId(),
                projectUpsert.project().projectName(),
                projectUpsert.created(),
                membershipUpsert.created(),
                membershipUpsert.status(),
                VERIFICATION_COMMAND);
    }

    private void validateLocalOnlyGuard(SmokeProjectSeedProperties properties) {
        if (!properties.enabled()) {
            throw new SmokeProjectSeedException("portal.smoke.seed.enabled must be true to run smoke seed");
        }
        Set<String> profiles = normalizedProfiles(properties.activeProfiles());
        boolean hasProductionLikeProfile = profiles.stream().anyMatch(PRODUCTION_LIKE_PROFILES::contains);
        if (hasProductionLikeProfile || !profiles.contains("local-smoke")) {
            throw new SmokeProjectSeedException(
                    "Smoke seed requires the local-smoke profile and must not run in production-like profiles");
        }
    }

    private ExternalIdentityEntity resolveIdentity(SmokeProjectSeedProperties properties) {
        if (properties.accountProviderSubject() != null) {
            return identityRepository
                    .findByProviderAndProviderSubject(PROVIDER_GITHUB, properties.accountProviderSubject())
                    .orElseThrow(() -> new SmokeProjectSeedException(
                            "No GitHub identity matched portal.smoke.seed.account-provider-subject"));
        }
        if (properties.accountDisplayName() != null) {
            List<ExternalIdentityEntity> identities = identityRepository
                    .findByProviderAndDisplayName(PROVIDER_GITHUB, properties.accountDisplayName());
            if (identities.size() == 1) {
                return identities.get(0);
            }
            throw new SmokeProjectSeedException(
                    "portal.smoke.seed.account-display-name must match exactly one GitHub identity");
        }
        throw new SmokeProjectSeedException(
                "Configure portal.smoke.seed.account-provider-subject or portal.smoke.seed.account-display-name");
    }

    private ProjectUpsert upsertProject(String projectName, ProjectKeyInput projectKey, UUID configuredProjectId) {
        Optional<ProjectEntity> byName = projectRepository.findByName(projectName);
        Optional<ProjectEntity> byPrefix = projectRepository.findByKeyPrefix(projectKey.keyPrefix());
        if (byName.isPresent() && byPrefix.isPresent()
                && !byName.orElseThrow().toCandidate().projectId().equals(byPrefix.orElseThrow().toCandidate().projectId())) {
            throw new SmokeProjectSeedException(
                    "Existing smoke project name and key prefix point to different projects");
        }

        Optional<ProjectEntity> existing = byName.or(() -> byPrefix);
        if (existing.isPresent()) {
            ProjectKeyCandidate project = existing.orElseThrow().toCandidate();
            validateExistingProject(projectName, projectKey, configuredProjectId, project);
            return new ProjectUpsert(project, false);
        }

        OffsetDateTime now = nowUtc();
        ProjectEntity saved = projectRepository.save(new ProjectEntity(
                configuredProjectId == null ? UUID.randomUUID() : configuredProjectId,
                projectName,
                projectKey.keyPrefix(),
                BCrypt.hashpw(projectKey.rawProjectKey(), BCrypt.gensalt()),
                STATUS_ACTIVE,
                now,
                now));
        return new ProjectUpsert(saved.toCandidate(), true);
    }

    private void validateExistingProject(
            String projectName,
            ProjectKeyInput projectKey,
            UUID configuredProjectId,
            ProjectKeyCandidate project) {
        if (configuredProjectId != null && !configuredProjectId.equals(project.projectId())) {
            throw new SmokeProjectSeedException("Existing smoke project id differs from portal.smoke.seed.project-id");
        }
        if (!project.isActive()) {
            throw new SmokeProjectSeedException("Smoke seed requires an active project");
        }
        if (!project.projectName().equals(projectName) || !project.keyPrefix().equals(projectKey.keyPrefix())) {
            throw new SmokeProjectSeedException(
                    "Existing smoke project name and key prefix must match the configured values");
        }
        if (!keyHashVerifier.matches(projectKey.rawProjectKey(), project.projectKeyHash())) {
            throw new SmokeProjectSeedException(
                    "Existing smoke project key hash does not match portal.smoke.seed.raw-project-key");
        }
    }

    private MembershipUpsert upsertMembership(UUID accountId, UUID projectId) {
        Optional<AccountProjectMembershipEntity> existing =
                membershipRepository.findByAccountIdAndProjectId(accountId, projectId);
        if (existing.isPresent()) {
            AccountProjectMembershipEntity membership = existing.orElseThrow();
            if (membership.isActive()) {
                return new MembershipUpsert(false, "already_active");
            }
            throw new SmokeProjectSeedException(
                    "Existing disabled membership was not changed; operator action is required");
        }

        OffsetDateTime now = nowUtc();
        membershipRepository.save(new AccountProjectMembershipEntity(
                UUID.randomUUID(),
                accountId,
                projectId,
                ROLE_MEMBER,
                STATUS_ACTIVE,
                now,
                now));
        return new MembershipUpsert(true, "created_active");
    }

    private OffsetDateTime nowUtc() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static Set<String> normalizedProfiles(Set<String> profiles) {
        if (profiles == null) {
            return Set.of();
        }
        return profiles.stream()
                .filter(Objects::nonNull)
                .map(profile -> profile.trim().toLowerCase(Locale.ROOT))
                .filter(profile -> !profile.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static String requireText(String value, String keyName) {
        if (value == null || value.isBlank()) {
            throw new SmokeProjectSeedException(keyName + " must be configured");
        }
        return value.trim();
    }

    private record ProjectUpsert(ProjectKeyCandidate project, boolean created) {
    }

    private record MembershipUpsert(boolean created, String status) {
    }

    private record ProjectKeyInput(String rawProjectKey, String keyPrefix) {

        /**
         * raw project key를 `<key_prefix>.<secret>` shape와 BCrypt 입력 제한에 맞게 검증한다.
         */
        private static ProjectKeyInput from(String rawProjectKey) {
            String normalized = requireText(rawProjectKey, "portal.smoke.seed.raw-project-key");
            if (normalized.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_INPUT_BYTES) {
                throw new SmokeProjectSeedException(
                        "portal.smoke.seed.raw-project-key must not exceed the BCrypt 72 byte input limit");
            }
            int separatorIndex = normalized.indexOf(PREFIX_SEPARATOR);
            if (separatorIndex <= 0 || separatorIndex == normalized.length() - 1) {
                throw new SmokeProjectSeedException(
                        "portal.smoke.seed.raw-project-key must use the <key_prefix>.<secret> shape");
            }
            String prefix = normalized.substring(0, separatorIndex);
            if (prefix.length() > MAX_KEY_PREFIX_LENGTH) {
                throw new SmokeProjectSeedException(
                        "portal.smoke.seed.raw-project-key key prefix must be 32 characters or fewer");
            }
            return new ProjectKeyInput(normalized, prefix);
        }
    }
}
