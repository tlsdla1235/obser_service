package com.observation.portal.domain.catalog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.observation.portal.domain.catalog.model.StarterCredentialRotationResult;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * starter credential rotation 성공 response다.
 *
 * <p>`displayValue`는 rotation 성공 직후 1회 표시 UI에서만 쓰며, metadata에는 raw value나 hash를 넣지 않는다.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StarterCredentialRotationResponse(
        UUID projectId,
        StarterCredential starterCredential) {

    /**
     * service rotation 결과를 secret-bearing HTTP response shape로 변환한다.
     */
    public static StarterCredentialRotationResponse from(StarterCredentialRotationResult result) {
        return new StarterCredentialRotationResponse(
                result.projectId(),
                new StarterCredential(
                        result.starterCredential().displayValue(),
                        result.metadata().keyPrefix(),
                        result.metadata().status().databaseValue(),
                        result.starterCredential().visibleOnce(),
                        result.metadata().issuedAt(),
                        result.metadata().rotatedAt(),
                        result.metadata().revokedAt()));
    }

    /**
     * 새 raw value를 1회 표시 field로 포함하는 rotation credential shape다.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StarterCredential(
            String displayValue,
            String keyPrefix,
            String status,
            boolean visibleOnce,
            OffsetDateTime issuedAt,
            OffsetDateTime rotatedAt,
            OffsetDateTime revokedAt) {

        /**
         * rotation response DTO가 문자열화될 때 raw display value가 남지 않도록 한다.
         */
        @Override
        public String toString() {
            return "StarterCredential[displayValue=<redacted>, keyPrefix=%s, status=%s, visibleOnce=%s, "
                    + "issuedAt=%s, rotatedAt=%s, revokedAt=%s]"
                    .formatted(keyPrefix, status, visibleOnce, issuedAt, rotatedAt, revokedAt);
        }
    }
}
