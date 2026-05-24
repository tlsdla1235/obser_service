package com.observation.starter.client;

import com.observation.starter.model.heartbeat.HeartbeatRequest;

/**
 * heartbeat 연결 설정이 없을 때 startup을 막지 않기 위한 비활성 client다.
 */
public final class NoopPortalHeartbeatClient implements PortalHeartbeatClient {

    /**
     * 설정 누락 상태에서는 네트워크 호출을 하지 않는다.
     */
    @Override
    public void send(HeartbeatRequest request) {
    }

    /**
     * background sender가 thread를 만들 필요가 없다는 신호를 제공한다.
     */
    @Override
    public boolean isEnabled() {
        return false;
    }
}
