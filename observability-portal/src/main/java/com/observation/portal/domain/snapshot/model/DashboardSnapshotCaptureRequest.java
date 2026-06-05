package com.observation.portal.domain.snapshot.model;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

/**
 * portal 내부 capture trigger가 capture use case에 전달하는 최소 요청 경계다.
 *
 * <p>MVP public API contract가 아니며, Post-MVP 외부 trigger가 붙더라도 dashboard read model 계산과 snapshot 저장은
 * portal service/use case가 계속 소유한다.</p>
 */
public record DashboardSnapshotCaptureRequest(
        UUID projectId,
        UUID applicationId,
        DashboardSnapshotCaptureReason captureReason,
        OffsetDateTime currentWindowEndUtc,
        OffsetDateTime snapshotCutoffAt,
        OffsetDateTime requestedAt,
        String triggerSource
) {

    /**
     * capture 대상 scope, reason, target window, 요청 시각을 검증한다.
     */
    public DashboardSnapshotCaptureRequest {
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(applicationId, "applicationId must not be null");
        Objects.requireNonNull(captureReason, "captureReason must not be null");
        currentWindowEndUtc = Objects.requireNonNull(
                currentWindowEndUtc,
                "currentWindowEndUtc must not be null")
                .withOffsetSameInstant(ZoneOffset.UTC);
        snapshotCutoffAt = Objects.requireNonNull(
                snapshotCutoffAt,
                "snapshotCutoffAt must not be null")
                .withOffsetSameInstant(ZoneOffset.UTC);
        requestedAt = Objects.requireNonNull(requestedAt, "requestedAt must not be null")
                .withOffsetSameInstant(ZoneOffset.UTC);
        if (snapshotCutoffAt.isBefore(currentWindowEndUtc)) {
            throw new IllegalArgumentException("snapshotCutoffAt must not be before currentWindowEndUtc");
        }
        triggerSource = normalizeTriggerSource(triggerSource);
    }

    private static String normalizeTriggerSource(String triggerSource) {
        if (triggerSource == null) {
            return null;
        }
        String normalized = triggerSource.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
