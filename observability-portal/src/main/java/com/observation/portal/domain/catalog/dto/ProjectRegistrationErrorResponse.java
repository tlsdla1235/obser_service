package com.observation.portal.domain.catalog.dto;

/**
 * registration 실패를 token/secret 없이 일반화해 반환하는 error response DTO다.
 */
public record ProjectRegistrationErrorResponse(String error, String message) {
}
