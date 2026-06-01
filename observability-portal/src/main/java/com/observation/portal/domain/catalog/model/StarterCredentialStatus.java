package com.observation.portal.domain.catalog.model;

import java.util.Locale;

/**
 * `projects` row에 저장되는 starter credential lifecycle 상태다.
 *
 * <p>project 표시 상태인 `projects.status`와 분리해, project는 남겨 두면서 ingest credential만 막을 수 있게 한다.</p>
 */
public enum StarterCredentialStatus {

    ACTIVE("active"),
    REVOKED("revoked");

    private final String databaseValue;

    StarterCredentialStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    /**
     * DB에 저장되는 소문자 상태 값을 반환한다.
     */
    public String databaseValue() {
        return databaseValue;
    }

    /**
     * repository가 읽은 credential 상태 문자열을 enum으로 변환한다.
     */
    public static StarterCredentialStatus fromDatabaseValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("starter credential status must not be blank");
        }
        String normalizedValue = value.trim().toLowerCase(Locale.ROOT);
        for (StarterCredentialStatus status : values()) {
            if (status.databaseValue.equals(normalizedValue)) {
                return status;
            }
        }
        throw new IllegalArgumentException("unknown starter credential status");
    }
}
