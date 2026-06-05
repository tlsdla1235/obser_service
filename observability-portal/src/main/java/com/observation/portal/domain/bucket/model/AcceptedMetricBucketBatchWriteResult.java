package com.observation.portal.domain.bucket.model;

import java.util.List;
import java.util.Objects;

/**
 * bounded batch writer 실행 결과와 bucket table에 사용한 statement 수 후보를 함께 담는다.
 *
 * <p>statement count는 benchmark evidence에서 worker MVP와 batch writer path를 분리해 기록하기 위한 내부 계측 값이다.</p>
 */
public record AcceptedMetricBucketBatchWriteResult(
        List<AcceptedMetricBucketBatchItemResult> items,
        int bucketStatementCount
) {

    /**
     * command별 결과 목록과 accepted bucket repository statement 수가 유효한지 검증한다.
     */
    public AcceptedMetricBucketBatchWriteResult {
        items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
        if (bucketStatementCount < 0) {
            throw new IllegalArgumentException("bucketStatementCount must not be negative");
        }
    }

    public static AcceptedMetricBucketBatchWriteResult empty() {
        return new AcceptedMetricBucketBatchWriteResult(List.of(), 0);
    }

    /**
     * 저장 성공 row 수를 반환한다.
     */
    public long insertedCount() {
        return count(AcceptedMetricBucketBatchItemStatus.INSERTED);
    }

    /**
     * same key/same hash no-op row 수를 반환한다.
     */
    public long duplicateNoopCount() {
        return count(AcceptedMetricBucketBatchItemStatus.DUPLICATE_NOOP);
    }

    /**
     * application DLQ 대상 deterministic conflict row 수를 반환한다.
     */
    public long conflictCount() {
        return items.stream()
                .filter(item -> item.status() == AcceptedMetricBucketBatchItemStatus.IDEMPOTENCY_PAYLOAD_CONFLICT
                        || item.status() == AcceptedMetricBucketBatchItemStatus.INSTANCE_BUCKET_IDENTITY_CONFLICT)
                .count();
    }

    private long count(AcceptedMetricBucketBatchItemStatus status) {
        return items.stream()
                .filter(item -> item.status() == status)
                .count();
    }
}
