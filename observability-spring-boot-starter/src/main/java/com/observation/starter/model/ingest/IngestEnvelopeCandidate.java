package com.observation.starter.model.ingest;

import java.util.Objects;

/**
 * flush client가 사용할 deterministic envelope payload와 Idempotency-Key header 후보를 함께 담는다.
 */
public record IngestEnvelopeCandidate(
        IngestEnvelope payload,
        String idempotencyKey
) {

    /**
     * payload와 header value가 전송 전에 모두 준비됐는지 검증한다.
     */
    public IngestEnvelopeCandidate {
        payload = Objects.requireNonNull(payload, "payload must not be null");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        idempotencyKey = idempotencyKey.trim();
    }
}
