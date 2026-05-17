package com.observation.starter.spring;

import com.observation.starter.model.metric.ClosedMetricBucket;
import com.observation.starter.model.metric.HttpServerObservationInput;
import com.observation.starter.queue.BoundedMetricQueue;
import com.observation.starter.queue.MetricQueueDropPolicy;
import com.observation.starter.service.LowCardinalityHttpObservationGuard;
import com.observation.starter.service.MetricBucketRollupService;
import com.observation.starter.service.StarterMetricIngestService;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StarterMetricDrainSchedulerTest {

    @Test
    void declaresThirtySecondScheduledDrainCadence() throws Exception {
        Method method = StarterMetricDrainScheduler.class.getMethod("drainDueBucketsOnTick");

        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertNotNull(scheduled);
        assertEquals(30_000L, scheduled.fixedDelay());
        assertEquals(30_000L, scheduled.initialDelay());
    }

    @Test
    void scheduledTickEnqueuesDueBucketWithoutNewSample() {
        BoundedMetricQueue queue = new BoundedMetricQueue(8, MetricQueueDropPolicy.DROP_NEWEST);
        AtomicReference<Instant> nowUtc = new AtomicReference<>(Instant.parse("2026-05-08T01:00:31Z"));
        StarterMetricIngestService ingestService = new StarterMetricIngestService(
                new LowCardinalityHttpObservationGuard(),
                new MetricBucketRollupService(),
                queue,
                nowUtc::get);
        StarterMetricDrainScheduler scheduler = new StarterMetricDrainScheduler(ingestService);

        ingestService.recordHttpServerObservation(http("2026-05-08T01:00:01Z"));
        assertEquals(0, queue.size());

        nowUtc.set(Instant.parse("2026-05-08T01:01:00Z"));
        scheduler.drainDueBucketsOnTick();

        ClosedMetricBucket drainedBucket = queue.pollNow().orElseThrow();
        assertEquals(Instant.parse("2026-05-08T01:00:00Z"), drainedBucket.interval().startUtc());
        assertEquals(Instant.parse("2026-05-08T01:00:30Z"), drainedBucket.interval().endUtc());
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
}
