package com.observation.portal.domain.catalog.service;

import com.observation.portal.domain.account.service.AccountProjectMembershipService;
import com.observation.portal.domain.admin.service.SmokeProjectSeedService;
import com.observation.portal.domain.catalog.entity.ProjectEntity;
import com.observation.portal.domain.catalog.model.GeneratedStarterCredential;
import com.observation.portal.domain.catalog.model.ProjectRegistrationCommand;
import com.observation.portal.domain.catalog.model.ProjectStatus;
import com.observation.portal.domain.catalog.model.StarterCredentialStatus;
import com.observation.portal.domain.catalog.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.springframework.security.crypto.bcrypt.BCrypt;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProjectRegistrationServiceTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000009301");
    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");
    private static final OffsetDateTime NOW_UTC = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
    private static final String RAW_CREDENTIAL = "obs_live_regabc.<shown-once-placeholder>";
    private static final String KEY_PREFIX = "obs_live_regabc";

    @Test
    void createsActiveProjectActiveMemberMembershipAndOneTimeCredentialWithoutPersistingRawValue() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        AccountProjectMembershipService membershipService = mock(AccountProjectMembershipService.class);
        StarterCredentialGenerator credentialGenerator = mock(StarterCredentialGenerator.class);
        when(projectRepository.findByName("orders-prod")).thenReturn(Optional.empty());
        when(credentialGenerator.generate()).thenReturn(new GeneratedStarterCredential(RAW_CREDENTIAL, KEY_PREFIX));
        when(projectRepository.save(any(ProjectEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectRegistrationService service = new ProjectRegistrationService(
                projectRepository,
                membershipService,
                credentialGenerator,
                Clock.fixed(NOW, ZoneOffset.UTC));

        var result = service.register(new ProjectRegistrationCommand(ACCOUNT_ID, " Orders-Prod "));

        assertThat(result.projectName()).isEqualTo("orders-prod");
        assertThat(result.starterCredential().displayValue()).isEqualTo(RAW_CREDENTIAL);
        assertThat(result.starterCredential().keyPrefix()).isEqualTo(KEY_PREFIX);
        assertThat(result.starterCredential().visibleOnce()).isTrue();
        assertThat(result.starterCredential().issuedAt()).isEqualTo(NOW_UTC);
        assertThat(result.toString()).doesNotContain(RAW_CREDENTIAL);
        assertThat(result.starterCredential().toString()).doesNotContain(RAW_CREDENTIAL);
        assertThat(new GeneratedStarterCredential(RAW_CREDENTIAL, KEY_PREFIX).toString()).doesNotContain(RAW_CREDENTIAL);

        ArgumentCaptor<ProjectEntity> projectCaptor = ArgumentCaptor.forClass(ProjectEntity.class);
        verify(projectRepository).save(projectCaptor.capture());
        ProjectEntity savedProject = projectCaptor.getValue();
        var candidate = savedProject.toCandidate();
        assertThat(candidate.projectName()).isEqualTo("orders-prod");
        assertThat(candidate.keyPrefix()).isEqualTo(KEY_PREFIX);
        assertThat(candidate.status()).isEqualTo(ProjectStatus.ACTIVE);
        assertThat(candidate.starterCredentialStatus()).isEqualTo(StarterCredentialStatus.ACTIVE);
        assertThat(candidate.starterCredentialIssuedAt()).isEqualTo(NOW_UTC);
        assertThat(candidate.starterCredentialRotatedAt()).isNull();
        assertThat(candidate.starterCredentialRevokedAt()).isNull();
        assertThat(candidate.projectKeyHash()).doesNotContain(RAW_CREDENTIAL).isNotEqualTo(RAW_CREDENTIAL);
        assertThat(BCrypt.checkpw(RAW_CREDENTIAL, candidate.projectKeyHash())).isTrue();

        verify(membershipService).createActiveMember(ACCOUNT_ID, candidate.projectId());
    }

    @Test
    void rejectsInvalidNameBeforeGeneratingCredentialOrWritingRows() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        AccountProjectMembershipService membershipService = mock(AccountProjectMembershipService.class);
        StarterCredentialGenerator credentialGenerator = mock(StarterCredentialGenerator.class);
        ProjectRegistrationService service = new ProjectRegistrationService(
                projectRepository,
                membershipService,
                credentialGenerator,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.register(new ProjectRegistrationCommand(ACCOUNT_ID, "admin secret")))
                .isInstanceOf(ProjectRegistrationException.class)
                .hasMessage("Project name is invalid");

        verifyNoInteractions(projectRepository, membershipService, credentialGenerator);
    }

    @Test
    void duplicateProjectNameFailsClosedBeforeCredentialGenerationOrMembershipWrite() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        AccountProjectMembershipService membershipService = mock(AccountProjectMembershipService.class);
        StarterCredentialGenerator credentialGenerator = mock(StarterCredentialGenerator.class);
        when(projectRepository.findByName("orders-prod")).thenReturn(Optional.of(existingProject()));
        ProjectRegistrationService service = new ProjectRegistrationService(
                projectRepository,
                membershipService,
                credentialGenerator,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.register(new ProjectRegistrationCommand(ACCOUNT_ID, "orders-prod")))
                .isInstanceOf(ProjectRegistrationException.class)
                .hasMessage("Project name already exists");

        verify(projectRepository, never()).save(any(ProjectEntity.class));
        verifyNoInteractions(membershipService, credentialGenerator);
    }

    @Test
    void serviceDoesNotDeclareLoggerThatCouldCaptureRawCredential() {
        assertThat(ProjectRegistrationService.class.getDeclaredFields())
                .noneMatch(field -> Logger.class.isAssignableFrom(field.getType()));
    }

    @Test
    void publicRegistrationServiceDoesNotDependOnLocalSmokeSeedPath() {
        assertThat(ProjectRegistrationService.class.getDeclaredFields())
                .noneMatch(field -> SmokeProjectSeedService.class.isAssignableFrom(field.getType()));
        assertThat(ProjectRegistrationService.class.getDeclaredConstructors())
                .allSatisfy(constructor -> assertThat(constructor.getParameterTypes())
                        .doesNotContain(SmokeProjectSeedService.class));
    }

    private static ProjectEntity existingProject() {
        return new ProjectEntity(
                UUID.fromString("00000000-0000-0000-0000-000000009302"),
                "orders-prod",
                "obs_live_existing",
                BCrypt.hashpw("obs_live_existing.<test-placeholder>", BCrypt.gensalt()),
                "active",
                NOW_UTC,
                NOW_UTC);
    }
}
