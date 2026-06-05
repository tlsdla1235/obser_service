package com.observation.portal.domain.ingest.dto;

import com.observation.portal.domain.ingest.service.IngestValidationError;

import java.util.List;
import java.util.Objects;

/**
 * ingest API의 validation/authorization 실패를 표현하는 response DTO다.
 */
public record IngestErrorResponse(String error, String message, List<ValidationErrorResponse> validationErrors) {

    /**
     * response에 필요한 error code와 message를 보장하고 validation error 목록을 immutable하게 고정한다.
     */
    public IngestErrorResponse {
        if (error == null || error.isBlank()) {
            throw new IllegalArgumentException("error must not be blank");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        validationErrors = List.copyOf(Objects.requireNonNull(validationErrors, "validationErrors must not be null"));
    }

    /**
     * payload/idempotency validation 실패 응답을 만든다.
     */
    public static IngestErrorResponse invalidRequest(List<IngestValidationError> errors) {
        return new IngestErrorResponse(
                "invalid_request",
                "Ingest request did not satisfy the supported envelope contract.",
                errors.stream()
                        .map(ValidationErrorResponse::from)
                        .toList());
    }

    /**
     * heartbeat payload validation 실패 응답을 만든다.
     */
    public static IngestErrorResponse invalidHeartbeatRequest(List<IngestValidationError> errors) {
        return new IngestErrorResponse(
                "invalid_request",
                "Heartbeat request did not satisfy the supported contract.",
                errors.stream()
                        .map(ValidationErrorResponse::from)
                        .toList());
    }

    /**
     * project key 검증 실패 응답을 만든다.
     */
    public static IngestErrorResponse unauthorized() {
        return new IngestErrorResponse(
                "unauthorized",
                "Project key is missing or invalid.",
                List.of());
    }

    /**
     * MVP duplicate 정책상 같은 project/idempotency key 재사용을 conflict로 알린다.
     */
    public static IngestErrorResponse duplicateIdempotencyKey() {
        return new IngestErrorResponse(
                "duplicate_idempotency_key",
                "Idempotency-Key has already been accepted for this project.",
                List.of());
    }

    /**
     * queue message가 configured SQS size guard를 넘어 enqueue 전에 거절됐음을 알린다.
     */
    public static IngestErrorResponse payloadTooLarge() {
        return new IngestErrorResponse(
                "payload_too_large",
                "Ingest payload is too large to enqueue.",
                List.of());
    }

    /**
     * queue config, serialization, publisher failure를 raw infrastructure detail 없이 retry 가능한 실패로 알린다.
     */
    public static IngestErrorResponse ingestEnqueueUnavailable() {
        return new IngestErrorResponse(
                "ingest_enqueue_unavailable",
                "Ingest queue is temporarily unavailable.",
                List.of());
    }

    /**
     * service validation error를 API-safe field/code/message 모양으로 옮긴다.
     */
    public record ValidationErrorResponse(String code, String field, String message) {

        private static ValidationErrorResponse from(IngestValidationError error) {
            return new ValidationErrorResponse(error.code(), error.field(), error.message());
        }
    }
}
