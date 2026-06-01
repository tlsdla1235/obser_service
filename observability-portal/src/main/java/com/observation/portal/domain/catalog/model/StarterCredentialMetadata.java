package com.observation.portal.domain.catalog.model;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * starter credential lifecycle read model이다.
 *
 * <p>project-scoped UI/API가 prefix/status/timestamps만 표시할 때 사용하며 raw value와 hash는 포함하지 않는다.</p>
 */
public record StarterCredentialMetadata(
        UUID projectId,
        String keyPrefix,
        StarterCredentialStatus status,
        OffsetDateTime issuedAt,
        OffsetDateTime rotatedAt,
        OffsetDateTime revokedAt) {

    /**
     * project key 검증 후보에서 secret이 아닌 lifecycle metadata만 추출한다.
     */
    public static StarterCredentialMetadata from(ProjectKeyCandidate candidate) {
        ProjectKeyCandidate requiredCandidate = Objects.requireNonNull(candidate, "candidate must not be null");
        return new StarterCredentialMetadata(
                requiredCandidate.projectId(),
                requiredCandidate.keyPrefix(),
                requiredCandidate.starterCredentialStatus(),
                requiredCandidate.starterCredentialIssuedAt(),
                requiredCandidate.starterCredentialRotatedAt(),
                requiredCandidate.starterCredentialRevokedAt());
    }

    public StarterCredentialMetadata {
        Objects.requireNonNull(projectId, "projectId must not be null");
        keyPrefix = requireText(keyPrefix, "keyPrefix");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(issuedAt, "issuedAt must not be null");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
