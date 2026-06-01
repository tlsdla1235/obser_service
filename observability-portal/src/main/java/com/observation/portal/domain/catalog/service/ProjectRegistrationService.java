package com.observation.portal.domain.catalog.service;

import com.observation.portal.domain.account.service.AccountProjectMembershipService;
import com.observation.portal.domain.catalog.entity.ProjectEntity;
import com.observation.portal.domain.catalog.model.GeneratedStarterCredential;
import com.observation.portal.domain.catalog.model.ProjectRegistrationCommand;
import com.observation.portal.domain.catalog.model.ProjectRegistrationResult;
import com.observation.portal.domain.catalog.model.ProjectStatus;
import com.observation.portal.domain.catalog.model.StarterCredentialDisplay;
import com.observation.portal.domain.catalog.model.StarterCredentialStatus;
import com.observation.portal.domain.catalog.repository.ProjectRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * public onboarding project registration use case를 orchestration한다.
 *
 * <p>검증된 Bearer account id 기준으로 active project, active member membership, starter credential hash/prefix를
 * 생성한다. raw starter credential은 DB에 저장하지 않고 service 결과로만 1회 전달한다.</p>
 */
@Service
public class ProjectRegistrationService {

    private static final Pattern PROJECT_NAME_PATTERN = Pattern.compile("[a-z0-9][a-z0-9-]{1,118}[a-z0-9]");

    private final ProjectRepository projectRepository;
    private final AccountProjectMembershipService membershipService;
    private final StarterCredentialGenerator credentialGenerator;
    private final Clock clock;

    /**
     * registration에 필요한 project repository, membership service, credential generator, UTC clock을 주입한다.
     */
    public ProjectRegistrationService(
            ProjectRepository projectRepository,
            AccountProjectMembershipService membershipService,
            StarterCredentialGenerator credentialGenerator,
            Clock clock) {
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository must not be null");
        this.membershipService = Objects.requireNonNull(membershipService, "membershipService must not be null");
        this.credentialGenerator = Objects.requireNonNull(
                credentialGenerator,
                "credentialGenerator must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null").withZone(ZoneOffset.UTC);
    }

    /**
     * project name을 정규화하고 중복을 fail-closed한 뒤 project와 active membership을 함께 생성한다.
     */
    @Transactional
    public ProjectRegistrationResult register(ProjectRegistrationCommand command) {
        ProjectRegistrationCommand requiredCommand =
                Objects.requireNonNull(command, "command must not be null");
        String projectName = normalizeAndValidateProjectName(requiredCommand.projectName());
        if (projectRepository.findByName(projectName).isPresent()) {
            throw ProjectRegistrationException.duplicateName();
        }

        GeneratedStarterCredential credential = credentialGenerator.generate();
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        ProjectEntity project = new ProjectEntity(
                UUID.randomUUID(),
                projectName,
                credential.keyPrefix(),
                BCrypt.hashpw(credential.displayValue(), BCrypt.gensalt()),
                ProjectStatus.ACTIVE.databaseValue(),
                StarterCredentialStatus.ACTIVE.databaseValue(),
                now,
                null,
                null,
                now,
                now);
        ProjectEntity savedProject = saveProject(project);
        UUID projectId = savedProject.toCandidate().projectId();
        membershipService.createActiveMember(requiredCommand.accountId(), projectId);
        return new ProjectRegistrationResult(
                projectId,
                projectName,
                new StarterCredentialDisplay(credential.displayValue(), credential.keyPrefix(), true, now));
    }

    private ProjectEntity saveProject(ProjectEntity project) {
        try {
            return projectRepository.save(project);
        } catch (DataIntegrityViolationException exception) {
            throw ProjectRegistrationException.duplicateName();
        }
    }

    private static String normalizeAndValidateProjectName(String projectName) {
        String normalized = Objects.requireNonNull(projectName, "projectName must not be null")
                .trim()
                .toLowerCase(Locale.ROOT);
        if (!PROJECT_NAME_PATTERN.matcher(normalized).matches()) {
            throw ProjectRegistrationException.invalidName();
        }
        return normalized;
    }
}
