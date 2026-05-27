package com.observation.portal.domain.snapshot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.portal.domain.bucket.model.RecentBucketEvidenceRow;
import com.observation.portal.domain.bucket.model.RecentBucketEvidenceRows;
import com.observation.portal.domain.bucket.repository.MetricBucketRepository;
import com.observation.portal.domain.dashboard.model.ApplicationDashboardReadModel;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotCaptureReason;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotLatestRow;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotWriteCommand;
import com.observation.portal.domain.snapshot.repository.DashboardSnapshotRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * snapshot write 직전에 capture reason 후보를 계약 threshold로 판정하는 policy service다.
 *
 * <p>caller가 전달한 scheduled/fallback reason은 보존하되, 같은 read model에서 state/high-confidence/short spike 후보가
 * 성립하면 fixed priority에 따라 대표 reason을 승격한다.</p>
 */
@Service
public class DashboardSnapshotCapturePolicy {

    static final double HIGH_CONFIDENCE_CONCERN_THRESHOLD = 0.82d;
    static final double SHORT_STRONG_SPIKE_CONFIDENCE_THRESHOLD = 0.90d;
    static final int SHORT_STRONG_SPIKE_BAD_BUCKET_THRESHOLD = 2;
    private static final BigDecimal ERROR_RATE_THRESHOLD = BigDecimal.valueOf(0.05d);
    private static final long LATENCY_SLOW_BUCKET_LE_MS = 500L;
    private static final BigDecimal LATENCY_SLOW_SHARE_THRESHOLD = BigDecimal.valueOf(0.20d);
    private static final int RECENT_BUCKET_WINDOW = 5;

    private final DashboardSnapshotRepository snapshotRepository;
    private final MetricBucketRepository metricBucketRepository;
    private final ObjectMapper objectMapper;

