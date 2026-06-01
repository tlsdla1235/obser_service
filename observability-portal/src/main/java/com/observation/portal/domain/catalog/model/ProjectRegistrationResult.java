package com.observation.portal.domain.catalog.model;

import java.util.Objects;
import java.util.UUID;

/**
 * project registration use case의 service 결과다.
 *
 * <p>JPA entity를 controller boundary로 넘기지 않고, response에 필요한 project summary와 1회 표시 credential만 담는다.</p>
 */
public record ProjectRegistrationResult(
        UUID projectId,
        String projectName,
        StarterCredentialDisplay starterCredential) {

    /**
     * controller가 response DTO로 변환할 수 있는 필수 결과 값을 보장한다.
     */
    public ProjectRegistrationResult {
        Objects.requireNonNull(projectId, "projectId must not be null");
        if (projectName == null || projectName.isBlank()) {
            throw new IllegalArgumentException("projectName must not be blank");
        }
        projectName = projectName.trim();
        Objects.requireNonNull(starterCredential, "starterCredential must not be null");
    }

    /**
     * registration 결과가 문자열화될 때 starter credential 원문이 노출되지 않도록 한다.
     */
    @Override
    public String toString() {
        return "ProjectRegistrationResult[projectId=%s, projectName=%s, starterCredential=%s]"
                .formatted(projectId, projectName, starterCredential);
    }
}
