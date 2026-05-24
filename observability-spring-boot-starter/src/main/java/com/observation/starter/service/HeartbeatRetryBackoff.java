package com.observation.starter.service;

import java.time.Duration;

/**
 * heartbeat 반복 시도 사이의 delay 실행 경계다.
 *
 * <p>heartbeat control-plane 전용 backoff로 유지해 bucket ingest retry/idempotency 의미와 섞이지 않게 한다.</p>
 */
@FunctionalInterface
public interface HeartbeatRetryBackoff {

    /**
     * 다음 heartbeat 시도 전까지 지정된 기간만큼 대기한다.
     */
    void sleep(Duration duration) throws InterruptedException;

    /**
     * 실제 sender thread에서 사용하는 기본 sleep 구현을 반환한다.
     */
    static HeartbeatRetryBackoff threadSleep() {
        return duration -> {
            if (!duration.isZero()) {
                Thread.sleep(duration.toMillis());
            }
        };
    }
}
