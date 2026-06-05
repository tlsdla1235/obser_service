package com.observation.portal.domain.ingest.service;

import com.observation.portal.domain.bucket.model.AcceptedMetricBucketReceipt;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * ingest acceptance service가 controller에 전달할 수 있는 validation/authorization 결과다.
 *
 * <p>401, 400, 409 중복, accepted path를 구분하되 raw project key나 raw route/query 후보는 결과 객체에 담지 않는다.</p>
 */
public final class IngestAcceptanceResult {

    private static final IngestAcceptanceResult UNAUTHORIZED =
            new IngestAcceptanceResult(Status.UNAUTHORIZED, null, null, null, List.of());
    private static final IngestAcceptanceResult DUPLICATE_IDEMPOTENCY_KEY =
            new IngestAcceptanceResult(Status.DUPLICATE_IDEMPOTENCY_KEY, null, null, null, List.of());
    private static final IngestAcceptanceResult PAYLOAD_TOO_LARGE =
            new IngestAcceptanceResult(Status.PAYLOAD_TOO_LARGE, null, null, null, List.of());
    private static final IngestAcceptanceResult INGEST_ENQUEUE_UNAVAILABLE =
            new IngestAcceptanceResult(Status.INGEST_ENQUEUE_UNAVAILABLE, null, null, null, List.of());

    private final Status status;
    private final ValidatedIngestCandidate acceptedCandidate;
    private final AcceptedMetricBucketReceipt acceptedReceipt;
    private final IngestQueuedResult queuedResult;
    private final List<IngestValidationError> errors;

    private IngestAcceptanceResult(
            Status status,
            ValidatedIngestCandidate acceptedCandidate,
            AcceptedMetricBucketReceipt acceptedReceipt,
            IngestQueuedResult queuedResult,
            List<IngestValidationError> errors) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.acceptedCandidate = acceptedCandidate;
        this.acceptedReceipt = acceptedReceipt;
        this.queuedResult = queuedResult;
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
        return new IngestAcceptanceResult(Status.INVALID_REQUEST, null, null, null, copiedErrors);
    }

    /**
     * MVP duplicate 정책에 따라 같은 project/idempotency key가 이미 있음을 conflict로 닫는다.
     */
    public static IngestAcceptanceResult duplicateIdempotencyKey() {
        return DUPLICATE_IDEMPOTENCY_KEY;
    }

    /**
     * project key와 envelope validation을 통과하고 bucket 저장까지 끝난 결과를 만든다.
     */
    public static IngestAcceptanceResult accepted(
            ValidatedIngestCandidate candidate,
            AcceptedMetricBucketReceipt receipt) {
        return new IngestAcceptanceResult(
                Status.ACCEPTED,
                Objects.requireNonNull(candidate, "candidate must not be null"),
                Objects.requireNonNull(receipt, "receipt must not be null"),
                null,
                List.of());
    }

    /**
     * queue enqueue가 성공한 뒤 controller가 `202 queued`로 매핑할 결과를 만든다.
     */
    public static IngestAcceptanceResult queued(IngestQueuedResult queuedResult) {
        return new IngestAcceptanceResult(
                Status.QUEUED,
                null,
                null,
                Objects.requireNonNull(queuedResult, "queuedResult must not be null"),
                List.of());
    }

    /**
     * queue message size limit 초과 결과를 만든다.
     */
    public static IngestAcceptanceResult payloadTooLarge() {
        return PAYLOAD_TOO_LARGE;
    }

    /**
     * queue config, message build, publisher 실패 결과를 만든다.
     */
    public static IngestAcceptanceResult ingestEnqueueUnavailable() {
        return INGEST_ENQUEUE_UNAVAILABLE;
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
     * enqueue 성공 후 public `202 queued` 응답으로 매핑할 수 있는지 확인한다.
     */
    public boolean isQueued() {
        return status == Status.QUEUED;
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
     * 같은 project/idempotency key가 이미 수용된 경우인지 확인한다.
     */
    public boolean isDuplicateIdempotencyKey() {
        return status == Status.DUPLICATE_IDEMPOTENCY_KEY;
    }

    /**
     * serialized queue body와 attributes가 configured size limit을 넘었는지 확인한다.
     */
    public boolean isPayloadTooLarge() {
        return status == Status.PAYLOAD_TOO_LARGE;
    }

    /**
     * queue config/message build/publisher failure로 retry 가능한 503인지 확인한다.
     */
    public boolean isIngestEnqueueUnavailable() {
        return status == Status.INGEST_ENQUEUE_UNAVAILABLE;
    }

    /**
     * accepted path에서만 검증 완료 후보를 반환한다.
     */
    public Optional<ValidatedIngestCandidate> acceptedCandidate() {
        return Optional.ofNullable(acceptedCandidate);
    }

    /**
     * accepted path에서 controller response로 매핑할 bucket 저장 receipt를 반환한다.
     */
    public Optional<AcceptedMetricBucketReceipt> acceptedReceipt() {
        return Optional.ofNullable(acceptedReceipt);
    }

    /**
     * queued path에서 public response로 매핑할 enqueue metadata를 반환한다.
     */
    public Optional<IngestQueuedResult> queuedResult() {
        return Optional.ofNullable(queuedResult);
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
        QUEUED,
        INVALID_REQUEST,
        UNAUTHORIZED,
        DUPLICATE_IDEMPOTENCY_KEY,
        PAYLOAD_TOO_LARGE,
        INGEST_ENQUEUE_UNAVAILABLE
    }
}
