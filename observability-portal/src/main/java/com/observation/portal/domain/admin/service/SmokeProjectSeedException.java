package com.observation.portal.domain.admin.service;

/**
 * local smoke seed가 row를 쓰면 안 되는 상태를 발견했을 때 사용하는 예외다.
 *
 * <p>메시지는 설정 key와 일반화된 원인만 담고, access token, refresh token, GitHub provider token,
 * raw project key 값은 포함하지 않는다.</p>
 */
public class SmokeProjectSeedException extends RuntimeException {

    /**
     * operator가 고칠 수 있는 일반화된 실패 원인을 담아 예외를 만든다.
     */
    public SmokeProjectSeedException(String message) {
        super(message);
    }
}
