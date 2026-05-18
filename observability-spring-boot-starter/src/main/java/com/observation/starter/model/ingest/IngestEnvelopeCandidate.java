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
        if (containsControlCharacter(idempotencyKey)) {
            throw new IllegalArgumentException("idempotencyKey must not contain control characters");
        }
    }

    private static boolean containsControlCharacter(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character <= 0x1F || character == 0x7F) {
                return true;
            }
        }
        return false;
    }
}
