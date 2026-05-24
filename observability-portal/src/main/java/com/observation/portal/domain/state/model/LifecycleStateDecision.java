package com.observation.portal.domain.state.model;

import java.util.Objects;
import java.util.Optional;

/**
 * LifecycleStateService가 반환하는 two-axis state 결정 결과다.
 *
 * <p>metricState와 starterConnection을 분리해 dashboard/read-model이 freshness와 liveness를 혼동하지 않도록 한다.</p>
 */
public record LifecycleStateDecision(
        MetricLifecycleState metricState,
        StarterConnectionSummary starterConnection,
        RecoveryGuidance recovery
) {

    /**
     * 두 축의 결과와 recovery guidance가 모두 존재하는지 검증한다.
     */
    public LifecycleStateDecision {
        Objects.requireNonNull(metricState, "metricState must not be null");
        Objects.requireNonNull(starterConnection, "starterConnection must not be null");
        Objects.requireNonNull(recovery, "recovery must not be null");
    }

    /**
     * recovery guidance가 필요 없는 기존 호출을 기본 non-recovery 결과로 연결한다.
     */
    public LifecycleStateDecision(
            MetricLifecycleState metricState,
            StarterConnectionSummary starterConnection) {
        this(metricState, starterConnection, RecoveryGuidance.notRecovering(Optional.empty()));
    }
}
