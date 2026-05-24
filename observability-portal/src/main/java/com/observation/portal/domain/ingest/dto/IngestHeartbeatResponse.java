package com.observation.portal.domain.ingest.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * starter heartbeat 수신 결과와 accepted bucket boundary를 분리해 반환하는 response DTO다.
 */
public record IngestHeartbeatResponse(
        String status,
        UUID projectId,
        OffsetDateTime serverTimeUtc,
        List<String> supportedIngestSchemaVersions,
        String metadataStatus,
        String heartbeatStatus,
        IngestBoundary ingestBoundary,
        String message) {

    /**
     * heartbeat response의 필수 상태값과 schema version 목록을 immutable하게 고정한다.
     */
    public IngestHeartbeatResponse {
        status = requireText(status, "status");
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(serverTimeUtc, "serverTimeUtc must not be null");
        supportedIngestSchemaVersions = List.copyOf(Objects.requireNonNull(
                supportedIngestSchemaVersions,
                "supportedIngestSchemaVersions must not be null"));
        metadataStatus = requireText(metadataStatus, "metadataStatus");
        heartbeatStatus = requireText(heartbeatStatus, "heartbeatStatus");
        Objects.requireNonNull(ingestBoundary, "ingestBoundary must not be null");
        message = requireText(message, "message");
    }

    /**
     * accepted bucket freshness source를 heartbeat 상태와 별도 namespace로 표현한다.
     */
    public record IngestBoundary(OffsetDateTime lastAcceptedBucketAt, String statusSource) {

        /**
         * statusSource는 accepted bucket 기준임을 명시한다.
         */
        public IngestBoundary {
            statusSource = requireText(statusSource, "statusSource");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
