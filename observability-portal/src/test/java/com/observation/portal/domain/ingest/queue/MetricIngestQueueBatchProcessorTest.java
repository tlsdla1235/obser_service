package com.observation.portal.domain.ingest.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.observation.portal.domain.bucket.model.AcceptedMetricBucketBatchItemResult;
import com.observation.portal.domain.bucket.model.AcceptedMetricBucketBatchWriteResult;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetricIngestQueueBatchProcessorTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-05-08T01:00:35Z"), ZoneOffset.UTC);

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final IngestPayloadHasher payloadHasher = new IngestPayloadHasher(objectMapper);

    @Test
    void batchProcessingKeepsMalformedOutOfWriterAndMapsWriterResultsPerSourceMessage() throws Exception {
        MetricBucketRepository repository = mock(MetricBucketRepository.class);
        when(repository.insertBatch(anyList())).thenAnswer(invocation -> {
            List<AcceptedMetricBucketWriteCommand> commands = invocation.getArgument(0);
            return new AcceptedMetricBucketBatchWriteResult(List.of(
                    AcceptedMetricBucketBatchItemResult.inserted(commands.get(0), UUID.randomUUID()),
                    AcceptedMetricBucketBatchItemResult.duplicateNoop(commands.get(1)),
                    AcceptedMetricBucketBatchItemResult.conflict(
                            commands.get(2),
                            "idempotency_payload_conflict",
                            identity(commands.get(2))),
                    AcceptedMetricBucketBatchItemResult.transientFailure(commands.get(3), "database_transient_failure")
            ), 3);
        });
        MetricIngestQueueProcessor processor = new MetricIngestQueueProcessor(
                objectMapper,
                payloadHasher,
                repository,
                FIXED_CLOCK);
        MetricIngestQueueMessage message = queueMessage();

        List<MetricIngestQueueProcessResult> results = processor.processBatch(List.of(
                received("source-inserted", message),
                received("source-duplicate", message),
                received("source-conflict", message),
                received("source-transient", message),
                MetricIngestReceivedMessage.fromBodyJson("source-malformed", "receipt-malformed", "{", java.util.Map.of(), 1)));

        assertThat(results).extracting(MetricIngestQueueProcessResult::status)
                .containsExactly(
                        MetricIngestQueueProcessStatus.INSERTED,
                        MetricIngestQueueProcessStatus.DUPLICATE_NOOP,
                        MetricIngestQueueProcessStatus.APPLICATION_DLQ,
                        MetricIngestQueueProcessStatus.TRANSIENT_FAILURE,
                        MetricIngestQueueProcessStatus.APPLICATION_DLQ);
        assertThat(results.get(2).dlqEnvelope()).hasValueSatisfying(envelope ->
                assertThat(envelope.failureCode()).isEqualTo("idempotency_payload_conflict"));
        verify(repository).insertBatch(org.mockito.ArgumentMatchers.argThat(commands -> commands.size() == 4));
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

    private static MetricIngestReceivedMessage received(String sourceMessageId, MetricIngestQueueMessage message) {
        return MetricIngestReceivedMessage.fromBodyJson(
                sourceMessageId,
                "receipt-" + sourceMessageId,
                message.bodyJson(),
                MetricIngestReceivedMessage.attributesFrom(message.attributes()),
                1);
    }

    private static AcceptedMetricBucketIdentity identity(AcceptedMetricBucketWriteCommand command) {
        return new AcceptedMetricBucketIdentity(
                UUID.fromString("00000000-0000-0000-0000-00000000b123"),
                command.projectId(),
                UUID.fromString("00000000-0000-0000-0000-00000000a123"),
                UUID.fromString("00000000-0000-0000-0000-00000000a124"),
                command.idempotencyKey(),
                "stored-hash",
                command.bucketStartUtc(),
                command.bucketEndUtc(),
                command.acceptedAt());
    }
}
