package com.observation.portal.domain.snapshot.model;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * snapshot writer의 idempotent upsert 결과를 service layer 내부에서 확인하기 위한 모델이다.
 */
public record DashboardSnapshotWriteResult(
        UUID snapshotId,
        Operation operation,
        DashboardSnapshotCaptureReason captureReason,
        OffsetDateTime currentWindowEndUtc,
        OffsetDateTime generatedAt
) {

    /**
     * writer가 실제 insert/update/no-op 중 무엇으로 수렴했는지와 대표 row metadata를 검증한다.
     */
    public DashboardSnapshotWriteResult {
        Objects.requireNonNull(snapshotId, "snapshotId must not be null");
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(captureReason, "captureReason must not be null");
        Objects.requireNonNull(currentWindowEndUtc, "currentWindowEndUtc must not be null");
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
    }

    /**
     * metric tag와 debug log에 사용할 낮은 cardinality write operation code다.
     */
    public enum Operation {
        INSERT("insert"),
        UPDATE("update"),
        NOOP("upsert");

        private final String metricTag;

        Operation(String metricTag) {
            this.metricTag = metricTag;
        }

        public String metricTag() {
            return metricTag;
        }
    }
}
