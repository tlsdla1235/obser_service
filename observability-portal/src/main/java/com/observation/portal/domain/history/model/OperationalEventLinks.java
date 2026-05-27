package com.observation.portal.domain.history.model;

/**
 * Operational event에서 관련 stored snapshot detail로 이동하기 위한 link block이다.
 */
public record OperationalEventLinks(String snapshot) {

    /**
     * snapshot detail link가 실제 API path로 채워졌는지 검증한다.
     */
    public OperationalEventLinks {
        snapshot = requireText(snapshot, "snapshot");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
