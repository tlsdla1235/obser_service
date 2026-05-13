package com.observation.starter.service;

import com.observation.starter.model.metric.DatasourcePoolMetricSample;
import com.observation.starter.model.metric.HttpServerObservationInput;
import com.observation.starter.model.metric.JvmMetricSample;

/**
 * Spring/Micrometer 바인딩이 로컬 관측 샘플을 starter 로직으로 전달할 때 사용하는 경계다.
 * 스토리 2.1은 이 경계에서 멈추며 포털 네트워크 호출, 제한 큐, 버킷 롤업은 구현하지 않는다.
 * 이후 스토리에서 이 입력을 라우트 가드, 롤업, 플러시 작업자와 연결한다.
 */
public interface ObservationSampleCollector {

    /**
     * HTTP 서버 요청의 메서드, 상태/오류 신호, 소요 시간 샘플을 기록한다.
     */
    void recordHttpServerObservation(HttpServerObservationInput input);

    /**
     * 애플리케이션 수준의 JVM CPU와 힙 샘플을 기록한다.
     */
    void recordJvmMetricSample(JvmMetricSample sample);

    /**
     * 애플리케이션 수준의 데이터소스 풀 사용률 샘플을 기록한다.
     */
    void recordDatasourcePoolMetricSample(DatasourcePoolMetricSample sample);
}
