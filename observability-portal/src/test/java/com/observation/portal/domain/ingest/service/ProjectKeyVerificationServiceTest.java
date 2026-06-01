package com.observation.portal.domain.ingest.service;

import com.observation.portal.domain.catalog.entity.ProjectEntity;
import com.observation.portal.domain.catalog.model.ProjectStatus;
import com.observation.portal.domain.catalog.repository.ProjectRepository;
import com.observation.portal.security.ProjectKeyHashVerifier;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.springframework.security.crypto.bcrypt.BCrypt;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProjectKeyVerificationServiceTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000003101");
    private static final String PROJECT_NAME = "checkout";
    private static final String KEY_PREFIX = "pk_live_checkout";
    private static final String RAW_PROJECT_KEY = KEY_PREFIX + ".<test-placeholder>";
    private static final OffsetDateTime FIXED_TIME = OffsetDateTime.parse("2026-05-19T00:00:00Z");

    @Test
    void rejectsMissingOrBlankProjectKeyHeader() {
        ProjectRepository repository = mock(ProjectRepository.class);
        ProjectKeyVerificationService service = service(repository);

        assertThat(service.verify(null).isVerified()).isFalse();
        assertThat(service.verify("  ").isVerified()).isFalse();

        verifyNoInteractions(repository);
    }

    @Test
    void rejectsProjectKeyExceedingBcryptLimitBeforeLookupAndHashVerification() {
        ProjectRepository repository = mock(ProjectRepository.class);
        ProjectKeyHashVerifier hashVerifier = mock(ProjectKeyHashVerifier.class);
        ProjectKeyVerificationService service = service(repository, hashVerifier);
        String tooLongProjectKey = KEY_PREFIX + "." + "가".repeat(25);

        ProjectKeyVerificationResult result = service.verify(tooLongProjectKey);

        assertThat(result.isVerified()).isFalse();
        verifyNoInteractions(repository, hashVerifier);
    }

    @Test
    void rejectsProjectKeyWithoutSeparatorBeforeRepositoryLookup() {
        ProjectRepository repository = mock(ProjectRepository.class);
        ProjectKeyVerificationService service = service(repository);

        assertThat(service.verify("short-raw-key").isVerified()).isFalse();
        assertThat(service.verify("a".repeat(40)).isVerified()).isFalse();

        verifyNoInteractions(repository);
    }

    @Test
    void rejectsEmptyProjectKeyPartsAndOversizedPrefixBeforeRepositoryLookup() {
        ProjectRepository repository = mock(ProjectRepository.class);
        ProjectKeyVerificationService service = service(repository);

        assertThat(service.verify(".<test-placeholder>").isVerified()).isFalse();
        assertThat(service.verify("prefix.").isVerified()).isFalse();
        assertThat(service.verify("a".repeat(33) + ".<test-placeholder>").isVerified()).isFalse();

        verifyNoInteractions(repository);
    }

    @Test
    void rejectsUnknownPrefixWithoutExposingRawKeyToRepositoryLookup() {
        ProjectRepository repository = mock(ProjectRepository.class);
        when(repository.findByKeyPrefix(KEY_PREFIX)).thenReturn(Optional.empty());
        ProjectKeyVerificationService service = service(repository);

        ProjectKeyVerificationResult result = service.verify(RAW_PROJECT_KEY);

        assertThat(result.isVerified()).isFalse();
        verify(repository).findByKeyPrefix(KEY_PREFIX);
        verify(repository, never()).findByKeyPrefix(RAW_PROJECT_KEY);
    }

    @Test
    void trimsProjectKeyHeaderBeforeValidationAndHashVerification() {
        ProjectRepository repository = repositoryReturning(entity(
                KEY_PREFIX,
                BCrypt.hashpw(RAW_PROJECT_KEY, BCrypt.gensalt()),
                ProjectStatus.ACTIVE));
        ProjectKeyVerificationService service = service(repository);

        ProjectKeyVerificationResult result = service.verify("  " + RAW_PROJECT_KEY + "  ");

        assertThat(result.isVerified()).isTrue();
        verify(repository).findByKeyPrefix(KEY_PREFIX);
    }

    @Test
    void rejectsHashMismatch() {
        ProjectRepository repository = repositoryReturning(entity(
                KEY_PREFIX,
                BCrypt.hashpw("different-raw-key", BCrypt.gensalt()),
                ProjectStatus.ACTIVE));
        ProjectKeyVerificationService service = service(repository);

        ProjectKeyVerificationResult result = service.verify(RAW_PROJECT_KEY);

        assertThat(result.isVerified()).isFalse();
        assertThat(result.verifiedProject()).isEmpty();
    }

    @Test
    void rejectsDisabledProjectEvenWhenHashMatches() {
        ProjectRepository repository = repositoryReturning(entity(
                KEY_PREFIX,
                BCrypt.hashpw(RAW_PROJECT_KEY, BCrypt.gensalt()),
                ProjectStatus.DISABLED));
        ProjectKeyVerificationService service = service(repository);

        ProjectKeyVerificationResult result = service.verify(RAW_PROJECT_KEY);

        assertThat(result.isVerified()).isFalse();
        assertThat(result.verifiedProject()).isEmpty();
    }

    @Test
    void rejectsInactiveStarterCredentialEvenWhenProjectAndHashAreActive() {
        ProjectRepository repository = repositoryReturning(entity(
                KEY_PREFIX,
                BCrypt.hashpw(RAW_PROJECT_KEY, BCrypt.gensalt()),
                ProjectStatus.ACTIVE,
                "revoked"));
        ProjectKeyVerificationService service = service(repository);

        ProjectKeyVerificationResult result = service.verify(RAW_PROJECT_KEY);

        assertThat(result.isVerified()).isFalse();
        assertThat(result.verifiedProject()).isEmpty();
    }

    @Test
    void rejectsRotatedOldCredentialBecauseOldPrefixNoLongerHasLookupCandidate() {
        String oldPrefix = KEY_PREFIX;
        String oldRawProjectKey = RAW_PROJECT_KEY;
        String newPrefix = "pk_live_rotated";
        String newRawProjectKey = newPrefix + ".<test-placeholder-new>";
        ProjectRepository repository = mock(ProjectRepository.class);
        when(repository.findByKeyPrefix(oldPrefix)).thenReturn(Optional.empty());
        when(repository.findByKeyPrefix(newPrefix)).thenReturn(Optional.of(entity(
                newPrefix,
                BCrypt.hashpw(newRawProjectKey, BCrypt.gensalt()),
                ProjectStatus.ACTIVE,
                "active")));
        ProjectKeyVerificationService service = service(repository);

        ProjectKeyVerificationResult oldResult = service.verify(oldRawProjectKey);
        ProjectKeyVerificationResult newResult = service.verify(newRawProjectKey);

        assertThat(oldResult.isVerified()).isFalse();
        assertThat(newResult.isVerified()).isTrue();
        verify(repository).findByKeyPrefix(oldPrefix);
        verify(repository).findByKeyPrefix(newPrefix);
    }

    @Test
    void returnsVerifiedProjectContextForActiveProjectWithMatchingHash() {
        ProjectRepository repository = repositoryReturning(entity(
                KEY_PREFIX,
                BCrypt.hashpw(RAW_PROJECT_KEY, BCrypt.gensalt()),
                ProjectStatus.ACTIVE));
        ProjectKeyVerificationService service = service(repository);

        ProjectKeyVerificationResult result = service.verify(RAW_PROJECT_KEY);

        assertThat(result.isVerified()).isTrue();
        assertThat(result.verifiedProject())
                .hasValue(new VerifiedProject(PROJECT_ID, PROJECT_NAME, ProjectStatus.ACTIVE));
    }

    @Test
    void doesNotReturnOrLogRawProjectKey() {
        ProjectRepository repository = repositoryReturning(entity(
                KEY_PREFIX,
                BCrypt.hashpw(RAW_PROJECT_KEY, BCrypt.gensalt()),
                ProjectStatus.ACTIVE));
        ProjectKeyVerificationService service = service(repository);

        ProjectKeyVerificationResult result = service.verify(RAW_PROJECT_KEY);

        assertThat(result.toString()).doesNotContain(RAW_PROJECT_KEY);
        assertThat(result.verifiedProject().orElseThrow().toString()).doesNotContain(RAW_PROJECT_KEY);
        assertThat(ProjectKeyVerificationService.class.getDeclaredFields())
                .noneMatch(field -> Logger.class.isAssignableFrom(field.getType()));
        verify(repository).findByKeyPrefix(KEY_PREFIX);
        verify(repository, never()).findByKeyPrefix(RAW_PROJECT_KEY);
    }

    private static ProjectKeyVerificationService service(ProjectRepository repository) {
        return service(repository, new ProjectKeyHashVerifier());
    }

    private static ProjectKeyVerificationService service(
            ProjectRepository repository,
            ProjectKeyHashVerifier keyHashVerifier) {
        return new ProjectKeyVerificationService(repository, keyHashVerifier);
    }

    private static ProjectRepository repositoryReturning(ProjectEntity entity) {
        ProjectRepository repository = mock(ProjectRepository.class);
        when(repository.findByKeyPrefix(KEY_PREFIX)).thenReturn(Optional.of(entity));
        return repository;
    }

    private static ProjectEntity entity(String keyPrefix, String projectKeyHash, ProjectStatus status) {
        return entity(keyPrefix, projectKeyHash, status, "active");
    }

    private static ProjectEntity entity(
            String keyPrefix,
            String projectKeyHash,
            ProjectStatus status,
            String starterCredentialStatus) {
        return new ProjectEntity(
                PROJECT_ID,
                PROJECT_NAME,
                keyPrefix,
                projectKeyHash,
                status.databaseValue(),
                starterCredentialStatus,
                FIXED_TIME,
                null,
                starterCredentialStatus.equals("revoked") ? FIXED_TIME : null,
                FIXED_TIME,
                FIXED_TIME);
    }
}
