package com.observation.portal.domain.ingest.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * starter heartbeat API가 받는 control-plane/liveness 요청 모델이다.
 *
 * <p>metric bucket ingest payload와 별도 shape로 두어 heartbeat가 accepted bucket을 만들지 않게 한다.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IngestHeartbeatRequest(
        String schemaVersion,
        String starterVersion,
        Heartbeat heartbeat,
        Application application
) {

    /**
     * starter가 전송한 heartbeat 시각, sequence, interval metadata다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Heartbeat(String sentAtUtc, Long sequence, Integer intervalSeconds) {
    }

    /**
     * heartbeat가 어느 application/environment/instance에서 왔는지 식별하는 metadata다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Application(String name, String environment, String instance) {
    }
}
