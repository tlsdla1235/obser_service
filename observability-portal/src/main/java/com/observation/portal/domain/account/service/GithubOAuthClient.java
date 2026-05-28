package com.observation.portal.domain.account.service;

import com.observation.portal.domain.account.model.GithubAuthorizationStart;
import com.observation.portal.domain.account.model.VerifiedGithubIdentity;

/**
 * GitHub OAuth App과 통신하는 client boundary다.
 *
 * <p>provider token은 verified identity를 얻는 데만 사용하고 service token model로 저장하지 않는다.</p>
 */
public interface GithubOAuthClient {

    /**
     * 브라우저가 이동할 GitHub OAuth authorization URL을 만든다.
     */
    GithubAuthorizationStart startAuthorization(String state);

    /**
     * authorization code를 GitHub provider token으로 교환한 뒤 stable provider subject를 조회한다.
     */
    VerifiedGithubIdentity exchangeCode(String code);
}
