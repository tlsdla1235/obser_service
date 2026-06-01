package com.observation.starter.service;

import com.observation.starter.client.PortalMetricBucketClient;
import com.observation.starter.client.PortalMetricBucketFailure;
import com.observation.starter.model.ingest.IngestEnvelopeCandidate;
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
    private static final String ENDPOINT_ALIAS = "portal-metric-bucket-ingest";
    private static final Duration MIN_POLL_INTERVAL = Duration.ofMillis(1);
    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMillis(100);
    private static final Duration SHUTDOWN_JOIN_TIMEOUT = Duration.ofSeconds(1);

    private final BoundedMetricQueue queue;
    private final IngestEnvelopeBuilderService envelopeBuilder;
    private final PortalMetricBucketClient client;
    private final MetricFlushRetryPolicy retryPolicy;
    private final MetricFlushBackoff backoff;
    private final Duration pollInterval;
    private final MetricBucketFailureReporter failureReporter;
    private final AtomicBoolean running = new AtomicBoolean();
    private String lastWarnedFailureCategory;
    private volatile Thread workerThread;

    /**
     * 기본 retry/backoff와 poll 간격을 사용하는 flush worker를 만든다.
     */
    public MetricBucketFlushWorker(
            BoundedMetricQueue queue,
            IngestEnvelopeBuilderService envelopeBuilder,
            PortalMetricBucketClient client) {
        this(queue,
                envelopeBuilder,
                client,
                MetricFlushRetryPolicy.defaults(),
                MetricFlushBackoff.threadSleep(),
                DEFAULT_POLL_INTERVAL,
                MetricBucketFailureReporter.logger());
    }

    /**
     * 테스트와 설정 확장을 위해 retry/backoff, poll 간격을 명시해 flush worker를 만든다.
     */
    public MetricBucketFlushWorker(
            BoundedMetricQueue queue,
            IngestEnvelopeBuilderService envelopeBuilder,
            PortalMetricBucketClient client,
            MetricFlushRetryPolicy retryPolicy,
            MetricFlushBackoff backoff,
            Duration pollInterval) {
        this(queue,
                envelopeBuilder,
                client,
                retryPolicy,
                backoff,
                pollInterval,
                MetricBucketFailureReporter.logger());
    }

    /**
     * 테스트와 설정 확장을 위해 failure reporter까지 명시해 flush worker를 만든다.
     */
    MetricBucketFlushWorker(
            BoundedMetricQueue queue,
            IngestEnvelopeBuilderService envelopeBuilder,
            PortalMetricBucketClient client,
            MetricFlushRetryPolicy retryPolicy,
            MetricFlushBackoff backoff,
            Duration pollInterval,
            MetricBucketFailureReporter failureReporter) {
        this.queue = Objects.requireNonNull(queue, "queue must not be null");
        this.envelopeBuilder = Objects.requireNonNull(envelopeBuilder, "envelopeBuilder must not be null");
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy must not be null");
        this.backoff = Objects.requireNonNull(backoff, "backoff must not be null");
        this.pollInterval = Objects.requireNonNull(pollInterval, "pollInterval must not be null");
        this.failureReporter = Objects.requireNonNull(failureReporter, "failureReporter must not be null");
        if (pollInterval.compareTo(MIN_POLL_INTERVAL) < 0) {
            throw new IllegalArgumentException("pollInterval must be at least 1ms");
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
        IngestEnvelopeCandidate candidate;
        try {
            candidate = envelopeBuilder.build(requiredBucket);
        } catch (RuntimeException ignored) {
            return false;
        }
        for (int attempt = 1; attempt <= retryPolicy.maxAttempts(); attempt++) {
            try {
                client.flush(candidate);
                resetFailureWindow();
                return true;
            } catch (RuntimeException exception) {
                boolean retryAvailable = attempt < retryPolicy.maxAttempts();
                warnFirstFailureForCategory(failureCategory(exception), retryAvailable);
                if (!retryAvailable || !sleepBeforeRetry()) {
                    return false;
                }
            }
        }
        return false;
    }

    private void warnFirstFailureForCategory(String failureCategory, boolean retryAvailable) {
        if (failureCategory.equals(lastWarnedFailureCategory)) {
            return;
        }
        lastWarnedFailureCategory = failureCategory;
        failureReporter.warn(new MetricBucketFailureReporter.Warning(
                ENDPOINT_ALIAS,
                failureCategory,
                retryAvailable,
                retryAvailable ? retryPolicy.backoff() : Duration.ZERO));
    }

    private void resetFailureWindow() {
        lastWarnedFailureCategory = null;
    }

    private static String failureCategory(RuntimeException exception) {
        if (exception instanceof PortalMetricBucketFailure metricBucketFailure) {
            return metricBucketFailure.failureCategoryLogValue();
        }
        return "unknown";
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
