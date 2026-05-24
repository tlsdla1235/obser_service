package com.observation.starter.service;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * heartbeat 전송을 host startup/request path 밖의 daemon thread에서 주기적으로 실행한다.
 */
public final class StarterHeartbeatSender implements AutoCloseable {

    private static final String THREAD_NAME = "observation-heartbeat-sender";
    private static final Duration SHUTDOWN_JOIN_TIMEOUT = Duration.ofSeconds(1);

    private final StarterHeartbeatService heartbeatService;
    private final Duration interval;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile Thread workerThread;

    /**
     * heartbeat service와 interval을 받아 background sender를 만든다.
     */
    public StarterHeartbeatSender(StarterHeartbeatService heartbeatService, Duration interval) {
        this.heartbeatService = Objects.requireNonNull(heartbeatService, "heartbeatService must not be null");
        this.interval = Objects.requireNonNull(interval, "interval must not be null");
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
            heartbeatService.sendOnce();
            if (!sleepInterval()) {
                return;
            }
        }
    }

    private boolean sleepInterval() {
        try {
            Thread.sleep(interval.toMillis());
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
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
