package com.observation.portal.domain.history.service;

import com.observation.portal.domain.history.model.OperationalEventType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OperationalEventIdFactoryTest {

    private static final UUID SNAPSHOT_ID = UUID.fromString("018f6b9a-2e1a-7d2b-9b2f-4db69d92c241");

    @Test
    void createsDeterministicSnapshotDerivedEventId() {
        String eventId = OperationalEventIdFactory.eventId(
                SNAPSHOT_ID,
                OperationalEventType.HIGH_CONFIDENCE_CONCERN,
                " endpoint_latency_spike: POST /orders ");

        assertThat(eventId).isEqualTo(
                "snapshot:018f6b9a-2e1a-7d2b-9b2f-4db69d92c241:high_confidence_concern:endpoint_latency_spike:post_orders");
        assertThat(OperationalEventIdFactory.eventId(
                SNAPSHOT_ID,
                OperationalEventType.HIGH_CONFIDENCE_CONCERN,
                " endpoint_latency_spike: POST /orders "))
                .isEqualTo(eventId);
    }

    @Test
    void eventIdDoesNotRequireOrReuseMarkerId() throws Exception {
        Method method = OperationalEventIdFactory.class.getDeclaredMethod(
                "eventId",
                UUID.class,
                OperationalEventType.class,
                String.class);

        assertThat(method.getParameterTypes()).containsExactly(UUID.class, OperationalEventType.class, String.class);
        assertThat(OperationalEventIdFactory.eventId(
                SNAPSHOT_ID,
                OperationalEventType.STATE_CHANGED,
                "marker:existing-marker-id"))
                .isNotEqualTo("marker:existing-marker-id");
    }

    @Test
    void normalizesStateAndConcernKeysForTypeSpecificSnapshotIds() {
        assertThat(OperationalEventIdFactory.eventId(
                SNAPSHOT_ID,
                OperationalEventType.STATE_CHANGED,
                "unknown_previous:Degraded"))
                .endsWith(":state_changed:unknown_previous:degraded");
        assertThat(OperationalEventIdFactory.eventId(
                SNAPSHOT_ID,
                OperationalEventType.STALE_ENTERED,
                "stale_entered: stale "))
                .endsWith(":stale_entered:stale_entered:stale");
        assertThat(OperationalEventIdFactory.eventId(
                SNAPSHOT_ID,
                OperationalEventType.HIGH_CONFIDENCE_CONCERN,
                "endpoint_error_spike:GET /inventory"))
                .endsWith(":high_confidence_concern:endpoint_error_spike:get_inventory");
    }
}
