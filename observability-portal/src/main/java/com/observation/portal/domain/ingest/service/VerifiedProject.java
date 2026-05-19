package com.observation.portal.domain.ingest.service;

import com.observation.portal.domain.catalog.model.ProjectStatus;

import java.util.Objects;
import java.util.UUID;

/**
 * project key 검증에 성공한 뒤 ingest acceptance가 사용할 project context다.
 *
 * <p>원문 project key나 BCrypt hash는 포함하지 않고, 내부 식별 정보만 담는다.</p>
 */
public record VerifiedProject(UUID projectId, String projectName, ProjectStatus status) {

    /**
     * 검증된 project context의 필수 식별 값을 보장한다.
     */
    public VerifiedProject {
        Objects.requireNonNull(projectId, "projectId must not be null");
        if (projectName == null || projectName.isBlank()) {
            throw new IllegalArgumentException("projectName must not be blank");
        }
        projectName = projectName.trim();
        Objects.requireNonNull(status, "status must not be null");
    }
}
