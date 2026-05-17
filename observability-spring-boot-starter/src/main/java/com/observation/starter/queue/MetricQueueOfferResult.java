package com.observation.starter.queue;

/**
 * bounded metric queue에 bucket enqueue를 시도한 결과 snapshot이다.
 */
public record MetricQueueOfferResult(
        MetricQueueOfferOutcome outcome,
        int sizeAfterOffer,
        long droppedCount
) {

    /**
     * enqueue 결과의 필수 필드와 관측 count 제약을 검증한다.
     */
    public MetricQueueOfferResult {
        if (outcome == null) {
            throw new IllegalArgumentException("outcome must not be null");
        }
        if (sizeAfterOffer < 0) {
            throw new IllegalArgumentException("sizeAfterOffer must not be negative");
        }
        if (droppedCount < 0) {
            throw new IllegalArgumentException("droppedCount must not be negative");
        }
    }

    /**
     * 이번 시도에서 새 bucket이 queue에 들어갔는지 반환한다.
     */
    public boolean enqueued() {
        return outcome == MetricQueueOfferOutcome.ENQUEUED
                || outcome == MetricQueueOfferOutcome.DROPPED_OLDEST_AND_ENQUEUED;
    }
}
