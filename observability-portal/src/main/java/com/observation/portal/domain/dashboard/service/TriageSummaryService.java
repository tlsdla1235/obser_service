package com.observation.portal.domain.dashboard.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.portal.common.time.AcceptedBucketFreshnessStatus;
import com.observation.portal.domain.bucket.model.RecentBucketEvidenceRow;
import com.observation.portal.domain.bucket.model.RecentBucketEvidenceRows;
import com.observation.portal.domain.bucket.model.RuntimeRatioEvidenceRow;
import com.observation.portal.domain.bucket.model.WindowBucketAggregate;
import com.observation.portal.domain.dashboard.model.ApplicationDashboardReadModel;
import com.observation.portal.domain.state.model.DegradedHysteresisInput;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Application Dashboard의 앱 단위 triage card와 degraded hysteresis 입력을 계산한다.
 *
 * <p>이 service는 endpoint ranking이나 p95/p99 rollup을 만들지 않고, bounded aggregate/histogram/runtime evidence만
 * 사용해 Story 5.4의 first-check triage surface를 만든다.</p>
 */
@Service
public class TriageSummaryService {

    static final long MINIMUM_REQUEST_COUNT = 30L;
    static final BigDecimal ERROR_RATE_THRESHOLD = BigDecimal.valueOf(0.05d);
    static final long LATENCY_SLOW_BUCKET_LE_MS = 500L;
    static final BigDecimal LATENCY_SLOW_SHARE_THRESHOLD = BigDecimal.valueOf(0.20d);
    static final double CARD_EXPOSURE_CONFIDENCE = 0.65d;
    private static final BigDecimal DATASOURCE_POOL_RATIO_THRESHOLD = BigDecimal.valueOf(0.85d);
    private static final BigDecimal CPU_USAGE_RATIO_THRESHOLD = BigDecimal.valueOf(0.85d);
    private static final BigDecimal HEAP_USED_RATIO_THRESHOLD = BigDecimal.valueOf(0.90d);
    private static final int MAX_TRIAGE_CARDS = 3;

    private final ObjectMapper objectMapper;

