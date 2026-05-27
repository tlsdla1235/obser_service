package com.observation.portal.domain.snapshot.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * snapshot write 결과를 낮은 cardinality Micrometer counter로 기록한다.
 *
 * <p>MeterRegistry bean이 없는 test/runtime에서도 writer가 동작하도록 no-op으로 수렴하며, tag에는 applicationId나
 * endpointKey 같은 높은 cardinality 값을 넣지 않는다.</p>
 */
@Component
public class DashboardSnapshotWriteMetrics {

    static final String SUCCESS_METRIC = "dashboard.snapshot.write.success";
    static final String FAILURE_METRIC = "dashboard.snapshot.write.failure";

    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    /**
     * optional MeterRegistry provider를 주입해 registry 유무와 writer 동작을 분리한다.
     */
    public DashboardSnapshotWriteMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistryProvider = Objects.requireNonNull(
                meterRegistryProvider,
                "meterRegistryProvider must not be null");
    }

    /**
     * insert/update/no-op 성공을 capture_reason과 operation tag로 기록한다.
     */
    public void recordSuccess(String captureReason, String operation) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry != null) {
            registry.counter(
                    SUCCESS_METRIC,
                    "capture_reason", captureReason,
                    "operation", operation)
                    .increment();
        }
    }

    /**
     * 실패를 capture_reason, operation, failure_type tag로 기록한다.
     */
    public void recordFailure(String captureReason, String operation, String failureType) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry != null) {
            registry.counter(
                    FAILURE_METRIC,
                    "capture_reason", captureReason,
                    "operation", operation,
                    "failure_type", failureType)
                    .increment();
        }
    }
}
