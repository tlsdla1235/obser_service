package com.observation.portal.common.time;

/**
 * 마지막 accepted bucket endUtc 기준 freshness 후보를 표현한다.
 *
 * <p>이 값은 Story 4.2의 lifecycle state 최종 판정이 아니라 freshness 입력 상태만 나타낸다.</p>
 */
public enum AcceptedBucketFreshnessStatus {
    WAITING_FIRST_DATA,
    CURRENT,
    STALE_CANDIDATE,
    DOWN_CANDIDATE
}
