package com.observation.portal.domain.state.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * starter connection/liveness axis를 판단하기 위한 heartbeat 기반 입력이다.
 *
 * <p>accepted bucket freshness, metric sample, degraded 판단 입력과 섞지 않고 별도 타입으로 전달한다.</p>
 */
public record StarterConnectionInput(
        String statusSource,
        Optional<Instant> lastHeartbeatAt,
        StarterHeartbeatStatus lastHeartbeatStatus,
        StarterConnectionFreshness freshness
) {

    public static final String STARTER_HEARTBEAT_SOURCE = "starter_heartbeat";

    /**
     * heartbeat adapter가 제공한 source, timestamp, status, freshness를 null 없이 보존한다.
     */
    public StarterConnectionInput {
        if (statusSource == null || statusSource.isBlank()) {
            throw new IllegalArgumentException("statusSource must not be blank");
        }
        lastHeartbeatAt = Objects.requireNonNull(lastHeartbeatAt, "lastHeartbeatAt must not be null");
        Objects.requireNonNull(lastHeartbeatStatus, "lastHeartbeatStatus must not be null");
        Objects.requireNonNull(freshness, "freshness must not be null");
    }

    /**
     * 최근 heartbeat가 수신된 starter connection 입력을 만든다.
     */
    public static StarterConnectionInput recentHeartbeat(Instant lastHeartbeatAt) {
        return new StarterConnectionInput(
                STARTER_HEARTBEAT_SOURCE,
                Optional.of(Objects.requireNonNull(lastHeartbeatAt, "lastHeartbeatAt must not be null")),
                StarterHeartbeatStatus.RECEIVED,
                StarterConnectionFreshness.RECENT);
    }

    /**
     * heartbeat row는 있으나 connection freshness가 오래된 입력을 만든다.
     */
    public static StarterConnectionInput staleHeartbeat(Instant lastHeartbeatAt) {
        return new StarterConnectionInput(
                STARTER_HEARTBEAT_SOURCE,
                Optional.of(Objects.requireNonNull(lastHeartbeatAt, "lastHeartbeatAt must not be null")),
                StarterHeartbeatStatus.RECEIVED,
                StarterConnectionFreshness.STALE);
    }

    /**
     * heartbeat telemetry를 아직 판단할 수 없는 입력을 만든다.
     */
    public static StarterConnectionInput unknown() {
        return new StarterConnectionInput(
                STARTER_HEARTBEAT_SOURCE,
                Optional.empty(),
                StarterHeartbeatStatus.UNKNOWN,
                StarterConnectionFreshness.UNKNOWN);
    }

    /**
     * heartbeat telemetry row가 없는 입력을 만든다.
     */
    public static StarterConnectionInput missing() {
        return new StarterConnectionInput(
                STARTER_HEARTBEAT_SOURCE,
                Optional.empty(),
                StarterHeartbeatStatus.MISSING,
                StarterConnectionFreshness.UNKNOWN);
    }
}
