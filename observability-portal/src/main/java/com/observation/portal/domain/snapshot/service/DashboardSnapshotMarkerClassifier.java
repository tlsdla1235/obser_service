package com.observation.portal.domain.snapshot.service;

import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel.LastHealthyAt;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel.PreviousState;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel.RecoveryMarker;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailRow;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotMarkerItem;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotMarkerSeverity;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotMarkerType;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotStoredReadModelProjection;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

/**
 * Stored snapshot row와 stored read model signal만 사용해 marker type/severity/copy를 분류한다.
 *
 * <p>`capture_reason`은 marker 후보 seed로만 해석하며, severity는 stored state/triage/recovery field에서 읽는다.
 * operational event promotion, deduplication, suppression은 수행하지 않는다.</p>
 */
@Component
public class DashboardSnapshotMarkerClassifier {

    private static final BigDecimal HIGH_CONFIDENCE_THRESHOLD = new BigDecimal("0.82");

    /**
     * Snapshot row를 marker API/detail marker block에서 공유하는 marker item으로 변환한다.
     */
    public DashboardSnapshotMarkerItem marker(
            DashboardSnapshotDetailRow row,
            PreviousState previousState,
            DashboardSnapshotStoredReadModelProjection projection,
            String snapshotLink) {
        DashboardSnapshotDetailRow requiredRow = Objects.requireNonNull(row, "row must not be null");
        PreviousState requiredPreviousState = Objects.requireNonNull(previousState, "previousState must not be null");
        DashboardSnapshotStoredReadModelProjection requiredProjection = Objects.requireNonNull(
                projection,
                "projection must not be null");
        DashboardSnapshotMarkerType type = markerType(requiredRow, requiredPreviousState, requiredProjection);
        DashboardSnapshotMarkerSeverity severity = severity(requiredRow, requiredPreviousState, requiredProjection);
        MarkerCopy copy = copy(type, severity, requiredRow.stateCode());
        return new DashboardSnapshotMarkerItem(
                markerId(requiredRow, type),
                requiredRow.snapshotId(),
                requiredRow.generatedAt(),
                requiredRow.currentWindowEndUtc(),
                type,
                severity,
                DashboardSnapshotMarkerItem.READ_MEANING,
                requiredRow.captureReason(),
                requiredRow.stateCode(),
                requiredPreviousState,
                copy.title(),
                copy.summary(),
                copy.recommendedAction(),
                requiredRow.maxConfidence(),
                requiredRow.primaryRuleId(),
                requiredRow.primaryEndpointKey(),
                new DashboardSnapshotMarkerItem.Links(snapshotLink));
    }

    /**
     * Recovery observed marker가 필요한 경우 detail response용 recovery marker block을 만든다.
     */
    public RecoveryMarker recoveryMarker(
            DashboardSnapshotMarkerItem marker,
            PreviousState previousState,
            LastHealthyAt lastHealthyAt) {
        Objects.requireNonNull(marker, "marker must not be null");
        if (marker.type() != DashboardSnapshotMarkerType.RECOVERY_OBSERVED) {
            return null;
        }
        return new RecoveryMarker(
                marker.markerId(),
                DashboardSnapshotMarkerType.RECOVERY_OBSERVED,
                DashboardSnapshotMarkerSeverity.WARNING,
                "회복 관찰 중",
                "저장 당시 stale/down 이후 metric data가 다시 들어왔지만 판단 sample이 아직 부족했습니다.",
                "다음 bucket에서 accepted bucket 수용과 sample 증가를 확인하세요.",
                Objects.requireNonNull(previousState, "previousState must not be null"),
                Objects.requireNonNull(lastHealthyAt, "lastHealthyAt must not be null"));
    }

    private DashboardSnapshotMarkerType markerType(
            DashboardSnapshotDetailRow row,
            PreviousState previousState,
            DashboardSnapshotStoredReadModelProjection projection) {
        if (isRecoveryObserved(row, previousState, projection)) {
            return DashboardSnapshotMarkerType.RECOVERY_OBSERVED;
        }
        if (isCaptureReason(row, "state_change") || previousStateChanged(row, previousState)) {
            return DashboardSnapshotMarkerType.STATE_CHANGE;
        }
        if (isCaptureReason(row, "high_confidence_concern") || highConfidence(row, projection)) {
            return DashboardSnapshotMarkerType.HIGH_CONFIDENCE_CONCERN;
        }
        if (isCaptureReason(row, "short_strong_spike")) {
            return DashboardSnapshotMarkerType.SHORT_STRONG_SPIKE;
        }
        if (isAttentionState(row.stateCode())) {
            return DashboardSnapshotMarkerType.STATE_OBSERVATION;
        }
        if (isCaptureReason(row, "query_fallback")) {
            return DashboardSnapshotMarkerType.QUERY_FALLBACK_SNAPSHOT;
        }
        if (isCaptureReason(row, "hourly_scheduled")) {
            return DashboardSnapshotMarkerType.SCHEDULED_SNAPSHOT;
        }
        return DashboardSnapshotMarkerType.STORED_SNAPSHOT;
    }

    private static DashboardSnapshotMarkerSeverity severity(
            DashboardSnapshotDetailRow row,
            PreviousState previousState,
            DashboardSnapshotStoredReadModelProjection projection) {
        String state = normalized(row.stateCode());
        if ("down".equals(state) || projection.criticalTriageSeverityPresent()) {
            return DashboardSnapshotMarkerSeverity.CRITICAL;
        }
        if (isWarningState(state)
                || isRecoveryObserved(row, previousState, projection)
                || highConfidence(row, projection)
                || projection.warningTriageSeverityPresent()) {
            return DashboardSnapshotMarkerSeverity.WARNING;
        }
        if (isNeutralState(state)) {
            return DashboardSnapshotMarkerSeverity.INFO;
        }
        return DashboardSnapshotMarkerSeverity.WARNING;
    }

