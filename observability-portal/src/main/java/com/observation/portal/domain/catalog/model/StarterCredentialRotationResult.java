package com.observation.portal.domain.catalog.model;

import java.util.Objects;
import java.util.UUID;

/**
 * rotation 성공 응답에 필요한 project id, 새 1회 표시 credential, metadata를 묶은 service 결과다.
 */
public record StarterCredentialRotationResult(
        UUID projectId,
        StarterCredentialDisplay starterCredential,
        StarterCredentialMetadata metadata) {

    public StarterCredentialRotationResult {
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(starterCredential, "starterCredential must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
    }

    /**
     * rotation 결과가 문자열화될 때 새 raw credential이 로그/테스트 실패 메시지에 남지 않게 한다.
     */
    @Override
    public String toString() {
        return "StarterCredentialRotationResult[projectId=%s, starterCredential=%s, metadata=%s]"
                .formatted(projectId, starterCredential, metadata);
    }
}
