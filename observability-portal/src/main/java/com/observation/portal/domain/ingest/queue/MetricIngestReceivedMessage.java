package com.observation.portal.domain.ingest.queue;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * worker가 queue backend에서 받은 source message의 최소 처리 정보를 담는 내부 모델이다.
 *
 * <p>delete에는 receipt handle만 사용하고, DLQ/log/result에는 raw body를 직접 전달하지 않도록 경계를 분리한다.</p>
 */
public record MetricIngestReceivedMessage(
        String messageId,
        String receiptHandle,
        byte[] bodyBytes,
        Map<String, String> attributes,
        int receiveCount
) {

    /**
     * source message id, receipt handle, body bytes, sanitized attribute snapshot, receive count를 보존한다.
     */
    public MetricIngestReceivedMessage {
        messageId = requireText(messageId, "messageId");
        receiptHandle = requireText(receiptHandle, "receiptHandle");
        bodyBytes = Objects.requireNonNull(bodyBytes, "bodyBytes must not be null").clone();
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes must not be null"));
        if (receiveCount < 1) {
            throw new IllegalArgumentException("receiveCount must be at least 1");
        }
    }

    /**
     * unit/fake/SQS boundary에서 source body 문자열을 UTF-8 bytes로 고정해 received message를 만든다.
     */
    public static MetricIngestReceivedMessage fromBodyJson(
            String messageId,
            String receiptHandle,
            String bodyJson,
            Map<String, String> attributes,
            int receiveCount) {
        return new MetricIngestReceivedMessage(
                messageId,
                receiptHandle,
                Objects.requireNonNull(bodyJson, "bodyJson must not be null").getBytes(StandardCharsets.UTF_8),
                attributes,
                receiveCount);
    }

    /**
     * queue message factory attribute list를 worker cross-check용 name/value map으로 변환한다.
     */
    public static Map<String, String> attributesFrom(List<MetricIngestMessageAttribute> attributes) {
        Map<String, String> values = new LinkedHashMap<>();
        for (MetricIngestMessageAttribute attribute : attributes) {
            values.put(attribute.name(), attribute.stringValue());
        }
        return values;
    }

    @Override
    public byte[] bodyBytes() {
        return bodyBytes.clone();
    }

    /**
     * parser가 사용할 source body JSON 문자열을 반환한다. 이 값은 log/DLQ envelope에 복사하지 않는다.
     */
    public String bodyJson() {
        return new String(bodyBytes, StandardCharsets.UTF_8);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
