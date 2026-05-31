package com.observation.portal.domain.account.service;

import com.observation.portal.domain.account.repository.AccountProjectMembershipRepository;
import com.observation.portal.domain.catalog.entity.ProjectEntity;
import com.observation.portal.domain.catalog.model.ProjectKeyCandidate;
import com.observation.portal.domain.catalog.model.ProjectStatus;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
            new AccountProjectMembershipService(membershipRepository);

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
                        ProjectStatus.ACTIVE));
        verify(membershipRepository).findActiveMembershipProjectsByAccountId(ACCOUNT_ID);
    }

    @Test
    void activeMembershipCheckUsesOnlyAccountIdAndProjectId() {
        when(membershipRepository.existsActiveMembership(ACCOUNT_ID, PROJECT_ID)).thenReturn(true);

        assertThat(service.hasActiveMembership(ACCOUNT_ID, PROJECT_ID)).isTrue();

        verify(membershipRepository).existsActiveMembership(ACCOUNT_ID, PROJECT_ID);
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
