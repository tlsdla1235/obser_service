package com.observation.portal.domain.ingest.model;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 검증 완료 heartbeat를 latest telemetry 저장소로 전달하는 내부 command다.
 *
 * <p>raw project key는 포함하지 않고, starter liveness 확인에 필요한 identity와 최신 수신 값만 담는다.</p>
 */
public record StarterHeartbeatTelemetryCommand(
        UUID projectId,
        String applicationName,
        String environment,
        String instanceName,
        String starterVersion,
        OffsetDateTime lastSentAtUtc,
        OffsetDateTime lastReceivedAtUtc,
        long lastSequence,
        int intervalSeconds,
        String metadataStatus,
        String heartbeatStatus) {

    /**
     * repository upsert에 필요한 필수 identity와 최신 heartbeat 값을 검증한다.
     */
    public StarterHeartbeatTelemetryCommand {
        projectId = Objects.requireNonNull(projectId, "projectId must not be null");
        applicationName = requireText(applicationName, "applicationName");
        environment = requireText(environment, "environment");
        instanceName = requireText(instanceName, "instanceName");
        starterVersion = requireText(starterVersion, "starterVersion");
        lastSentAtUtc = Objects.requireNonNull(lastSentAtUtc, "lastSentAtUtc must not be null");
        lastReceivedAtUtc = Objects.requireNonNull(lastReceivedAtUtc, "lastReceivedAtUtc must not be null");
        if (lastSequence < 0) {
            throw new IllegalArgumentException("lastSequence must not be negative");
        }
        if (intervalSeconds <= 0) {
            throw new IllegalArgumentException("intervalSeconds must be positive");
        }
        metadataStatus = requireText(metadataStatus, "metadataStatus");
        heartbeatStatus = requireText(heartbeatStatus, "heartbeatStatus");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
