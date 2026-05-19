package com.observation.portal.domain.ingest.service;

import com.observation.portal.domain.catalog.entity.ProjectEntity;
import com.observation.portal.domain.catalog.model.ProjectKeyCandidate;
import com.observation.portal.domain.catalog.repository.ProjectRepository;
import com.observation.portal.security.ProjectKeyHashVerifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

/**
 * `X-OBS-Project-Key` header를 active project context로 검증하는 ingest service다.
 *
 * <p>controller는 header 문자열만 넘기고, 이 서비스는 prefix 조회, BCrypt hash 검증, disabled
 * project 차단을 수행한다. 원문 key는 DB 저장, 결과 모델, 로그에 남기지 않는다.</p>
 */
@Service
public class ProjectKeyVerificationService {

    private static final int MAX_KEY_PREFIX_LENGTH = 32;
    private static final int MAX_BCRYPT_INPUT_BYTES = 72;
    private static final char PREFIX_SEPARATOR = '.';

    private final ProjectRepository projectRepository;
    private final ProjectKeyHashVerifier keyHashVerifier;

    /**
     * Spring Data project repository와 BCrypt 검증 컴포넌트로 service를 구성한다.
     */
    public ProjectKeyVerificationService(
            ProjectRepository projectRepository,
            ProjectKeyHashVerifier keyHashVerifier) {
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository must not be null");
        this.keyHashVerifier = Objects.requireNonNull(keyHashVerifier, "keyHashVerifier must not be null");
    }

    /**
     * `X-OBS-Project-Key` header 값을 검증하고 성공 시 project context를 반환한다.
     */
    @Transactional(readOnly = true)
    public ProjectKeyVerificationResult verify(String projectKeyHeader) {
        Optional<ProjectKeyInput> input = ProjectKeyInput.fromHeader(projectKeyHeader);
        if (input.isEmpty()) {
            return ProjectKeyVerificationResult.unauthorized();
        }

        ProjectKeyInput projectKeyInput = input.orElseThrow();
        Optional<ProjectKeyCandidate> candidate = projectRepository.findByKeyPrefix(projectKeyInput.keyPrefix())
                .map(ProjectEntity::toCandidate);
        if (candidate.isEmpty()) {
            return ProjectKeyVerificationResult.unauthorized();
        }

        ProjectKeyCandidate project = candidate.orElseThrow();
        if (!project.isActive()) {
            return ProjectKeyVerificationResult.unauthorized();
        }
        if (!keyHashVerifier.matches(projectKeyInput.rawProjectKey(), project.projectKeyHash())) {
            return ProjectKeyVerificationResult.unauthorized();
        }

        return ProjectKeyVerificationResult.verified(
                new VerifiedProject(project.projectId(), project.projectName(), project.status()));
    }

    private record ProjectKeyInput(String rawProjectKey, String keyPrefix) {

        /**
         * header 원문을 trim한 뒤 BCrypt와 repository lookup에 안전한 project key 형태로 정규화한다.
         */
        private static Optional<ProjectKeyInput> fromHeader(String headerValue) {
            if (headerValue == null || headerValue.isBlank()) {
                return Optional.empty();
            }
            String rawProjectKey = headerValue.trim();
            if (exceedsBcryptInputLimit(rawProjectKey)) {
                return Optional.empty();
            }
            return extractKeyPrefix(rawProjectKey)
                    .map(keyPrefix -> new ProjectKeyInput(rawProjectKey, keyPrefix));
        }

        /**
         * project key는 `<key_prefix>.<secret>` 형식만 허용하고 repository에는 prefix만 전달한다.
         */
        private static Optional<String> extractKeyPrefix(String rawProjectKey) {
            int separatorIndex = rawProjectKey.indexOf(PREFIX_SEPARATOR);
            if (separatorIndex <= 0 || separatorIndex == rawProjectKey.length() - 1) {
                return Optional.empty();
            }
            String prefix = rawProjectKey.substring(0, separatorIndex);
            if (prefix.length() > MAX_KEY_PREFIX_LENGTH) {
                return Optional.empty();
            }
            return Optional.of(prefix);
        }

        private static boolean exceedsBcryptInputLimit(String rawProjectKey) {
            return rawProjectKey.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_INPUT_BYTES;
        }
    }
}
