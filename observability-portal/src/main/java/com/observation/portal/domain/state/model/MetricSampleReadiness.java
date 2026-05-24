package com.observation.portal.domain.state.model;

/**
 * current freshness 이후 metric state를 판단할 수 있을 만큼 sample이 준비됐는지 나타낸다.
 */
public enum MetricSampleReadiness {
    INSUFFICIENT,
    SUFFICIENT
}
