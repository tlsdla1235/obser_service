package com.observation.portal.domain.account.dto;

/**
 * OAuth 실패나 token 오류를 provider 내부 payload 없이 일반화해 전달하는 DTO다.
 */
public record AuthErrorResponse(
        String error,
        String message
) {
}
