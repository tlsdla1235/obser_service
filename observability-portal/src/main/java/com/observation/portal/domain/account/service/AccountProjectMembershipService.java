package com.observation.portal.domain.account.service;

import com.observation.portal.domain.account.repository.AccountProjectMembershipRepository;
import com.observation.portal.domain.catalog.entity.ProjectEntity;
import com.observation.portal.domain.catalog.model.ProjectKeyCandidate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Bearer account 기준 project visibility와 project-scoped API authorization membership을 판정한다.
 *
 * <p>access token 원문이나 provider token을 받지 않고, 인증이 끝난 account id와 path project id만 사용한다.</p>
 */
@Service
public class AccountProjectMembershipService {

    private final AccountProjectMembershipRepository membershipRepository;

    /**
     * account-project membership source of truth인 repository를 주입한다.
     */
    public AccountProjectMembershipService(AccountProjectMembershipRepository membershipRepository) {
        this.membershipRepository = Objects.requireNonNull(
                membershipRepository,
                "membershipRepository must not be null");
    }

    /**
     * account가 active membership으로 볼 수 있는 active project 후보를 service-facing model로 반환한다.
     */
    @Transactional(readOnly = true)
    public List<ProjectKeyCandidate> listActiveProjects(UUID accountId) {
        UUID requiredAccountId = Objects.requireNonNull(accountId, "accountId must not be null");
        return membershipRepository.findActiveMembershipProjectsByAccountId(requiredAccountId).stream()
                .map(ProjectEntity::toCandidate)
                .toList();
    }

    /**
     * project-scoped resource API가 catalog lookup을 수행하기 전에 account-project active membership을 확인한다.
     */
    @Transactional(readOnly = true)
    public boolean hasActiveMembership(UUID accountId, UUID projectId) {
        UUID requiredAccountId = Objects.requireNonNull(accountId, "accountId must not be null");
        UUID requiredProjectId = Objects.requireNonNull(projectId, "projectId must not be null");
        return membershipRepository.existsActiveMembership(requiredAccountId, requiredProjectId);
    }
}
