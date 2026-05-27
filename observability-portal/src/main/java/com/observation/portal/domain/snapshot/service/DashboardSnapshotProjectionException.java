package com.observation.portal.domain.snapshot.service;

/**
 * Stored snapshot row는 찾았지만 `read_model_json` root projection에 실패했을 때 사용하는 예외다.
 *
 * <p>controller는 generic 500으로 매핑하며, current dashboard fallback이나 query fallback capture를 시도하지 않는다.</p>
 */
public class DashboardSnapshotProjectionException extends RuntimeException {

    public DashboardSnapshotProjectionException(String message, Throwable cause) {
        super(message, cause);
    }

    public DashboardSnapshotProjectionException(String message) {
        super(message);
    }
}
