package com.observation.portal.domain.state.model;

/**
 * dashboard/read-model이 사용할 application metric lifecycle state 코드다.
 *
 * <p>코드는 accepted bucket metric data axis의 최종 판정을 표현하며 starter heartbeat 연결 상태를 대체하지 않는다.</p>
 */
public enum LifecycleStateCode {
    WAITING_FIRST_DATA("waiting_first_data"),
    UNKNOWN("unknown"),
    IDLE("idle"),
    ACTIVE("active"),
    STALE("stale"),
    DOWN("down"),
    DEGRADED("degraded");

    private final String code;

    LifecycleStateCode(String code) {
        this.code = code;
    }

    /**
     * 외부 read model에 그대로 실을 수 있는 snake_case 상태 코드를 반환한다.
     */
    public String code() {
        return code;
    }
}
