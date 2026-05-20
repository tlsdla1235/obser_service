package com.observation.portal.domain.bucket.model;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * accepted bucket 저장 후 service/controller가 사용할 수 있는 persistence receipt다.
 *
 * <p>JPA entity를 외부 결과로 노출하지 않기 위해 bucket id와 수용 시각만 담는다.</p>
 */
public record AcceptedMetricBucketReceipt(UUID bucketId, OffsetDateTime acceptedAt) {

    /**
     * 저장된 bucket 식별자와 수용 시각이 항상 존재하도록 보장한다.
     */
    public AcceptedMetricBucketReceipt {
        Objects.requireNonNull(bucketId, "bucketId must not be null");
        Objects.requireNonNull(acceptedAt, "acceptedAt must not be null");
    }
}
