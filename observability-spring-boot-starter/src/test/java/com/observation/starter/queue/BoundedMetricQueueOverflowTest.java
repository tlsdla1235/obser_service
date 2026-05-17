package com.observation.starter.queue;

import com.observation.starter.model.metric.AppMetricRollup;
import com.observation.starter.model.metric.ClosedMetricBucket;
import com.observation.starter.model.metric.HistogramBucket;
import com.observation.starter.model.time.MetricBucketInterval;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedMetricQueueOverflowTest {

    @Test
    void requiresFinitePositiveCapacity() {
        assertThrows(IllegalArgumentException.class,
                () -> new BoundedMetricQueue(0, MetricQueueDropPolicy.DROP_NEWEST));
    }

    @Test
    void dropsNewestWithoutBlockingOrThrowingWhenFull() {
        BoundedMetricQueue queue = new BoundedMetricQueue(1, MetricQueueDropPolicy.DROP_NEWEST);
        ClosedMetricBucket first = bucket("2026-05-08T01:00:00Z");
        ClosedMetricBucket second = bucket("2026-05-08T01:00:30Z");

        assertEquals(MetricQueueOfferOutcome.ENQUEUED, queue.offer(first).outcome());
        MetricQueueOfferResult result = queue.offer(second);

        assertEquals(MetricQueueOfferOutcome.DROPPED_NEWEST, result.outcome());
        assertEquals(1, queue.size());
        assertEquals(1, queue.droppedCount());
        assertEquals(first, queue.pollNow().orElseThrow());
    }

    @Test
    void dropsOldestAndEnqueuesNewestWhenConfigured() {
        BoundedMetricQueue queue = new BoundedMetricQueue(1, MetricQueueDropPolicy.DROP_OLDEST);
        ClosedMetricBucket first = bucket("2026-05-08T01:00:00Z");
        ClosedMetricBucket second = bucket("2026-05-08T01:00:30Z");

        queue.offer(first);
        MetricQueueOfferResult result = queue.offer(second);

        assertEquals(MetricQueueOfferOutcome.DROPPED_OLDEST_AND_ENQUEUED, result.outcome());
        assertEquals(1, queue.droppedCount());
        assertEquals(second, queue.pollNow().orElseThrow());
        assertTrue(queue.pollNow().isEmpty());
    }

    @Test
    void dropOldestOffersAreAtomicAcrossConcurrentRequestProducers() throws Exception {
        for (int iteration = 0; iteration < 200; iteration++) {
            BoundedMetricQueue queue = new BoundedMetricQueue(1, MetricQueueDropPolicy.DROP_OLDEST);
            queue.offer(bucket("2026-05-08T01:00:00Z"));
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService producers = Executors.newFixedThreadPool(2);

            try {
                Future<MetricQueueOfferOutcome> first = producers.submit(() -> offerAfterStart(
                        queue,
                        bucket("2026-05-08T01:00:30Z"),
                        ready,
                        start));
                Future<MetricQueueOfferOutcome> second = producers.submit(() -> offerAfterStart(
                        queue,
                        bucket("2026-05-08T01:01:00Z"),
                        ready,
                        start));

                assertTrue(ready.await(1, TimeUnit.SECONDS));
                start.countDown();

                assertEquals(MetricQueueOfferOutcome.DROPPED_OLDEST_AND_ENQUEUED,
                        first.get(1, TimeUnit.SECONDS));
                assertEquals(MetricQueueOfferOutcome.DROPPED_OLDEST_AND_ENQUEUED,
                        second.get(1, TimeUnit.SECONDS));
                assertEquals(2, queue.droppedCount());
                assertEquals(1, queue.size());
            } finally {
                producers.shutdownNow();
            }
        }
    }

    @Test
    void pollRejectsSubMillisecondTimeouts() {
        BoundedMetricQueue queue = new BoundedMetricQueue(1, MetricQueueDropPolicy.DROP_NEWEST);

        assertThrows(IllegalArgumentException.class, () -> queue.poll(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> queue.poll(Duration.ofNanos(999_999)));
    }

    private static MetricQueueOfferOutcome offerAfterStart(
            BoundedMetricQueue queue,
            ClosedMetricBucket bucket,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        assertTrue(start.await(1, TimeUnit.SECONDS));
        return queue.offer(bucket).outcome();
    }

    private static ClosedMetricBucket bucket(String startUtc) {
        Instant start = Instant.parse(startUtc);
        return new ClosedMetricBucket(
                new MetricBucketInterval(start, start.plusSeconds(30)),
                new AppMetricRollup(
                        1,
                        0,
                        List.of(new HistogramBucket(50, 1)),
                        Optional.empty(),
                        Optional.empty()),
                List.of());
    }
}
