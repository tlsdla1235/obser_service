package com.observation.portal.domain.catalog.service;

/**
 * public project registration 실패를 sanitized error code로 표현하는 예외다.
 *
 * <p>예외 메시지는 raw credential, hash, token, provider payload를 포함하지 않는 고정 문구만 사용한다.</p>
 */
public final class ProjectRegistrationException extends RuntimeException {

    public enum Reason {
        INVALID_NAME,
        DUPLICATE_NAME
    }

    private final Reason reason;
    private final String errorCode;
    private final String responseMessage;

    private ProjectRegistrationException(Reason reason, String message, String errorCode, String responseMessage) {
        super(message);
        this.reason = reason;
        this.errorCode = errorCode;
        this.responseMessage = responseMessage;
    }

    /**
     * project name validation 실패를 나타낸다.
     */
    public static ProjectRegistrationException invalidName() {
        return new ProjectRegistrationException(
                Reason.INVALID_NAME,
                "Project name is invalid",
                "invalid_project_name",
                "Project name must use 3-120 lowercase letters, numbers, and hyphens.");
    }

    /**
     * normalized project name 중복을 나타낸다.
     */
    public static ProjectRegistrationException duplicateName() {
        return new ProjectRegistrationException(
                Reason.DUPLICATE_NAME,
                "Project name already exists",
                "duplicate_project_name",
                "Project name is already registered.");
    }

    /**
     * controller가 HTTP status를 고를 때 사용할 실패 분류를 반환한다.
     */
    public Reason reason() {
        return reason;
    }

    /**
     * client response에 사용할 sanitized error code를 반환한다.
     */
    public String errorCode() {
        return errorCode;
    }

    /**
     * client response에 사용할 sanitized message를 반환한다.
     */
    public String responseMessage() {
        return responseMessage;
    }
}
