package com.observation.portal.domain.account.dto;

/**
 * refresh token revoke 기반 logout 요청 body다.
 */
public record LogoutRequest(String refreshToken) {
}
