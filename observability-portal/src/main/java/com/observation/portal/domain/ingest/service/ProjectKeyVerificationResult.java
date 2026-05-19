package com.observation.portal.domain.ingest.service;

import com.observation.portal.domain.catalog.model.ProjectStatus;

import java.util.Objects;
import java.util.Optional;

/**
 * project key 검증 결과를 ingest service/controller boundary에 전달하는 모델이다.
 *
 * <p>인증 실패 사유를 외부로 세분화하지 않고, 성공 시에만 검증된 project context를 제공한다.</p>
 */
public final class ProjectKeyVerificationResult {

    private static final ProjectKeyVerificationResult UNAUTHORIZED = new ProjectKeyVerificationResult(null);

    private final VerifiedProject verifiedProject;

    private ProjectKeyVerificationResult(VerifiedProject verifiedProject) {
        this.verifiedProject = verifiedProject;
    }

    /**
     * missing, unknown, mismatch, disabled project를 모두 같은 unauthorized 결과로 표현한다.
     */
    public static ProjectKeyVerificationResult unauthorized() {
        return UNAUTHORIZED;
    }

    /**
     * active project 검증 성공 결과를 만든다.
     */
    public static ProjectKeyVerificationResult verified(VerifiedProject verifiedProject) {
        VerifiedProject requiredProject = Objects.requireNonNull(verifiedProject, "verifiedProject must not be null");
        if (requiredProject.status() != ProjectStatus.ACTIVE) {
            throw new IllegalArgumentException("verified project must be active");
        }
        return new ProjectKeyVerificationResult(requiredProject);
    }

    /**
     * project key가 active project로 검증되었는지 반환한다.
     */
    public boolean isVerified() {
        return verifiedProject != null;
    }

    /**
     * 검증 성공 시 ingest 처리에 사용할 project context를 반환한다.
     */
    public Optional<VerifiedProject> verifiedProject() {
        return Optional.ofNullable(verifiedProject);
    }

    @Override
    public String toString() {
        if (!isVerified()) {
            return "ProjectKeyVerificationResult[verified=false]";
        }
        return "ProjectKeyVerificationResult[verified=true, projectId=%s, projectName=%s, status=%s]"
                .formatted(verifiedProject.projectId(), verifiedProject.projectName(), verifiedProject.status());
    }
}
