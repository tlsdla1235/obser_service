package com.observation.portal.domain.state.model;

/**
 * starter heartbeat adapter가 이미 판단한 connection freshness다.
 *
 * <p>정확한 recency 정책은 후속 read-model adapter가 정하고, lifecycle service는 typed 결과만 소비한다.</p>
 */
public enum StarterConnectionFreshness {
    RECENT,
    STALE,
    UNKNOWN
}
