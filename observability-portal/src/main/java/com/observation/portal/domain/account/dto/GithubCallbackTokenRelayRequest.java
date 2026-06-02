package com.observation.portal.domain.account.dto;

/**
 * 브라우저 callback HTML이 1회용 relay 결과를 회수할 때 보내는 요청이다.
 *
 * <p>relay id는 service token이 아니며 URL, storage, cookie에 저장하지 않고 callback page script 안에서만 사용한다.</p>
 */
public record GithubCallbackTokenRelayRequest(String relayId) {
}
