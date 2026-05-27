package com.observation.portal.domain.history.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OperationalEventHistoryReadModelTest {

    private static final UUID APPLICATION_ID = UUID.fromString("00000000-0000-0000-0000-000000005901");
    private static final UUID SNAPSHOT_ID = UUID.fromString("00000000-0000-0000-0000-000000005911");

    @Test
    void responseSortsEventsByOccurredAtDescThenEventIdAsc() {
        OperationalEventItem older = event("snapshot:1:state_changed:older", "2026-05-27T10:00:00Z");
        OperationalEventItem tiedB = event("snapshot:2:state_changed:b", "2026-05-27T11:00:00Z");
        OperationalEventItem tiedA = event("snapshot:2:state_changed:a", "2026-05-27T11:00:00Z");
        OperationalEventItem newest = event("snapshot:3:state_changed:newest", "2026-05-27T12:00:00Z");

        OperationalEventHistoryReadModel readModel = readModel(List.of(older, tiedB, newest, tiedA));

        assertThat(readModel.events())
                .extracting(OperationalEventItem::eventId)
                .containsExactly(
                        "snapshot:3:state_changed:newest",
                        "snapshot:2:state_changed:a",
                        "snapshot:2:state_changed:b",
                        "snapshot:1:state_changed:older");
    }

    @Test
    void compactEventShapeDoesNotExposeRawOrPercentileFields() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        String json = objectMapper.writeValueAsString(readModel(List.of(event(
                "snapshot:1:high_confidence_concern:endpoint",
                "2026-05-27T12:00:00Z"))));

        assertThat(json).contains("eventId");
        assertThat(json).contains("resolvedAt");
        assertThat(json).contains("links");
        assertThat(json).contains("snapshot");
        assertThat(json).doesNotContain(
                "rawSnapshotJson",
                "rawBucket",
                "rawEndpointJson",
                "p95",
                "p99",
                "traceId",
                "requestSample",
                "queryString",
                "queryKey");
    }

    private static OperationalEventHistoryReadModel readModel(List<OperationalEventItem> events) {
        return new OperationalEventHistoryReadModel(
                offset("2026-05-27T13:10:35Z"),
                APPLICATION_ID,
                OperationalEventHistoryReadModel.SOURCE,
                new OperationalEventHistoryReadModel.Horizon(
                        offset("2026-05-26T13:10:35Z"),
                        offset("2026-05-27T13:10:35Z"),
                        "24h",
                        OperationalEventHistoryReadModel.DEFAULT_SINCE,
                        OperationalEventHistoryReadModel.MAX_SINCE,
                        50,
                        OperationalEventHistoryReadModel.MAX_LIMIT,
                        OperationalEventHistoryReadModel.ORDER),
                events);
    }

    private static OperationalEventItem event(String eventId, String occurredAt) {
        return new OperationalEventItem(
                eventId,
                OperationalEventType.HIGH_CONFIDENCE_CONCERN,
                OperationalEventSeverity.WARNING,
                "저장된 snapshot 관찰",
                "저장된 snapshot에서 운영 신호가 관찰됐습니다.",
                offset(occurredAt),
                null,
                "degraded",
                new BigDecimal("0.84"),
                SNAPSHOT_ID,
                new OperationalEventEvidence(
                        "endpoint_latency_spike",
                        "POST /orders",
                        "POST",
                        "/orders",
                        "endpoint-evidence-1",
                        "resolved"),
                new OperationalEventLinks("/api/projects/p/applications/a/dashboard/snapshots/s"));
    }

    private static OffsetDateTime offset(String instant) {
        return OffsetDateTime.parse(instant);
    }
}
