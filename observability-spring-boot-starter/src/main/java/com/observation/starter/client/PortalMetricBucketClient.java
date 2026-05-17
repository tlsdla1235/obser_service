package com.observation.starter.client;

import com.observation.starter.model.metric.ClosedMetricBucket;

/**
 * background worker가 닫힌 metric bucket을 포털 수집 경계로 넘길 때 사용하는 클라이언트 경계다.
 *
 * <p>Story 2.4에서는 최종 ingest envelope JSON 직렬화와 idempotency header 생성을 확정하지 않는다.
 * 따라서 이 경계는 worker 분리, 실패 격리, retry/backoff 동작을 검증할 수 있는 최소 입력으로
 * {@link ClosedMetricBucket} snapshot만 받는다.</p>
 */
@FunctionalInterface
public interface PortalMetricBucketClient {

    /**
     * 닫힌 bucket 하나를 포털 수집 경계로 전송한다.
     *
     * <p>구현체가 timeout, 연결 실패, 5xx 등으로 예외를 던지더라도 호출자는 background worker여야 하며,
     * host request path로 예외가 전파되면 안 된다.</p>
     */
    void flush(ClosedMetricBucket bucket);
}
