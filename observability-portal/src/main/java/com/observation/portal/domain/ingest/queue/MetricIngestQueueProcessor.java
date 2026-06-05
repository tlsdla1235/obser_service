package com.observation.portal.domain.ingest.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.portal.domain.bucket.model.AcceptedMetricBucketIdentity;
import com.observation.portal.domain.bucket.model.AcceptedMetricBucketReceipt;
import com.observation.portal.domain.bucket.model.AcceptedMetricBucketWriteCommand;
import com.observation.portal.domain.bucket.repository.MetricBucketRepository;
import com.observation.portal.domain.catalog.model.ProjectStatus;
import com.observation.portal.domain.ingest.model.IngestEnvelopeRequest;
import com.observation.portal.domain.ingest.service.IngestAcceptanceService;
import com.observation.portal.domain.ingest.service.IngestPayloadHasher;
import com.observation.portal.domain.ingest.service.IngestValidationError;
import com.observation.portal.domain.ingest.service.ValidatedIngestCandidate;
import com.observation.portal.domain.ingest.service.VerifiedProject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * queue source message를 재검증하고 기존 accepted bucket repository insert path에 연결하는 worker processor다.
 */
@Component
public class MetricIngestQueueProcessor {

    private static final Map<String, String> ATTRIBUTE_BODY_FIELDS = Map.of(
            "messageVersion", "messageVersion",
            "projectId", "projectId",
            "schemaVersion", "payload.schemaVersion",
            "bucketStartUtc", "payload.bucket.startUtc",
            "bucketEndUtc", "payload.bucket.endUtc",
            "applicationName", "payload.application.name",
            "environment", "payload.application.environment",
            "instanceName", "payload.application.instance");
    private static final String IDEMPOTENCY_CONSTRAINT = "uk_buckets_project_idempotency_key";
    private static final String INSTANCE_BUCKET_CONSTRAINT = "uk_buckets_instance_bucket_start";

    private final ObjectMapper objectMapper;
    private final IngestPayloadHasher payloadHasher;
    private final MetricBucketRepository metricBucketRepository;
    private final Clock clock;

