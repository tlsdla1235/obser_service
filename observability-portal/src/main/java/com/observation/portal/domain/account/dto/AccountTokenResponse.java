package com.observation.portal.domain.account.dto;

import com.observation.portal.domain.account.model.AccountAuthResult;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 우리 서비스 access/refresh token을 JSON body로 전달하는 DTO다.
 */
public record AccountTokenResponse(
        UUID accountId,
        String provider,
        String tokenType,
        String accessToken,
        OffsetDateTime accessTokenExpiresAt,
        String refreshToken,
        OffsetDateTime refreshTokenExpiresAt
) {

    /**
     * auth service result를 token issuance/refresh HTTP response body로 변환한다.
     */
    public static AccountTokenResponse from(AccountAuthResult result) {
        return new AccountTokenResponse(
                result.accountId(),
                result.provider(),
                result.tokens().tokenType(),
                result.tokens().accessToken(),
                result.tokens().accessTokenExpiresAt(),
                result.tokens().refreshToken(),
                result.tokens().refreshTokenExpiresAt());
    }
}
