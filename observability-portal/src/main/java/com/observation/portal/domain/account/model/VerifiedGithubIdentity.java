package com.observation.portal.domain.account.model;

/**
 * GitHub OAuth 교환 후 검증된 provider identity metadata다.
 *
 * <p>providerSubject만 stable key로 사용하고 email/display/avatar는 profile metadata로만 저장한다.</p>
 */
public record VerifiedGithubIdentity(
        String providerSubject,
        String email,
        String displayName,
        String avatarUrl
) {

    /**
     * GitHub user id 또는 provider subject가 없는 identity는 account 생성에 사용할 수 없게 막는다.
     */
    public VerifiedGithubIdentity {
        providerSubject = requireText(providerSubject, "providerSubject");
        email = trimToNull(email);
        displayName = trimToNull(displayName);
        avatarUrl = trimToNull(avatarUrl);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
