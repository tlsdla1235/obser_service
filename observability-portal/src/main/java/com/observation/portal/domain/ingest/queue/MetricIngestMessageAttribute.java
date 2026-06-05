package com.observation.portal.domain.ingest.queue;

/**
 * SQS message attribute로 보낼 수 있는 allow-list name/type/value triple이다.
 */
public record MetricIngestMessageAttribute(String name, String dataType, String stringValue) {

    /**
     * attribute는 SQS String type만 사용하고 raw secret이 들어갈 동적 key/value를 허용하지 않는다.
     */
    public MetricIngestMessageAttribute {
        name = requireText(name, "name");
        dataType = requireText(dataType, "dataType");
        stringValue = requireText(stringValue, "stringValue");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
