package com.observation.portal.domain.account.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.observation.portal.domain.account.model.GithubAuthorizationStart;
import com.observation.portal.domain.account.model.VerifiedGithubIdentity;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Objects;

/**
 * GitHub OAuth App token exchange와 user profile 조회를 수행하는 HTTP client다.
 *
 * <p>GitHub provider access token은 user id 조회 직후 폐기하고 저장소나 response model에 싣지 않는다.</p>
 */
@Service
public class HttpGithubOAuthClient implements GithubOAuthClient {

    private static final String PROVIDER_GITHUB = "github";
    private static final String SCOPE_READ_USER = "read:user";
    private static final String GENERIC_FAILURE_MESSAGE = "GitHub OAuth를 완료할 수 없습니다.";

    private final GithubOAuthAppProperties properties;
    private final RestClient restClient;

    /**
     * GitHub OAuth 설정을 주입하고 provider 통신용 RestClient를 자체 구성한다.
     */
    public HttpGithubOAuthClient(GithubOAuthAppProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.restClient = RestClient.builder().build();
    }

    /**
     * GitHub OAuth authorization URL을 만들되 server session이나 cookie state를 만들지 않는다.
     */
    @Override
    public GithubAuthorizationStart startAuthorization(String state) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.authorizeUri())
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("scope", SCOPE_READ_USER);
        if (state != null && !state.isBlank()) {
            builder.queryParam("state", state.trim());
        }
        return new GithubAuthorizationStart(PROVIDER_GITHUB, builder.build().toUriString(), true);
    }

    /**
     * authorization code를 GitHub API로 검증하고 stable provider subject를 반환한다.
     */
    @Override
    public VerifiedGithubIdentity exchangeCode(String code) {
        try {
            GithubTokenResponse tokenResponse = requestToken(code);
            if (tokenResponse == null || tokenResponse.accessToken() == null || tokenResponse.accessToken().isBlank()) {
                throw new AccountAuthException("github_oauth_failed", GENERIC_FAILURE_MESSAGE);
            }
            GithubUserResponse userResponse = requestUser(tokenResponse.accessToken());
            if (userResponse == null || userResponse.id() == null) {
                throw new AccountAuthException("github_oauth_failed", GENERIC_FAILURE_MESSAGE);
            }
            return new VerifiedGithubIdentity(
                    userResponse.id().toString(),
                    userResponse.email(),
                    userResponse.login(),
                    userResponse.avatarUrl());
        } catch (RestClientException exception) {
            throw new AccountAuthException("github_oauth_failed", GENERIC_FAILURE_MESSAGE);
        }
    }

    private GithubTokenResponse requestToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("code", requireText(code, "code"));
        form.add("redirect_uri", properties.redirectUri());
        return restClient.post()
                .uri(properties.tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(form)
                .retrieve()
                .body(GithubTokenResponse.class);
    }

    private GithubUserResponse requestUser(String providerAccessToken) {
        return restClient.get()
                .uri(properties.userUri())
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(providerAccessToken))
                .retrieve()
                .body(GithubUserResponse.class);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new AccountAuthException("github_oauth_failed", GENERIC_FAILURE_MESSAGE);
        }
        return value.trim();
    }

    private record GithubTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            String scope
    ) {
    }

    private record GithubUserResponse(
            Long id,
            String login,
            String email,
            @JsonProperty("avatar_url") String avatarUrl
    ) {
    }
}
