package com.observation.portal.domain.ingest.service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * heartbeat service가 controller에 전달하는 authorization/validation/received 결과다.
 */
public final class IngestHeartbeatResult {

    private static final IngestHeartbeatResult UNAUTHORIZED =
            new IngestHeartbeatResult(Status.UNAUTHORIZED, null, List.of());

    private final Status status;
    private final IngestHeartbeatReceipt receipt;
    private final List<IngestValidationError> errors;

    private IngestHeartbeatResult(
            Status status,
            IngestHeartbeatReceipt receipt,
            List<IngestValidationError> errors) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.receipt = receipt;
        this.errors = List.copyOf(Objects.requireNonNull(errors, "errors must not be null"));
    }

    /**
     * project key 검증 실패 결과를 만든다.
     */
    public static IngestHeartbeatResult unauthorized() {
        return UNAUTHORIZED;
    }

    /**
     * heartbeat request shape validation 실패 결과를 만든다.
     */
    public static IngestHeartbeatResult invalid(List<IngestValidationError> errors) {
        List<IngestValidationError> copiedErrors = List.copyOf(Objects.requireNonNull(errors, "errors must not be null"));
        if (copiedErrors.isEmpty()) {
            throw new IllegalArgumentException("errors must not be empty");
        }
        return new IngestHeartbeatResult(Status.INVALID_REQUEST, null, copiedErrors);
    }

    /**
     * heartbeat 수신 성공 결과를 만든다.
     */
    public static IngestHeartbeatResult received(IngestHeartbeatReceipt receipt) {
        return new IngestHeartbeatResult(
                Status.RECEIVED,
                Objects.requireNonNull(receipt, "receipt must not be null"),
                List.of());
    }

    /**
     * controller HTTP status mapping에 사용할 상태를 반환한다.
     */
    public Status status() {
        return status;
    }

    /**
     * heartbeat 수신 성공인지 확인한다.
     */
    public boolean isReceived() {
        return status == Status.RECEIVED;
    }

    /**
     * request validation 실패인지 확인한다.
     */
    public boolean isInvalidRequest() {
        return status == Status.INVALID_REQUEST;
    }

    /**
     * project key 검증 실패인지 확인한다.
     */
    public boolean isUnauthorized() {
        return status == Status.UNAUTHORIZED;
    }

    /**
     * 수신 성공 response를 반환한다.
     */
    public Optional<IngestHeartbeatReceipt> receipt() {
        return Optional.ofNullable(receipt);
    }

    /**
     * validation 실패 상세를 반환한다.
     */
    public List<IngestValidationError> errors() {
        return errors;
    }

    @Override
    public String toString() {
        return "IngestHeartbeatResult[status=%s, errorCount=%d]".formatted(status, errors.size());
    }

    /**
     * heartbeat 처리 결과 상태다.
     */
    public enum Status {
        RECEIVED,
        INVALID_REQUEST,
        UNAUTHORIZED
    }
}
