package com.observation.portal.domain.account.dto;

/**
 * refresh token rotation 요청 body다.
 */
public record RefreshTokenRequest(String refreshToken) {
}
