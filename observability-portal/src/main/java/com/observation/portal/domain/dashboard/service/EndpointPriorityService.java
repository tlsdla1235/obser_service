package com.observation.portal.domain.dashboard.service;

import com.observation.portal.common.time.AcceptedBucketFreshnessStatus;
import com.observation.portal.domain.bucket.model.EndpointEvidenceRow;
import com.observation.portal.domain.dashboard.model.ApplicationDashboardReadModel;
import com.observation.portal.domain.dashboard.service.EndpointEvidenceAggregationService.EndpointAggregate;
import com.observation.portal.domain.dashboard.service.EndpointEvidenceAggregationService.WindowEndpointEvidence;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * accepted bucket endpoint evidence에서 Dashboard endpoint priority read model을 계산한다.
 *
 * <p>이 service가 endpoint JSON parsing, merge, rule, confidence, score, ranking, recommended action을 담당한다.
 * repository/controller/UI는 endpoint priority를 계산하지 않는다.</p>
 */
@Service
public class EndpointPriorityService {

    private static final long COMMON_MINIMUM_REQUEST_COUNT = 30L;
    private static final BigDecimal ERROR_RATE_THRESHOLD = BigDecimal.valueOf(0.05d);
    private static final long LATENCY_SLOW_BUCKET_LE_MS = 500L;
    private static final BigDecimal LATENCY_SLOW_SHARE_THRESHOLD = BigDecimal.valueOf(0.20d);
    private static final double LOW_SAMPLE_ATTENTION_CONFIDENCE_CAP = 0.64d;
    private static final int MAX_ENDPOINT_PRIORITY_ITEMS = 5;

    private final EndpointEvidenceAggregationService endpointEvidenceAggregationService;

    /**
     * endpoint JSON parsing/merge helper를 주입받아 priority 계산과 evidence parsing 규칙을 분리한다.
     */
    public EndpointPriorityService(EndpointEvidenceAggregationService endpointEvidenceAggregationService) {
        this.endpointEvidenceAggregationService = Objects.requireNonNull(
                endpointEvidenceAggregationService,
                "endpointEvidenceAggregationService must not be null");
    }

