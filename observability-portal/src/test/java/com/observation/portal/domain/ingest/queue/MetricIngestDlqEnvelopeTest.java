package com.observation.portal.domain.ingest.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MetricIngestDlqEnvelopeTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void serializesOnlyAllowListFieldsForMalformedAndConflict() throws Exception {
        MetricIngestDlqEnvelope envelope = new MetricIngestDlqEnvelope(
                "1",
                "conflict",
                "idempotency_payload_conflict",
                "source-message-1",
                4,
                OffsetDateTime.parse("2026-05-08T01:00:35Z"),
                "1",
                UUID.fromString("00000000-0000-0000-0000-000000003201"),
                "orders-api",
                "prod",
                "orders-api-7f9c9c8c9d-x2p4k",
                OffsetDateTime.parse("2026-05-08T01:00:00Z"),
                OffsetDateTime.parse("2026-05-08T01:00:30Z"),
                "project-123:orders-api:prod:orders-api-7f9c9c8c9d-x2p4k:20260508T010000Z",
                "incoming-hash",
                "stored-hash",
                UUID.fromString("00000000-0000-0000-0000-00000000b123"),
                "sent_to_application_dlq");

        String json = objectMapper.writeValueAsString(envelope);

        assertThat(json).contains(
                "\"dlqEnvelopeVersion\":\"1\"",
                "\"failureCategory\":\"conflict\"",
                "\"failureCode\":\"idempotency_payload_conflict\"",
                "\"workerAction\":\"sent_to_application_dlq\"");
        assertThat(json).doesNotContain(
                "\"payload\":",
                "bodyJson",
                "Authorization",
                "Bearer",
                "webhook",
                "credential",
                "queueUrl",
                "https://sqs",
                "exception",
                "raw");
        assertThat(envelope.toString()).doesNotContain("Authorization", "webhook", "https://sqs", "exception");
    }
}
