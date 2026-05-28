package com.observation.portal.domain.account.model;

/**
 * GitHub OAuth callback query를 service로 넘기는 command다.
 *
 * <p>provider error 값은 account 생성 전에 일반화된 실패로 수렴시키는 데만 사용한다.</p>
 */
public record GithubOAuthCallbackCommand(
        String code,
        String state,
        String error
) {

    /**
     * GitHub가 실패/취소를 돌려줬는지 확인한다.
     */
    public boolean hasProviderFailure() {
        return error != null && !error.isBlank();
    }

    /**
     * account 생성 전에 교환 가능한 authorization code가 있는지 확인한다.
     */
    public boolean hasAuthorizationCode() {
        return code != null && !code.isBlank();
    }

    /**
     * 공백을 제거한 authorization code를 반환한다.
     */
    public String normalizedCode() {
        if (!hasAuthorizationCode()) {
            throw new IllegalStateException("authorization code is missing");
        }
        return code.trim();
    }
}
