package com.observation.portal.domain.state.model;

/**
 * starter connection 결과가 metric lifecycle state를 직접 바꾸는지 나타낸다.
 */
public enum StarterStateImpact {
    NONE("none");

    private final String code;

    StarterStateImpact(String code) {
        this.code = code;
    }

    /**
     * 외부 read model에 사용할 impact code를 반환한다.
     */
    public String code() {
        return code;
    }
}
