package com.observation.portal.domain.dashboard.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Application Dashboard current API가 반환하는 read-model-contract skeleton이다.
 *
 * <p>Story 5.5에서는 typed endpoint priority를 포함하되, endpoint p95/p99나 raw endpoint detail은 만들지 않는다.</p>
 */
public record ApplicationDashboardReadModel(
        OffsetDateTime generatedAt,
        Application application,
        State state,
        StarterConnection starterConnection,
        ZeroInsight zeroInsight,
        Recovery recovery,
        Metrics metrics,
        SourceScopedPercentiles sourceScopedPercentiles,
        HistogramDistribution histogramDistribution,
        List<TriageCard> triageCards,
        List<EndpointPriorityItem> endpointPriority,
        List<InstanceEntry> instances,
        Object snapshot
) {

    /**
     * top-level dashboard field가 항상 존재하도록 필수 field와 placeholder collection을 검증한다.
     */
    public ApplicationDashboardReadModel {
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        Objects.requireNonNull(application, "application must not be null");
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(starterConnection, "starterConnection must not be null");
        Objects.requireNonNull(recovery, "recovery must not be null");
        Objects.requireNonNull(metrics, "metrics must not be null");
        Objects.requireNonNull(sourceScopedPercentiles, "sourceScopedPercentiles must not be null");
        Objects.requireNonNull(histogramDistribution, "histogramDistribution must not be null");
        triageCards = List.copyOf(Objects.requireNonNull(triageCards, "triageCards must not be null"));
        if (triageCards.isEmpty()) {
            Objects.requireNonNull(zeroInsight, "zeroInsight must not be null when triageCards is empty");
        }
        endpointPriority = List.copyOf(Objects.requireNonNull(endpointPriority, "endpointPriority must not be null"));
        instances = List.copyOf(Objects.requireNonNull(instances, "instances must not be null"));
        if (instances.size() > 50) {
            throw new IllegalArgumentException("instances must not exceed 50");
        }
    }

    /**
     * dashboard가 속한 application/environment row identity와 metric data window/freshness를 담는다.
     */
    public record Application(
            UUID projectId,
            UUID applicationId,
            String name,
            String environment,
            OffsetDateTime lastAcceptedBucketAt,
            OffsetDateTime lastHealthyAt,
            SourceWindow sourceWindow,
            Freshness freshness
    ) {

        /**
         * application identity와 source axis 정보를 검증한다.
         */
        public Application {
            Objects.requireNonNull(projectId, "projectId must not be null");
            Objects.requireNonNull(applicationId, "applicationId must not be null");
            name = requireText(name, "name");
            environment = requireText(environment, "environment");
            Objects.requireNonNull(sourceWindow, "sourceWindow must not be null");
            Objects.requireNonNull(freshness, "freshness must not be null");
        }
    }

    /**
     * query evaluationAt 기준 current 15분 window와 직전 baseline 15분 window를 담는다.
     */
    public record SourceWindow(Window current, Window baseline) {

        /**
         * current와 baseline window가 모두 존재하도록 검증한다.
         */
        public SourceWindow {
            Objects.requireNonNull(current, "current must not be null");
            Objects.requireNonNull(baseline, "baseline must not be null");
        }
    }

    /**
     * dashboard response에 노출되는 UTC window boundary다.
     */
    public record Window(OffsetDateTime startUtc, OffsetDateTime endUtc) {

        /**
         * 시작/종료 boundary가 유효한 순서를 갖도록 검증한다.
         */
        public Window {
            Objects.requireNonNull(startUtc, "startUtc must not be null");
            Objects.requireNonNull(endUtc, "endUtc must not be null");
            if (!endUtc.isAfter(startUtc)) {
                throw new IllegalArgumentException("endUtc must be after startUtc");
            }
        }
    }

    /**
     * accepted bucket endUtc 기반 freshness source와 threshold 시각을 담는다.
     */
    public record Freshness(
            OffsetDateTime lastObservedAt,
            OffsetDateTime staleAt,
            OffsetDateTime downAt
    ) {
    }

    /**
     * LifecycleStateService가 결정한 metric data-plane state를 API copy로 옮긴 값이다.
     */
    public record State(
            String code,
            String label,
            String rationale,
            String recommendedAction,
            String scope
    ) {

        /**
         * state code/copy/scope가 응답에서 비어 있지 않도록 검증한다.
         */
        public State {
            code = requireText(code, "code");
            label = requireText(label, "label");
            rationale = requireText(rationale, "rationale");
            recommendedAction = requireText(recommendedAction, "recommendedAction");
            scope = requireText(scope, "scope");
        }
    }

    /**
     * starter heartbeat control-plane source만 사용한 connection summary다.
     */
    public record StarterConnection(
            String statusSource,
            OffsetDateTime lastHeartbeatAt,
            String lastHeartbeatStatus,
            String connectionMeaning,
            String stateImpact
    ) {

        /**
         * starter connection source와 metric state 영향 여부를 명시적으로 보존한다.
         */
        public StarterConnection {
            statusSource = requireText(statusSource, "statusSource");
            lastHeartbeatStatus = requireText(lastHeartbeatStatus, "lastHeartbeatStatus");
            connectionMeaning = requireText(connectionMeaning, "connectionMeaning");
            stateImpact = requireText(stateImpact, "stateImpact");
        }
    }

    /**
     * triageCards가 비어 있을 때 UI가 표시할 server-computed 이유와 권장 행동이다.
     */
    public record ZeroInsight(
            String reasonCode,
            String message,
            String recommendedAction
    ) {

        /**
         * zero insight reason과 copy가 항상 존재하도록 검증한다.
         */
        public ZeroInsight {
            reasonCode = requireText(reasonCode, "reasonCode");
            message = requireText(message, "message");
            recommendedAction = requireText(recommendedAction, "recommendedAction");
        }
    }

    /**
     * stale/down 이후 회복 관찰 안내를 top-level state와 분리해 담는다.
     */
    public record Recovery(
            boolean isRecovering,
            OffsetDateTime lastHealthyAt,
            Integer retryAfterSeconds,
            String recommendedAction
    ) {
    }

    /**
     * current 15분 window의 request/error scalar만 담는다.
     */
    public record Metrics(
            long requestCount,
            long errorCount,
            BigDecimal errorRate
    ) {

        /**
         * request/error count가 음수가 되지 않도록 검증한다.
         */
        public Metrics {
            if (requestCount < 0) {
                throw new IllegalArgumentException("requestCount must not be negative");
            }
            if (errorCount < 0) {
                throw new IllegalArgumentException("errorCount must not be negative");
            }
        }
    }

    /**
     * starter가 보낸 instance bucket scope의 canonical p95/p99 point 목록과 표시 정책을 담는다.
     *
     * <p>items는 current window 안 instance별 latest point만 포함하며, 여러 point를 평균/최댓값/병합하지 않는다.</p>
     */
    public record SourceScopedPercentiles(
            String source,
            String scope,
            String displayPolicy,
            String aggregatePolicy,
            String status,
            String reason,
            List<PercentileItem> items
    ) {

        /**
         * source/scope 정책과 item collection이 response에서 항상 명시되도록 검증한다.
         */
        public SourceScopedPercentiles {
            source = requireText(source, "source");
            scope = requireText(scope, "scope");
            displayPolicy = requireText(displayPolicy, "displayPolicy");
            aggregatePolicy = requireText(aggregatePolicy, "aggregatePolicy");
            status = requireText(status, "status");
            reason = trimNullable(reason);
            items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
        }

        /**
         * current window에서 starter percentile point를 찾지 못했을 때의 명시적 missing response를 만든다.
         */
        public static SourceScopedPercentiles empty() {
            return new SourceScopedPercentiles(
                    "starter_local",
                    "instance_bucket",
                    "latest_starter_point_per_instance_in_current_window",
                    "no_average_no_max_no_merge_no_histogram_recalculation",
                    "missing",
                    "no_percentile_points_in_current_window",
                    List.of());
        }

        /**
         * valid starter percentile point가 있을 때 available 상태의 source-scoped response를 만든다.
         */
        public static SourceScopedPercentiles available(List<PercentileItem> items) {
            return new SourceScopedPercentiles(
                    "starter_local",
                    "instance_bucket",
                    "latest_starter_point_per_instance_in_current_window",
                    "no_average_no_max_no_merge_no_histogram_recalculation",
                    "available",
                    null,
                    items);
        }

        /**
         * persisted row는 있었지만 표시 가능한 valid point가 없을 때 insufficient response를 만든다.
         */
        public static SourceScopedPercentiles insufficient(String reason) {
            return new SourceScopedPercentiles(
                    "starter_local",
                    "instance_bucket",
                    "latest_starter_point_per_instance_in_current_window",
                    "no_average_no_max_no_merge_no_histogram_recalculation",
                    "insufficient",
                    reason,
                    List.of());
        }
    }

    /**
     * persisted `local_percentiles_json` 값을 API item으로 옮긴 instance bucket percentile point다.
     *
     * <p>p95Ms/p99Ms는 starter가 해당 30초 instance bucket에서 직접 산출해 보낸 canonical value이며, dashboard service가
     * 새로 계산한 값이 아니다.</p>
     */
    public record PercentileItem(
            String source,
            String application,
            String environment,
            String instance,
            OffsetDateTime bucketStartUtc,
            OffsetDateTime bucketEndUtc,
            long requestCount,
            long p95Ms,
            long p99Ms
    ) {

        /**
         * percentile point의 identity, source, bucket boundary와 numeric evidence를 검증한다.
         */
        public PercentileItem {
            source = requireText(source, "source");
            application = requireText(application, "application");
            environment = requireText(environment, "environment");
            instance = requireText(instance, "instance");
            Objects.requireNonNull(bucketStartUtc, "bucketStartUtc must not be null");
            Objects.requireNonNull(bucketEndUtc, "bucketEndUtc must not be null");
            if (!bucketEndUtc.isAfter(bucketStartUtc)) {
                throw new IllegalArgumentException("bucketEndUtc must be after bucketStartUtc");
            }
            if (requestCount <= 0) {
                throw new IllegalArgumentException("requestCount must be positive");
            }
            if (p95Ms < 0) {
                throw new IllegalArgumentException("p95Ms must not be negative");
            }
            if (p99Ms < p95Ms) {
                throw new IllegalArgumentException("p99Ms must be greater than or equal to p95Ms");
            }
        }
    }

    /**
     * application-level summary duration histogram distribution evidence를 current/baseline window별로 담는다.
     *
     * <p>이 block은 bucket distribution 표시 source이며 p95/p99, delta, regression, confidence, rule 판단을 포함하지 않는다.</p>
     */
    public record HistogramDistribution(
            String source,
            String scope,
            String displayPolicy,
            String aggregatePolicy,
            HistogramWindow current,
            HistogramWindow baseline
    ) {

        /**
         * histogram distribution top-level object와 window evidence가 항상 존재하도록 검증한다.
         */
        public HistogramDistribution {
            source = requireText(source, "source");
            scope = requireText(scope, "scope");
            displayPolicy = requireText(displayPolicy, "displayPolicy");
            aggregatePolicy = requireText(aggregatePolicy, "aggregatePolicy");
            Objects.requireNonNull(current, "current must not be null");
            Objects.requireNonNull(baseline, "baseline must not be null");
        }

        /**
         * current/baseline 모두 histogram evidence가 없는 기본 response를 만든다.
         */
        public static HistogramDistribution empty() {
            return new HistogramDistribution(
                    "histogram_bucket_distribution",
                    "application",
                    "bucket_distribution_evidence",
                    "sum_cumulative_counts_only_when_boundary_set_matches",
                    HistogramWindow.missing("no_histogram_buckets_in_current_window"),
                    HistogramWindow.missing("no_histogram_buckets_in_baseline_window"));
        }
    }

    /**
     * 하나의 dashboard window에 대한 histogram bucket distribution evidence와 상태를 담는다.
     */
    public record HistogramWindow(
            String status,
            String reason,
            long totalCount,
            List<HistogramBucket> buckets
    ) {

        /**
         * status/reason과 bucket collection이 null 없이 표현되고 count가 음수가 되지 않도록 검증한다.
         */
        public HistogramWindow {
            status = requireText(status, "status");
            reason = trimNullable(reason);
            if (totalCount < 0) {
                throw new IllegalArgumentException("totalCount must not be negative");
            }
            buckets = List.copyOf(Objects.requireNonNull(buckets, "buckets must not be null"));
        }

        /**
         * histogram bucket row가 없을 때 window-specific missing reason을 담은 empty distribution을 만든다.
         */
        public static HistogramWindow missing(String reason) {
            return new HistogramWindow("missing", reason, 0L, List.of());
        }

        /**
         * boundary mismatch처럼 distribution을 안전하게 만들 수 없을 때 unavailable 상태를 반환한다.
         */
        public static HistogramWindow unavailable(String reason) {
            return new HistogramWindow("unavailable", reason, 0L, List.of());
        }

        /**
         * row는 있었지만 표시 가능한 bucket evidence가 부족할 때 insufficient 상태를 반환한다.
         */
        public static HistogramWindow insufficient(String reason) {
            return new HistogramWindow("insufficient", reason, 0L, List.of());
        }

        /**
         * boundary가 일치해 합산된 cumulative bucket distribution을 available 상태로 반환한다.
         */
        public static HistogramWindow available(long totalCount, List<HistogramBucket> buckets) {
            return new HistogramWindow("available", null, totalCount, buckets);
        }
    }

    /**
     * cumulative histogram distribution의 한 boundary와 해당 boundary 이하 누적 count다.
     */
    public record HistogramBucket(long leMs, long count) {

        /**
         * histogram boundary와 cumulative count가 음수가 아닌 값인지 검증한다.
         */
        public HistogramBucket {
            if (leMs < 0) {
                throw new IllegalArgumentException("leMs must not be negative");
            }
            if (count < 0) {
                throw new IllegalArgumentException("count must not be negative");
            }
        }
    }

    /**
     * server-computed application-level triage card다.
     *
     * <p>Story 5.4에서는 endpoint ranking을 만들지 않고, optional affectedEndpoint는 단일 확인 힌트로만 사용한다.</p>
     */
    public record TriageCard(
            String ruleId,
            TriageSeverity severity,
            String title,
            String summary,
            String recommendation,
            double confidence,
            int score,
            String affectedEndpoint,
            TriageEvidence evidence
    ) {

        /**
         * card copy와 bounded score/confidence/evidence를 검증해 UI가 그대로 렌더링할 수 있게 한다.
         */
        public TriageCard {
            ruleId = requireText(ruleId, "ruleId");
            Objects.requireNonNull(severity, "severity must not be null");
            title = requireText(title, "title");
            summary = requireText(summary, "summary");
            recommendation = requireText(recommendation, "recommendation");
            if (confidence < 0.0d || confidence > 1.0d) {
                throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
            }
            if (score < 0 || score > 100) {
                throw new IllegalArgumentException("score must be between 0 and 100");
            }
            affectedEndpoint = trimNullable(affectedEndpoint);
            Objects.requireNonNull(evidence, "evidence must not be null");
        }
    }

    /**
     * MVP triage card severity를 제한된 JSON 문자열로 노출하는 enum이다.
     */
    public enum TriageSeverity {
        INFO("info"),
        WARNING("warning"),
        CRITICAL("critical");

        private final String value;

        TriageSeverity(String value) {
            this.value = value;
        }

        /**
         * public API에는 enum 이름이 아니라 계약의 lower-case severity code를 반환한다.
         */
        @JsonValue
        public String value() {
            return value;
        }
    }

    /**
     * triage card가 나온 이유를 설명하는 bounded evidence object다.
     *
     * <p>raw path/query/trace/per-request sample, raw JSON string, endpoint p95/p99, histogram-derived percentile은 포함하지
     * 않는다.</p>
     */
    public record TriageEvidence(
            Long requestCount,
            Long currentErrorCount,
            BigDecimal currentErrorRate,
            Long baselineRequestCount,
            Long baselineErrorCount,
            BigDecimal baselineErrorRate,
            BigDecimal errorRateDelta,
            BigDecimal currentSlowShare,
            BigDecimal baselineSlowShare,
            HistogramEvidenceSummary currentHistogram,
            HistogramEvidenceSummary baselineHistogram,
            RuntimeRatioEvidence runtimeRatio,
            String freshnessStatusReason,
            SourcePercentilePointSummary sourcePercentilePoint
    ) {
    }

    /**
     * histogram distribution window의 bounded summary만 card evidence에 복사한다.
     */
    public record HistogramEvidenceSummary(
            String status,
            long totalCount,
            List<HistogramBucket> buckets
    ) {

        /**
         * histogram evidence summary가 raw JSON string 없이 bounded bucket list만 갖도록 검증한다.
         */
        public HistogramEvidenceSummary {
            status = requireText(status, "status");
            if (totalCount < 0L) {
                throw new IllegalArgumentException("totalCount must not be negative");
            }
            buckets = List.copyOf(Objects.requireNonNull(buckets, "buckets must not be null"));
        }
    }

    /**
     * saturation hint가 사용할 latest runtime ratio sample summary다.
     */
    public record RuntimeRatioEvidence(
            BigDecimal cpuUsageRatio,
            BigDecimal heapUsedRatio,
            BigDecimal datasourcePoolUsageRatio
    ) {

        /**
         * runtime ratio가 nullable이더라도 값이 있으면 0~1 범위 안에 있는지 검증한다.
         */
        public RuntimeRatioEvidence {
            validateRatio(cpuUsageRatio, "cpuUsageRatio");
            validateRatio(heapUsedRatio, "heapUsedRatio");
            validateRatio(datasourcePoolUsageRatio, "datasourcePoolUsageRatio");
        }
    }

    /**
     * source-scoped starter percentile point를 평균/병합 없이 요약하는 optional evidence다.
     */
    public record SourcePercentilePointSummary(
            String source,
            String scope,
            String instance,
            OffsetDateTime bucketEndUtc,
            long requestCount,
            Long p95Ms,
            Long p99Ms
    ) {

        /**
         * percentile point summary의 source/scope와 bucket timestamp를 보존한다.
         */
        public SourcePercentilePointSummary {
            source = requireText(source, "source");
            scope = requireText(scope, "scope");
            instance = requireText(instance, "instance");
            Objects.requireNonNull(bucketEndUtc, "bucketEndUtc must not be null");
            if (requestCount <= 0L) {
                throw new IllegalArgumentException("requestCount must be positive");
            }
            if (p95Ms != null && p95Ms < 0L) {
                throw new IllegalArgumentException("p95Ms must not be negative");
            }
            if (p99Ms != null && p99Ms < 0L) {
                throw new IllegalArgumentException("p99Ms must not be negative");
            }
        }
    }

    /**
     * server-computed endpoint priority item이다.
     *
     * <p>endpoint ranking의 canonical response source이며, controller/UI/repository가 rank, rule, confidence, action을 다시
     * 계산하지 않도록 필요한 bounded evidence만 함께 담는다.</p>
     */
    public record EndpointPriorityItem(
            int rank,
            String method,
            String route,
            String endpointKey,
            EndpointPriorityReason reason,
            List<String> ruleIds,
            double confidence,
            int score,
            EndpointPriorityFreshness freshness,
            EndpointPriorityEvidence evidence,
            String recommendedAction
    ) {

        /**
         * priority item의 public API shape와 bounded numeric 값을 검증한다.
         */
        public EndpointPriorityItem {
            if (rank < 1) {
                throw new IllegalArgumentException("rank must be greater than or equal to 1");
            }
            method = requireText(method, "method");
            route = requireText(route, "route");
            endpointKey = requireText(endpointKey, "endpointKey");
            if (!endpointKey.equals(method + " " + route)) {
                throw new IllegalArgumentException("endpointKey must match method + ' ' + route");
            }
            if ("UNKNOWN".equalsIgnoreCase(route)) {
                throw new IllegalArgumentException("UNKNOWN route must not be exposed as endpoint priority");
            }
            Objects.requireNonNull(reason, "reason must not be null");
            ruleIds = List.copyOf(Objects.requireNonNull(ruleIds, "ruleIds must not be null"));
            if (ruleIds.isEmpty() || ruleIds.stream().anyMatch(ruleId -> ruleId == null || ruleId.isBlank())) {
                throw new IllegalArgumentException("ruleIds must contain non-blank values");
            }
            if (!Double.isFinite(confidence) || confidence < 0.0d || confidence > 1.0d) {
                throw new IllegalArgumentException("confidence must be finite and between 0.0 and 1.0");
            }
            if (score < 0 || score > 100) {
                throw new IllegalArgumentException("score must be between 0 and 100");
            }
            Objects.requireNonNull(freshness, "freshness must not be null");
            Objects.requireNonNull(evidence, "evidence must not be null");
            recommendedAction = requireText(recommendedAction, "recommendedAction");
        }
    }

    /**
     * Story 5.5 MVP endpoint priority reason을 닫힌 JSON 문자열로 제한한다.
     */
    public enum EndpointPriorityReason {
        ERROR_SPIKE("error_spike"),
        LATENCY_SPIKE("latency_spike"),
        ERROR_AND_LATENCY("error_and_latency"),
        COMPARATIVE_REGRESSION("comparative_regression"),
        RECENT_ERROR("recent_error");

        private final String value;

        EndpointPriorityReason(String value) {
            this.value = value;
        }

        /**
         * public API에는 enum 이름 대신 계약의 lower-case reason code를 반환한다.
         */
        @JsonValue
        public String value() {
            return value;
        }
    }

    /**
     * endpoint evidence availability를 bounded status code로 표현한다.
     */
    public enum EndpointEvidenceStatus {
        AVAILABLE("available"),
        MISSING("missing"),
        INSUFFICIENT("insufficient"),
        INSUFFICIENT_BASELINE("insufficient_baseline"),
        UNAVAILABLE("unavailable");

        private final String value;

        EndpointEvidenceStatus(String value) {
            this.value = value;
        }

        /**
         * public API에는 enum 이름 대신 계약의 lower-case status code를 반환한다.
         */
        @JsonValue
        public String value() {
            return value;
        }
    }

    /**
     * Application Dashboard에서 Instance Detail로 진입할 때 사용하는 bounded instance entry다.
     *
     * <p>instance health/state/priority를 계산하지 않고 catalog UUID, 표시 이름, latest seen 시각, evidence link만 담는다.</p>
     */
    public record InstanceEntry(
            UUID instanceId,
            String instanceName,
            OffsetDateTime lastSeenAt,
            InstanceEntryLinks links
    ) {

        /**
         * instance UUID path identity와 evidence navigation link가 비어 있지 않도록 검증한다.
         */
        public InstanceEntry {
            Objects.requireNonNull(instanceId, "instanceId must not be null");
            instanceName = requireText(instanceName, "instanceName");
            Objects.requireNonNull(links, "links must not be null");
        }
    }

    /**
     * Instance Detail evidence API로 이동하기 위한 link block이다.
     */
    public record InstanceEntryLinks(String evidence) {

        /**
         * evidence link가 UUID 기반 path로 채워졌는지 확인한다.
         */
        public InstanceEntryLinks {
            evidence = requireText(evidence, "evidence");
        }
    }

    /**
     * current endpoint priority item의 metric freshness provenance를 담는다.
     */
    public record EndpointPriorityFreshness(
            String status,
            OffsetDateTime lastObservedAt,
            String sourceWindow,
            String reason
    ) {

        /**
         * freshness status와 current endpoint evidence timestamp가 response에서 누락되지 않도록 검증한다.
         */
        public EndpointPriorityFreshness {
            status = requireText(status, "status");
            Objects.requireNonNull(lastObservedAt, "lastObservedAt must not be null");
            sourceWindow = requireText(sourceWindow, "sourceWindow");
            reason = trimNullable(reason);
        }
    }

    /**
     * endpoint priority 판단에 사용한 bounded evidence object다.
     *
     * <p>raw endpoint JSON, raw path/query/trace/per-request sample, endpoint percentile scalar는 포함하지 않는다.</p>
     */
    public record EndpointPriorityEvidence(
            long requestCount,
            long errorCount,
            BigDecimal errorRate,
            Long baselineRequestCount,
            Long baselineErrorCount,
            BigDecimal baselineErrorRate,
            BigDecimal errorRateDelta,
            List<HistogramBucket> durationBuckets,
            List<HistogramBucket> baselineDurationBuckets,
            BigDecimal slowShare,
            BigDecimal baselineSlowShare,
            BigDecimal slowShareDelta,
            String bucketDistributionSource,
            EndpointEvidenceStatus errorEvidenceStatus,
            EndpointEvidenceStatus latencyEvidenceStatus
    ) {

        /**
         * evidence field가 bounded count/rate/bucket list만 갖도록 검증하고 bucket list를 방어적으로 복사한다.
         */
        public EndpointPriorityEvidence {
            validateCount(requestCount, "requestCount");
            validateCount(errorCount, "errorCount");
            if (errorCount > requestCount) {
                throw new IllegalArgumentException("errorCount must not exceed requestCount");
            }
            validateFraction(Objects.requireNonNull(errorRate, "errorRate must not be null"), "errorRate");
            validateNullableCount(baselineRequestCount, "baselineRequestCount");
            validateNullableCount(baselineErrorCount, "baselineErrorCount");
            if (baselineRequestCount != null && baselineErrorCount != null
                    && baselineErrorCount > baselineRequestCount) {
                throw new IllegalArgumentException("baselineErrorCount must not exceed baselineRequestCount");
            }
            validateNullableFraction(baselineErrorRate, "baselineErrorRate");
            validateNullableDelta(errorRateDelta, "errorRateDelta");
            durationBuckets = copyNullableBuckets(durationBuckets);
            baselineDurationBuckets = copyNullableBuckets(baselineDurationBuckets);
            validateNullableFraction(slowShare, "slowShare");
            validateNullableFraction(baselineSlowShare, "baselineSlowShare");
            validateNullableDelta(slowShareDelta, "slowShareDelta");
            bucketDistributionSource = requireText(bucketDistributionSource, "bucketDistributionSource");
            if (!"histogram_bucket_distribution".equals(bucketDistributionSource)) {
                throw new IllegalArgumentException(
                        "bucketDistributionSource must be histogram_bucket_distribution");
            }
            Objects.requireNonNull(errorEvidenceStatus, "errorEvidenceStatus must not be null");
            Objects.requireNonNull(latencyEvidenceStatus, "latencyEvidenceStatus must not be null");
        }
    }

    private static void validateRatio(BigDecimal value, String fieldName) {
        if (value != null
                && (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0)) {
            throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0");
        }
    }

    private static void validateFraction(BigDecimal value, String fieldName) {
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0");
        }
    }

    private static void validateNullableFraction(BigDecimal value, String fieldName) {
        if (value != null) {
            validateFraction(value, fieldName);
        }
    }

    private static void validateNullableDelta(BigDecimal value, String fieldName) {
        if (value != null
                && (value.compareTo(BigDecimal.ONE.negate()) < 0
                || value.compareTo(BigDecimal.ONE) > 0)) {
            throw new IllegalArgumentException(fieldName + " must be between -1.0 and 1.0");
        }
    }

    private static void validateCount(long value, String fieldName) {
        if (value < 0L) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
    }

    private static void validateNullableCount(Long value, String fieldName) {
        if (value != null) {
            validateCount(value, fieldName);
        }
    }

    private static List<HistogramBucket> copyNullableBuckets(List<HistogramBucket> buckets) {
        return buckets == null ? null : List.copyOf(buckets);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String trimNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
