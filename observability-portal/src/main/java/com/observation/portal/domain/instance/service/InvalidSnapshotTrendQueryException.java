package com.observation.portal.domain.instance.service;

/**
 * Instance Snapshot Trend API의 `since` 또는 `limit` query parameter가 Story 5.7 bounded query 계약을 벗어났음을
 * 나타내는 service-level 예외다.
 *
 * <p>controller는 이 예외를 400 Bad Request로 매핑하고, account-project authorization 실패와 snapshot absence는
 * 별도 경로로 처리한다.</p>
 */
public class InvalidSnapshotTrendQueryException extends IllegalArgumentException {

    /**
     * client query validation 실패 사유를 담아 예외를 만든다.
     */
    public InvalidSnapshotTrendQueryException(String message) {
        super(message);
    }
}
