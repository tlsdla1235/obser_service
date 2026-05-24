package com.observation.portal.domain.ingest.model;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * starter heartbeat latest telemetry 조회 결과를 service 계층에서 쓰기 위한 immutable model이다.
 */
public record StarterHeartbeatTelemetryRecord(
        UUID id,
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
        String heartbeatStatus,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    /**
     * JPA entity를 외부 boundary로 직접 노출하지 않도록 latest telemetry 값을 복사한다.
     */
    public StarterHeartbeatTelemetryRecord {
        id = Objects.requireNonNull(id, "id must not be null");
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
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
