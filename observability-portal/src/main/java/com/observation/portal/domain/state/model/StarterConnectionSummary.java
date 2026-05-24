package com.observation.portal.domain.state.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * starter heartbeat connection/liveness 축의 판단 결과를 metric state와 별도 field로 전달한다.
 */
public record StarterConnectionSummary(
        String statusSource,
        Optional<Instant> lastHeartbeatAt,
        StarterHeartbeatStatus lastHeartbeatStatus,
        StarterConnectionFreshness freshness,
        StarterConnectionMeaning meaning,
        StarterConnectionDiagnosis diagnosis,
        StarterStateImpact stateImpact,
        String label,
        String rationale,
        String recommendedAction
) {

    /**
     * read model adapter가 분리된 starter connection surface를 만들 수 있도록 필수 값을 검증한다.
     */
    public StarterConnectionSummary {
        if (statusSource == null || statusSource.isBlank()) {
            throw new IllegalArgumentException("statusSource must not be blank");
        }
        lastHeartbeatAt = Objects.requireNonNull(lastHeartbeatAt, "lastHeartbeatAt must not be null");
        Objects.requireNonNull(lastHeartbeatStatus, "lastHeartbeatStatus must not be null");
        Objects.requireNonNull(freshness, "freshness must not be null");
        Objects.requireNonNull(meaning, "meaning must not be null");
        Objects.requireNonNull(diagnosis, "diagnosis must not be null");
        Objects.requireNonNull(stateImpact, "stateImpact must not be null");
        Objects.requireNonNull(label, "label must not be null");
        Objects.requireNonNull(rationale, "rationale must not be null");
        Objects.requireNonNull(recommendedAction, "recommendedAction must not be null");
    }
}
