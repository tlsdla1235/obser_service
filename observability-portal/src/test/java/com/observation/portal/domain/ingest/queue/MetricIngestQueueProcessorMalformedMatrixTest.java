package com.observation.portal.domain.ingest.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.observation.portal.domain.bucket.repository.MetricBucketRepository;
import com.observation.portal.domain.ingest.model.IngestEnvelopeRequest;
import com.observation.portal.domain.ingest.service.IngestPayloadHasher;
import com.observation.portal.domain.ingest.service.PortalIngestValidationFixture;
import com.observation.portal.domain.ingest.service.ValidatedIngestCandidate;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MetricIngestQueueProcessorMalformedMatrixTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-05-08T01:00:35Z"), ZoneOffset.UTC);

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final IngestPayloadHasher payloadHasher = new IngestPayloadHasher(objectMapper);
    private final MetricBucketRepository repository = mock(MetricBucketRepository.class);
    private final MetricIngestQueueProcessor processor =
            new MetricIngestQueueProcessor(objectMapper, payloadHasher, repository, FIXED_CLOCK);

    @Test
    void invalidJsonIsMalformedDlqWithoutRawBody() {
        MetricIngestReceivedMessage source = MetricIngestReceivedMessage.fromBodyJson(
                "source-message-1",
                "receipt-handle-1",
                "{\"payload\":{\"secret\":\"Authorization Bearer token\"",
                Map.of(),
                2);

        assertMalformed(source, "invalid_json");
    }

    @Test
    void unsupportedMessageVersionIsMalformedDlq() throws Exception {
        MetricIngestQueueMessage message = queueMessage(PortalIngestValidationFixture.IDEMPOTENCY_KEY);
        ObjectNode root = (ObjectNode) objectMapper.readTree(message.bodyJson());
        root.put("messageVersion", "2");

        assertMalformed(received(objectMapper.writeValueAsString(root), attributes(message)), "unsupported_message_version");
    }

    @Test
    void missingAttributeAndBodyAttributeMismatchAreMalformedDlq() throws Exception {
        MetricIngestQueueMessage message = queueMessage(PortalIngestValidationFixture.IDEMPOTENCY_KEY);
        Map<String, String> missing = new LinkedHashMap<>(attributes(message));
        missing.remove("projectId");
        assertMalformed(received(message.bodyJson(), missing), "missing_message_attribute");

        Map<String, String> mismatched = new LinkedHashMap<>(attributes(message));
        mismatched.put("applicationName", "payments-api");
        assertMalformed(received(message.bodyJson(), mismatched), "body_attribute_mismatch");
    }

    @Test
    void payloadHashMismatchIsMalformedDlq() throws Exception {
        MetricIngestQueueMessage message = queueMessage(PortalIngestValidationFixture.IDEMPOTENCY_KEY);
        ObjectNode root = (ObjectNode) objectMapper.readTree(message.bodyJson());
        root.put("payloadHash", "different-payload-hash");

        assertMalformed(received(objectMapper.writeValueAsString(root), attributes(message)), "payload_hash_mismatch");
    }

    @Test
    void validationFailuresAreMalformedDlq() throws Exception {
        assertMalformed(
                received(queueMessage(PortalIngestValidationFixture.IDEMPOTENCY_KEY, request(root ->
                        ((ObjectNode) root.get("bucket")).put("startUtc", "2026-05-08T01:00:01Z")))),
                "invalid_bucket_boundary");
        assertMalformed(
                received(queueMessage(PortalIngestValidationFixture.IDEMPOTENCY_KEY, request(root ->
                        root.put("schemaVersion", "1.1")))),
                "unsupported_schema_version");
        assertMalformed(
                received(queueMessage("bad-key", PortalIngestValidationFixture.goldenRequest())),
                "invalid_idempotency_key");
    }

    @Test
    void malformedDlqRedactsSecretLikeParsedIdentityValues() throws Exception {
        MetricIngestQueueMessage message = queueMessage(PortalIngestValidationFixture.IDEMPOTENCY_KEY);
        ObjectNode root = (ObjectNode) objectMapper.readTree(message.bodyJson());
        root.put("idempotencyKey", "pk_live_checkout.test-secret:orders-api:prod:orders-api-7f9c9c8c9d-x2p4k:20260508T010000Z");
        root.put("payloadHash", "https://sqs.example.test/source?credential=secret");
        ((ObjectNode) root.get("payload").get("application")).put("name", "Authorization-Bearer-token");
        Map<String, String> attributes = new LinkedHashMap<>(attributes(message));
        attributes.put("applicationName", "Authorization-Bearer-token");

        MetricIngestQueueProcessResult result = processor.process(received(objectMapper.writeValueAsString(root), attributes));

        assertThat(result.status()).isEqualTo(MetricIngestQueueProcessStatus.APPLICATION_DLQ);
        assertThat(result.dlqEnvelope()).hasValueSatisfying(envelope -> {
            assertThat(envelope.failureCategory()).isEqualTo("malformed");
            assertThat(envelope.toString()).doesNotContain(
                    "pk_live_checkout",
                    "test-secret",
                    "Authorization",
                    "Bearer",
                    "credential=secret",
                    "https://sqs");
        });
        verify(repository, never()).insert(any());
    }

    private void assertMalformed(MetricIngestReceivedMessage source, String failureCode) {
        MetricIngestQueueProcessResult result = processor.process(source);

        assertThat(result.status()).isEqualTo(MetricIngestQueueProcessStatus.APPLICATION_DLQ);
        assertThat(result.dlqEnvelope()).hasValueSatisfying(envelope -> {
            assertThat(envelope.failureCategory()).isEqualTo("malformed");
            assertThat(envelope.failureCode()).isEqualTo(failureCode);
            assertThat(envelope.workerAction()).isEqualTo("sent_to_application_dlq");
            assertThat(envelope.toString()).doesNotContain("Authorization", "Bearer", "token");
        });
        verify(repository, never()).insert(any());
    }

    private MetricIngestQueueMessage queueMessage(String idempotencyKey) throws Exception {
        return queueMessage(idempotencyKey, PortalIngestValidationFixture.goldenRequest());
    }

    private MetricIngestQueueMessage queueMessage(String idempotencyKey, IngestEnvelopeRequest request) {
        return new MetricIngestQueueMessageFactory(objectMapper, payloadHasher).build(
                new ValidatedIngestCandidate(
                        PortalIngestValidationFixture.VERIFIED_PROJECT,
                        idempotencyKey,
                        request),
                OffsetDateTime.parse("2026-05-08T01:00:31Z"),
                OffsetDateTime.parse("2026-05-08T01:00:31.120Z"));
    }

    private static IngestEnvelopeRequest request(java.util.function.Consumer<ObjectNode> mutation) throws Exception {
        return PortalIngestValidationFixture.requestWith(mutation);
    }

    private static MetricIngestReceivedMessage received(MetricIngestQueueMessage message) {
        return received(message.bodyJson(), attributes(message));
    }

    private static MetricIngestReceivedMessage received(String bodyJson, Map<String, String> attributes) {
        return MetricIngestReceivedMessage.fromBodyJson("source-message-1", "receipt-handle-1", bodyJson, attributes, 1);
    }

    private static Map<String, String> attributes(MetricIngestQueueMessage message) {
        return MetricIngestReceivedMessage.attributesFrom(message.attributes());
    }
}
