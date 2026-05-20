package com.observation.portal.domain.ingest.service;

import java.util.Objects;

/**
 * ingest validation 실패를 raw payload 값 없이 표현하는 service error model이다.
 *
 * <p>field path와 안정적인 code만으로 controller가 400 응답을 만들 수 있게 하며, route/query/project key
 * 원문 같은 민감하거나 high-cardinality인 값은 보관하지 않는다.</p>
 */
public record IngestValidationError(String code, String field, String message) {

    /**
     * validation error의 필수 설명값을 정규화한다.
     */
    public IngestValidationError {
        code = requireText(code, "code");
        field = requireText(field, "field");
        message = requireText(message, "message");
    }

    /**
     * raw 입력값을 포함하지 않는 validation error를 만든다.
     */
    public static IngestValidationError of(String code, String field, String message) {
        return new IngestValidationError(code, field, message);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
