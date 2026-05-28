package com.observation.portal.domain.account.dto;

import com.observation.portal.domain.account.model.GithubAuthorizationStart;

/**
 * GitHub OAuth App authorization 시작 정보를 JSON으로 반환하는 DTO다.
 */
public record GithubAuthorizeResponse(
        String provider,
        String authorizationUrl,
        boolean stateRequired
) {

    /**
     * service result를 HTTP response body shape로 변환한다.
     */
    public static GithubAuthorizeResponse from(GithubAuthorizationStart start) {
        return new GithubAuthorizeResponse(start.provider(), start.authorizationUrl(), start.stateRequired());
    }
}
