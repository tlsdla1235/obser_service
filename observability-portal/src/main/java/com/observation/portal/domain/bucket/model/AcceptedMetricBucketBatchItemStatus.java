package com.observation.portal.domain.bucket.model;

/**
 * batch writer가 command별 accepted bucket 저장 결과를 queue processor에 알려주는 상태 값이다.
 */
public enum AcceptedMetricBucketBatchItemStatus {
    INSERTED,
    DUPLICATE_NOOP,
    IDEMPOTENCY_PAYLOAD_CONFLICT,
    INSTANCE_BUCKET_IDENTITY_CONFLICT,
    TRANSIENT_FAILURE
}
