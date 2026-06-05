package com.observation.portal.domain.ingest.queue;

import java.util.Objects;
import java.util.Optional;

/**
 * processor가 message별 persistence/DLQ 판단을 worker에 전달하는 sanitized result다.
 */
public record MetricIngestQueueProcessResult(
        MetricIngestQueueProcessStatus status,
        MetricIngestDlqEnvelope dlqEnvelopeValue,
        String failureCodeValue
) {

    /**
     * result에는 raw payload나 exception detail을 담지 않고 status, DLQ envelope, 안정적인 failure code만 보존한다.
     */
    public MetricIngestQueueProcessResult {
        Objects.requireNonNull(status, "status must not be null");
        if (status == MetricIngestQueueProcessStatus.APPLICATION_DLQ) {
            Objects.requireNonNull(dlqEnvelopeValue, "dlqEnvelope must not be null for APPLICATION_DLQ");
        }
    }

    public static MetricIngestQueueProcessResult inserted() {
        return new MetricIngestQueueProcessResult(MetricIngestQueueProcessStatus.INSERTED, null, null);
    }

    public static MetricIngestQueueProcessResult duplicateNoop() {
        return new MetricIngestQueueProcessResult(MetricIngestQueueProcessStatus.DUPLICATE_NOOP, null, null);
    }

    public static MetricIngestQueueProcessResult applicationDlq(MetricIngestDlqEnvelope envelope) {
        return new MetricIngestQueueProcessResult(
                MetricIngestQueueProcessStatus.APPLICATION_DLQ,
                envelope,
                envelope.failureCode());
    }

    public static MetricIngestQueueProcessResult transientFailure(String failureCode) {
        return new MetricIngestQueueProcessResult(
                MetricIngestQueueProcessStatus.TRANSIENT_FAILURE,
                null,
                requireText(failureCode, "failureCode"));
    }

    /**
     * DLQ action이 필요한 경우 전송할 envelope를 반환한다.
     */
    public Optional<MetricIngestDlqEnvelope> dlqEnvelope() {
        return Optional.ofNullable(dlqEnvelopeValue);
    }

    /**
     * transient/result diagnostic code가 있는 경우 반환한다.
     */
    public Optional<String> failureCode() {
        return Optional.ofNullable(failureCodeValue);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
