package com.observation.portal.domain.account.model;

/**
 * GitHub OAuth App authorization URL을 JSON API로 전달하기 위한 service result다.
 */
public record GithubAuthorizationStart(
        String provider,
        String authorizationUrl,
        boolean stateRequired
) {

    /**
     * GitHub provider와 authorization URL이 비어 있지 않도록 보장한다.
     */
    public GithubAuthorizationStart {
        provider = requireText(provider, "provider");
        authorizationUrl = requireText(authorizationUrl, "authorizationUrl");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
