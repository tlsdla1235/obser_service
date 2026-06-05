package com.observation.portal.domain.ingest.queue;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

/**
 * ingest buffered transition의 mode, size guard, SQS publisher 설정을 묶는 configuration properties다.
 */
@Component
@ConfigurationProperties(prefix = "portal.ingest.buffer")
public class IngestBufferProperties {

    /**
     * Amazon SQS 단일 message body+attribute hard limit이다.
     */
    public static final long SQS_MESSAGE_SIZE_LIMIT_BYTES = 1_048_576L;

    private IngestBufferMode mode = IngestBufferMode.DIRECT;
    private long messageSizeLimitBytes = SQS_MESSAGE_SIZE_LIMIT_BYTES;
    private Duration publisherTimeout = Duration.ofSeconds(3);
    private final Sqs sqs = new Sqs();

    /**
     * `direct`, `fake`, `sqs` 중 하나로만 바인딩되며 기본값은 direct다.
     */
    public IngestBufferMode getMode() {
        return mode;
    }

    /**
     * Spring relaxed enum binding 결과를 저장한다. 알 수 없는 값은 application startup 단계에서 실패한다.
     */
    public void setMode(IngestBufferMode mode) {
        this.mode = Objects.requireNonNull(mode, "mode must not be null");
    }

    /**
     * body와 attribute UTF-8 bytes를 합산한 application-level message size limit이다.
     */
    public long getMessageSizeLimitBytes() {
        return messageSizeLimitBytes;
    }

    /**
     * SQS quota보다 큰 poison message가 queue로 들어가지 않도록 1 byte 이상 SQS hard limit 이하만 허용한다.
     */
    public void setMessageSizeLimitBytes(long messageSizeLimitBytes) {
        if (messageSizeLimitBytes <= 0 || messageSizeLimitBytes > SQS_MESSAGE_SIZE_LIMIT_BYTES) {
            throw new IllegalArgumentException("messageSizeLimitBytes must be between 1 and 1048576");
        }
        this.messageSizeLimitBytes = messageSizeLimitBytes;
    }

    /**
     * SQS publisher 호출에 적용할 API call timeout 후보 값이다.
     */
    public Duration getPublisherTimeout() {
        return publisherTimeout;
    }

    /**
     * publisher timeout은 null이 아니며 0보다 커야 한다.
     */
    public void setPublisherTimeout(Duration publisherTimeout) {
        Duration requiredTimeout = Objects.requireNonNull(publisherTimeout, "publisherTimeout must not be null");
        if (requiredTimeout.isZero() || requiredTimeout.isNegative()) {
            throw new IllegalArgumentException("publisherTimeout must be positive");
        }
        this.publisherTimeout = requiredTimeout;
    }

    /**
     * SQS mode에서만 사용하는 queue URL과 LocalStack endpoint override 설정이다.
     */
    public Sqs getSqs() {
        return sqs;
    }

    /**
     * SQS publisher 전용 설정이다. blank queue URL은 service에서 503 fail-closed로 처리한다.
     */
    public static class Sqs {

        private String queueUrl = "";
        private String endpointOverride = "";

        public String getQueueUrl() {
            return queueUrl;
        }

        public void setQueueUrl(String queueUrl) {
            this.queueUrl = queueUrl == null ? "" : queueUrl;
        }

        public String getEndpointOverride() {
            return endpointOverride;
        }

        public void setEndpointOverride(String endpointOverride) {
            this.endpointOverride = endpointOverride == null ? "" : endpointOverride;
        }
    }
}
