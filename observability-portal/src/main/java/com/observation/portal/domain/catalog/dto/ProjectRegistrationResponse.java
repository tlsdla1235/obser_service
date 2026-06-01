package com.observation.portal.domain.catalog.dto;

import com.observation.portal.domain.catalog.model.ProjectRegistrationResult;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * project registration 성공 response DTO다.
 *
 * <p>`starterCredential.displayValue`는 생성 직후 1회 표시용으로만 담고, 일반 read API DTO에는 포함하지 않는다.</p>
 */
public record ProjectRegistrationResponse(ProjectSummary project, StarterCredential starterCredential) {

    /**
     * service 결과를 HTTP response shape로 변환한다.
     */
    public static ProjectRegistrationResponse from(ProjectRegistrationResult result) {
        UUID projectId = result.projectId();
        return new ProjectRegistrationResponse(
                new ProjectSummary(
                        projectId,
                        result.projectName(),
                        new ProjectLinks("/api/projects/%s/applications".formatted(projectId))),
                new StarterCredential(
                        result.starterCredential().displayValue(),
                        result.starterCredential().keyPrefix(),
                        result.starterCredential().visibleOnce(),
                        result.starterCredential().issuedAt()));
    }

    /**
     * 등록 직후 Project Entry refresh가 사용할 project summary다.
     */
    public record ProjectSummary(UUID projectId, String name, ProjectLinks links) {
    }

    /**
     * 기존 Application List navigation으로 이어지는 server-provided link다.
     */
    public record ProjectLinks(String applications) {
    }

    /**
     * starter 설정에 복사할 1회 표시 credential과 non-secret metadata다.
     */
    public record StarterCredential(
            String displayValue,
            String keyPrefix,
            boolean visibleOnce,
            OffsetDateTime issuedAt) {

        /**
         * HTTP body에는 1회 표시 원문을 담지만, DTO 문자열화에서는 raw 값을 숨긴다.
         */
        @Override
        public String toString() {
            return "StarterCredential[displayValue=<redacted>, keyPrefix=%s, visibleOnce=%s, issuedAt=%s]"
                    .formatted(keyPrefix, visibleOnce, issuedAt);
        }
    }
}
