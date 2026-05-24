package com.observation.portal.domain.ingest.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * heartbeat service가 controller에 전달하는 내부 수신 결과 모델이다.
 */
public record IngestHeartbeatReceipt(
        String status,
        UUID projectId,
        OffsetDateTime serverTimeUtc,
        List<String> supportedIngestSchemaVersions,
        String metadataStatus,
        String heartbeatStatus,
        IngestBoundary ingestBoundary,
        String message) {

    /**
     * controller response 변환에 필요한 값을 immutable하게 고정한다.
     */
    public IngestHeartbeatReceipt {
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
     * accepted bucket 기준 boundary를 heartbeat status와 분리해 담는다.
     */
    public record IngestBoundary(OffsetDateTime lastAcceptedBucketAt, String statusSource) {

        /**
         * status source가 비어 있지 않게 보장한다.
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
