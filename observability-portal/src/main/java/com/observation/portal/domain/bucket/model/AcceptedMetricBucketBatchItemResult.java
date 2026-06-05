package com.observation.portal.domain.bucket.model;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * batch writer가 단일 write command에 대해 내린 저장/중복/conflict/재시도 판단이다.
 *
 * <p>queue layer는 이 결과를 source message delete, application DLQ 전송, retry 유지 결정으로 변환한다.</p>
 */
public record AcceptedMetricBucketBatchItemResult(
        AcceptedMetricBucketWriteCommand command,
        AcceptedMetricBucketBatchItemStatus status,
        AcceptedMetricBucketReceipt receiptValue,
        AcceptedMetricBucketIdentity conflictIdentityValue,
        String failureCodeValue
) {

    /**
     * raw payload 없이 command identity와 저장 결과 판단만 보존한다.
     */
    public AcceptedMetricBucketBatchItemResult {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (status == AcceptedMetricBucketBatchItemStatus.INSERTED) {
            Objects.requireNonNull(receiptValue, "receipt must not be null for INSERTED");
        }
        if (status == AcceptedMetricBucketBatchItemStatus.IDEMPOTENCY_PAYLOAD_CONFLICT
                || status == AcceptedMetricBucketBatchItemStatus.INSTANCE_BUCKET_IDENTITY_CONFLICT) {
            Objects.requireNonNull(conflictIdentityValue, "conflictIdentity must not be null for conflict result");
        }
        if (status == AcceptedMetricBucketBatchItemStatus.TRANSIENT_FAILURE) {
            failureCodeValue = requireText(failureCodeValue, "failureCode");
        }
    }

    public static AcceptedMetricBucketBatchItemResult inserted(
            AcceptedMetricBucketWriteCommand command,
            UUID bucketId) {
        return inserted(command, new AcceptedMetricBucketReceipt(
                Objects.requireNonNull(bucketId, "bucketId must not be null"),
                command.acceptedAt()));
    }

    public static AcceptedMetricBucketBatchItemResult inserted(
            AcceptedMetricBucketWriteCommand command,
            AcceptedMetricBucketReceipt receipt) {
        return new AcceptedMetricBucketBatchItemResult(
                command,
                AcceptedMetricBucketBatchItemStatus.INSERTED,
                receipt,
                null,
                null);
    }

    public static AcceptedMetricBucketBatchItemResult duplicateNoop(AcceptedMetricBucketWriteCommand command) {
        return new AcceptedMetricBucketBatchItemResult(
                command,
                AcceptedMetricBucketBatchItemStatus.DUPLICATE_NOOP,
                null,
                null,
                null);
    }

    public static AcceptedMetricBucketBatchItemResult conflict(
            AcceptedMetricBucketWriteCommand command,
            String failureCode,
            AcceptedMetricBucketIdentity identity) {
        AcceptedMetricBucketBatchItemStatus status = switch (requireText(failureCode, "failureCode")) {
            case "idempotency_payload_conflict" ->
                    AcceptedMetricBucketBatchItemStatus.IDEMPOTENCY_PAYLOAD_CONFLICT;
            case "instance_bucket_identity_conflict" ->
                    AcceptedMetricBucketBatchItemStatus.INSTANCE_BUCKET_IDENTITY_CONFLICT;
            default -> throw new IllegalArgumentException("unsupported conflict failureCode");
        };
        return new AcceptedMetricBucketBatchItemResult(command, status, null, identity, failureCode);
    }

    public static AcceptedMetricBucketBatchItemResult transientFailure(
            AcceptedMetricBucketWriteCommand command,
            String failureCode) {
        return new AcceptedMetricBucketBatchItemResult(
                command,
                AcceptedMetricBucketBatchItemStatus.TRANSIENT_FAILURE,
                null,
                null,
                failureCode);
    }

    /**
     * insert 성공 시 생성된 bucket receipt를 반환한다.
     */
    public Optional<AcceptedMetricBucketReceipt> receipt() {
        return Optional.ofNullable(receiptValue);
    }

    /**
     * deterministic conflict 분류에 사용한 기존 저장 row identity를 반환한다.
     */
    public Optional<AcceptedMetricBucketIdentity> conflictIdentity() {
        return Optional.ofNullable(conflictIdentityValue);
    }

    /**
     * transient failure나 conflict diagnostic code를 반환한다.
     */
    public Optional<String> failureCode() {
        return Optional.ofNullable(failureCodeValue);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
