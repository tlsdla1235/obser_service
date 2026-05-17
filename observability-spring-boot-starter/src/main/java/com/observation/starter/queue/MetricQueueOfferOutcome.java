package com.observation.starter.queue;

/**
 * bounded metric queue enqueue 시도 결과를 표현한다.
 */
public enum MetricQueueOfferOutcome {

    /**
     * bucket이 drop 없이 queue에 들어갔다.
     */
    ENQUEUED,

    /**
     * queue가 가득 차 새 bucket이 버려졌다.
     */
    DROPPED_NEWEST,

    /**
     * queue가 가득 차 가장 오래된 bucket을 버리고 새 bucket이 들어갔다.
     */
    DROPPED_OLDEST_AND_ENQUEUED
}
