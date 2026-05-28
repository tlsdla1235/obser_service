package com.observation.portal.domain.account.model;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * 우리 서비스 access token과 refresh token을 함께 전달하는 auth result다.
 */
public record ServiceTokenPair(
        String tokenType,
        String accessToken,
        OffsetDateTime accessTokenExpiresAt,
        String refreshToken,
        OffsetDateTime refreshTokenExpiresAt
) {

    /**
     * token issuance/refresh JSON body에 필요한 token 값과 만료 시각을 보장한다.
     */
    public ServiceTokenPair {
        tokenType = requireText(tokenType, "tokenType");
        accessToken = requireText(accessToken, "accessToken");
        Objects.requireNonNull(accessTokenExpiresAt, "accessTokenExpiresAt must not be null");
        refreshToken = requireText(refreshToken, "refreshToken");
        Objects.requireNonNull(refreshTokenExpiresAt, "refreshTokenExpiresAt must not be null");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
