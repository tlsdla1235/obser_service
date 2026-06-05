package com.observation.portal.domain.ingest.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.observation.portal.domain.ingest.model.IngestEnvelopeRequest;
import com.observation.portal.domain.ingest.service.IngestPayloadHasher;
import com.observation.portal.domain.ingest.service.PortalIngestValidationFixture;
import com.observation.portal.domain.ingest.service.ValidatedIngestCandidate;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MetricIngestQueueMessageContractTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final OffsetDateTime RECEIVED_AT = OffsetDateTime.parse("2026-05-08T01:00:31Z");
    private static final OffsetDateTime ENQUEUED_AT = OffsetDateTime.parse("2026-05-08T01:00:31.120Z");

    private final IngestPayloadHasher payloadHasher = new IngestPayloadHasher(OBJECT_MAPPER);
    private final MetricIngestQueueMessageFactory factory =
            new MetricIngestQueueMessageFactory(OBJECT_MAPPER, payloadHasher);

    @Test
    void serializesGoldenMessageJsonExactlyWithVersionHashAndSupportedPayloadOnly() throws Exception {
        IngestEnvelopeRequest request = PortalIngestValidationFixture.goldenRequest();
        MetricIngestQueueMessage message = factory.build(candidate(request), RECEIVED_AT, ENQUEUED_AT);
        String payloadHash = payloadHasher.sha256(request);

        assertThat(message.bodyJson()).isEqualTo("""
                {"messageVersion":"1","projectId":"00000000-0000-0000-0000-000000003201","projectName":"checkout","idempotencyKey":"project-123:orders-api:prod:orders-api-7f9c9c8c9d-x2p4k:20260508T010000Z","payloadHash":"%s","receivedAt":"2026-05-08T01:00:31Z","enqueuedAt":"2026-05-08T01:00:31.12Z","payload":{"schemaVersion":"1.0","application":{"name":"orders-api","environment":"prod","instance":"orders-api-7f9c9c8c9d-x2p4k"},"bucket":{"startUtc":"2026-05-08T01:00:00Z","endUtc":"2026-05-08T01:00:30Z","durationSeconds":30},"summary":{"requestCount":3,"errorCount":1,"httpServerDurationBuckets":[{"leMs":50,"count":1},{"leMs":100,"count":2},{"leMs":250,"count":3},{"leMs":500,"count":3},{"leMs":1000,"count":3}],"jvm":{"cpuUsage":0.64,"heapUsedRatio":0.71},"datasource":{"poolUsageRatio":0.82},"localPercentiles":null},"endpoints":[{"method":"GET","route":"/orders/{orderId}","requestCount":2,"errorCount":0,"durationBuckets":[{"leMs":50,"count":1},{"leMs":100,"count":2},{"leMs":250,"count":2},{"leMs":500,"count":2},{"leMs":1000,"count":2}]},{"method":"POST","route":"/orders","requestCount":1,"errorCount":1,"durationBuckets":[{"leMs":50,"count":0},{"leMs":100,"count":0},{"leMs":250,"count":1},{"leMs":500,"count":1},{"leMs":1000,"count":1}]}]}}"""
                .formatted(payloadHash));
        assertThat(message.body().payloadHash()).isEqualTo(payloadHash);
        assertThat(message.bodyJson()).doesNotContain(
                PortalIngestValidationFixture.PROJECT_KEY_HEADER,
                "Authorization",
                "Discord",
                "webhook",
                "token=secret",
                "/orders/12345?");
    }

    @Test
    void buildsExactlyEightAllowListAttributesWithQuotaHeadroom() throws Exception {
        MetricIngestQueueMessage message = factory.build(
                candidate(PortalIngestValidationFixture.goldenRequest()),
                RECEIVED_AT,
                ENQUEUED_AT);

        assertThat(message.attributes()).hasSize(8);
        assertThat(message.attributes())
                .extracting(MetricIngestMessageAttribute::name)
                .containsExactly(
                        "messageVersion",
                        "projectId",
                        "schemaVersion",
                        "bucketStartUtc",
                        "bucketEndUtc",
                        "applicationName",
                        "environment",
                        "instanceName");
        assertThat(message.attributes())
                .allSatisfy(attribute -> assertThat(attribute.dataType()).isEqualTo("String"));
        assertThat(message.attributes())
                .extracting(MetricIngestMessageAttribute::stringValue)
                .containsExactly(
                        "1",
                        "00000000-0000-0000-0000-000000003201",
                        "1.0",
                        "2026-05-08T01:00:00Z",
                        "2026-05-08T01:00:30Z",
                        "orders-api",
                        "prod",
                        "orders-api-7f9c9c8c9d-x2p4k");
    }

    @Test
    void payloadHashIgnoresQueueMetadataAndUnknownJsonFields() throws Exception {
        IngestEnvelopeRequest withUnknownFields = OBJECT_MAPPER.readValue(
                PortalIngestValidationFixture.jsonWith(root -> {
                    root.putObject("customMetrics").put("ignored", 1);
                    root.putArray("rawTimeseries").add(42);
                    ((ObjectNode) root.get("summary")).putObject("tags").put("tenant", "checkout");
                }),
                IngestEnvelopeRequest.class);
        MetricIngestQueueMessage first = factory.build(candidate(withUnknownFields), RECEIVED_AT, ENQUEUED_AT);
        MetricIngestQueueMessage second = factory.build(
                candidate(withUnknownFields),
                OffsetDateTime.parse("2026-05-08T01:00:32Z"),
                OffsetDateTime.parse("2026-05-08T01:00:32.120Z"));

        assertThat(first.body().payloadHash()).isEqualTo(payloadHasher.sha256(PortalIngestValidationFixture.goldenRequest()));
        assertThat(second.body().payloadHash()).isEqualTo(first.body().payloadHash());
        assertThat(second.bodyJson()).isNotEqualTo(first.bodyJson());
        assertThat(first.bodyJson()).doesNotContain("customMetrics", "rawTimeseries", "\"tags\"", "tenant");
    }

    @Test
    void estimatesMessageSizeFromBodyAndAttributeUtf8Bytes() throws Exception {
        MetricIngestQueueMessage message = factory.build(
                candidate(PortalIngestValidationFixture.goldenRequest()),
                RECEIVED_AT,
                ENQUEUED_AT);

        assertThat(message.estimatedSizeBytes()).isGreaterThan(message.bodyBytes().length);
        assertThat(message.estimatedSizeBytes()).isLessThan(1_048_576L);
    }

    private static ValidatedIngestCandidate candidate(IngestEnvelopeRequest request) {
        return new ValidatedIngestCandidate(
                PortalIngestValidationFixture.VERIFIED_PROJECT,
                PortalIngestValidationFixture.IDEMPOTENCY_KEY,
                request);
    }
}
