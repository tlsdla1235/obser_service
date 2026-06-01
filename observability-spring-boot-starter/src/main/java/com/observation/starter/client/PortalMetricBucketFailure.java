package com.observation.starter.client;

/**
 * background flush worker가 HTTP 구현 세부 패키지를 알지 않고 bucket 전송 실패 category를 읽는 경계다.
 */
public interface PortalMetricBucketFailure {

    /**
     * raw project key 없이 WARN log와 retry 진단에 사용할 failure category 문자열을 반환한다.
     */
    String failureCategoryLogValue();
}
