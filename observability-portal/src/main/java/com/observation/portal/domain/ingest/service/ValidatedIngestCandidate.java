package com.observation.portal.domain.ingest.service;

import com.observation.portal.domain.ingest.model.IngestEnvelopeRequest;

import java.util.Objects;

/**
 * Story 3.3 persistence가 소비할 수 있는 검증 완료 ingest 후보 모델이다.
 *
 * <p>project key 원문은 포함하지 않고, payload도 service validation을 통과한 normalized envelope만 보관한다.</p>
 */
public record ValidatedIngestCandidate(
        VerifiedProject verifiedProject,
        String idempotencyKey,
        IngestEnvelopeRequest payload
) {

    /**
     * 검증 완료 후보에 필요한 project context, idempotency key, payload를 보장한다.
     */
    public ValidatedIngestCandidate {
        verifiedProject = Objects.requireNonNull(verifiedProject, "verifiedProject must not be null");
        if (idempotencyKey == null || idempotencyKey.isBlank()
                || hasLeadingOrTrailingWhitespace(idempotencyKey)
                || containsControlCharacter(idempotencyKey)) {
            throw new IllegalArgumentException("idempotencyKey must be a validated raw header value");
        }
        payload = Objects.requireNonNull(payload, "payload must not be null");
    }

    @Override
    public String toString() {
        return "ValidatedIngestCandidate[projectId=%s, application=%s, environment=%s, instance=%s]"
                .formatted(
                        verifiedProject.projectId(),
                        payload.application().name(),
                        payload.application().environment(),
                        payload.application().instance());
    }

    private static boolean containsControlCharacter(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasLeadingOrTrailingWhitespace(String value) {
        return !value.isEmpty()
                && (isWhitespace(value.charAt(0)) || isWhitespace(value.charAt(value.length() - 1)));
    }

    private static boolean isWhitespace(char value) {
        return Character.isWhitespace(value) || Character.isSpaceChar(value);
    }
}
