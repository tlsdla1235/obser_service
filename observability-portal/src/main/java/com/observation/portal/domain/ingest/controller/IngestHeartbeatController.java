package com.observation.portal.domain.ingest.controller;

import com.observation.portal.domain.ingest.dto.IngestErrorResponse;
import com.observation.portal.domain.ingest.dto.IngestHeartbeatResponse;
import com.observation.portal.domain.ingest.model.IngestHeartbeatRequest;
import com.observation.portal.domain.ingest.service.IngestHeartbeatReceipt;
import com.observation.portal.domain.ingest.service.IngestHeartbeatResult;
import com.observation.portal.domain.ingest.service.IngestHeartbeatService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * starter heartbeat 요청을 bucket ingest와 분리된 HTTP boundary로 매핑하는 controller다.
 */
@RestController
@RequestMapping("/api/ingest/v1/heartbeat")
public class IngestHeartbeatController {

    private static final String PROJECT_KEY_HEADER = "X-OBS-Project-Key";

    private final IngestHeartbeatService heartbeatService;

    /**
     * heartbeat validation과 response 생성은 service에 위임한다.
     */
    public IngestHeartbeatController(IngestHeartbeatService heartbeatService) {
        this.heartbeatService = Objects.requireNonNull(heartbeatService, "heartbeatService must not be null");
    }

    /**
     * valid heartbeat는 200으로, project key 실패와 request shape 실패는 각각 401/400으로 응답한다.
     */
    @PostMapping
    public ResponseEntity<?> receiveHeartbeat(
            @RequestHeader(name = PROJECT_KEY_HEADER, required = false) String projectKeyHeader,
            @RequestBody(required = false) IngestHeartbeatRequest request) {
        IngestHeartbeatResult result = heartbeatService.receive(projectKeyHeader, request);
        if (result.isReceived()) {
            return ResponseEntity.ok(toResponse(result.receipt().orElseThrow()));
        }
        if (result.isInvalidRequest()) {
            return ResponseEntity.badRequest().body(IngestErrorResponse.invalidHeartbeatRequest(result.errors()));
        }
        if (result.isUnauthorized()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(IngestErrorResponse.unauthorized());
        }
        throw new IllegalStateException("Unsupported heartbeat status: " + result.status());
    }

    private static IngestHeartbeatResponse toResponse(IngestHeartbeatReceipt receipt) {
        return new IngestHeartbeatResponse(
                receipt.status(),
                receipt.projectId(),
                receipt.serverTimeUtc(),
                receipt.supportedIngestSchemaVersions(),
                receipt.metadataStatus(),
                receipt.heartbeatStatus(),
                new IngestHeartbeatResponse.IngestBoundary(
                        receipt.ingestBoundary().lastAcceptedBucketAt(),
                        receipt.ingestBoundary().statusSource()),
                receipt.message());
    }
}
