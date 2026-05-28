package com.observation.portal.domain.account.service;

/**
 * account auth boundary에서 provider payload나 secret을 노출하지 않는 safe exception이다.
 */
public class AccountAuthException extends RuntimeException {

    private final String errorCode;

    /**
     * 외부에 반환해도 되는 error code와 일반화된 message로 exception을 만든다.
     */
    public AccountAuthException(String errorCode, String message) {
        super(requireText(message, "message"));
        this.errorCode = requireText(errorCode, "errorCode");
    }

    /**
     * HTTP error response에 사용할 safe error code를 반환한다.
     */
    public String errorCode() {
        return errorCode;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
