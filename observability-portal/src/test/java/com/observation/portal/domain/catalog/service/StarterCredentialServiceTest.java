package com.observation.portal.domain.catalog.service;

import com.observation.portal.domain.catalog.entity.ProjectEntity;
import com.observation.portal.domain.catalog.model.GeneratedStarterCredential;
import com.observation.portal.domain.catalog.model.ProjectStatus;
import com.observation.portal.domain.catalog.model.StarterCredentialStatus;
import com.observation.portal.domain.catalog.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StarterCredentialServiceTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000009251");
    private static final Instant NOW = Instant.parse("2026-06-01T11:00:00Z");
    private static final OffsetDateTime NOW_UTC = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
    private static final OffsetDateTime ISSUED_AT = OffsetDateTime.parse("2026-06-01T10:00:00Z");
    private static final String OLD_RAW = "obs_live_oldabc.<shown-once-old>";
    private static final String OLD_PREFIX = "obs_live_oldabc";
    private static final String NEW_RAW = "obs_live_newabc.<shown-once-new>";
    private static final String NEW_PREFIX = "obs_live_newabc";

    @Test
    void metadataReturnsOnlyPrefixStatusAndTimestamps() {
        ProjectRepository repository = mock(ProjectRepository.class);
        StarterCredentialGenerator generator = mock(StarterCredentialGenerator.class);
        when(repository.findById(PROJECT_ID)).thenReturn(Optional.of(activeProject()));
        StarterCredentialService service = service(repository, generator);

        var metadata = service.metadata(PROJECT_ID);

        assertThat(metadata.projectId()).isEqualTo(PROJECT_ID);
        assertThat(metadata.keyPrefix()).isEqualTo(OLD_PREFIX);
        assertThat(metadata.status()).isEqualTo(StarterCredentialStatus.ACTIVE);
        assertThat(metadata.issuedAt()).isEqualTo(ISSUED_AT);
        assertThat(metadata.rotatedAt()).isNull();
        assertThat(metadata.revokedAt()).isNull();
        assertThat(metadata.toString()).doesNotContain(OLD_RAW);
        verifyNoInteractions(generator);
    }

    @Test
    void rotationReplacesOldCredentialImmediatelyAndReturnsNewRawValueOnce() {
        ProjectRepository repository = mock(ProjectRepository.class);
        StarterCredentialGenerator generator = mock(StarterCredentialGenerator.class);
        ProjectEntity project = activeProject();
        when(repository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(generator.generate()).thenReturn(new GeneratedStarterCredential(NEW_RAW, NEW_PREFIX));
        StarterCredentialService service = service(repository, generator);

        var result = service.rotate(PROJECT_ID);

        assertThat(result.projectId()).isEqualTo(PROJECT_ID);
        assertThat(result.starterCredential().displayValue()).isEqualTo(NEW_RAW);
        assertThat(result.starterCredential().keyPrefix()).isEqualTo(NEW_PREFIX);
        assertThat(result.starterCredential().visibleOnce()).isTrue();
        assertThat(result.starterCredential().issuedAt()).isEqualTo(NOW_UTC);
        assertThat(result.toString()).doesNotContain(NEW_RAW);
        assertThat(result.starterCredential().toString()).doesNotContain(NEW_RAW);
        assertThat(result.metadata().keyPrefix()).isEqualTo(NEW_PREFIX);
        assertThat(result.metadata().status()).isEqualTo(StarterCredentialStatus.ACTIVE);
        assertThat(result.metadata().issuedAt()).isEqualTo(NOW_UTC);
        assertThat(result.metadata().rotatedAt()).isEqualTo(NOW_UTC);
        assertThat(result.metadata().revokedAt()).isNull();

        var rotatedCandidate = project.toCandidate();
        assertThat(rotatedCandidate.keyPrefix()).isEqualTo(NEW_PREFIX);
        assertThat(BCrypt.checkpw(NEW_RAW, rotatedCandidate.projectKeyHash())).isTrue();
        assertThat(BCrypt.checkpw(OLD_RAW, rotatedCandidate.projectKeyHash())).isFalse();
        assertThat(rotatedCandidate.projectKeyHash()).doesNotContain(NEW_RAW, OLD_RAW);
    }

    @Test
    void revocationMarksCredentialRevokedWithoutReturningRawValue() {
        ProjectRepository repository = mock(ProjectRepository.class);
        StarterCredentialGenerator generator = mock(StarterCredentialGenerator.class);
        ProjectEntity project = activeProject();
        when(repository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        StarterCredentialService service = service(repository, generator);

        var metadata = service.revoke(PROJECT_ID);

        assertThat(metadata.projectId()).isEqualTo(PROJECT_ID);
        assertThat(metadata.keyPrefix()).isEqualTo(OLD_PREFIX);
        assertThat(metadata.status()).isEqualTo(StarterCredentialStatus.REVOKED);
        assertThat(metadata.revokedAt()).isEqualTo(NOW_UTC);
        assertThat(metadata.toString()).doesNotContain(OLD_RAW);
        assertThat(project.toCandidate().starterCredentialStatus()).isEqualTo(StarterCredentialStatus.REVOKED);
        assertThat(BCrypt.checkpw(OLD_RAW, project.toCandidate().projectKeyHash())).isTrue();
        verifyNoInteractions(generator);
    }

    @Test
    void missingOrDisabledProjectMapsToLifecycleNotFoundWithoutCredentialGeneration() {
        ProjectRepository repository = mock(ProjectRepository.class);
        StarterCredentialGenerator generator = mock(StarterCredentialGenerator.class);
        when(repository.findById(PROJECT_ID)).thenReturn(Optional.empty());
        StarterCredentialService service = service(repository, generator);

        assertThatThrownBy(() -> service.rotate(PROJECT_ID))
                .isInstanceOf(StarterCredentialLifecycleException.class)
                .hasMessage("starter credential resource not found");
        verifyNoInteractions(generator);

        when(repository.findById(PROJECT_ID)).thenReturn(Optional.of(disabledProject()));
        assertThatThrownBy(() -> service.metadata(PROJECT_ID))
                .isInstanceOf(StarterCredentialLifecycleException.class);
        verifyNoInteractions(generator);
    }

    @Test
    void serviceDoesNotDeclareLoggerThatCouldCaptureRawCredential() {
        assertThat(StarterCredentialService.class.getDeclaredFields())
                .noneMatch(field -> Logger.class.isAssignableFrom(field.getType()));
    }

    private static StarterCredentialService service(
            ProjectRepository repository,
            StarterCredentialGenerator generator) {
        return new StarterCredentialService(repository, generator, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static ProjectEntity activeProject() {
        return project(ProjectStatus.ACTIVE);
    }

    private static ProjectEntity disabledProject() {
        return project(ProjectStatus.DISABLED);
    }

    private static ProjectEntity project(ProjectStatus status) {
        return new ProjectEntity(
                PROJECT_ID,
                "orders-prod",
                OLD_PREFIX,
                BCrypt.hashpw(OLD_RAW, BCrypt.gensalt()),
                status.databaseValue(),
                StarterCredentialStatus.ACTIVE.databaseValue(),
                ISSUED_AT,
                null,
                null,
                ISSUED_AT,
                ISSUED_AT);
    }
}