    /**
     * portal ObjectMapper, 기존 payload hasher, accepted bucket repository를 공유해 worker MVP processor를 구성한다.
     */
    public MetricIngestQueueProcessor(
            ObjectMapper objectMapper,
            IngestPayloadHasher payloadHasher,
            MetricBucketRepository metricBucketRepository,
            Clock clock) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.payloadHasher = Objects.requireNonNull(payloadHasher, "payloadHasher must not be null");
        this.metricBucketRepository = Objects.requireNonNull(
                metricBucketRepository,
                "metricBucketRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * production Spring context에서 UTC clock을 사용하는 processor를 구성한다.
     */
    @Autowired
    public MetricIngestQueueProcessor(
            ObjectMapper objectMapper,
            IngestPayloadHasher payloadHasher,
            MetricBucketRepository metricBucketRepository) {
        this(objectMapper, payloadHasher, metricBucketRepository, Clock.systemUTC());
    }

    /**
     * 한 source message를 독립적으로 처리하고 worker가 delete/DLQ/no-delete를 판단할 수 있는 result를 반환한다.
     */
    public MetricIngestQueueProcessResult process(MetricIngestReceivedMessage source) {
        MetricIngestReceivedMessage requiredSource = Objects.requireNonNull(source, "source must not be null");
        ParsedMessage parsed = parse(requiredSource);
        if (parsed.failureCode().isPresent()) {
            return malformed(requiredSource, parsed.failureCode().orElseThrow(), parsed.message().orElse(null));
        }

        QueuedMetricBucketMessage message = parsed.message().orElseThrow();
        Optional<String> messageFailure = validateMessageShape(requiredSource, message);
        if (messageFailure.isPresent()) {
            return malformed(requiredSource, messageFailure.orElseThrow(), message);
        }

        IngestEnvelopeRequest payload = message.payload();
        String recalculatedHash;
        try {
            recalculatedHash = payloadHasher.sha256(payload);
        } catch (RuntimeException exception) {
            return MetricIngestQueueProcessResult.transientFailure("payload_hash_recalculation_failed");
        }
        if (!message.payloadHash().equals(recalculatedHash)) {
            return malformed(requiredSource, "payload_hash_mismatch", message);
        }

        List<IngestValidationError> validationErrors =
                IngestAcceptanceService.validateEnvelope(payload, message.idempotencyKey());
        if (!validationErrors.isEmpty()) {
            return malformed(requiredSource, validationErrors.get(0).code(), message);
        }

        ValidatedIngestCandidate candidate = new ValidatedIngestCandidate(
                new VerifiedProject(message.projectId(), message.projectName(), ProjectStatus.ACTIVE),
                message.idempotencyKey(),
                payload);
        AcceptedMetricBucketWriteCommand command = AcceptedMetricBucketWriteCommand.from(
                candidate,
                message.payloadHash(),
                OffsetDateTime.now(clock));

        Optional<MetricIngestQueueProcessResult> preExisting = classifyExisting(requiredSource, message, command);
        if (preExisting.isPresent()) {
            return preExisting.orElseThrow();
        }

        try {
            AcceptedMetricBucketReceipt ignored = metricBucketRepository.insert(command);
            return MetricIngestQueueProcessResult.inserted();
        } catch (DataIntegrityViolationException exception) {
            return classifyAfterIntegrityViolation(requiredSource, message, command, exception)
                    .orElseGet(() -> MetricIngestQueueProcessResult.transientFailure("database_integrity_retry_pending"));
        } catch (DataAccessException exception) {
            return MetricIngestQueueProcessResult.transientFailure("database_transient_failure");
        } catch (RuntimeException exception) {
            return MetricIngestQueueProcessResult.transientFailure("database_transient_failure");
        }
    }

    private ParsedMessage parse(MetricIngestReceivedMessage source) {
        try {
            return ParsedMessage.success(objectMapper.readValue(source.bodyBytes(), QueuedMetricBucketMessage.class));
        } catch (IOException | IllegalArgumentException exception) {
            return ParsedMessage.failure("invalid_json");
        }
    }

    private Optional<String> validateMessageShape(
            MetricIngestReceivedMessage source,
            QueuedMetricBucketMessage message) {
        if (!MetricIngestQueueMessageFactory.MESSAGE_VERSION.equals(message.messageVersion())) {
            return Optional.of("unsupported_message_version");
        }
        if (message.payload() == null
                || message.payload().application() == null
                || message.payload().bucket() == null) {
            return Optional.of("missing_required_body_field");
        }
        for (String attributeName : ATTRIBUTE_BODY_FIELDS.keySet()) {
            if (!source.attributes().containsKey(attributeName)) {
                return Optional.of("missing_message_attribute");
            }
        }
        if (!source.attributes().get("messageVersion").equals(message.messageVersion())
                || !source.attributes().get("projectId").equals(message.projectId().toString())
                || !source.attributes().get("schemaVersion").equals(message.payload().schemaVersion())
                || !source.attributes().get("bucketStartUtc").equals(message.payload().bucket().startUtc())
                || !source.attributes().get("bucketEndUtc").equals(message.payload().bucket().endUtc())
                || !source.attributes().get("applicationName").equals(message.payload().application().name())
                || !source.attributes().get("environment").equals(message.payload().application().environment())
                || !source.attributes().get("instanceName").equals(message.payload().application().instance())) {
            return Optional.of("body_attribute_mismatch");
        }
        return Optional.empty();
    }

    private Optional<MetricIngestQueueProcessResult> classifyExisting(
            MetricIngestReceivedMessage source,
            QueuedMetricBucketMessage message,
            AcceptedMetricBucketWriteCommand command) {
        Optional<AcceptedMetricBucketIdentity> byKey = metricBucketRepository.findIdentityByProjectIdAndIdempotencyKey(
                command.projectId(),
                command.idempotencyKey());
        if (byKey.isPresent()) {
            return Optional.of(classifyIdempotencyIdentity(source, message, byKey.orElseThrow()));
        }

        Optional<AcceptedMetricBucketIdentity> byInstance =
                metricBucketRepository.findIdentityByProjectApplicationInstanceAndBucketStartUtc(
                        command.projectId(),
                        command.applicationName(),
                        command.environment(),
                        command.instanceName(),
                        command.bucketStartUtc());
        if (byInstance.isPresent()
                && !byInstance.orElseThrow().idempotencyKey().equals(command.idempotencyKey())) {
            return Optional.of(conflict(
                    source,
                    "instance_bucket_identity_conflict",
                    message,
                    byInstance.orElseThrow()));
        }
        return Optional.empty();
    }

    private Optional<MetricIngestQueueProcessResult> classifyAfterIntegrityViolation(
            MetricIngestReceivedMessage source,
            QueuedMetricBucketMessage message,
            AcceptedMetricBucketWriteCommand command,
            DataIntegrityViolationException exception) {
        Optional<AcceptedMetricBucketIdentity> byKey = metricBucketRepository.findIdentityByProjectIdAndIdempotencyKey(
                command.projectId(),
                command.idempotencyKey());
        if (byKey.isPresent()) {
            return Optional.of(classifyIdempotencyIdentity(source, message, byKey.orElseThrow()));
        }

        Optional<AcceptedMetricBucketIdentity> byInstance =
                metricBucketRepository.findIdentityByProjectApplicationInstanceAndBucketStartUtc(
                        command.projectId(),
                        command.applicationName(),
                        command.environment(),
                        command.instanceName(),
                        command.bucketStartUtc());
        if (byInstance.isPresent()
                && !byInstance.orElseThrow().idempotencyKey().equals(command.idempotencyKey())) {
            return Optional.of(conflict(
                    source,
                    "instance_bucket_identity_conflict",
                    message,
                    byInstance.orElseThrow()));
        }

        if (containsConstraint(exception, IDEMPOTENCY_CONSTRAINT)
                || containsConstraint(exception, INSTANCE_BUCKET_CONSTRAINT)) {
            return Optional.of(MetricIngestQueueProcessResult.transientFailure("database_integrity_reread_missing"));
        }
        return Optional.empty();
    }

    private MetricIngestQueueProcessResult classifyIdempotencyIdentity(
            MetricIngestReceivedMessage source,
            QueuedMetricBucketMessage message,
            AcceptedMetricBucketIdentity identity) {
        if (identity.payloadHash().equals(message.payloadHash())) {
            return MetricIngestQueueProcessResult.duplicateNoop();
        }
        return conflict(source, "idempotency_payload_conflict", message, identity);
    }

    private MetricIngestQueueProcessResult malformed(
            MetricIngestReceivedMessage source,
            String failureCode,
            QueuedMetricBucketMessage message) {
        return MetricIngestQueueProcessResult.applicationDlq(MetricIngestDlqEnvelope.of(
                "malformed",
                failureCode,
                source,
                OffsetDateTime.now(clock),
                message,
                null,
                null));
    }

    private MetricIngestQueueProcessResult conflict(
            MetricIngestReceivedMessage source,
            String failureCode,
            QueuedMetricBucketMessage message,
            AcceptedMetricBucketIdentity identity) {
        return MetricIngestQueueProcessResult.applicationDlq(MetricIngestDlqEnvelope.of(
                "conflict",
                failureCode,
                source,
                OffsetDateTime.now(clock),
                message,
                identity.payloadHash(),
                identity.bucketId()));
    }

    private static boolean containsConstraint(Throwable throwable, String constraintName) {
        Throwable cursor = throwable;
        while (cursor != null) {
            String message = cursor.getMessage();
            if (message != null && message.contains(constraintName)) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private record ParsedMessage(Optional<QueuedMetricBucketMessage> message, Optional<String> failureCode) {

        private static ParsedMessage success(QueuedMetricBucketMessage message) {
            return new ParsedMessage(Optional.of(message), Optional.empty());
        }

        private static ParsedMessage failure(String failureCode) {
            return new ParsedMessage(Optional.empty(), Optional.of(failureCode));
        }
    }
}
