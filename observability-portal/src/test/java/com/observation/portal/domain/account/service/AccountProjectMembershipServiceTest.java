package com.observation.portal.domain.account.service;

import com.observation.portal.domain.account.repository.AccountProjectMembershipRepository;
import com.observation.portal.domain.account.repository.AccountProjectMembershipEntity;
import com.observation.portal.domain.catalog.entity.ProjectEntity;
import com.observation.portal.domain.catalog.model.ProjectKeyCandidate;
import com.observation.portal.domain.catalog.model.ProjectStatus;
import com.observation.portal.domain.catalog.model.StarterCredentialStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountProjectMembershipServiceTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000006601");
    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000006701");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-05-31T13:00:00Z");

    private final AccountProjectMembershipRepository membershipRepository =
            mock(AccountProjectMembershipRepository.class);
    private final AccountProjectMembershipService service =
            new AccountProjectMembershipService(membershipRepository, Clock.fixed(NOW.toInstant(), NOW.getOffset()));

    @Test
    void listsActiveMembershipProjectsAsServiceFacingCandidates() {
        when(membershipRepository.findActiveMembershipProjectsByAccountId(ACCOUNT_ID))
                .thenReturn(List.of(project()));

        List<ProjectKeyCandidate> projects = service.listActiveProjects(ACCOUNT_ID);

        assertThat(projects)
                .containsExactly(new ProjectKeyCandidate(
                        PROJECT_ID,
                        "scoped-project",
                        "pk_scoped",
                        "$2a$10$membershiphashmembershiphashmembership12",
                        ProjectStatus.ACTIVE,
                        StarterCredentialStatus.ACTIVE,
                        NOW,
                        null,
                        null));
        verify(membershipRepository).findActiveMembershipProjectsByAccountId(ACCOUNT_ID);
    }

    @Test
    void activeMembershipCheckUsesOnlyAccountIdAndProjectId() {
        when(membershipRepository.existsActiveMembership(ACCOUNT_ID, PROJECT_ID)).thenReturn(true);

        assertThat(service.hasActiveMembership(ACCOUNT_ID, PROJECT_ID)).isTrue();

        verify(membershipRepository).existsActiveMembership(ACCOUNT_ID, PROJECT_ID);
    }

    @Test
    void createsActiveMemberMembershipWithServerSideRoleStatusAndTimestamp() {
        when(membershipRepository.save(any(AccountProjectMembershipEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.createActiveMember(ACCOUNT_ID, PROJECT_ID);

        verify(membershipRepository).save(argThat(membership ->
                membership.accountId().equals(ACCOUNT_ID)
                        && membership.projectId().equals(PROJECT_ID)
                        && membership.isActive()));
    }

    private static ProjectEntity project() {
        return new ProjectEntity(
                PROJECT_ID,
                "scoped-project",
                "pk_scoped",
                "$2a$10$membershiphashmembershiphashmembership12",
                "active",
                NOW,
                NOW);
    }
}
