package com.observation.starter.service;

import com.observation.starter.client.HeartbeatFailureCategory;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;

/**
 * heartbeat failure window의 최초 실패를 WARN 수준으로 노출하는 logging 경계다.
 */
@FunctionalInterface
public interface HeartbeatFailureReporter {

    /**
     * 실패 window에서 관측 가능한 WARN 한 건을 남긴다.
     */
    void warn(Warning warning);

    /**
     * Spring Boot host app의 logging pipeline과 맞물리는 SLF4J 기반 기본 WARN reporter를 반환한다.
     */
    static HeartbeatFailureReporter logger() {
        return new Slf4jHeartbeatFailureReporter(LoggerFactory.getLogger(StarterHeartbeatSender.class));
    }

    /**
     * raw project key 없이 endpoint alias, category, backoff 사실만 담는 WARN payload다.
     */
    record Warning(
            String endpointAlias,
            HeartbeatFailureCategory failureCategory,
            Duration nextRetryDelay
    ) {

        public Warning {
            endpointAlias = requireText(endpointAlias, "endpointAlias");
            failureCategory = Objects.requireNonNull(failureCategory, "failureCategory must not be null");
            nextRetryDelay = Objects.requireNonNull(nextRetryDelay, "nextRetryDelay must not be null");
        }

        /**
         * starter-failure-semantics 계약이 요구하는 최초 WARN 메시지를 만든다.
         */
        public String message() {
            return "starter heartbeat failure"
                    + " endpointAlias=" + endpointAlias
                    + " failureCategory=" + failureCategory.logValue()
                    + " hostApplicationContinues=true"
                    + " retryBackoffApplied=true"
                    + " nextRetryDelayMillis=" + nextRetryDelay.toMillis();
        }

        private static String requireText(String value, String fieldName) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(fieldName + " must not be blank");
            }
            return value.trim().replace('\n', '_').replace('\r', '_');
        }
    }
}
