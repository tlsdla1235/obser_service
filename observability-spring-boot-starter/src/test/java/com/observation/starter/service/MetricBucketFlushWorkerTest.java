package com.observation.starter.service;

import com.observation.starter.client.PortalMetricBucketClient;
import com.observation.starter.model.ingest.IngestEnvelopeCandidate;
import com.observation.starter.model.ingest.IngestEnvelopeIdentity;
import com.observation.starter.model.metric.AppMetricRollup;
import com.observation.starter.model.metric.ClosedMetricBucket;
import com.observation.starter.model.metric.HistogramBucket;
import com.observation.starter.model.time.MetricBucketInterval;
import com.observation.starter.queue.BoundedMetricQueue;
import com.observation.starter.queue.MetricQueueDropPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricBucketFlushWorkerTest {

    @Test
    void callsPortalClientFromBackgroundWorkerThread() throws Exception {
        BoundedMetricQueue queue = new BoundedMetricQueue(8, MetricQueueDropPolicy.DROP_NEWEST);
        CountDownLatch called = new CountDownLatch(1);
        AtomicReference<String> clientThreadName = new AtomicReference<>();
        PortalMetricBucketClient client = candidate -> {
            clientThreadName.set(Thread.currentThread().getName());
            called.countDown();
        };
        MetricBucketFlushWorker worker = new MetricBucketFlushWorker(
                queue,
                builder(),
                client,
                new MetricFlushRetryPolicy(1, Duration.ZERO),
                duration -> {
                },
                Duration.ofMillis(10));

        worker.start();
        try {
            String requestThreadName = Thread.currentThread().getName();
            queue.offer(bucket("2026-05-08T01:00:00Z"));

            assertTrue(called.await(1, TimeUnit.SECONDS));
            assertNotEquals(requestThreadName, clientThreadName.get());
            assertTrue(clientThreadName.get().contains("metric-flush-worker"));
        } finally {
            worker.close();
        }
    }

    @Test
    void retriesWithWorkerLocalBackoffBeforeSuccess() {
        AtomicInteger attempts = new AtomicInteger();
        List<Duration> backoffs = new ArrayList<>();
        PortalMetricBucketClient flakyClient = candidate -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("portal down");
            }
        };
        MetricBucketFlushWorker worker = new MetricBucketFlushWorker(
                new BoundedMetricQueue(8, MetricQueueDropPolicy.DROP_NEWEST),
                builder(),
                flakyClient,
                new MetricFlushRetryPolicy(3, Duration.ofMillis(25)),
                backoffs::add,
                Duration.ofMillis(10));

        assertTrue(worker.flushBucket(bucket("2026-05-08T01:00:00Z")));

        assertEquals(3, attempts.get());
        assertEquals(List.of(Duration.ofMillis(25), Duration.ofMillis(25)), backoffs);
    }

    @Test
    void exhaustsRetryWithoutPropagatingFailureToCaller() {
        AtomicInteger attempts = new AtomicInteger();
        PortalMetricBucketClient downClient = candidate -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("portal down");
        };
        MetricBucketFlushWorker worker = new MetricBucketFlushWorker(
                new BoundedMetricQueue(8, MetricQueueDropPolicy.DROP_NEWEST),
                builder(),
                downClient,
                new MetricFlushRetryPolicy(2, Duration.ofMillis(5)),
                duration -> {
                },
                Duration.ofMillis(10));

        assertDoesNotThrow(() -> worker.flushBucket(bucket("2026-05-08T01:00:00Z")));

        assertEquals(2, attempts.get());
    }

    @Test
    void requiresAtLeastOneMillisecondPollInterval() {
        BoundedMetricQueue queue = new BoundedMetricQueue(8, MetricQueueDropPolicy.DROP_NEWEST);
        PortalMetricBucketClient client = candidate -> {
        };

        assertThrows(IllegalArgumentException.class, () -> new MetricBucketFlushWorker(
                queue,
                builder(),
                client,
                new MetricFlushRetryPolicy(1, Duration.ZERO),
                duration -> {
                },
                Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new MetricBucketFlushWorker(
                queue,
                builder(),
                client,
                new MetricFlushRetryPolicy(1, Duration.ZERO),
                duration -> {
                },
                Duration.ofNanos(999_999)));
    }

    @Test
    void buildsEnvelopeCandidateBeforeCallingPortalClientBoundary() {
        AtomicReference<IngestEnvelopeCandidate> sentCandidate = new AtomicReference<>();
        MetricBucketFlushWorker worker = new MetricBucketFlushWorker(
                new BoundedMetricQueue(8, MetricQueueDropPolicy.DROP_NEWEST),
                builder(),
                sentCandidate::set,
                new MetricFlushRetryPolicy(1, Duration.ZERO),
                duration -> {
                },
                Duration.ofMillis(10));

        assertTrue(worker.flushBucket(bucket("2026-05-08T01:00:00Z")));

        IngestEnvelopeCandidate candidate = sentCandidate.get();
        assertEquals("project-123:orders-api:prod:instance-1:2026-05-08T01:00:00Z",
                candidate.idempotencyKey());
        assertEquals("1.0", candidate.payload().schemaVersion());
        assertEquals("orders-api", candidate.payload().application().name());
        assertEquals("2026-05-08T01:00:00Z", candidate.payload().bucket().startUtc());
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

    private static IngestEnvelopeBuilderService builder() {
        return new IngestEnvelopeBuilderService(new IngestEnvelopeIdentity(
                "project-123",
                "orders-api",
                "prod",
                "instance-1"));
    }
}
