package com.observation.portal.domain.ingest.queue;

import java.time.Duration;
import java.util.List;

/**
 * worker가 source queue에서 message를 받고 안전하게 delete/change visibility를 요청하는 경계다.
 */
public interface MetricIngestQueueConsumer {

    /**
     * 설정된 receive bounds 안에서 source message page를 읽는다. 실패는 sanitized exception으로 수렴한다.
     */
    List<MetricIngestReceivedMessage> receive();

    /**
     * 처리 완료가 확정된 source message만 receipt handle로 삭제한다.
     */
    void delete(MetricIngestReceivedMessage message);

    /**
     * 필요한 경우 source message visibility timeout을 조정한다. MVP 기본 흐름에서는 직접 호출하지 않는다.
     */
    void changeVisibility(MetricIngestReceivedMessage message, Duration visibilityTimeout);
}
