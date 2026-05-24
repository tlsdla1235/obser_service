package com.observation.starter.client;

import com.observation.starter.model.heartbeat.HeartbeatRequest;

/**
 * starter heartbeat 요청을 portal control-plane API로 전송하는 클라이언트 경계다.
 *
 * <p>metric bucket ingest client와 별도 타입으로 유지해 retry, timeout, 의미 해석이 섞이지 않게 한다.</p>
 */
@FunctionalInterface
public interface PortalHeartbeatClient {

    /**
     * heartbeat 요청 하나를 portal로 보낸다.
     *
     * <p>구현체 실패는 caller 쪽 background sender에서 fail-open으로 격리해야 한다.</p>
     */
    void send(HeartbeatRequest request);

    /**
     * 실제 전송 설정이 준비된 client인지 반환한다.
     */
    default boolean isEnabled() {
        return true;
    }
}
