package com.observation.portal.domain.account.dto;

import com.observation.portal.domain.account.model.AccountAuthResult;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 브라우저용 GitHub OAuth callback relay가 dashboard 메모리 상태로 넘기는 service access token 응답이다.
 *
 * <p>기본 dashboard 흐름은 refresh token을 브라우저에 전달하지 않는다. Token pair JSON이 필요한 도구는
 * 별도 API endpoint를 사용한다.</p>
 */
public record GithubCallbackSessionResponse(
        UUID accountId,
        String provider,
        String tokenType,
        String accessToken,
        OffsetDateTime accessTokenExpiresAt
) {

    /**
     * auth service result에서 dashboard가 resource API Bearer 인증에 사용할 access token 정보만 추출한다.
     */
    public static GithubCallbackSessionResponse from(AccountAuthResult result) {
        return new GithubCallbackSessionResponse(
                result.accountId(),
                result.provider(),
                result.tokens().tokenType(),
                result.tokens().accessToken(),
                result.tokens().accessTokenExpiresAt());
    }
}
