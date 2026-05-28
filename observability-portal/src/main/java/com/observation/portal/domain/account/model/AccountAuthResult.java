package com.observation.portal.domain.account.model;

import java.util.Objects;
import java.util.UUID;

/**
 * GitHub account entry 또는 refresh token rotation 성공 결과다.
 */
public record AccountAuthResult(
        UUID accountId,
        String provider,
        ServiceTokenPair tokens
) {

    /**
     * account identity와 service token pair가 null 없이 전달되도록 보장한다.
     */
    public AccountAuthResult {
        Objects.requireNonNull(accountId, "accountId must not be null");
        provider = requireText(provider, "provider");
        Objects.requireNonNull(tokens, "tokens must not be null");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
