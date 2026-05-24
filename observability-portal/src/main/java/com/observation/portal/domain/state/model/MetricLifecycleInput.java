package com.observation.portal.domain.state.model;

import com.observation.portal.common.time.AcceptedBucketFreshness;

import java.util.Objects;
import java.util.Optional;

/**
 * application metric lifecycle state를 판단하기 위한 accepted bucket axis 입력이다.
 *
 * <p>heartbeat 정보는 의도적으로 포함하지 않는다. heartbeat는 {@link StarterConnectionInput}으로 별도 전달한다.</p>
 */
public record MetricLifecycleInput(
        AcceptedBucketFreshness freshness,
        MetricSampleReadiness sampleReadiness,
        MetricTrafficActivity trafficActivity,
        DegradedHysteresisInput degradedHysteresis,
        Optional<LifecycleStateCode> previousState
) {

    /**
     * service가 metric axis만 신뢰할 수 있도록 필수 typed input을 null 없이 고정한다.
     */
    public MetricLifecycleInput {
        Objects.requireNonNull(freshness, "freshness must not be null");
        Objects.requireNonNull(sampleReadiness, "sampleReadiness must not be null");
        Objects.requireNonNull(trafficActivity, "trafficActivity must not be null");
        Objects.requireNonNull(degradedHysteresis, "degradedHysteresis must not be null");
        previousState = Objects.requireNonNull(previousState, "previousState must not be null");
    }
}
