package com.observation.portal.domain.ingest.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * portal runtime 안에서 source queue를 poll하고 processor 결과에 따라 delete/DLQ/no-delete를 적용하는 worker다.
 */
@Component
@ConditionalOnProperty(name = "portal.ingest.buffer.worker.enabled", havingValue = "true")
public class MetricIngestQueueWorker implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(MetricIngestQueueWorker.class);

    private final IngestBufferProperties properties;
    private final MetricIngestQueueConsumer consumer;
    private final MetricIngestDlqPublisher dlqPublisher;
    private final MetricIngestQueueProcessor processor;
    private volatile boolean running;
    private Thread workerThread;

    /**
     * worker가 사용할 queue consumer, DLQ publisher, processor를 주입한다.
     */
    public MetricIngestQueueWorker(
            IngestBufferProperties properties,
            MetricIngestQueueConsumer consumer,
            MetricIngestDlqPublisher dlqPublisher,
            MetricIngestQueueProcessor processor) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.consumer = Objects.requireNonNull(consumer, "consumer must not be null");
        this.dlqPublisher = Objects.requireNonNull(dlqPublisher, "dlqPublisher must not be null");
        this.processor = Objects.requireNonNull(processor, "processor must not be null");
    }

    /**
     * 한 번의 receive page를 message별로 독립 처리한다. 테스트와 fake smoke는 이 method를 직접 호출한다.
     */
    public void pollOnce() {
        if (!isWorkerModeProcessable()) {
            return;
        }
        List<MetricIngestReceivedMessage> messages;
        try {
            messages = consumer.receive();
        } catch (RuntimeException exception) {
            log.warn("metric ingest worker receive failed category=receive_failure");
            return;
        }
        for (MetricIngestReceivedMessage message : messages) {
            processOne(message);
        }
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        workerThread = new Thread(this::runLoop, "metric-ingest-queue-worker");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    @Override
    public void stop() {
        running = false;
        Thread current = workerThread;
        if (current != null) {
            current.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    private void runLoop() {
        while (running) {
            try {
                pollOnce();
                Thread.sleep(properties.getWorker().getMaxBatchAge().toMillis());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                running = false;
            } catch (RuntimeException exception) {
                log.warn("metric ingest worker loop failed category=loop_failure");
            }
        }
    }

    private void processOne(MetricIngestReceivedMessage message) {
        MetricIngestQueueProcessResult result;
        try {
            result = processor.process(message);
        } catch (RuntimeException exception) {
            log.warn(
                    "metric ingest worker processor failed category=processor_failure sourceMessageId={} receiveCount={}",
                    message.messageId(),
                    message.receiveCount());
            return;
        }

        if (result.status() == MetricIngestQueueProcessStatus.INSERTED
                || result.status() == MetricIngestQueueProcessStatus.DUPLICATE_NOOP) {
            deleteSource(message, result.status());
            return;
        }

        if (result.status() == MetricIngestQueueProcessStatus.APPLICATION_DLQ) {
            MetricIngestDlqEnvelope envelope = result.dlqEnvelope().orElseThrow();
            try {
                dlqPublisher.publish(envelope);
            } catch (RuntimeException exception) {
                log.warn(
                        "metric ingest worker dlq publish failed category=application_dlq_failure sourceMessageId={} receiveCount={}",
                        message.messageId(),
                        message.receiveCount());
                return;
            }
            deleteSource(message, result.status());
        }
    }

    private void deleteSource(MetricIngestReceivedMessage message, MetricIngestQueueProcessStatus status) {
        try {
            consumer.delete(message);
        } catch (RuntimeException exception) {
            log.warn(
                    "metric ingest worker delete failed category=delete_failure status={} sourceMessageId={} receiveCount={}",
                    status,
                    message.messageId(),
                    message.receiveCount());
        }
    }

    private boolean isWorkerModeProcessable() {
        if (properties.getMode() == IngestBufferMode.FAKE) {
            return true;
        }
        if (properties.getMode() != IngestBufferMode.SQS) {
            log.warn("metric ingest worker disabled category=unsupported_mode");
            return false;
        }
        if (isBlank(properties.getSqs().getQueueUrl()) || isBlank(properties.getWorker().getDlqUrl())) {
            log.warn("metric ingest worker disabled category=queue_config_missing");
            return false;
        }
        return true;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