    private static boolean isRecoveryObserved(
            DashboardSnapshotDetailRow row,
            PreviousState previousState,
            DashboardSnapshotStoredReadModelProjection projection) {
        return projection.recoveryObserved()
                || (isPreviousStaleOrDown(previousState)
                && "unknown".equals(normalized(row.stateCode()))
                && projection.recoveryExpressionPresent());
    }

    private static boolean previousStateChanged(DashboardSnapshotDetailRow row, PreviousState previousState) {
        String previous = normalized(previousState.stateCode());
        String current = normalized(row.stateCode());
        return previous != null && current != null && !previous.equals(current);
    }

    private static boolean highConfidence(
            DashboardSnapshotDetailRow row,
            DashboardSnapshotStoredReadModelProjection projection) {
        return row.maxConfidence() != null && row.maxConfidence().compareTo(HIGH_CONFIDENCE_THRESHOLD) >= 0
                || DashboardSnapshotDetailProjectionParser.hasHighConfidenceTriage(projection);
    }

    private static boolean isPreviousStaleOrDown(PreviousState previousState) {
        String previous = normalized(previousState.stateCode());
        return "stale".equals(previous) || "down".equals(previous);
    }

    private static boolean isAttentionState(String stateCode) {
        String state = normalized(stateCode);
        return "degraded".equals(state)
                || "stale".equals(state)
                || "down".equals(state)
                || "unknown".equals(state);
    }

    private static boolean isWarningState(String state) {
        return "degraded".equals(state) || "stale".equals(state) || "unknown".equals(state);
    }

    private static boolean isNeutralState(String state) {
        return "active".equals(state) || "idle".equals(state) || "waiting_first_data".equals(state);
    }

    private static boolean isCaptureReason(DashboardSnapshotDetailRow row, String token) {
        return token.equals(normalized(row.captureReason()));
    }

    private static String markerId(DashboardSnapshotDetailRow row, DashboardSnapshotMarkerType type) {
        return "snapshot:%s:%s".formatted(row.snapshotId(), type.value());
    }

    private static MarkerCopy copy(
            DashboardSnapshotMarkerType type,
            DashboardSnapshotMarkerSeverity severity,
            String stateCode) {
        if (type == DashboardSnapshotMarkerType.RECOVERY_OBSERVED) {
            return new MarkerCopy(
                    "회복 관찰 중",
                    "저장 당시 stale/down 이후 metric data가 다시 들어왔지만 판단 sample이 아직 부족했습니다.",
                    "다음 bucket에서 accepted bucket 수용과 sample 증가를 확인하세요.");
        }
        String normalizedState = normalized(stateCode);
        if ("unknown".equals(normalizedState) || (!isNeutralState(normalizedState) && !isAttentionState(stateCode))) {
            return new MarkerCopy(
                    "저장 상태 해석 제한",
                    "저장된 상태를 완전히 해석하지 못했습니다.",
                    "현재 상태는 application dashboard에서 다시 확인하세요.");
        }
        return switch (type) {
            case STATE_CHANGE -> new MarkerCopy(
                    "상태 변화 snapshot",
                    "저장 당시 이전 snapshot과 다른 application state가 관찰되었습니다.",
                    "관련 snapshot detail에서 저장된 근거를 확인하세요.");
            case HIGH_CONFIDENCE_CONCERN -> new MarkerCopy(
                    "고신뢰 concern snapshot",
                    "저장 당시 dashboard read model에서 확인 우선 신호가 있었습니다.",
                    "관련 rule과 endpoint evidence를 먼저 확인하세요.");
            case SHORT_STRONG_SPIKE -> new MarkerCopy(
                    "짧고 강한 spike snapshot",
                    "저장 당시 짧은 구간의 강한 이상 신호가 snapshot으로 남았습니다.",
                    "다음 snapshot과 함께 추세가 이어지는지 확인하세요.");
            case STATE_OBSERVATION -> new MarkerCopy(
                    "주의 상태 snapshot",
                    severity == DashboardSnapshotMarkerSeverity.CRITICAL
                            ? "저장 당시 application state가 critical 수준으로 보였습니다."
                            : "저장 당시 application state가 주의가 필요한 상태로 보였습니다.",
                    "저장된 detail evidence를 확인하세요.");
            case QUERY_FALLBACK_SNAPSHOT -> new MarkerCopy(
                    "Query fallback snapshot",
                    "저장된 dashboard read model입니다.",
                    "현재 상태는 application dashboard에서 다시 확인하세요.");
            case SCHEDULED_SNAPSHOT -> new MarkerCopy(
                    "정기 snapshot",
                    "저장된 dashboard read model입니다.",
                    "필요하면 snapshot detail에서 저장된 근거를 확인하세요.");
            case STORED_SNAPSHOT -> new MarkerCopy(
                    "저장된 snapshot",
                    "저장된 dashboard read model입니다.",
                    "현재 상태는 application dashboard에서 다시 확인하세요.");
            case RECOVERY_OBSERVED -> throw new IllegalStateException("recovery copy handled earlier");
        };
    }

    private static String normalized(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private record MarkerCopy(String title, String summary, String recommendedAction) {
    }
}
