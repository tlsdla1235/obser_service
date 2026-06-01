package com.observation.portal.domain.catalog.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Bearer 인증을 통과한 account가 project 등록을 요청할 때 service에 전달하는 command다.
 *
 * <p>client가 보낸 account id, role, key/hash 같은 값은 포함하지 않고 검증된 account id와 project name만 담는다.</p>
 */
public record ProjectRegistrationCommand(UUID accountId, String projectName) {

    /**
     * registration service 입력값의 필수성을 보장한다.
     */
    public ProjectRegistrationCommand {
        Objects.requireNonNull(accountId, "accountId must not be null");
        if (projectName == null || projectName.isBlank()) {
            throw new IllegalArgumentException("projectName must not be blank");
        }
        projectName = projectName.trim();
    }
}
