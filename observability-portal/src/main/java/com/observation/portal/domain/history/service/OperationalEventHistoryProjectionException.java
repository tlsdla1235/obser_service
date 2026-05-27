package com.observation.portal.domain.history.service;

/**
 * Stored snapshot row 조회나 5.9-a projection skeleton 처리 중 실패했을 때 generic 500으로 매핑하는 예외다.
 */
public class OperationalEventHistoryProjectionException extends RuntimeException {

    public OperationalEventHistoryProjectionException(String message) {
        super(message);
    }

    public OperationalEventHistoryProjectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
