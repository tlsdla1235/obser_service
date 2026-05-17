package com.observation.starter.service;

import java.time.Duration;

/**
 * flush worker retry 사이의 backoff 실행 경계다.
 *
 * <p>프로덕션 기본값은 thread sleep이고, 테스트에서는 no-op 또는 기록용 구현을 주입해
 * retry/backoff가 request path가 아니라 worker-local 동작임을 검증한다.</p>
 */
@FunctionalInterface
public interface MetricFlushBackoff {

    /**
     * 다음 retry 전까지 지정된 기간만큼 대기한다.
     */
    void sleep(Duration duration) throws InterruptedException;

    /**
     * 실제 worker thread에서 사용하는 기본 backoff 구현을 반환한다.
     */
    static MetricFlushBackoff threadSleep() {
        return duration -> {
            if (!duration.isZero()) {
                Thread.sleep(duration.toMillis());
            }
        };
    }
}
