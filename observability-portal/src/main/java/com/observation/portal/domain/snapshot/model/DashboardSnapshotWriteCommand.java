package com.observation.portal.domain.snapshot.model;

import com.observation.portal.domain.dashboard.model.ApplicationDashboardReadModel;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * dashboard snapshot writer에 전달하는 저장 명령이다.
 *
 * <p>caller가 이미 생성한 `ApplicationDashboardReadModel`을 전달하며, writer는 이 read model을 다시 계산하지 않고
 * bounded endpoint evidence와 instance summary를 채워 저장한다.</p>
 */
public record DashboardSnapshotWriteCommand(
        UUID projectId,
        UUID applicationId,
        ApplicationDashboardReadModel readModel,
        DashboardSnapshotCaptureReason captureReason,
        OffsetDateTime currentWindowEndUtc,
        OffsetDateTime requestedAt,
        String triggerSource
) {

    /**
     * snapshot 저장에 필요한 application/window metadata와 capture reason을 검증한다.
     */
    public DashboardSnapshotWriteCommand {
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(applicationId, "applicationId must not be null");
        Objects.requireNonNull(readModel, "readModel must not be null");
        Objects.requireNonNull(captureReason, "captureReason must not be null");
        Objects.requireNonNull(currentWindowEndUtc, "currentWindowEndUtc must not be null");
        Objects.requireNonNull(requestedAt, "requestedAt must not be null");
        triggerSource = normalizeTriggerSource(triggerSource);
        if (!projectId.equals(readModel.application().projectId())) {
            throw new IllegalArgumentException("projectId must match readModel.application.projectId");
        }
        if (!applicationId.equals(readModel.application().applicationId())) {
            throw new IllegalArgumentException("applicationId must match readModel.application.applicationId");
        }
    }

    private static String normalizeTriggerSource(String triggerSource) {
        if (triggerSource == null) {
            return null;
        }
        String normalized = triggerSource.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
