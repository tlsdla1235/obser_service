package com.observation.starter.service;

import com.observation.starter.client.PortalMetricBucketClient;
import com.observation.starter.model.ingest.IngestEnvelopeIdentity;
import com.observation.starter.model.metric.ClosedMetricBucket;
import com.observation.starter.model.metric.HttpServerObservationInput;
import com.observation.starter.queue.BoundedMetricQueue;
import com.observation.starter.queue.MetricQueueDropPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StarterNonBlockingIngestTest {

    @Test
    void requestPathReturnsBeforeTimeoutLikePortalClientCompletes() throws Exception {
        BoundedMetricQueue queue = new BoundedMetricQueue(8, MetricQueueDropPolicy.DROP_NEWEST);
        CountDownLatch clientEntered = new CountDownLatch(1);
        CountDownLatch releaseClient = new CountDownLatch(1);
        AtomicReference<String> requestThreadName = new AtomicReference<>();
        AtomicReference<String> clientThreadName = new AtomicReference<>();
        PortalMetricBucketClient timeoutLikeClient = candidate -> {
            clientThreadName.set(Thread.currentThread().getName());
            clientEntered.countDown();
            try {
                releaseClient.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        };
        MetricBucketFlushWorker worker = new MetricBucketFlushWorker(
                queue,
                builder(),
                timeoutLikeClient,
                new MetricFlushRetryPolicy(1, Duration.ZERO),
                duration -> {
                },
                Duration.ofMillis(10));
        StarterMetricIngestService ingestService = new StarterMetricIngestService(
                new LowCardinalityHttpObservationGuard(),
                new MetricBucketRollupService(),
                queue,
                () -> Instant.parse("2026-05-08T01:01:00Z"));
        ExecutorService requestExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "host-request-thread");
            thread.setDaemon(true);
            return thread;
        });

        worker.start();
        try {
            Future<?> requestFuture = requestExecutor.submit(() -> {
                requestThreadName.set(Thread.currentThread().getName());
                ingestService.recordHttpServerObservation(http("2026-05-08T01:00:01Z"));
            });

            requestFuture.get(200, TimeUnit.MILLISECONDS);
            assertTrue(clientEntered.await(1, TimeUnit.SECONDS));
            assertNotEquals(requestThreadName.get(), clientThreadName.get());
            assertTrue(clientThreadName.get().contains("metric-flush-worker"));
        } finally {
            releaseClient.countDown();
            worker.close();
            requestExecutor.shutdownNow();
        }
    }

    @Test
    void requestPathDoesNotThrowWhenFlushQueueIsFull() {
        BoundedMetricQueue queue = new BoundedMetricQueue(1, MetricQueueDropPolicy.DROP_NEWEST);
        AtomicReference<Instant> nowUtc = new AtomicReference<>(Instant.parse("2026-05-08T01:00:31Z"));
        StarterMetricIngestService ingestService = new StarterMetricIngestService(
                new LowCardinalityHttpObservationGuard(),
                new MetricBucketRollupService(),
                queue,
                nowUtc::get);

        nowUtc.set(Instant.parse("2026-05-08T01:01:00Z"));
        ingestService.recordHttpServerObservation(http("2026-05-08T01:00:01Z"));
        nowUtc.set(Instant.parse("2026-05-08T01:01:30Z"));

        assertDoesNotThrow(() -> ingestService.recordHttpServerObservation(http("2026-05-08T01:00:31Z")));
        assertEquals(1, queue.size());
        assertEquals(1, queue.droppedCount());
    }

    @Test
    void scheduledDrainEnqueuesDueBucketWithoutNewSample() {
        BoundedMetricQueue queue = new BoundedMetricQueue(8, MetricQueueDropPolicy.DROP_NEWEST);
        AtomicReference<Instant> nowUtc = new AtomicReference<>(Instant.parse("2026-05-08T01:00:31Z"));
        StarterMetricIngestService ingestService = new StarterMetricIngestService(
                new LowCardinalityHttpObservationGuard(),
                new MetricBucketRollupService(),
                queue,
                nowUtc::get);

        ingestService.recordHttpServerObservation(http("2026-05-08T01:00:01Z"));
        assertEquals(0, queue.size());

        nowUtc.set(Instant.parse("2026-05-08T01:01:00Z"));
        ingestService.drainDueBuckets();

        ClosedMetricBucket drainedBucket = queue.pollNow().orElseThrow();
        assertEquals(Instant.parse("2026-05-08T01:00:00Z"), drainedBucket.interval().startUtc());
        assertEquals(Instant.parse("2026-05-08T01:00:30Z"), drainedBucket.interval().endUtc());
    }

    @Test
    void scheduledDrainBeforeGraceWindowDoesNotEnqueueBucket() {
        BoundedMetricQueue queue = new BoundedMetricQueue(8, MetricQueueDropPolicy.DROP_NEWEST);
        AtomicReference<Instant> nowUtc = new AtomicReference<>(Instant.parse("2026-05-08T01:00:31Z"));
        StarterMetricIngestService ingestService = new StarterMetricIngestService(
                new LowCardinalityHttpObservationGuard(),
                new MetricBucketRollupService(),
                queue,
                nowUtc::get);

        ingestService.recordHttpServerObservation(http("2026-05-08T01:00:01Z"));
        nowUtc.set(Instant.parse("2026-05-08T01:00:59Z"));
        ingestService.drainDueBuckets();

        assertEquals(0, queue.size());
        assertEquals(0, queue.enqueuedCount());
    }

    private static HttpServerObservationInput http(String observedAt) {
        return new HttpServerObservationInput(
                Instant.parse(observedAt),
                "GET",
                200,
                false,
                null,
                Duration.ofMillis(25),
                Optional.of("/orders/{orderId}"),
                Optional.empty());
    }

    private static IngestEnvelopeBuilderService builder() {
        return new IngestEnvelopeBuilderService(new IngestEnvelopeIdentity(
                "project-123",
                "orders-api",
                "prod",
                "instance-1"));
    }
}
