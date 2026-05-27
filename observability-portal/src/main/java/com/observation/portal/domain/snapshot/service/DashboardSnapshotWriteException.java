package com.observation.portal.domain.snapshot.service;

/**
 * snapshot writer가 저장 실패를 structured log/metric으로 남긴 뒤 caller에 전달하는 runtime exception이다.
 */
public class DashboardSnapshotWriteException extends RuntimeException {

    private final String failureType;

    /**
     * low-cardinality failureType과 원인을 함께 보존한다.
     */
    public DashboardSnapshotWriteException(String message, String failureType, Throwable cause) {
        super(message, cause);
        this.failureType = failureType;
    }

    public String failureType() {
        return failureType;
    }
}
