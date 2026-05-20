package com.observation.portal.domain.ingest.service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * ingest acceptance service가 controller에 전달할 수 있는 validation/authorization 결과다.
 *
 * <p>401, 400, accepted path를 구분하되 raw project key나 raw route/query 후보는 결과 객체에 담지 않는다.</p>
 */
public final class IngestAcceptanceResult {

    private static final IngestAcceptanceResult UNAUTHORIZED =
            new IngestAcceptanceResult(Status.UNAUTHORIZED, null, List.of());

    private final Status status;
    private final ValidatedIngestCandidate acceptedCandidate;
    private final List<IngestValidationError> errors;

    private IngestAcceptanceResult(
            Status status,
            ValidatedIngestCandidate acceptedCandidate,
            List<IngestValidationError> errors) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.acceptedCandidate = acceptedCandidate;
        this.errors = List.copyOf(Objects.requireNonNull(errors, "errors must not be null"));
    }

    /**
     * project key 검증 실패를 세부 사유 없이 unauthorized로 닫는다.
     */
    public static IngestAcceptanceResult unauthorized() {
        return UNAUTHORIZED;
    }

    /**
     * payload/idempotency validation 실패 결과를 만든다.
     */
    public static IngestAcceptanceResult invalid(List<IngestValidationError> errors) {
        List<IngestValidationError> copiedErrors = List.copyOf(Objects.requireNonNull(errors, "errors must not be null"));
        if (copiedErrors.isEmpty()) {
            throw new IllegalArgumentException("errors must not be empty");
        }
        return new IngestAcceptanceResult(Status.INVALID_REQUEST, null, copiedErrors);
    }

    /**
     * project key와 envelope validation을 모두 통과한 결과를 만든다.
     */
    public static IngestAcceptanceResult accepted(ValidatedIngestCandidate candidate) {
        return new IngestAcceptanceResult(
                Status.ACCEPTED,
                Objects.requireNonNull(candidate, "candidate must not be null"),
                List.of());
    }

    /**
     * controller status mapping에 사용할 acceptance status를 반환한다.
     */
    public Status status() {
        return status;
    }

    /**
     * validation과 authorization을 모두 통과했는지 확인한다.
     */
    public boolean isAccepted() {
        return status == Status.ACCEPTED;
    }

    /**
     * payload/idempotency validation 실패인지 확인한다.
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
     * accepted path에서만 검증 완료 후보를 반환한다.
     */
    public Optional<ValidatedIngestCandidate> acceptedCandidate() {
        return Optional.ofNullable(acceptedCandidate);
    }

    /**
     * invalid request path에서 controller response로 변환 가능한 validation errors를 반환한다.
     */
    public List<IngestValidationError> errors() {
        return errors;
    }

    @Override
    public String toString() {
        return "IngestAcceptanceResult[status=%s, errorCount=%d]".formatted(status, errors.size());
    }

    /**
     * HTTP status mapping 전 service-level acceptance 상태다.
     */
    public enum Status {
        ACCEPTED,
        INVALID_REQUEST,
        UNAUTHORIZED
    }
}
