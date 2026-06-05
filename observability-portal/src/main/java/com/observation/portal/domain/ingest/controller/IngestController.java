package com.observation.portal.domain.ingest.controller;

import com.observation.portal.domain.bucket.model.AcceptedMetricBucketReceipt;
import com.observation.portal.domain.ingest.dto.IngestAcceptedResponse;
import com.observation.portal.domain.ingest.dto.IngestErrorResponse;
import com.observation.portal.domain.ingest.dto.IngestQueuedResponse;
import com.observation.portal.domain.ingest.model.IngestEnvelopeRequest;
import com.observation.portal.domain.ingest.service.IngestAcceptanceResult;
import com.observation.portal.domain.ingest.service.IngestAcceptanceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Objects;

/**
 * starter가 전송한 metric bucket ingest 요청을 HTTP status와 response DTO로 매핑하는 controller다.
 *
 * <p>검증과 저장 orchestration은 service에 위임하고, repository나 JPA entity에는 직접 접근하지 않는다.</p>
 */
@RestController
@RequestMapping("/api/ingest/v1/buckets")
public class IngestController {
    // ingest 요청이 어느 프로젝트의 요청인지 확인하고, 허가된 project key인지 검증한다.
    private static final String PROJECT_KEY_HEADER = "X-OBS-Project-Key";
    // 멱등성 키.
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final IngestAcceptanceService ingestAcceptanceService;

    /**
     * ingest request 처리를 위임할 service를 주입한다.
     */
    public IngestController(IngestAcceptanceService ingestAcceptanceService) {
        this.ingestAcceptanceService = Objects.requireNonNull(
                ingestAcceptanceService,
                "ingestAcceptanceService must not be null");
    }

    /**
     * mode별 service 결과를 기존 direct `201 Created` 또는 queue `202 Accepted` 계약으로 응답한다.
     */
    @PostMapping
    public ResponseEntity<?> acceptBucket(
            @RequestHeader(name = PROJECT_KEY_HEADER, required = false) String projectKeyHeader,
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKeyHeader,
            @RequestBody(required = false) IngestEnvelopeRequest request) {
        IngestAcceptanceResult result = ingestAcceptanceService.accept(
                projectKeyHeader,
                idempotencyKeyHeader,
                request);

        if (result.isAccepted()) {
            AcceptedMetricBucketReceipt receipt = result.acceptedReceipt().orElseThrow();
            return ResponseEntity.created(URI.create("/api/ingest/v1/buckets/" + receipt.bucketId()))
                    .body(IngestAcceptedResponse.created(receipt));
        }
        if (result.isQueued()) {
            return ResponseEntity.accepted().body(IngestQueuedResponse.queued(result.queuedResult().orElseThrow()));
        }
        if (result.isInvalidRequest()) {
            return ResponseEntity.badRequest().body(IngestErrorResponse.invalidRequest(result.errors()));
        }
        if (result.isUnauthorized()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(IngestErrorResponse.unauthorized());
        }
        if (result.isDuplicateIdempotencyKey()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(IngestErrorResponse.duplicateIdempotencyKey());
        }
        if (result.isPayloadTooLarge()) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(IngestErrorResponse.payloadTooLarge());
        }
        if (result.isIngestEnqueueUnavailable()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(IngestErrorResponse.ingestEnqueueUnavailable());
        }
        throw new IllegalStateException("Unsupported ingest acceptance status: " + result.status());
    }
}
