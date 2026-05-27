package com.observation.portal.domain.history.service;

/**
 * Operational event history query parameter가 blank/malformed/non-positive일 때 controller가 400으로 매핑하는 예외다.
 */
public class InvalidOperationalEventHistoryQueryException extends RuntimeException {

    public InvalidOperationalEventHistoryQueryException(String message) {
        super(message);
    }
}
