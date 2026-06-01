package com.observation.portal.domain.catalog.service;

import com.observation.portal.domain.catalog.entity.ProjectEntity;
import com.observation.portal.domain.catalog.model.GeneratedStarterCredential;
import com.observation.portal.domain.catalog.model.ProjectKeyCandidate;
import com.observation.portal.domain.catalog.model.ProjectStatus;
import com.observation.portal.domain.catalog.model.StarterCredentialDisplay;
import com.observation.portal.domain.catalog.model.StarterCredentialMetadata;
import com.observation.portal.domain.catalog.model.StarterCredentialRotationResult;
import com.observation.portal.domain.catalog.model.StarterCredentialStatus;
import com.observation.portal.domain.catalog.repository.ProjectRepository;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

/**
 * starter credential metadata 조회, rotation, revocation lifecycle을 처리한다.
 *
 * <p>controller/interceptor가 account-project membership을 먼저 확인한 뒤 호출하며, 이 service는 raw value를 rotation
 * 성공 결과에만 포함하고 persistence에는 BCrypt hash와 metadata만 남긴다.</p>
 */
@Service
public class StarterCredentialService {

    private final ProjectRepository projectRepository;
    private final StarterCredentialGenerator credentialGenerator;
    private final Clock clock;

    /**
     * lifecycle 처리에 필요한 project repository, credential generator, UTC clock을 주입한다.
     */
    public StarterCredentialService(
            ProjectRepository projectRepository,
            StarterCredentialGenerator credentialGenerator,
            Clock clock) {
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository must not be null");
        this.credentialGenerator = Objects.requireNonNull(
                credentialGenerator,
                "credentialGenerator must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null").withZone(ZoneOffset.UTC);
    }

    /**
     * raw value나 hash 없이 starter credential metadata만 반환한다.
     */
    @Transactional(readOnly = true)
    public StarterCredentialMetadata metadata(UUID projectId) {
        return StarterCredentialMetadata.from(activeProject(projectId).toCandidate());
    }

    /**
     * 기존 credential을 즉시 새 prefix/hash로 교체하고 새 raw value를 1회 표시 결과로만 반환한다.
     */
    @Transactional
    public StarterCredentialRotationResult rotate(UUID projectId) {
        ProjectEntity project = activeProject(projectId);
        GeneratedStarterCredential credential = credentialGenerator.generate();
        OffsetDateTime now = now();
        project.rotateStarterCredential(
                credential.keyPrefix(),
                BCrypt.hashpw(credential.displayValue(), BCrypt.gensalt()),
                now);
        StarterCredentialMetadata metadata = StarterCredentialMetadata.from(project.toCandidate());
        return new StarterCredentialRotationResult(
                metadata.projectId(),
                new StarterCredentialDisplay(credential.displayValue(), credential.keyPrefix(), true, now),
                metadata);
    }

    /**
     * project는 그대로 두고 starter ingest credential만 revoked 상태로 바꾼 뒤 metadata를 반환한다.
     */
    @Transactional
    public StarterCredentialMetadata revoke(UUID projectId) {
        ProjectEntity project = activeProject(projectId);
        if (project.toCandidate().starterCredentialStatus() != StarterCredentialStatus.REVOKED) {
            project.revokeStarterCredential(now());
        }
        return StarterCredentialMetadata.from(project.toCandidate());
    }

    private ProjectEntity activeProject(UUID projectId) {
        UUID requiredProjectId = Objects.requireNonNull(projectId, "projectId must not be null");
        ProjectEntity project = projectRepository.findById(requiredProjectId)
                .orElseThrow(StarterCredentialLifecycleException::notFound);
        ProjectKeyCandidate candidate = project.toCandidate();
        if (candidate.status() != ProjectStatus.ACTIVE) {
            throw StarterCredentialLifecycleException.notFound();
        }
        return project;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
