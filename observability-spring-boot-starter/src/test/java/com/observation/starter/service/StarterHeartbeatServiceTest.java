package com.observation.starter.service;

import com.observation.starter.client.NoopPortalHeartbeatClient;
import com.observation.starter.client.PortalHeartbeatClient;
import com.observation.starter.client.HeartbeatFailureCategory;
import com.observation.starter.client.PortalHeartbeatException;
import com.observation.starter.config.HeartbeatProperties;
import com.observation.starter.model.heartbeat.HeartbeatRequest;
import com.observation.starter.model.ingest.IngestEnvelopeIdentity;
import org.junit.jupiter.api.Test;

import java.net.http.HttpTimeoutException;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StarterHeartbeatServiceTest {

    @Test
    void buildsHeartbeatPayloadFromStarterIdentityAndSequence() {
        AtomicReference<HeartbeatRequest> sentRequest = new AtomicReference<>();
        HeartbeatProperties properties = properties();
        properties.setStarterVersion("0.1.0-test");
        properties.setIntervalSeconds(15);
        StarterHeartbeatService service = new StarterHeartbeatService(
                sentRequest::set,
                identity(),
                properties,
                () -> Instant.parse("2026-05-24T08:30:00Z"));

        assertTrue(service.sendOnce());

        HeartbeatRequest request = sentRequest.get();
        assertEquals("1.0", request.schemaVersion());
        assertEquals("0.1.0-test", request.starterVersion());
        assertEquals("2026-05-24T08:30:00Z", request.heartbeat().sentAtUtc());
        assertEquals(1L, request.heartbeat().sequence());
        assertEquals(15, request.heartbeat().intervalSeconds());
        assertEquals("orders-api", request.application().name());
        assertEquals("prod", request.application().environment());
        assertEquals("instance-1", request.application().instance());

        assertTrue(service.sendOnce());
        assertEquals(2L, sentRequest.get().heartbeat().sequence());
    }

    @Test
    void failOpenWhenClientThrows() {
        PortalHeartbeatClient failingClient = request -> {
            throw new IllegalStateException("portal down");
        };
        StarterHeartbeatService service = new StarterHeartbeatService(
                failingClient,
                identity(),
                properties(),
                () -> Instant.parse("2026-05-24T08:30:00Z"));

        assertFalse(service.sendOnce());
    }

    @Test
    void exposesFailureCategoryWithoutThrowingOrLeakingRawProjectKeyInResult() {
        PortalHeartbeatClient failingClient = request -> {
            throw PortalHeartbeatException.forStatus(401);
        };
        StarterHeartbeatService service = new StarterHeartbeatService(
                failingClient,
                identity(),
                properties(),
                () -> Instant.parse("2026-05-24T08:30:00Z"));

        StarterHeartbeatResult result = service.sendOnceResult();

        assertFalse(result.success());
        assertTrue(result.failed());
        assertEquals(HeartbeatFailureCategory.UNAUTHORIZED, result.failureCategory());
        assertFalse(result.toString().contains("pk_live.secret"));
    }

    @Test
    void rawGenericExceptionMessageDoesNotEnterResult() {
        PortalHeartbeatClient failingClient = request -> {
            throw new IllegalStateException("portal rejected raw key pk_live.secret");
        };
        StarterHeartbeatService service = new StarterHeartbeatService(
                failingClient,
                identity(),
                properties(),
                () -> Instant.parse("2026-05-24T08:30:00Z"));

        StarterHeartbeatResult result = service.sendOnceResult();

        assertFalse(result.success());
        assertEquals(HeartbeatFailureCategory.UNKNOWN, result.failureCategory());
        assertFalse(result.toString().contains("pk_live.secret"));
    }

    @Test
    void classifiesWrappedTimeoutFailureAsReadTimeout() {
        PortalHeartbeatClient failingClient = request -> {
            throw new RuntimeException(new HttpTimeoutException("request timed out"));
        };
        StarterHeartbeatService service = new StarterHeartbeatService(
                failingClient,
                identity(),
                properties(),
                () -> Instant.parse("2026-05-24T08:30:00Z"));

        StarterHeartbeatResult result = service.sendOnceResult();

        assertFalse(result.success());
        assertEquals(HeartbeatFailureCategory.READ_TIMEOUT, result.failureCategory());
    }

    @Test
    void disabledClientDoesNotAttemptSend() {
        StarterHeartbeatService service = new StarterHeartbeatService(
                new NoopPortalHeartbeatClient(),
                identity(),
                properties(),
                Instant::now);

        assertFalse(service.canSend());
        assertFalse(service.sendOnce());
    }

    private static HeartbeatProperties properties() {
        return new HeartbeatProperties();
    }

    private static IngestEnvelopeIdentity identity() {
        return new IngestEnvelopeIdentity("project-123", "orders-api", "prod", "instance-1");
    }
}
