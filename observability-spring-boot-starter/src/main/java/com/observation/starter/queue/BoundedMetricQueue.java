package com.observation.starter.queue;

import com.observation.starter.model.metric.ClosedMetricBucket;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * request path와 background flush worker 사이에서 닫힌 metric bucket을 제한 용량으로 보관한다.
 *
 * <p>이 queue는 항상 finite capacity를 가진다. enqueue는 즉시 반환하며, 가득 찬 경우 설정된
 * {@link MetricQueueDropPolicy}에 따라 bucket을 버리고 host request path를 대기시키지 않는다.</p>
 */
public final class BoundedMetricQueue {

    private final BlockingQueue<ClosedMetricBucket> queue;
    private final MetricQueueDropPolicy dropPolicy;
    private final AtomicLong enqueuedCount = new AtomicLong();
    private final AtomicLong droppedCount = new AtomicLong();

    /**
     * 지정한 finite capacity와 overflow drop policy로 metric bucket queue를 만든다.
     */
    public BoundedMetricQueue(int capacity, MetricQueueDropPolicy dropPolicy) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.dropPolicy = Objects.requireNonNull(dropPolicy, "dropPolicy must not be null");
    }

    /**
     * 닫힌 bucket을 즉시 enqueue한다.
     *
     * <p>queue가 가득 차도 예외를 던지거나 빈자리를 기다리지 않는다. 반환값에는 실제 enqueue 여부와
     * drop 결과가 담기므로 운영 관측 지표로 연결할 수 있다.</p>
     */
    public MetricQueueOfferResult offer(ClosedMetricBucket bucket) {
        ClosedMetricBucket requiredBucket = Objects.requireNonNull(bucket, "bucket must not be null");
        if (queue.offer(requiredBucket)) {
            enqueuedCount.incrementAndGet();
            return new MetricQueueOfferResult(MetricQueueOfferOutcome.ENQUEUED, size(), droppedCount.get());
        }

        if (dropPolicy == MetricQueueDropPolicy.DROP_NEWEST) {
            droppedCount.incrementAndGet();
            return new MetricQueueOfferResult(MetricQueueOfferOutcome.DROPPED_NEWEST, size(), droppedCount.get());
        }

        ClosedMetricBucket dropped = queue.poll();
        if (dropped != null) {
            droppedCount.incrementAndGet();
        }
        if (queue.offer(requiredBucket)) {
            enqueuedCount.incrementAndGet();
            MetricQueueOfferOutcome outcome = dropped == null
                    ? MetricQueueOfferOutcome.ENQUEUED
                    : MetricQueueOfferOutcome.DROPPED_OLDEST_AND_ENQUEUED;
            return new MetricQueueOfferResult(outcome, size(), droppedCount.get());
        }

        droppedCount.incrementAndGet();
        return new MetricQueueOfferResult(MetricQueueOfferOutcome.DROPPED_NEWEST, size(), droppedCount.get());
    }

    /**
     * worker가 다음 bucket을 기다리되, 지정된 timeout까지만 대기한다.
     */
    public Optional<ClosedMetricBucket> poll(Duration timeout) throws InterruptedException {
        Duration requiredTimeout = Objects.requireNonNull(timeout, "timeout must not be null");
        if (requiredTimeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }
        return Optional.ofNullable(queue.poll(requiredTimeout.toMillis(), TimeUnit.MILLISECONDS));
    }

    /**
     * 테스트와 drain loop에서 현재 준비된 bucket을 대기 없이 하나 꺼낸다.
     */
    public Optional<ClosedMetricBucket> pollNow() {
        return Optional.ofNullable(queue.poll());
    }

    /**
     * configured finite capacity를 반환한다.
     */
    public int capacity() {
        return queue.remainingCapacity() + queue.size();
    }

    /**
     * 현재 queue에 보관 중인 bucket 수를 반환한다.
     */
    public int size() {
        return queue.size();
    }

    /**
     * 성공적으로 enqueue된 bucket 수를 반환한다.
     */
    public long enqueuedCount() {
        return enqueuedCount.get();
    }

    /**
     * overflow policy에 의해 버려진 bucket 수를 반환한다.
     */
    public long droppedCount() {
        return droppedCount.get();
    }
}
