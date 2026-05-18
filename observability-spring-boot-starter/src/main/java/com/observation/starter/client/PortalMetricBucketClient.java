package com.observation.starter.client;

import com.observation.starter.model.ingest.IngestEnvelopeCandidate;

/**
 * background worker가 ingest envelope 후보를 포털 수집 경계로 넘길 때 사용하는 클라이언트 경계다.
 *
 * <p>이 경계의 구현체는 {@code X-OBS-Project-Key}, {@code Idempotency-Key}, JSON body 전송을 담당할 수
 * 있지만, 포털 검증/저장/idempotency 판정 자체는 Epic 3 scope로 남긴다.</p>
 */
@FunctionalInterface
public interface PortalMetricBucketClient {

    /**
     * envelope 후보 하나를 포털 수집 경계로 전송한다.
     *
     * <p>구현체가 timeout, 연결 실패, 5xx 등으로 예외를 던지더라도 호출자는 background worker여야 하며,
     * host request path로 예외가 전파되면 안 된다.</p>
     */
    void flush(IngestEnvelopeCandidate candidate);
}
