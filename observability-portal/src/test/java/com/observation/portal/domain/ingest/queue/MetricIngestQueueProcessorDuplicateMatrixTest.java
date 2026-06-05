package com.observation.portal.domain.ingest.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.observation.portal.domain.bucket.model.AcceptedMetricBucketIdentity;
import com.observation.portal.domain.bucket.model.AcceptedMetricBucketWriteCommand;
import com.observation.portal.domain.bucket.repository.MetricBucketRepository;
import com.observation.portal.domain.ingest.service.IngestPayloadHasher;
import com.observation.portal.domain.ingest.service.PortalIngestValidationFixture;
import com.observation.portal.domain.ingest.service.ValidatedIngestCandidate;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetricIngestQueueProcessorDuplicateMatrixTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-05-08T01:00:35Z"), ZoneOffset.UTC);
    private static final UUID BUCKET_ID = UUID.fromString("00000000-0000-0000-0000-00000000b123");
    private static final UUID APPLICATION_ID = UUID.fromString("00000000-0000-0000-0000-00000000a123");
    private static final UUID APPLICATION_INSTANCE_ID = UUID.fromString("00000000-0000-0000-0000-00000000a124");
    private static final String DIFFERENT_STORED_HASH =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final IngestPayloadHasher payloadHasher = new IngestPayloadHasher(objectMapper);

    @Test
    void sameIdempotencyKeyAndSamePayloadHashIsDuplicateNoopSuccess() throws Exception {
        MetricBucketRepository repository = mock(MetricBucketRepository.class);
        MetricIngestQueueMessage message = queueMessage();
        when(repository.findIdentityByProjectIdAndIdempotencyKey(
                PortalIngestValidationFixture.VERIFIED_PROJECT.projectId(),
                PortalIngestValidationFixture.IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(identity(
                        PortalIngestValidationFixture.IDEMPOTENCY_KEY,
                        message.body().payloadHash(),
                        "2026-05-08T01:00:00Z")));
        MetricIngestQueueProcessor processor = processor(repository);

        MetricIngestQueueProcessResult result = processor.process(received(message));

        assertThat(result.status()).isEqualTo(MetricIngestQueueProcessStatus.DUPLICATE_NOOP);
        assertThat(result.dlqEnvelope()).isEmpty();
        verify(repository, never()).insert(any(AcceptedMetricBucketWriteCommand.class));
    }

    @Test
    void sameIdempotencyKeyAndDifferentPayloadHashIsConflictDlq() throws Exception {
        MetricBucketRepository repository = mock(MetricBucketRepository.class);
        MetricIngestQueueMessage message = queueMessage();
        when(repository.findIdentityByProjectIdAndIdempotencyKey(
                PortalIngestValidationFixture.VERIFIED_PROJECT.projectId(),
                PortalIngestValidationFixture.IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(identity(
                        PortalIngestValidationFixture.IDEMPOTENCY_KEY,
                        DIFFERENT_STORED_HASH,
                        "2026-05-08T01:00:00Z")));
        MetricIngestQueueProcessor processor = processor(repository);

        MetricIngestQueueProcessResult result = processor.process(received(message));

        assertThat(result.status()).isEqualTo(MetricIngestQueueProcessStatus.APPLICATION_DLQ);
        assertThat(result.dlqEnvelope()).hasValueSatisfying(envelope -> {
            assertThat(envelope.failureCategory()).isEqualTo("conflict");
            assertThat(envelope.failureCode()).isEqualTo("idempotency_payload_conflict");
            assertThat(envelope.storedPayloadHash()).isEqualTo(DIFFERENT_STORED_HASH);
            assertThat(envelope.storedBucketId()).isEqualTo(BUCKET_ID);
        });
        verify(repository, never()).insert(any(AcceptedMetricBucketWriteCommand.class));
    }

    @Test
    void sameInstanceBucketAndDifferentIdempotencyKeyIsConflictDlq() throws Exception {
        MetricBucketRepository repository = mock(MetricBucketRepository.class);
        MetricIngestQueueMessage message = queueMessage();
        when(repository.findIdentityByProjectIdAndIdempotencyKey(
                PortalIngestValidationFixture.VERIFIED_PROJECT.projectId(),
                PortalIngestValidationFixture.IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());
        when(repository.findIdentityByProjectApplicationInstanceAndBucketStartUtc(
                PortalIngestValidationFixture.VERIFIED_PROJECT.projectId(),
                "orders-api",
                "prod",
                "orders-api-7f9c9c8c9d-x2p4k",
                OffsetDateTime.parse("2026-05-08T01:00:00Z")))
                .thenReturn(Optional.of(identity(
                        "project-123:orders-api:prod:orders-api-7f9c9c8c9d-x2p4k:other",
                        "stored-hash",
                        "2026-05-08T01:00:00Z")));
        MetricIngestQueueProcessor processor = processor(repository);

        MetricIngestQueueProcessResult result = processor.process(received(message));

        assertThat(result.status()).isEqualTo(MetricIngestQueueProcessStatus.APPLICATION_DLQ);
        assertThat(result.dlqEnvelope()).hasValueSatisfying(envelope -> {
            assertThat(envelope.failureCategory()).isEqualTo("conflict");
            assertThat(envelope.failureCode()).isEqualTo("instance_bucket_identity_conflict");
            assertThat(envelope.storedBucketId()).isEqualTo(BUCKET_ID);
        });
        verify(repository, never()).insert(any(AcceptedMetricBucketWriteCommand.class));
    }

    private MetricIngestQueueProcessor processor(MetricBucketRepository repository) {
        return new MetricIngestQueueProcessor(objectMapper, payloadHasher, repository, FIXED_CLOCK);
    }

    private MetricIngestQueueMessage queueMessage() throws Exception {
        return new MetricIngestQueueMessageFactory(objectMapper, payloadHasher).build(
                new ValidatedIngestCandidate(
                        PortalIngestValidationFixture.VERIFIED_PROJECT,
                        PortalIngestValidationFixture.IDEMPOTENCY_KEY,
                        PortalIngestValidationFixture.goldenRequest()),
                OffsetDateTime.parse("2026-05-08T01:00:31Z"),
                OffsetDateTime.parse("2026-05-08T01:00:31.120Z"));
    }

    private static MetricIngestReceivedMessage received(MetricIngestQueueMessage message) {
        return MetricIngestReceivedMessage.fromBodyJson(
                "source-message-1",
                "receipt-handle-1",
                message.bodyJson(),
                MetricIngestReceivedMessage.attributesFrom(message.attributes()),
                1);
    }

    private static AcceptedMetricBucketIdentity identity(
            String idempotencyKey,
            String payloadHash,
            String bucketStartUtc) {
        OffsetDateTime start = OffsetDateTime.parse(bucketStartUtc);
        return new AcceptedMetricBucketIdentity(
                BUCKET_ID,
                PortalIngestValidationFixture.VERIFIED_PROJECT.projectId(),
                APPLICATION_ID,
                APPLICATION_INSTANCE_ID,
                idempotencyKey,
                payloadHash,
                start,
                start.plusSeconds(30),
                OffsetDateTime.parse("2026-05-08T01:00:31Z"));
    }
}
