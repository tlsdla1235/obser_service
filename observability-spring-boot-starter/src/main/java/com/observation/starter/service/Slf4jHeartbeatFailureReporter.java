package com.observation.starter.service;

import org.slf4j.Logger;

import java.util.Objects;

/**
 * heartbeat failure WARN을 host Spring Boot application의 SLF4J logging facade로 전달한다.
 */
final class Slf4jHeartbeatFailureReporter implements HeartbeatFailureReporter {

    private final Logger logger;

    /**
     * 테스트와 기본 wiring에서 사용할 SLF4J logger boundary를 만든다.
     */
    Slf4jHeartbeatFailureReporter(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
    }

    @Override
    public void warn(Warning warning) {
        logger.warn(Objects.requireNonNull(warning, "warning must not be null").message());
    }
}
