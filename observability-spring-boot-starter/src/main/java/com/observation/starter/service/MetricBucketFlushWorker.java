package com.observation.starter.service;

import com.observation.starter.client.PortalMetricBucketClient;
import com.observation.starter.model.metric.ClosedMetricBucket;
import com.observation.starter.queue.BoundedMetricQueue;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * bounded queue의 닫힌 metric bucket을 background thread에서 포털 client boundary로 flush한다.
 *
 * <p>portal timeout/down, retry, backoff, 최종 실패 처리는 모두 이 worker 내부에 갇힌다.
 * host request path는 이 worker를 직접 실행하지 않고 queue enqueue까지만 수행해야 한다.</p>
 */
public final class MetricBucketFlushWorker implements AutoCloseable {

    private static final String WORKER_THREAD_NAME = "observation-metric-flush-worker";
    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMillis(100);
    private static final Duration SHUTDOWN_JOIN_TIMEOUT = Duration.ofSeconds(1);

    private final BoundedMetricQueue queue;
    private final PortalMetricBucketClient client;
    private final MetricFlushRetryPolicy retryPolicy;
    private final MetricFlushBackoff backoff;
    private final Duration pollInterval;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile Thread workerThread;

    /**
     * 기본 retry/backoff와 poll 간격을 사용하는 flush worker를 만든다.
     */
    public MetricBucketFlushWorker(BoundedMetricQueue queue, PortalMetricBucketClient client) {
        this(queue, client, MetricFlushRetryPolicy.defaults(), MetricFlushBackoff.threadSleep(), DEFAULT_POLL_INTERVAL);
    }

    /**
     * 테스트와 설정 확장을 위해 retry/backoff, poll 간격을 명시해 flush worker를 만든다.
     */
    public MetricBucketFlushWorker(
            BoundedMetricQueue queue,
            PortalMetricBucketClient client,
            MetricFlushRetryPolicy retryPolicy,
            MetricFlushBackoff backoff,
            Duration pollInterval) {
        this.queue = Objects.requireNonNull(queue, "queue must not be null");
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy must not be null");
        this.backoff = Objects.requireNonNull(backoff, "backoff must not be null");
        this.pollInterval = Objects.requireNonNull(pollInterval, "pollInterval must not be null");
        if (pollInterval.isNegative()) {
            throw new IllegalArgumentException("pollInterval must not be negative");
        }
    }

    /**
     * background flush thread를 시작한다. 이미 실행 중이면 아무 작업도 하지 않는다.
     */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        Thread thread = new Thread(this::runLoop, WORKER_THREAD_NAME);
        thread.setDaemon(true);
        workerThread = thread;
        thread.start();
    }

    private void runLoop() {
        while (running.get()) {
            try {
                //일단 100ms 씩 확인. take()는 close 메소드 때문에, 일단 poll로 구현
                queue.poll(pollInterval).ifPresent(this::flushBucket);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException ignored) {
                // 예상 밖 queue/client 실패도 worker 내부에서 격리해 request path로 새지 않게 한다.
            }
        }
    }

    /**
     * bucket 하나를 retry policy에 따라 flush하고 최종 실패는 boolean으로만 남긴다.
     */
    boolean flushBucket(ClosedMetricBucket bucket) {
        ClosedMetricBucket requiredBucket = Objects.requireNonNull(bucket, "bucket must not be null");
        for (int attempt = 1; attempt <= retryPolicy.maxAttempts(); attempt++) {
            try {
                client.flush(requiredBucket);
                return true;
            } catch (RuntimeException exception) {
                if (attempt >= retryPolicy.maxAttempts() || !sleepBeforeRetry()) {
                    return false;
                }
            }
        }
        return false;
    }

    private boolean sleepBeforeRetry() {
        try {
            backoff.sleep(retryPolicy.backoff());
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /**
     * worker를 중지하고 bounded timeout 안에서 thread 종료를 기다린다.
     */
    @Override
    public void close() {
        running.set(false);
        Thread thread = workerThread;
        if (thread == null) {
            return;
        }
        thread.interrupt();
        try {
            thread.join(SHUTDOWN_JOIN_TIMEOUT.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
