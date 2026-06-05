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
    private final Worker worker = new Worker();

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
     * portal 내부 queue worker의 receive/delete/DLQ 동작을 제한하는 안전 기본값 묶음이다.
     */
    public Worker getWorker() {
        return worker;
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

    /**
     * worker는 기본 비활성화 상태이며, SQS receive bounds와 DLQ URL을 명시적으로 켠 환경에서만 사용한다.
     */
    public static class Worker {

        private boolean enabled = false;
        private String dlqUrl = "";
        private int longPollSeconds = 20;
        private int maxMessagesPerPoll = 10;
        private Duration visibilityTimeout = Duration.ofSeconds(60);
        private int maxReceiveCount = 5;
        private int maxBatchSize = 10;
        private Duration maxBatchAge = Duration.ofSeconds(2);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getDlqUrl() {
            return dlqUrl;
        }

        public void setDlqUrl(String dlqUrl) {
            this.dlqUrl = dlqUrl == null ? "" : dlqUrl;
        }

        public int getLongPollSeconds() {
            return longPollSeconds;
        }

        public void setLongPollSeconds(int longPollSeconds) {
            if (longPollSeconds < 0 || longPollSeconds > 20) {
                throw new IllegalArgumentException("longPollSeconds must be between 0 and 20");
            }
            this.longPollSeconds = longPollSeconds;
        }

        public int getMaxMessagesPerPoll() {
            return maxMessagesPerPoll;
        }

        public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
            if (maxMessagesPerPoll < 1 || maxMessagesPerPoll > 10) {
                throw new IllegalArgumentException("maxMessagesPerPoll must be between 1 and 10");
            }
            this.maxMessagesPerPoll = maxMessagesPerPoll;
        }

        public Duration getVisibilityTimeout() {
            return visibilityTimeout;
        }

        public void setVisibilityTimeout(Duration visibilityTimeout) {
            Duration requiredTimeout = Objects.requireNonNull(
                    visibilityTimeout,
                    "visibilityTimeout must not be null");
            if (requiredTimeout.isZero() || requiredTimeout.isNegative()) {
                throw new IllegalArgumentException("visibilityTimeout must be positive");
            }
            this.visibilityTimeout = requiredTimeout;
        }

        public int getMaxReceiveCount() {
            return maxReceiveCount;
        }

        public void setMaxReceiveCount(int maxReceiveCount) {
            if (maxReceiveCount < 1) {
                throw new IllegalArgumentException("maxReceiveCount must be at least 1");
            }
            this.maxReceiveCount = maxReceiveCount;
        }

        public int getMaxBatchSize() {
            return maxBatchSize;
        }

        public void setMaxBatchSize(int maxBatchSize) {
            if (maxBatchSize < 1) {
                throw new IllegalArgumentException("maxBatchSize must be at least 1");
            }
            this.maxBatchSize = maxBatchSize;
        }

        public Duration getMaxBatchAge() {
            return maxBatchAge;
        }

        public void setMaxBatchAge(Duration maxBatchAge) {
            Duration requiredMaxBatchAge = Objects.requireNonNull(maxBatchAge, "maxBatchAge must not be null");
            if (requiredMaxBatchAge.isZero() || requiredMaxBatchAge.isNegative()) {
                throw new IllegalArgumentException("maxBatchAge must be positive");
            }
            this.maxBatchAge = requiredMaxBatchAge;
        }
    }
}
