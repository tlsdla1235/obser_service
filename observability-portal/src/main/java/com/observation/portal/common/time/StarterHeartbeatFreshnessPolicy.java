package com.observation.portal.common.time;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * starter heartbeat row의 보고 주기를 기준으로 snapshot 저장 대상 freshness를 판정하는 helper다.
 *
 * <p>heartbeat는 metric freshness를 계산하지 않으며, scheduled/query fallback snapshot 저장 가능 여부만 제한한다.</p>
 */
public final class StarterHeartbeatFreshnessPolicy {

    public static final Duration MINIMUM_RECENT_WINDOW = Duration.ofSeconds(90);
    private static final long INTERVAL_MULTIPLIER = 3L;

    private StarterHeartbeatFreshnessPolicy() {
    }

    /**
     * heartbeat row의 `interval_seconds`에서 최근 수신으로 인정할 최대 지연 시간을 계산한다.
     */
    public static Duration recentWindow(int intervalSeconds) {
        if (intervalSeconds <= 0) {
            throw new IllegalArgumentException("intervalSeconds must be positive");
        }
        Duration reportedWindow = Duration.ofSeconds(intervalSeconds * INTERVAL_MULTIPLIER);
        return reportedWindow.compareTo(MINIMUM_RECENT_WINDOW) > 0
                ? reportedWindow
                : MINIMUM_RECENT_WINDOW;
    }

    /**
     * 기준 시각 안에서 heartbeat row가 아직 최근 수신 상태인지 확인한다.
     */
    public static boolean isRecent(
            OffsetDateTime lastReceivedAtUtc,
            int intervalSeconds,
            OffsetDateTime referenceAtUtc) {
        OffsetDateTime lastReceived = Objects.requireNonNull(
                lastReceivedAtUtc,
                "lastReceivedAtUtc must not be null")
                .withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime referenceAt = Objects.requireNonNull(referenceAtUtc, "referenceAtUtc must not be null")
                .withOffsetSameInstant(ZoneOffset.UTC);
        return !lastReceived.isAfter(referenceAt)
                && !lastReceived.isBefore(referenceAt.minus(recentWindow(intervalSeconds)));
    }
}
