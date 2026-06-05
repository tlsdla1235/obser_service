package com.observation.portal.domain.ingest.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.portal.domain.ingest.model.IngestEnvelopeRequest;
import com.observation.portal.domain.ingest.service.IngestPayloadHasher;
import com.observation.portal.domain.ingest.service.ValidatedIngestCandidate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 검증 완료 ingest candidate를 messageVersion 1 queue body와 SQS attribute로 변환한다.
 */
@Component
public class MetricIngestQueueMessageFactory {

    public static final String MESSAGE_VERSION = "1";
    private static final String ATTRIBUTE_TYPE_STRING = "String";

    private final ObjectMapper objectMapper;
    private final IngestPayloadHasher payloadHasher;

    /**
     * portal ObjectMapper와 기존 payload hasher를 공유해 worker 재검증 가능한 message를 만든다.
     */
    public MetricIngestQueueMessageFactory(ObjectMapper objectMapper, IngestPayloadHasher payloadHasher) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.payloadHasher = Objects.requireNonNull(payloadHasher, "payloadHasher must not be null");
    }

    /**
     * queue metadata를 제외한 payload hash와 compact JSON body, 8개 allow-list attribute를 생성한다.
     */
    public MetricIngestQueueMessage build(
            ValidatedIngestCandidate candidate,
            OffsetDateTime receivedAt,
            OffsetDateTime enqueuedAt) {
        try {
            ValidatedIngestCandidate requiredCandidate = Objects.requireNonNull(
                    candidate,
                    "candidate must not be null");
            IngestEnvelopeRequest payload = requiredCandidate.payload();
            QueuedMetricBucketMessage body = new QueuedMetricBucketMessage(
                    MESSAGE_VERSION,
                    requiredCandidate.verifiedProject().projectId(),
                    requiredCandidate.verifiedProject().projectName(),
                    requiredCandidate.idempotencyKey(),
                    payloadHasher.sha256(payload),
                    Objects.requireNonNull(receivedAt, "receivedAt must not be null"),
                    Objects.requireNonNull(enqueuedAt, "enqueuedAt must not be null"),
                    payload);
            byte[] bodyBytes = objectMapper.writeValueAsBytes(body);
            List<MetricIngestMessageAttribute> attributes = attributes(body);
            return new MetricIngestQueueMessage(body, bodyBytes, attributes, estimateSizeBytes(bodyBytes, attributes));
        } catch (MetricIngestMessageBuildException exception) {
            throw exception;
        } catch (JsonProcessingException exception) {
            throw new MetricIngestMessageBuildException("queue_message_serialization_failed", exception);
        } catch (RuntimeException exception) {
            throw new MetricIngestMessageBuildException("queue_message_build_failed", exception);
        }
    }

    private static List<MetricIngestMessageAttribute> attributes(QueuedMetricBucketMessage body) {
        IngestEnvelopeRequest payload = body.payload();
        return List.of(
                attribute("messageVersion", body.messageVersion()),
                attribute("projectId", body.projectId().toString()),
                attribute("schemaVersion", payload.schemaVersion()),
                attribute("bucketStartUtc", payload.bucket().startUtc()),
                attribute("bucketEndUtc", payload.bucket().endUtc()),
                attribute("applicationName", payload.application().name()),
                attribute("environment", payload.application().environment()),
                attribute("instanceName", payload.application().instance()));
    }

    private static MetricIngestMessageAttribute attribute(String name, String value) {
        return new MetricIngestMessageAttribute(name, ATTRIBUTE_TYPE_STRING, value);
    }

    private static long estimateSizeBytes(byte[] bodyBytes, List<MetricIngestMessageAttribute> attributes) {
        long size = bodyBytes.length;
        for (MetricIngestMessageAttribute attribute : attributes) {
            size += utf8Length(attribute.name());
            size += utf8Length(attribute.dataType());
            size += utf8Length(attribute.stringValue());
        }
        return size;
    }

    private static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}