    /**
     * current freshness일 때만 recent 30분 endpoint evidence를 ranking item으로 변환한다.
     */
    public List<ApplicationDashboardReadModel.EndpointPriorityItem> endpointPriority(EndpointPriorityInput input) {
        EndpointPriorityInput requiredInput = Objects.requireNonNull(input, "input must not be null");
        if (requiredInput.freshnessStatus() != AcceptedBucketFreshnessStatus.CURRENT) {
            return List.of();
        }

        WindowEndpointEvidence currentEvidence = endpointEvidenceAggregationService.mergeWindow(
                requiredInput.currentRows());

        OffsetDateTime lastObservedAt = latestObservedAt(requiredInput.currentRows())
                .or(() -> requiredInput.lastObservedAt())
                .orElse(null);
        if (lastObservedAt == null) {
            return List.of();
        }

        List<EndpointPriorityCandidate> candidates = currentEvidence.endpoints().values().stream()
                .filter(endpoint -> !isUnknownRoute(endpoint.route()))
                .flatMap(endpoint -> candidate(endpoint, lastObservedAt).stream())
                .sorted(EndpointPriorityService::compareCandidates)
                .limit(MAX_ENDPOINT_PRIORITY_ITEMS)
                .toList();

        List<ApplicationDashboardReadModel.EndpointPriorityItem> items = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            EndpointPriorityCandidate candidate = candidates.get(index);
            items.add(new ApplicationDashboardReadModel.EndpointPriorityItem(
                    index + 1,
                    candidate.method(),
                    candidate.route(),
                    candidate.endpointKey(),
                    candidate.reason(),
                    candidate.ruleIds(),
                    candidate.confidence(),
                    candidate.score(),
                    new ApplicationDashboardReadModel.EndpointPriorityFreshness(
                            "current",
                            lastObservedAt,
                            "recent_30_minutes",
                            null),
                    candidate.evidence(),
                    candidate.recommendedAction()));
        }
        return items;
    }

    private Optional<EndpointPriorityCandidate> candidate(
            EndpointAggregate current,
            OffsetDateTime lastObservedAt) {
        if (current.requestCount() <= 0L) {
            return Optional.empty();
        }

        BigDecimal currentErrorRate = decimal(current.errorCount(), current.requestCount());
        boolean errorRateHigh = current.requestCount() >= COMMON_MINIMUM_REQUEST_COUNT
                && currentErrorRate.compareTo(ERROR_RATE_THRESHOLD) >= 0
                && current.errorCount() > 0L;
        Optional<BigDecimal> slowShareValue = current.durationBoundaryMismatch()
                ? Optional.empty()
                : slowShare(current.durationBuckets());
        boolean latencyAvailable = slowShareValue.isPresent();
        BigDecimal slowShare = latencyAvailable ? slowShareValue.orElseThrow() : null;
        boolean latencyHigh = current.requestCount() >= COMMON_MINIMUM_REQUEST_COUNT
                && latencyAvailable
                && slowShare.compareTo(LATENCY_SLOW_SHARE_THRESHOLD) >= 0;
        boolean recentError = current.errorCount() > 0L;

        ApplicationDashboardReadModel.EndpointPriorityReason reason;
        List<String> ruleIds;
        double confidence;
        if (errorRateHigh && latencyHigh) {
            reason = ApplicationDashboardReadModel.EndpointPriorityReason.ERROR_AND_LATENCY;
            ruleIds = List.of("endpoint_error_rate_high", "endpoint_slow_share_high");
            confidence = clamp(Math.max(
                    confidenceFromErrorRate(currentErrorRate),
                    confidenceFromSlowShare(slowShare)) + 0.05d);
        } else if (errorRateHigh) {
            reason = ApplicationDashboardReadModel.EndpointPriorityReason.ERROR_SPIKE;
            ruleIds = List.of("endpoint_error_rate_high");
            confidence = confidenceFromErrorRate(currentErrorRate);
        } else if (latencyHigh) {
            reason = ApplicationDashboardReadModel.EndpointPriorityReason.LATENCY_SPIKE;
            ruleIds = List.of("endpoint_slow_share_high");
            confidence = confidenceFromSlowShare(slowShare);
        } else if (recentError) {
            reason = ApplicationDashboardReadModel.EndpointPriorityReason.RECENT_ERROR;
            ruleIds = List.of("endpoint_recent_server_error");
            confidence = recentErrorConfidence(currentErrorRate, current.errorCount());
        } else {
            return Optional.empty();
        }

        ApplicationDashboardReadModel.EndpointPriorityEvidence evidence =
                new ApplicationDashboardReadModel.EndpointPriorityEvidence(
                        current.requestCount(),
                        current.errorCount(),
                        currentErrorRate,
                        null,
                        null,
                        null,
                        null,
                        latencyAvailable ? current.durationBuckets() : null,
                        null,
                        slowShare,
                        null,
                        null,
                        "accepted_bucket",
                        ApplicationDashboardReadModel.EndpointEvidenceStatus.AVAILABLE,
                        latencyAvailable
                                ? ApplicationDashboardReadModel.EndpointEvidenceStatus.AVAILABLE
                                : ApplicationDashboardReadModel.EndpointEvidenceStatus.UNAVAILABLE);
        int score = score(confidence);
        return Optional.of(new EndpointPriorityCandidate(
                current.method(),
                current.route(),
                current.endpointKey(),
                reason,
                ruleIds,
                confidence,
                score,
                current.requestCount(),
                evidence,
                recommendedAction(reason)));
    }

    private static Optional<BigDecimal> slowShare(List<ApplicationDashboardReadModel.HistogramBucket> buckets) {
        if (buckets == null || buckets.isEmpty()) {
            return Optional.empty();
        }
        long totalCount = buckets.get(buckets.size() - 1).count();
        if (totalCount <= 0L) {
            return Optional.empty();
        }
        Optional<ApplicationDashboardReadModel.HistogramBucket> thresholdBucket = buckets.stream()
                .filter(bucket -> bucket.leMs() == LATENCY_SLOW_BUCKET_LE_MS)
                .findFirst();
        if (thresholdBucket.isEmpty()) {
            return Optional.empty();
        }
        long slowCount = Math.max(0L, totalCount - thresholdBucket.orElseThrow().count());
        return Optional.of(decimal(slowCount, totalCount));
    }

    private static Optional<OffsetDateTime> latestObservedAt(List<EndpointEvidenceRow> rows) {
        return List.copyOf(Objects.requireNonNullElse(rows, List.of())).stream()
                .map(EndpointEvidenceRow::bucketEndUtc)
                .max(Comparator.naturalOrder());
    }

    private static boolean isUnknownRoute(String route) {
        return "UNKNOWN".equalsIgnoreCase(route == null ? "" : route.trim());
    }

    private static BigDecimal decimal(long numerator, long denominator) {
        if (denominator <= 0L) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP)
                .stripTrailingZeros();
    }

    private static double confidenceFromErrorRate(BigDecimal currentRate) {
        BigDecimal confidence = BigDecimal.valueOf(0.65d)
                .add(currentRate.subtract(ERROR_RATE_THRESHOLD).multiply(BigDecimal.valueOf(2.0d)));
        return clamp(confidence.doubleValue());
    }

    private static double confidenceFromSlowShare(BigDecimal currentShare) {
        BigDecimal confidence = BigDecimal.valueOf(0.65d)
                .add(currentShare.subtract(LATENCY_SLOW_SHARE_THRESHOLD).multiply(BigDecimal.valueOf(1.5d)));
        return clamp(confidence.doubleValue());
    }

    private static double recentErrorConfidence(BigDecimal currentErrorRate, long errorCount) {
        BigDecimal errorCountContribution = BigDecimal.valueOf(Math.min(errorCount, 10L))
                .multiply(BigDecimal.valueOf(0.02d));
        double confidence = BigDecimal.valueOf(0.35d)
                .add(currentErrorRate.multiply(BigDecimal.valueOf(0.25d)))
                .add(errorCountContribution)
                .doubleValue();
        return Math.min(LOW_SAMPLE_ATTENTION_CONFIDENCE_CAP, clamp(confidence));
    }

    private static double clamp(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private static int score(double confidence) {
        return Math.max(0, Math.min(100, (int) Math.round(confidence * 100.0d)));
    }

    private static String recommendedAction(ApplicationDashboardReadModel.EndpointPriorityReason reason) {
        return switch (reason) {
            case ERROR_AND_LATENCY -> "최근 30분 동안 이 endpoint의 오류와 느린 응답 근거를 함께 확인하세요.";
            case ERROR_SPIKE -> "최근 30분 동안 이 endpoint의 5xx 오류 로그와 공통 예외를 먼저 확인하세요.";
            case LATENCY_SPIKE -> "최근 30분 동안 이 endpoint의 느린 요청 구간과 외부 호출, DB query 대기를 확인하세요.";
            case RECENT_ERROR -> "최근 오류가 있었던 API입니다. 빈도가 낮아도 해당 endpoint의 5xx 로그를 확인해보세요.";
        };
    }

    private static int compareCandidates(EndpointPriorityCandidate first, EndpointPriorityCandidate second) {
        int reasonPriority = Integer.compare(reasonPriority(first.reason()), reasonPriority(second.reason()));
        if (reasonPriority != 0) {
            return reasonPriority;
        }
        int confidence = Double.compare(second.confidence(), first.confidence());
        if (confidence != 0) {
            return confidence;
        }
        int score = Integer.compare(second.score(), first.score());
        if (score != 0) {
            return score;
        }
        int requestCount = Long.compare(second.requestCount(), first.requestCount());
        if (requestCount != 0) {
            return requestCount;
        }
        return first.endpointKey().compareTo(second.endpointKey());
    }

    private static int reasonPriority(ApplicationDashboardReadModel.EndpointPriorityReason reason) {
        return switch (reason) {
            case ERROR_AND_LATENCY -> 1;
            case ERROR_SPIKE -> 2;
            case LATENCY_SPIKE -> 3;
            case RECENT_ERROR -> 4;
        };
    }

    /**
     * endpoint priority service에 전달되는 bounded orchestration input이다.
     */
    public record EndpointPriorityInput(
            AcceptedBucketFreshnessStatus freshnessStatus,
            List<EndpointEvidenceRow> currentRows,
            List<EndpointEvidenceRow> baselineRows,
            Optional<OffsetDateTime> lastObservedAt
    ) {

        /**
         * freshness status와 repository projection collection이 null 없이 전달되도록 검증한다.
         */
        public EndpointPriorityInput {
            Objects.requireNonNull(freshnessStatus, "freshnessStatus must not be null");
            currentRows = List.copyOf(Objects.requireNonNull(currentRows, "currentRows must not be null"));
            baselineRows = List.copyOf(Objects.requireNonNull(baselineRows, "baselineRows must not be null"));
            lastObservedAt = Objects.requireNonNull(lastObservedAt, "lastObservedAt must not be null");
        }

        /**
         * nullable timestamp 호출부를 Optional 기반 input으로 변환한다.
         */
        public EndpointPriorityInput(
                AcceptedBucketFreshnessStatus freshnessStatus,
                List<EndpointEvidenceRow> currentRows,
                List<EndpointEvidenceRow> baselineRows,
                OffsetDateTime lastObservedAt) {
            this(freshnessStatus, currentRows, baselineRows, Optional.ofNullable(lastObservedAt));
        }
    }

    private record EndpointPriorityCandidate(
            String method,
            String route,
            String endpointKey,
            ApplicationDashboardReadModel.EndpointPriorityReason reason,
            List<String> ruleIds,
            double confidence,
            int score,
            long requestCount,
            ApplicationDashboardReadModel.EndpointPriorityEvidence evidence,
            String recommendedAction
    ) {
    }

}
