package com.observation.starter.model.heartbeat;

import java.util.Objects;

/**
 * starter가 portal heartbeat API로 보내는 control-plane payload다.
 *
 * <p>metric bucket payload와 분리된 liveness 신호이며 application metadata shape 검증 입력으로 쓰인다.</p>
 */
public record HeartbeatRequest(
        String schemaVersion,
        String starterVersion,
        Heartbeat heartbeat,
        Application application) {

    /**
     * schema, starter version, heartbeat metadata, application identity를 모두 가진 요청을 보장한다.
     */
    public HeartbeatRequest {
        schemaVersion = requireText(schemaVersion, "schemaVersion");
        starterVersion = requireText(starterVersion, "starterVersion");
        Objects.requireNonNull(heartbeat, "heartbeat must not be null");
        Objects.requireNonNull(application, "application must not be null");
    }

    /**
     * 주기 전송 시점과 sequence를 담는 heartbeat 세부 metadata다.
     */
    public record Heartbeat(String sentAtUtc, long sequence, int intervalSeconds) {

        /**
         * portal이 shape를 검증할 수 있도록 UTC 시각, 증가 sequence, 전송 interval을 보장한다.
         */
        public Heartbeat {
            sentAtUtc = requireText(sentAtUtc, "heartbeat.sentAtUtc");
            if (sequence < 0) {
                throw new IllegalArgumentException("heartbeat.sequence must not be negative");
            }
            if (intervalSeconds <= 0) {
                throw new IllegalArgumentException("heartbeat.intervalSeconds must be positive");
            }
        }
    }

    /**
     * accepted bucket ingest와 같은 application/environment/instance identity를 표현한다.
     */
    public record Application(String name, String environment, String instance) {

        /**
         * portal metadata validation에 사용할 세 identity 값이 비어 있지 않게 한다.
         */
        public Application {
            name = requireText(name, "application.name");
            environment = requireText(environment, "application.environment");
            instance = requireText(instance, "application.instance");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
