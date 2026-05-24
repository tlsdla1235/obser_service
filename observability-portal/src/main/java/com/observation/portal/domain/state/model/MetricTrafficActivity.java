package com.observation.portal.domain.state.model;

/**
 * accepted bucket이 current일 때 traffic 부족을 active/degraded 판단보다 먼저 표현하기 위한 입력 값이다.
 */
public enum MetricTrafficActivity {
    IDLE,
    ACTIVE
}
