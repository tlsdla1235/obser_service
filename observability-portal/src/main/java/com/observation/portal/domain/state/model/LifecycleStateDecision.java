package com.observation.portal.domain.state.model;

import java.util.Objects;

/**
 * LifecycleStateService가 반환하는 two-axis state 결정 결과다.
 *
 * <p>metricState와 starterConnection을 분리해 dashboard/read-model이 freshness와 liveness를 혼동하지 않도록 한다.</p>
 */
public record LifecycleStateDecision(
        MetricLifecycleState metricState,
        StarterConnectionSummary starterConnection
) {

    /**
     * 두 축의 결과가 모두 존재하는지 검증한다.
     */
    public LifecycleStateDecision {
        Objects.requireNonNull(metricState, "metricState must not be null");
        Objects.requireNonNull(starterConnection, "starterConnection must not be null");
    }
}
