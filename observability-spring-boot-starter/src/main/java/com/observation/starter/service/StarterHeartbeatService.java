package com.observation.starter.service;

import com.observation.starter.client.PortalHeartbeatClient;
import com.observation.starter.config.HeartbeatProperties;
import com.observation.starter.model.heartbeat.HeartbeatRequest;
import com.observation.starter.model.ingest.IngestEnvelopeIdentity;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * starter heartbeat payload를 만들고 client 실패를 fail-open으로 격리하는 service다.
 */
public final class StarterHeartbeatService {

    private static final String SCHEMA_VERSION = "1.0";

    private final PortalHeartbeatClient client;
    private final IngestEnvelopeIdentity identity;
    private final HeartbeatProperties properties;
    private final Supplier<Instant> clock;
    private final AtomicLong sequence = new AtomicLong();

    /**
     * runtime heartbeat sender가 사용할 request builder와 client boundary를 구성한다.
     */
    public StarterHeartbeatService(
            PortalHeartbeatClient client,
            IngestEnvelopeIdentity identity,
            HeartbeatProperties properties,
            Supplier<Instant> clock) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.identity = Objects.requireNonNull(identity, "identity must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * heartbeat 전송 설정과 client가 모두 활성인지 확인한다.
     */
    public boolean canSend() {
        return properties.isEnabled() && client.isEnabled();
    }

    /**
     * heartbeat 한 번을 만들고 전송한다. 실패는 boolean 결과로만 남기며 caller로 전파하지 않는다.
     */
    public boolean sendOnce() {
        if (!canSend()) {
            return false;
        }
        try {
            client.send(buildRequest(sequence.incrementAndGet()));
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private HeartbeatRequest buildRequest(long nextSequence) {
        return new HeartbeatRequest(
                SCHEMA_VERSION,
                properties.getStarterVersion(),
                new HeartbeatRequest.Heartbeat(
                        clock.get().toString(),
                        nextSequence,
                        properties.getIntervalSeconds()),
                new HeartbeatRequest.Application(
                        identity.applicationName(),
                        identity.environment(),
                        identity.instance()));
    }
}
