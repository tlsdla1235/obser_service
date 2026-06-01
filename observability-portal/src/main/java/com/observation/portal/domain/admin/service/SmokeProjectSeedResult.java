package com.observation.portal.domain.admin.service;

import java.util.Objects;
import java.util.UUID;

/**
 * local smoke seed 실행 결과 중 operator에게 출력해도 되는 비밀이 아닌 값만 담는다.
 */
public record SmokeProjectSeedResult(
        UUID projectId,
        String projectName,
        boolean projectCreated,
        boolean membershipCreated,
        String membershipStatus,
        String verificationCommand) {

    /**
     * seed 결과가 secret을 포함하지 않도록 project 식별자와 다음 검증 명령만 정규화한다.
     */
    public SmokeProjectSeedResult {
        Objects.requireNonNull(projectId, "projectId must not be null");
        projectName = requireText(projectName, "projectName");
        membershipStatus = requireText(membershipStatus, "membershipStatus");
        verificationCommand = requireText(verificationCommand, "verificationCommand");
    }

    @Override
    public String toString() {
        return "SmokeProjectSeedResult[projectId=%s, projectName=%s, projectCreated=%s, "
                + "membershipCreated=%s, membershipStatus=%s, verificationCommand=%s]"
                .formatted(
                        projectId,
                        projectName,
                        projectCreated,
                        membershipCreated,
                        membershipStatus,
                        verificationCommand);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
