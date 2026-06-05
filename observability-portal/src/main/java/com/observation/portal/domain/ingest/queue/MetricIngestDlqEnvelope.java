package com.observation.portal.domain.ingest.queue;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * worker가 malformed/conflict source message를 application-level DLQ에 보낼 때 쓰는 sanitized envelope다.
 *
 * <p>원본 payload/body JSON, queue URL, raw secret, exception message는 필드로 갖지 않는다.</p>
 */
public record MetricIngestDlqEnvelope(
        String dlqEnvelopeVersion,
        String failureCategory,
        String failureCode,
        String sourceMessageId,
        int receiveCount,
        OffsetDateTime occurredAt,
        String messageVersion,
        UUID projectId,
        String applicationName,
        String environment,
        String instanceName,
        OffsetDateTime bucketStartUtc,
        OffsetDateTime bucketEndUtc,
        String idempotencyKey,
        String payloadHash,
        String storedPayloadHash,
        UUID storedBucketId,
        String workerAction
) {

    public static final String VERSION = "1";
    public static final String ACTION_SENT_TO_APPLICATION_DLQ = "sent_to_application_dlq";
    private static final Pattern SAFE_SHORT_TOKEN = Pattern.compile("[A-Za-z0-9._~-]{1,128}");
    private static final Pattern SAFE_IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._~:-]{1,256}");
    private static final Pattern SHA_256_HEX = Pattern.compile("(?i)[0-9a-f]{64}");

    /**
     * DLQ 조사에 필요한 allow-list metadata만 보존하고 category/code/action은 필수로 검증한다.
     */
    public MetricIngestDlqEnvelope {
        dlqEnvelopeVersion = requireText(dlqEnvelopeVersion, "dlqEnvelopeVersion");
        failureCategory = requireText(failureCategory, "failureCategory");
        failureCode = requireText(failureCode, "failureCode");
        sourceMessageId = requireText(sourceMessageId, "sourceMessageId");
        if (receiveCount < 1) {
            throw new IllegalArgumentException("receiveCount must be at least 1");
        }
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        workerAction = requireText(workerAction, "workerAction");
    }

    /**
     * raw body 없이 source message와 parsed identity 후보만 사용해 DLQ envelope를 만든다.
     */
    public static MetricIngestDlqEnvelope of(
            String failureCategory,
            String failureCode,
            MetricIngestReceivedMessage source,
            OffsetDateTime occurredAt,
            QueuedMetricBucketMessage message,
            String storedPayloadHash,
            UUID storedBucketId) {
        return new MetricIngestDlqEnvelope(
                VERSION,
                failureCategory,
                failureCode,
                source.messageId(),
                source.receiveCount(),
                occurredAt,
                safeShortToken(message == null ? null : message.messageVersion()),
                message == null ? null : message.projectId(),
                message == null || message.payload() == null || message.payload().application() == null
                        ? null
                        : safeShortToken(message.payload().application().name()),
                message == null || message.payload() == null || message.payload().application() == null
                        ? null
                        : safeShortToken(message.payload().application().environment()),
                message == null || message.payload() == null || message.payload().application() == null
                        ? null
                        : safeShortToken(message.payload().application().instance()),
                parseOffsetDateTime(message == null || message.payload() == null || message.payload().bucket() == null
                        ? null
                        : message.payload().bucket().startUtc()),
                parseOffsetDateTime(message == null || message.payload() == null || message.payload().bucket() == null
                        ? null
                        : message.payload().bucket().endUtc()),
                safeIdempotencyKey(message == null ? null : message.idempotencyKey()),
                safePayloadHash(message == null ? null : message.payloadHash()),
                safePayloadHash(storedPayloadHash),
                storedBucketId,
                ACTION_SENT_TO_APPLICATION_DLQ);
    }

    private static OffsetDateTime parseOffsetDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static String safeShortToken(String value) {
        return safeString(value, SAFE_SHORT_TOKEN);
    }

    private static String safeIdempotencyKey(String value) {
        return safeString(value, SAFE_IDEMPOTENCY_KEY);
    }

    private static String safePayloadHash(String value) {
        return value == null || SHA_256_HEX.matcher(value).matches() ? value : null;
    }

    /**
     * DLQ envelope에 복사되는 parsed string 값은 검증 전 입력일 수 있으므로 secret/URL-like 값은 버린다.
     */
    private static String safeString(String value, Pattern allowedPattern) {
        if (value == null || value.isBlank() || !allowedPattern.matcher(value).matches() || looksSensitive(value)) {
            return null;
        }
        return value;
    }

    private static boolean looksSensitive(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("authorization")
                || normalized.contains("bearer")
                || normalized.contains("credential")
                || normalized.contains("webhook")
                || normalized.contains("aws")
                || normalized.contains("sqs")
                || normalized.startsWith("pk_live")
                || normalized.startsWith("pk_test")
                || normalized.startsWith("sk_")
                || normalized.contains("://");
    }
}
