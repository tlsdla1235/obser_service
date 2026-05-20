package com.observation.portal.domain.ingest.dto;

import com.observation.portal.domain.bucket.model.AcceptedMetricBucketReceipt;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 새 metric bucket 수용 성공을 HTTP response body로 표현하는 DTO다.
 */
public record IngestAcceptedResponse(
        String status,
        boolean duplicate,
        UUID bucketId,
        OffsetDateTime acceptedAt
) {

    /**
     * first successful ingest 응답에 필요한 상태와 receipt 값을 보장한다.
     */
    public IngestAcceptedResponse {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status must not be blank");
        }
        Objects.requireNonNull(bucketId, "bucketId must not be null");
        Objects.requireNonNull(acceptedAt, "acceptedAt must not be null");
    }

    /**
     * 신규 저장 성공 응답을 만든다. MVP duplicate retry는 성공 응답이 아니라 conflict로 다룬다.
     */
    public static IngestAcceptedResponse created(AcceptedMetricBucketReceipt receipt) {
        AcceptedMetricBucketReceipt requiredReceipt = Objects.requireNonNull(receipt, "receipt must not be null");
        return new IngestAcceptedResponse("accepted", false, requiredReceipt.bucketId(), requiredReceipt.acceptedAt());
    }
}
