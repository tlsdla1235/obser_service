package com.observation.portal.domain.catalog.model;

import java.util.Locale;

/**
 * `projects.status` 컬럼 값을 portal 내부에서 안전하게 다루기 위한 상태 enum이다.
 */
public enum ProjectStatus {

    ACTIVE("active"),
    DISABLED("disabled");

    private final String databaseValue;

    ProjectStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    /**
     * DB에 저장되는 소문자 상태 값을 반환한다.
     */
    public String databaseValue() {
        return databaseValue;
    }

    /**
     * repository가 읽은 DB 상태 문자열을 enum으로 변환한다.
     */
    public static ProjectStatus fromDatabaseValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("project status must not be blank");
        }
        String normalizedValue = value.trim().toLowerCase(Locale.ROOT);
        for (ProjectStatus status : values()) {
            if (status.databaseValue.equals(normalizedValue)) {
                return status;
            }
        }
        throw new IllegalArgumentException("unknown project status");
    }
}
