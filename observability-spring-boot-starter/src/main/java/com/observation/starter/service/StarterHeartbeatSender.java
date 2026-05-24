package com.observation.starter.service;

import com.observation.starter.client.HeartbeatFailureCategory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * heartbeat 전송을 host startup/request path 밖의 daemon thread에서 주기적으로 실행한다.
 *
 * <p>전송 실패는 이 sender 내부에서만 WARN, suppress, backoff로 다루고 host flow로 전파하지 않는다.</p>
 */
public final class StarterHeartbeatSender implements AutoCloseable {

    private static final String THREAD_NAME = "observation-heartbeat-sender";
    private static final String ENDPOINT_ALIAS = "portal-heartbeat";
    private static final int MAX_BACKOFF_EXPONENT = 3;
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(5);
    private static final Duration SHUTDOWN_JOIN_TIMEOUT = Duration.ofSeconds(1);

    private final StarterHeartbeatService heartbeatService;
    private final Duration interval;
    private final HeartbeatRetryBackoff retryBackoff;
    private final HeartbeatFailureReporter failureReporter;
    private final AtomicBoolean running = new AtomicBoolean();
    private long consecutiveFailures;
    private HeartbeatFailureCategory lastWarnedFailureCategory;
    private volatile Thread workerThread;

    /**
     * heartbeat service와 interval을 받아 background sender를 만든다.
     */
    public StarterHeartbeatSender(StarterHeartbeatService heartbeatService, Duration interval) {
        this(heartbeatService, interval, HeartbeatRetryBackoff.threadSleep(), HeartbeatFailureReporter.logger());
    }

    /**
     * 테스트와 설정 확장을 위해 heartbeat 전용 backoff와 failure reporter를 명시해 sender를 만든다.
     */
    StarterHeartbeatSender(
            StarterHeartbeatService heartbeatService,
            Duration interval,
            HeartbeatRetryBackoff retryBackoff,
            HeartbeatFailureReporter failureReporter) {
        this.heartbeatService = Objects.requireNonNull(heartbeatService, "heartbeatService must not be null");
        this.interval = Objects.requireNonNull(interval, "interval must not be null");
        this.retryBackoff = Objects.requireNonNull(retryBackoff, "retryBackoff must not be null");
        this.failureReporter = Objects.requireNonNull(failureReporter, "failureReporter must not be null");
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("interval must be positive");
        }
    }

    /**
     * daemon sender를 시작한다. 비활성 설정이면 아무 thread도 만들지 않는다.
     */
    public void start() {
        if (!heartbeatService.canSend() || !running.compareAndSet(false, true)) {
            return;
        }
        Thread thread = new Thread(this::runLoop, THREAD_NAME);
        thread.setDaemon(true);
        workerThread = thread;
        thread.start();
    }

    private void runLoop() {
        while (running.get()) {
            StarterHeartbeatResult result = heartbeatService.sendOnceResult();
            Duration nextDelay = nextDelay(result);
            if (!sleepDelay(nextDelay)) {
                return;
            }
        }
    }

    private Duration nextDelay(StarterHeartbeatResult result) {
        if (result.success()) {
            resetFailureWindow();
            return interval;
        }
        if (!result.failed()) {
            return interval;
        }
        consecutiveFailures++;
        Duration nextDelay = backoffDelay();
        warnFirstFailureForCategory(result.failureCategory(), nextDelay);
        return nextDelay;
    }

    private void warnFirstFailureForCategory(HeartbeatFailureCategory failureCategory, Duration nextDelay) {
        if (failureCategory == lastWarnedFailureCategory) {
            return;
        }
        lastWarnedFailureCategory = failureCategory;
        failureReporter.warn(new HeartbeatFailureReporter.Warning(ENDPOINT_ALIAS, failureCategory, nextDelay));
    }

    private void resetFailureWindow() {
        consecutiveFailures = 0;
        lastWarnedFailureCategory = null;
    }

    private Duration backoffDelay() {
        int exponent = (int) Math.min(consecutiveFailures, MAX_BACKOFF_EXPONENT);
        long multiplier = 1L << exponent;
        long intervalMillis = Math.max(1L, interval.toMillis());
        long maxMillis = Math.max(saturatingMultiply(intervalMillis, 2), MAX_BACKOFF.toMillis());
        long candidateMillis = saturatingMultiply(intervalMillis, multiplier);
        return Duration.ofMillis(Math.min(candidateMillis, maxMillis));
    }

    private static long saturatingMultiply(long value, long multiplier) {
        if (value > Long.MAX_VALUE / multiplier) {
            return Long.MAX_VALUE;
        }
        return value * multiplier;
    }

    private boolean sleepDelay(Duration delay) {
        try {
            retryBackoff.sleep(delay);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /**
     * sender를 멈추고 bounded timeout 안에서 thread 종료를 기다린다.
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
