package com.observation.portal.domain.catalog.model;

import java.util.Objects;
import java.util.UUID;

/**
 * project key prefix 조회 결과로 얻은 검증 후보를 표현한다.
 *
 * <p>BCrypt hash는 검증 입력으로만 쓰며, 원문 project key는 이 모델에 담지 않는다.</p>
 */
public record ProjectKeyCandidate(
        UUID projectId,
        String projectName,
        String keyPrefix,
        String projectKeyHash,
        ProjectStatus status) {

    /**
     * repository row를 service가 검증 가능한 형태로 정규화한다.
     */
    public ProjectKeyCandidate {
        Objects.requireNonNull(projectId, "projectId must not be null");
        projectName = requireText(projectName, "projectName");
        keyPrefix = requireText(keyPrefix, "keyPrefix");
        projectKeyHash = requireText(projectKeyHash, "projectKeyHash");
        Objects.requireNonNull(status, "status must not be null");
    }

    /**
     * active project인지 빠르게 확인한다.
     */
    public boolean isActive() {
        return status == ProjectStatus.ACTIVE;
    }

    @Override
    public String toString() {
        return "ProjectKeyCandidate[projectId=%s, projectName=%s, keyPrefix=%s, status=%s]"
                .formatted(projectId, projectName, keyPrefix, status);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
