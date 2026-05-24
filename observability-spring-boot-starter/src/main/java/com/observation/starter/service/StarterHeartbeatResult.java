package com.observation.starter.service;

import com.observation.starter.client.HeartbeatFailureCategory;

import java.util.Objects;

/**
 * heartbeat 한 번의 전송 결과를 raw exception message 없이 sender에 전달하는 fail-open result다.
 */
public record StarterHeartbeatResult(
        boolean success,
        boolean attempted,
        HeartbeatFailureCategory failureCategory
) {

    /**
     * 성공, 비활성, 실패 result의 조합이 모호하지 않도록 검증한다.
     */
    public StarterHeartbeatResult {
        if (success && failureCategory != null) {
            throw new IllegalArgumentException("success result must not have a failure category");
        }
        if (!success && attempted) {
            Objects.requireNonNull(failureCategory, "failureCategory must not be null for failed attempts");
        }
    }

    /**
     * heartbeat 전송 성공 result를 만든다.
     */
    public static StarterHeartbeatResult sent() {
        return new StarterHeartbeatResult(true, true, null);
    }

    /**
     * 설정상 heartbeat 전송을 시도하지 않은 result를 만든다.
     */
    public static StarterHeartbeatResult disabled() {
        return new StarterHeartbeatResult(false, false, null);
    }

    /**
     * 전송 실패를 category만 남겨 sender가 안전하게 관측할 수 있게 한다.
     */
    public static StarterHeartbeatResult failure(HeartbeatFailureCategory failureCategory) {
        return new StarterHeartbeatResult(false, true, failureCategory);
    }

    /**
     * 실제 전송 시도가 실패했는지 반환한다.
     */
    public boolean failed() {
        return attempted && !success;
    }
}
