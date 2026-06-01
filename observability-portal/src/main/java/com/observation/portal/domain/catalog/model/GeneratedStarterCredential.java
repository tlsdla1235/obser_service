package com.observation.portal.domain.catalog.model;

import java.util.Objects;

/**
 * 새 starter credential 원문과 DB lookup에 저장할 prefix를 함께 전달하는 내부 모델이다.
 *
 * <p>원문은 create/rotate 성공 응답까지 전달하는 동안만 사용하고, persistence 모델에는 저장하지 않는다.</p>
 */
public record GeneratedStarterCredential(String displayValue, String keyPrefix) {

    /**
     * generator 결과가 비어 있지 않음을 보장한다.
     */
    public GeneratedStarterCredential {
        displayValue = requireText(displayValue, "displayValue");
        keyPrefix = requireText(keyPrefix, "keyPrefix");
    }

    /**
     * raw credential이 실패 메시지나 디버그 출력에 섞이지 않도록 원문 표시 값을 숨긴다.
     */
    @Override
    public String toString() {
        return "GeneratedStarterCredential[displayValue=<redacted>, keyPrefix=%s]".formatted(keyPrefix);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
