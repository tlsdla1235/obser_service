package com.observation.portal.domain.account.service;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * `.private/github-oauth.properties`에서 읽은 GitHub OAuth App 설정을 제공한다.
 */
@Component
public class GithubOAuthAppProperties {

    private static final String DEFAULT_AUTHORIZE_URI = "https://github.com/login/oauth/authorize";
    private static final String DEFAULT_TOKEN_URI = "https://github.com/login/oauth/access_token";
    private static final String DEFAULT_USER_URI = "https://api.github.com/user";

    private final Environment environment;

    /**
     * Spring environment에 import된 `portal.auth.github.*` 설정을 읽는다.
     */
    public GithubOAuthAppProperties(Environment environment) {
        this.environment = Objects.requireNonNull(environment, "environment must not be null");
    }

    /**
     * GitHub OAuth App client id를 반환한다.
     */
    public String clientId() {
        return required("portal.auth.github.client-id");
    }

    /**
     * GitHub OAuth App client secret을 반환한다.
     */
    public String clientSecret() {
        return required("portal.auth.github.client-secret");
    }

    /**
     * GitHub OAuth callback URL을 반환한다.
     */
    public String redirectUri() {
        return required("portal.auth.github.redirect-uri");
    }

    /**
     * GitHub OAuth App homepage URL을 반환한다.
     */
    public String homepageUrl() {
        return required("portal.auth.github.homepage-url");
    }

    /**
     * GitHub authorization endpoint URL을 반환한다.
     */
    public String authorizeUri() {
        return optional("portal.auth.github.authorize-uri", DEFAULT_AUTHORIZE_URI);
    }

    /**
     * GitHub token endpoint URL을 반환한다.
     */
    public String tokenUri() {
        return optional("portal.auth.github.token-uri", DEFAULT_TOKEN_URI);
    }

    /**
     * GitHub user API URL을 반환한다.
     */
    public String userUri() {
        return optional("portal.auth.github.user-uri", DEFAULT_USER_URI);
    }

    private String required(String key) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new AccountAuthException(
                    "github_oauth_unavailable",
                    "GitHub OAuth를 완료할 수 없습니다.");
        }
        return value.trim();
    }

    private String optional(String key, String defaultValue) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }
}