    /**
     * recent bucket histogram JSON을 bounded bucket list로 읽기 위해 ObjectMapper를 주입받는다.
     */
    public TriageSummaryService(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * recent 30분 aggregate와 histogram/runtime evidence에서 triage card와 degraded input을 분리해 반환한다.
     */
    public TriageSummary summarize(TriageSummaryInput input) {
        TriageSummaryInput requiredInput = Objects.requireNonNull(input, "input must not be null");
        if (requiredInput.freshnessStatus() != AcceptedBucketFreshnessStatus.CURRENT) {
            return new TriageSummary(List.of(), DegradedHysteresisInput.noConcern());
        }

        List<TriageCandidate> candidates = new ArrayList<>();
        Optional<TriageCandidate> errorConcernCandidate = errorRateHighCandidate(requiredInput);
        Optional<TriageCandidate> latencyCandidate = latencySlowShareHighCandidate(requiredInput);
        errorConcernCandidate.ifPresent(candidates::add);
        latencyCandidate.ifPresent(candidates::add);
        saturationHintCandidates(requiredInput, errorConcernCandidate, latencyCandidate).forEach(candidates::add);

        List<ApplicationDashboardReadModel.TriageCard> cards = candidates.stream()
                .filter(candidate -> candidate.card().confidence() >= CARD_EXPOSURE_CONFIDENCE)
                .sorted(TriageSummaryService::compareCandidates)
                .limit(MAX_TRIAGE_CARDS)
                .map(TriageCandidate::card)
                .toList();

        DegradedHysteresisInput degradedInput = candidates.stream()
                .filter(TriageCandidate::degradedCandidate)
                .max(Comparator.comparingInt(candidate -> candidate.card().score()))
                .map(candidate -> DegradedHysteresisInput.of(
                        true,
                        true,
                        candidate.card().confidence(),
                        candidate.badBucketsInRecentFive(),
                        false,
                        0))
                .orElseGet(DegradedHysteresisInput::noConcern);

        return new TriageSummary(cards, degradedInput);
    }

    private Optional<TriageCandidate> errorRateHighCandidate(TriageSummaryInput input) {
        WindowBucketAggregate current = input.currentAggregate();
        if (current.requestCount() < MINIMUM_REQUEST_COUNT) {
            return Optional.empty();
        }

        BigDecimal currentRate = errorRate(current.errorCount(), current.requestCount());
        if (currentRate.compareTo(ERROR_RATE_THRESHOLD) < 0) {
            return Optional.empty();
        }

        double confidence = confidenceFromAbsoluteErrorRate(currentRate);
        int score = score(confidence);
        ApplicationDashboardReadModel.TriageEvidence evidence = new ApplicationDashboardReadModel.TriageEvidence(
                current.requestCount(),
                current.errorCount(),
                currentRate,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                runtimeRatioEvidence(input.runtimeRatio()),
                input.freshnessStatus().name().toLowerCase(),
                sourcePercentilePoint(input.sourceScopedPercentiles()));
        ApplicationDashboardReadModel.TriageCard card = new ApplicationDashboardReadModel.TriageCard(
                "application_error_rate_high",
                severity(confidence),
                "Application 오류율 높음",
                "recent 30 minutes window의 오류율이 절대 기준 이상입니다.",
                "최근 오류가 있었던 API와 공통 오류 로그를 먼저 확인해보세요.",
                confidence,
                score,
                null,
                evidence);
        return Optional.of(new TriageCandidate(card, true, errorBadBucketCount(input.recentBuckets())));
    }

    private Optional<TriageCandidate> latencySlowShareHighCandidate(TriageSummaryInput input) {
        WindowBucketAggregate currentAggregate = input.currentAggregate();
        if (currentAggregate.requestCount() < MINIMUM_REQUEST_COUNT) {
            return Optional.empty();
        }

        ApplicationDashboardReadModel.HistogramWindow current = input.histogramDistribution().current();
        Optional<SlowShare> currentSlowShare = slowShare(current);
        if (currentSlowShare.isEmpty()) {
            return Optional.empty();
        }

        BigDecimal currentShare = currentSlowShare.orElseThrow().share();
        if (currentShare.compareTo(LATENCY_SLOW_SHARE_THRESHOLD) < 0) {
            return Optional.empty();
        }

        double confidence = confidenceFromAbsoluteSlowShare(currentShare);
        int score = score(confidence);
        ApplicationDashboardReadModel.TriageEvidence evidence = new ApplicationDashboardReadModel.TriageEvidence(
                currentAggregate.requestCount(),
                null,
                null,
                null,
                null,
                null,
                null,
                currentShare,
                null,
                histogramSummary(current),
                null,
                runtimeRatioEvidence(input.runtimeRatio()),
                input.freshnessStatus().name().toLowerCase(),
                sourcePercentilePoint(input.sourceScopedPercentiles()));
        ApplicationDashboardReadModel.TriageCard card = new ApplicationDashboardReadModel.TriageCard(
                "application_slow_share_high",
                severity(confidence),
                "Application 느린 응답 비중 높음",
                "recent 30 minutes window의 500ms 초과 duration bucket 비중이 절대 기준 이상입니다.",
                "느린 요청 구간과 외부 호출, DB query 대기 가능성을 먼저 확인해보세요.",
                confidence,
                score,
                null,
                evidence);
        return Optional.of(new TriageCandidate(card, true, latencyBadBucketCount(input.recentBuckets())));
    }

    private List<TriageCandidate> saturationHintCandidates(
            TriageSummaryInput input,
            Optional<TriageCandidate> errorCandidate,
            Optional<TriageCandidate> latencyCandidate) {
        Optional<RuntimeRatioEvidenceRow> runtime = input.runtimeRatio();
        if (runtime.isEmpty()) {
            return List.of();
        }

        List<TriageCandidate> candidates = new ArrayList<>();
        RuntimeRatioEvidenceRow row = runtime.orElseThrow();
        if (latencyCandidate.isPresent()
                && ratioAtLeast(row.datasourcePoolUsageRatio(), DATASOURCE_POOL_RATIO_THRESHOLD)) {
            candidates.add(saturationCandidate(
                    "db_pool_high_with_latency",
                    "DB pool 사용률 확인 필요",
                    "DB pool 사용률이 높고 느린 응답 신호도 함께 관찰됩니다.",
                    "DB 연결 대기 가능성을 먼저 확인해보세요.",
                    row,
                    latencyCandidate.orElseThrow().card().confidence()));
        }
        if (latencyCandidate.isPresent()
                && ratioAtLeast(row.cpuUsageRatio(), CPU_USAGE_RATIO_THRESHOLD)) {
            candidates.add(saturationCandidate(
                    "cpu_high_with_latency",
                    "CPU 사용률 확인 필요",
                    "CPU 사용률이 높고 느린 응답 신호도 함께 관찰됩니다.",
                    "CPU saturation 가능성을 먼저 확인해보세요.",
                    row,
                    latencyCandidate.orElseThrow().card().confidence()));
        }
        if ((latencyCandidate.isPresent() || errorCandidate.isPresent())
                && ratioAtLeast(row.heapUsedRatio(), HEAP_USED_RATIO_THRESHOLD)) {
            double companionConfidence = latencyCandidate
                    .or(() -> errorCandidate)
                    .orElseThrow()
                    .card()
                    .confidence();
            candidates.add(saturationCandidate(
                    "heap_high_hint",
                    "Heap 사용률 확인 필요",
                    "Heap 사용률이 높고 오류 또는 지연 신호도 함께 관찰됩니다.",
                    "GC pressure나 memory pressure 가능성을 먼저 확인해보세요.",
                    row,
                    companionConfidence));
        }
        return candidates;
    }

    private static TriageCandidate saturationCandidate(
            String ruleId,
            String title,
            String summary,
            String recommendation,
            RuntimeRatioEvidenceRow runtime,
            double companionConfidence) {
        double confidence = clamp(companionConfidence - 0.04d);
        ApplicationDashboardReadModel.TriageEvidence evidence = new ApplicationDashboardReadModel.TriageEvidence(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                runtimeRatioEvidence(Optional.of(runtime)),
                "current",
                null);
        ApplicationDashboardReadModel.TriageCard card = new ApplicationDashboardReadModel.TriageCard(
                ruleId,
                ApplicationDashboardReadModel.TriageSeverity.INFO,
                title,
                summary,
                recommendation,
                confidence,
                Math.max(0, score(confidence) - 5),
                null,
                evidence);
        return new TriageCandidate(card, false, 0);
    }

    private int errorBadBucketCount(List<RecentBucketEvidenceRow> rows) {
        return (int) applicationLevelRecentBuckets(rows).stream()
                .filter(row -> row.requestCount() > 0L)
                .filter(row -> errorRate(row.errorCount(), row.requestCount()).compareTo(ERROR_RATE_THRESHOLD) >= 0)
                .count();
    }

    private int latencyBadBucketCount(List<RecentBucketEvidenceRow> rows) {
        return (int) applicationLevelRecentBuckets(rows).stream()
                .filter(row -> slowShare(row.durationBucketsJson())
                        .filter(share -> share.share().compareTo(LATENCY_SLOW_SHARE_THRESHOLD) >= 0)
                        .isPresent())
                .count();
    }

    /**
     * raw instance row가 섞여 들어와도 최근 5개 application-level 30초 bucket으로만 bad count를 계산한다.
     */
    private List<RecentBucketEvidenceRow> applicationLevelRecentBuckets(List<RecentBucketEvidenceRow> rows) {
        return RecentBucketEvidenceRows.applicationLevelBuckets(
                rows,
                DegradedHysteresisInput.RECENT_BUCKET_WINDOW,
                objectMapper);
    }

    private Optional<SlowShare> slowShare(ApplicationDashboardReadModel.HistogramWindow window) {
        if (!"available".equals(window.status()) || window.totalCount() <= 0L) {
            return Optional.empty();
        }
        return slowShare(window.buckets(), window.totalCount());
    }

    private Optional<SlowShare> slowShare(String durationBucketsJson) {
        return parseDurationBuckets(durationBucketsJson)
                .flatMap(buckets -> slowShare(buckets, buckets.isEmpty() ? 0L : buckets.get(buckets.size() - 1).count()));
    }

    private static Optional<SlowShare> slowShare(
            List<ApplicationDashboardReadModel.HistogramBucket> buckets,
            long totalCount) {
        if (buckets.isEmpty() || totalCount <= 0L) {
            return Optional.empty();
        }
        Optional<ApplicationDashboardReadModel.HistogramBucket> thresholdBucket = buckets.stream()
                .filter(bucket -> bucket.leMs() == LATENCY_SLOW_BUCKET_LE_MS)
                .findFirst();
        if (thresholdBucket.isEmpty()) {
            return Optional.empty();
        }
        long slowCount = Math.max(0L, totalCount - thresholdBucket.orElseThrow().count());
        return Optional.of(new SlowShare(decimal(slowCount, totalCount)));
    }

    private Optional<List<ApplicationDashboardReadModel.HistogramBucket>> parseDurationBuckets(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isArray()) {
                return Optional.empty();
            }
            List<ApplicationDashboardReadModel.HistogramBucket> buckets = new ArrayList<>();
            for (JsonNode item : root) {
                JsonNode leMs = item.get("leMs");
                JsonNode count = item.get("count");
                if (leMs == null || count == null || !leMs.canConvertToLong() || !count.canConvertToLong()) {
                    return Optional.empty();
                }
                long boundary = leMs.asLong();
                long cumulativeCount = count.asLong();
                if (boundary < 0L || cumulativeCount < 0L) {
                    return Optional.empty();
                }
                buckets.add(new ApplicationDashboardReadModel.HistogramBucket(boundary, cumulativeCount));
            }
            return Optional.of(buckets.stream()
                    .sorted(Comparator.comparingLong(ApplicationDashboardReadModel.HistogramBucket::leMs))
                    .toList());
        } catch (JsonProcessingException exception) {
            return Optional.empty();
        }
    }

    private static double confidenceFromAbsoluteErrorRate(BigDecimal currentRate) {
        BigDecimal confidence = BigDecimal.valueOf(CARD_EXPOSURE_CONFIDENCE)
                .add(currentRate.subtract(ERROR_RATE_THRESHOLD).multiply(BigDecimal.valueOf(3.0d)));
        return clamp(confidence.doubleValue());
    }

    private static double confidenceFromAbsoluteSlowShare(BigDecimal currentShare) {
        BigDecimal confidence = BigDecimal.valueOf(CARD_EXPOSURE_CONFIDENCE)
                .add(currentShare.subtract(LATENCY_SLOW_SHARE_THRESHOLD).multiply(BigDecimal.valueOf(1.5d)));
        return clamp(confidence.doubleValue());
    }

    private static double clamp(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private static int score(double confidence) {
        return Math.max(0, Math.min(100, (int) Math.round(confidence * 100.0d)));
    }

    private static ApplicationDashboardReadModel.TriageSeverity severity(double confidence) {
        if (confidence >= 0.90d) {
            return ApplicationDashboardReadModel.TriageSeverity.CRITICAL;
        }
        if (confidence >= CARD_EXPOSURE_CONFIDENCE) {
            return ApplicationDashboardReadModel.TriageSeverity.WARNING;
        }
        return ApplicationDashboardReadModel.TriageSeverity.INFO;
    }

    private static BigDecimal errorRate(long errorCount, long requestCount) {
        return decimal(errorCount, requestCount);
    }

    private static BigDecimal decimal(long numerator, long denominator) {
        if (denominator <= 0L) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP)
                .stripTrailingZeros();
    }

    private static boolean ratioAtLeast(BigDecimal value, BigDecimal threshold) {
        return value != null && value.compareTo(threshold) >= 0;
    }

    private static ApplicationDashboardReadModel.HistogramEvidenceSummary histogramSummary(
            ApplicationDashboardReadModel.HistogramWindow window) {
        return new ApplicationDashboardReadModel.HistogramEvidenceSummary(
                window.status(),
                window.totalCount(),
                window.buckets());
    }

    private static ApplicationDashboardReadModel.RuntimeRatioEvidence runtimeRatioEvidence(
            Optional<RuntimeRatioEvidenceRow> runtimeRatio) {
        return runtimeRatio
                .map(row -> new ApplicationDashboardReadModel.RuntimeRatioEvidence(
                        row.cpuUsageRatio(),
                        row.heapUsedRatio(),
                        row.datasourcePoolUsageRatio()))
                .orElse(null);
    }

    private static ApplicationDashboardReadModel.SourcePercentilePointSummary sourcePercentilePoint(
            ApplicationDashboardReadModel.SourceScopedPercentiles sourceScopedPercentiles) {
        if (sourceScopedPercentiles.items().isEmpty()) {
            return null;
        }
        ApplicationDashboardReadModel.PercentileItem item = sourceScopedPercentiles.items().get(0);
        return new ApplicationDashboardReadModel.SourcePercentilePointSummary(
                item.source(),
                sourceScopedPercentiles.scope(),
                item.instance(),
                item.bucketEndUtc(),
                item.requestCount(),
                item.p95Ms(),
                item.p99Ms());
    }

    private static int compareCandidates(TriageCandidate first, TriageCandidate second) {
        int severity = Integer.compare(severityRank(second.card().severity()), severityRank(first.card().severity()));
        if (severity != 0) {
            return severity;
        }
        int score = Integer.compare(second.card().score(), first.card().score());
        if (score != 0) {
            return score;
        }
        int confidence = Double.compare(second.card().confidence(), first.card().confidence());
        if (confidence != 0) {
            return confidence;
        }
        return first.card().ruleId().compareTo(second.card().ruleId());
    }

    private static int severityRank(ApplicationDashboardReadModel.TriageSeverity severity) {
        return switch (severity) {
            case CRITICAL -> 3;
            case WARNING -> 2;
            case INFO -> 1;
        };
    }

    /**
     * triage 계산에 필요한 bounded service input이다.
     */
    public record TriageSummaryInput(
            WindowBucketAggregate currentAggregate,
            ApplicationDashboardReadModel.HistogramDistribution histogramDistribution,
            ApplicationDashboardReadModel.SourceScopedPercentiles sourceScopedPercentiles,
            List<RecentBucketEvidenceRow> recentBuckets,
            Optional<RuntimeRatioEvidenceRow> runtimeRatio,
            AcceptedBucketFreshnessStatus freshnessStatus
    ) {

        /**
         * repository projection과 evidence object를 null 없이 보존한다.
         */
        public TriageSummaryInput {
            Objects.requireNonNull(currentAggregate, "currentAggregate must not be null");
            Objects.requireNonNull(histogramDistribution, "histogramDistribution must not be null");
            Objects.requireNonNull(sourceScopedPercentiles, "sourceScopedPercentiles must not be null");
            recentBuckets = List.copyOf(Objects.requireNonNull(recentBuckets, "recentBuckets must not be null"));
            runtimeRatio = Objects.requireNonNull(runtimeRatio, "runtimeRatio must not be null");
            Objects.requireNonNull(freshnessStatus, "freshnessStatus must not be null");
        }
    }

    /**
     * UI 노출 card 목록과 lifecycle service에 넘길 degraded input을 분리한 결과다.
     */
    public record TriageSummary(
            List<ApplicationDashboardReadModel.TriageCard> triageCards,
            DegradedHysteresisInput degradedInput
    ) {

        /**
         * card 목록과 degraded input을 null 없이 보존한다.
         */
        public TriageSummary {
            triageCards = List.copyOf(Objects.requireNonNull(triageCards, "triageCards must not be null"));
            Objects.requireNonNull(degradedInput, "degradedInput must not be null");
        }

        /**
         * triage card와 degraded concern이 모두 없는 기본 결과를 만든다.
         */
        public static TriageSummary empty() {
            return new TriageSummary(List.of(), DegradedHysteresisInput.noConcern());
        }
    }

    private record TriageCandidate(
            ApplicationDashboardReadModel.TriageCard card,
            boolean degradedCandidate,
            int badBucketsInRecentFive
    ) {
    }

    private record SlowShare(BigDecimal share) {
    }
}
