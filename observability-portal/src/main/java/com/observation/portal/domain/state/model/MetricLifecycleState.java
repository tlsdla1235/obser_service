package com.observation.portal.domain.state.model;

import java.util.Objects;

/**
 * accepted bucket metric axis에서 결정된 application lifecycle state와 설명 후보를 담는다.
 */
public record MetricLifecycleState(
        LifecycleStateCode code,
        String label,
        String rationale,
        String recommendedAction
) {

    /**
     * 후속 read model이 그대로 사용할 수 있도록 copy 후보를 null 없이 보존한다.
     */
    public MetricLifecycleState {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(label, "label must not be null");
        Objects.requireNonNull(rationale, "rationale must not be null");
        Objects.requireNonNull(recommendedAction, "recommendedAction must not be null");
    }
}
