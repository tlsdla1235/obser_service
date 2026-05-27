package com.observation.portal.domain.snapshot.service;

/**
 * Snapshot marker query token/limit이 Story 5.8-b의 supported 범위를 벗어났음을 표현한다.
 *
 * <p>controller는 이 예외를 400 Bad Request로 매핑하고, membership 실패나 empty marker list와 구분한다.</p>
 */
public class InvalidSnapshotMarkerQueryException extends IllegalArgumentException {

    public InvalidSnapshotMarkerQueryException(String message) {
        super(message);
    }
}
