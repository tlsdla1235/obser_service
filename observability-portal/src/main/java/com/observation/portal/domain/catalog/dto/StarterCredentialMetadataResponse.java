package com.observation.portal.domain.catalog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.observation.portal.domain.catalog.model.StarterCredentialMetadata;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * starter credential metadata API response다.
 *
 * <p>UI가 lifecycle 상태를 표시할 수 있는 prefix/status/timestamps만 포함하고 raw value/hash는 제외한다.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StarterCredentialMetadataResponse(
        UUID projectId,
        StarterCredential starterCredential) {

    /**
     * service metadata model을 HTTP response shape로 변환한다.
     */
    public static StarterCredentialMetadataResponse from(StarterCredentialMetadata metadata) {
        return new StarterCredentialMetadataResponse(
                metadata.projectId(),
                new StarterCredential(
                        metadata.keyPrefix(),
                        metadata.status().databaseValue(),
                        metadata.issuedAt(),
                        metadata.rotatedAt(),
                        metadata.revokedAt()));
    }

    /**
     * raw value 없는 starter credential metadata shape다.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StarterCredential(
            String keyPrefix,
            String status,
            OffsetDateTime issuedAt,
            OffsetDateTime rotatedAt,
            OffsetDateTime revokedAt) {
    }
}
