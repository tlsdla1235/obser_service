package com.observation.portal.domain.ingest.queue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * 직렬화된 queue body와 SQS attribute, size guard 계산 결과를 함께 운반하는 내부 모델이다.
 */
public record MetricIngestQueueMessage(
        QueuedMetricBucketMessage body,
        byte[] bodyBytes,
        List<MetricIngestMessageAttribute> attributes,
        long estimatedSizeBytes
) {

    /**
     * byte array와 attribute list를 방어적으로 복사해 publisher/test가 같은 message snapshot을 보게 한다.
     */
    public MetricIngestQueueMessage {
        Objects.requireNonNull(body, "body must not be null");
        bodyBytes = Objects.requireNonNull(bodyBytes, "bodyBytes must not be null").clone();
        attributes = List.copyOf(Objects.requireNonNull(attributes, "attributes must not be null"));
        if (estimatedSizeBytes < bodyBytes.length) {
            throw new IllegalArgumentException("estimatedSizeBytes must include bodyBytes");
        }
    }

    @Override
    public byte[] bodyBytes() {
        return bodyBytes.clone();
    }

    /**
     * publisher가 SQS `messageBody`로 보낼 compact UTF-8 JSON 문자열을 반환한다.
     */
    public String bodyJson() {
        return new String(bodyBytes, StandardCharsets.UTF_8);
    }
}
