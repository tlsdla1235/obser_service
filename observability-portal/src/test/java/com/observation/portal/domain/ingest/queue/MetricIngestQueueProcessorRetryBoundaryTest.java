package com.observation.portal.domain.ingest.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.observation.portal.domain.bucket.model.AcceptedMetricBucketReceipt;
import com.observation.portal.domain.bucket.model.AcceptedMetricBucketWriteCommand;
import com.observation.portal.domain.bucket.repository.MetricBucketRepository;
import com.observation.portal.domain.ingest.service.IngestPayloadHasher;
import com.observation.portal.domain.ingest.service.PortalIngestValidationFixture;
import com.observation.portal.domain.ingest.service.ValidatedIngestCandidate;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

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

class MetricIngestQueueProcessorRetryBoundaryTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-05-08T01:00:35Z"), ZoneOffset.UTC);

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final IngestPayloadHasher payloadHasher = new IngestPayloadHasher(objectMapper);

    @Test
    void transientRepositoryFailureDoesNotCreateDlqResult() throws Exception {
        MetricBucketRepository repository = mock(MetricBucketRepository.class);
        when(repository.findIdentityByProjectIdAndIdempotencyKey(any(), any()))
                .thenReturn(Optional.empty());
        when(repository.findIdentityByProjectApplicationInstanceAndBucketStartUtc(any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(repository.insert(any(AcceptedMetricBucketWriteCommand.class)))
                .thenThrow(new DataAccessResourceFailureException("jdbc url secret"));
        MetricIngestQueueProcessor processor = new MetricIngestQueueProcessor(
                objectMapper,
                payloadHasher,
                repository,
                FIXED_CLOCK);

        MetricIngestQueueProcessResult result = processor.process(received(queueMessage()));

        assertThat(result.status()).isEqualTo(MetricIngestQueueProcessStatus.TRANSIENT_FAILURE);
        assertThat(result.dlqEnvelope()).isEmpty();
        assertThat(result.failureCode()).contains("database_transient_failure");
    }

    @Test
    void validNewMessageInsertsUsingRepositoryCommand() throws Exception {
        MetricBucketRepository repository = mock(MetricBucketRepository.class);
        when(repository.findIdentityByProjectIdAndIdempotencyKey(any(), any()))
                .thenReturn(Optional.empty());
        when(repository.findIdentityByProjectApplicationInstanceAndBucketStartUtc(any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(repository.insert(any(AcceptedMetricBucketWriteCommand.class)))
                .thenReturn(new AcceptedMetricBucketReceipt(
                        UUID.fromString("00000000-0000-0000-0000-00000000b123"),
                        OffsetDateTime.parse("2026-05-08T01:00:35Z")));
        MetricIngestQueueProcessor processor = new MetricIngestQueueProcessor(
                objectMapper,
                payloadHasher,
                repository,
                FIXED_CLOCK);

        MetricIngestQueueProcessResult result = processor.process(received(queueMessage()));

        assertThat(result.status()).isEqualTo(MetricIngestQueueProcessStatus.INSERTED);
        assertThat(result.dlqEnvelope()).isEmpty();
        verify(repository).insert(any(AcceptedMetricBucketWriteCommand.class));
    }

    @Test
    void malformedDoesNotCallRepositoryInsert() {
        MetricBucketRepository repository = mock(MetricBucketRepository.class);
        MetricIngestQueueProcessor processor = new MetricIngestQueueProcessor(
                objectMapper,
                payloadHasher,
                repository,
                FIXED_CLOCK);

        MetricIngestQueueProcessResult result = processor.process(MetricIngestReceivedMessage.fromBodyJson(
                "source-message-1",
                "receipt-handle-1",
                "{",
                java.util.Map.of(),
                1));

        assertThat(result.status()).isEqualTo(MetricIngestQueueProcessStatus.APPLICATION_DLQ);
        verify(repository, never()).insert(any(AcceptedMetricBucketWriteCommand.class));
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
}
