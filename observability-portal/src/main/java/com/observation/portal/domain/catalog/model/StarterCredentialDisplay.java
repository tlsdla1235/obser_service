package com.observation.portal.domain.catalog.model;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * create/rotate 성공 response에서만 외부로 전달할 starter credential 1회 표시 모델이다.
 */
public record StarterCredentialDisplay(
        String displayValue,
        String keyPrefix,
        boolean visibleOnce,
        OffsetDateTime issuedAt) {

    /**
     * 1회 표시 값과 non-secret metadata를 함께 보장한다.
     */
    public StarterCredentialDisplay {
        displayValue = requireText(displayValue, "displayValue");
        keyPrefix = requireText(keyPrefix, "keyPrefix");
        Objects.requireNonNull(issuedAt, "issuedAt must not be null");
    }

    /**
     * 1회 표시 원문이 record 기본 문자열화로 로그/테스트 실패 메시지에 남지 않게 한다.
     */
    @Override
    public String toString() {
        return "StarterCredentialDisplay[displayValue=<redacted>, keyPrefix=%s, visibleOnce=%s, issuedAt=%s]"
                .formatted(keyPrefix, visibleOnce, issuedAt);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