    /**
     * latest snapshot state와 최근 accepted bucket evidence를 조회할 repository, JSON mapper를 주입한다.
     */
    public DashboardSnapshotCapturePolicy(
            DashboardSnapshotRepository snapshotRepository,
            MetricBucketRepository metricBucketRepository,
            ObjectMapper objectMapper) {
        this.snapshotRepository = Objects.requireNonNull(snapshotRepository, "snapshotRepository must not be null");
        this.metricBucketRepository = Objects.requireNonNull(
                metricBucketRepository,
                "metricBucketRepository must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * command의 read model과 stored/latest evidence를 기준으로 같은 window 대표 capture reason을 고른다.
     */
    public DashboardSnapshotCaptureReason representativeReason(DashboardSnapshotWriteCommand command) {
        DashboardSnapshotWriteCommand requiredCommand = Objects.requireNonNull(
                command,
                "command must not be null");
        List<DashboardSnapshotCaptureReason> candidates = new ArrayList<>();
        if (requiredCommand.captureReason() == DashboardSnapshotCaptureReason.HOURLY_SCHEDULED
                || requiredCommand.captureReason() == DashboardSnapshotCaptureReason.QUERY_FALLBACK) {
            candidates.add(requiredCommand.captureReason());
        }
        if (isEligible(DashboardSnapshotCaptureReason.STATE_CHANGE, requiredCommand)) {
            candidates.add(DashboardSnapshotCaptureReason.STATE_CHANGE);
        }
        if (isEligible(DashboardSnapshotCaptureReason.HIGH_CONFIDENCE_CONCERN, requiredCommand)) {
            candidates.add(DashboardSnapshotCaptureReason.HIGH_CONFIDENCE_CONCERN);
        }
        if (isEligible(DashboardSnapshotCaptureReason.SHORT_STRONG_SPIKE, requiredCommand)) {
            candidates.add(DashboardSnapshotCaptureReason.SHORT_STRONG_SPIKE);
        }
        return candidates.stream()
                .max(Comparator.comparingInt(DashboardSnapshotCaptureReason::priority))
                .orElse(DashboardSnapshotCaptureReason.HOURLY_SCHEDULED);
    }

    /**
     * 특정 capture reason이 command/read model 근거에서 새 write 후보가 될 수 있는지 검증한다.
     */
    public boolean isEligible(DashboardSnapshotCaptureReason reason, DashboardSnapshotWriteCommand command) {
        DashboardSnapshotWriteCommand requiredCommand = Objects.requireNonNull(
                command,
                "command must not be null");
        return switch (Objects.requireNonNull(reason, "reason must not be null")) {
            case HOURLY_SCHEDULED, QUERY_FALLBACK -> true;
            case STATE_CHANGE -> isStateChangeCandidate(requiredCommand.readModel());
            case HIGH_CONFIDENCE_CONCERN -> isHighConfidenceConcernCandidate(requiredCommand.readModel());
            case SHORT_STRONG_SPIKE -> isShortStrongSpikeCandidate(requiredCommand);
        };
    }

    private boolean isStateChangeCandidate(ApplicationDashboardReadModel readModel) {
        String currentState = readModel.state().code();
        return snapshotRepository.findLatestByApplicationId(readModel.application().applicationId())
                .map(DashboardSnapshotLatestRow::stateCode)
                .map(previousState -> !previousState.equals(currentState))
                .orElse(false);
    }

    private static boolean isHighConfidenceConcernCandidate(ApplicationDashboardReadModel readModel) {
        return readModel.triageCards().stream()
                .anyMatch(card -> card.confidence() >= HIGH_CONFIDENCE_CONCERN_THRESHOLD);
    }

    private boolean isShortStrongSpikeCandidate(DashboardSnapshotWriteCommand command) {
        // short_strong_spike는 강한 confidence와 최근 bucket bad-count가 함께 있을 때만 실험 후보로 둔다.
        if (!hasShortStrongSpikeConfidence(command.readModel())) {
            return false;
        }
        List<RecentBucketEvidenceRow> recentBuckets =
                metricBucketRepository.findRecentFiveBucketEvidenceRowsByApplicationIdAtOrBefore(
                        command.applicationId(),
                        command.currentWindowEndUtc().toInstant());
        return badBucketCount(recentBuckets) >= SHORT_STRONG_SPIKE_BAD_BUCKET_THRESHOLD;
    }

    private static boolean hasShortStrongSpikeConfidence(ApplicationDashboardReadModel readModel) {
        return readModel.triageCards().stream()
                .anyMatch(card -> card.confidence() >= SHORT_STRONG_SPIKE_CONFIDENCE_THRESHOLD)
                || readModel.endpointPriority().stream()
                .anyMatch(item -> item.confidence() >= SHORT_STRONG_SPIKE_CONFIDENCE_THRESHOLD);
    }

    private int badBucketCount(List<RecentBucketEvidenceRow> rows) {
        return (int) RecentBucketEvidenceRows.applicationLevelBuckets(
                        Objects.requireNonNullElse(rows, List.of()),
                        RECENT_BUCKET_WINDOW,
                        objectMapper)
                .stream()
                .filter(this::isBadBucket)
                .count();
    }

    private boolean isBadBucket(RecentBucketEvidenceRow row) {
        return isErrorBadBucket(row) || isLatencyBadBucket(row);
    }

    private static boolean isErrorBadBucket(RecentBucketEvidenceRow row) {
        return row.requestCount() > 0L
                && BigDecimal.valueOf(row.errorCount())
                .divide(BigDecimal.valueOf(row.requestCount()), 6, RoundingMode.HALF_UP)
                .compareTo(ERROR_RATE_THRESHOLD) >= 0;
    }

    private boolean isLatencyBadBucket(RecentBucketEvidenceRow row) {
        return slowShare(row.durationBucketsJson())
                .map(share -> share.compareTo(LATENCY_SLOW_SHARE_THRESHOLD) >= 0)
                .orElse(false);
    }

    private Optional<BigDecimal> slowShare(String durationBucketsJson) {
        try {
            JsonNode root = objectMapper.readTree(durationBucketsJson);
            if (root == null || !root.isArray()) {
                return Optional.empty();
            }
            long totalCount = 0L;
            Long thresholdCount = null;
            for (JsonNode item : root) {
                JsonNode boundary = item.get("leMs");
                JsonNode count = item.get("count");
                if (boundary == null || count == null
                        || !boundary.canConvertToLong()
                        || !count.canConvertToLong()) {
                    return Optional.empty();
                }
                long boundaryValue = boundary.asLong();
                long countValue = count.asLong();
                if (boundaryValue < 0L || countValue < 0L) {
                    return Optional.empty();
                }
                totalCount = Math.max(totalCount, countValue);
                if (boundaryValue == LATENCY_SLOW_BUCKET_LE_MS) {
                    thresholdCount = countValue;
                }
            }
            if (thresholdCount == null || totalCount <= 0L) {
                return Optional.empty();
            }
            long slowCount = Math.max(0L, totalCount - thresholdCount);
            return Optional.of(BigDecimal.valueOf(slowCount)
                    .divide(BigDecimal.valueOf(totalCount), 6, RoundingMode.HALF_UP));
        } catch (JsonProcessingException exception) {
            return Optional.empty();
        }
    }
}
