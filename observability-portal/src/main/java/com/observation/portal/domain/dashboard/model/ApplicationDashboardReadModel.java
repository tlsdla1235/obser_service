package com.observation.portal.domain.dashboard.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Application Dashboard current API와 snapshot 저장 payload가 공유하는 dashboard read model이다.
 *
 * <p>canonical field는 Source of Truth의 `dashboard_read_model.v1` shape를 제공하고, 기존 legacy field는 frontend
 * migration 동안 같은 server-computed 값을 읽는 compatibility surface로 유지한다.</p>
 */
public record ApplicationDashboardReadModel(
        String schemaVersion,
        String mode,
        OffsetDateTime generatedAt,
        Application application,
        CanonicalWindow window,
        Thresholds thresholds,
        OperatorSummary operatorSummary,
        DataQuality dataQuality,
        StarterConnection starterConnection,
        Signals signals,
        State state,
        List<StateReason> stateReasons,
        List<AttentionEvidence> attentionEvidence,
        List<FirstLookCandidate> firstLookCandidates,
        ReadSemantics readSemantics,
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
    public static final String SCHEMA_VERSION = "dashboard_read_model.v1";
    public static final String LIVE_MODE = "live";
    public static final String SNAPSHOT_MODE = "snapshot";
    private static final String RECENT_30_MINUTES_WINDOW = "recent_30_minutes";
    private static final String ACCEPTED_METRIC_BUCKETS_SOURCE = "accepted_metric_buckets";
    private static final String DASHBOARD_SNAPSHOTS_READ_MODEL_SOURCE = "dashboard_snapshots.read_model_json";
    private static final String ACCEPTED_BUCKET_DISTRIBUTION_SOURCE = "accepted_bucket";
    private static final long MINIMUM_REQUEST_COUNT = 30L;
    private static final BigDecimal ERROR_RATE_THRESHOLD = new BigDecimal("0.05");
    private static final BigDecimal SLOW_SHARE_OVER_500MS_THRESHOLD = new BigDecimal("0.20");
    private static final BigDecimal DATASOURCE_POOL_USAGE_THRESHOLD = new BigDecimal("0.85");
    private static final BigDecimal CPU_USAGE_THRESHOLD = new BigDecimal("0.85");
    private static final BigDecimal HEAP_USAGE_THRESHOLD = new BigDecimal("0.90");
    private static final long LATENCY_SLOW_BUCKET_LE_MS = 500L;

    /**
     * canonical dashboard field와 legacy compatibility field가 항상 존재하도록 검증한다.
     */
    public ApplicationDashboardReadModel {
        schemaVersion = requireText(schemaVersion, "schemaVersion");
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("schemaVersion must be " + SCHEMA_VERSION);
        }
        mode = requireText(mode, "mode");
        if (!LIVE_MODE.equals(mode) && !SNAPSHOT_MODE.equals(mode)) {
            throw new IllegalArgumentException("mode must be live or snapshot");
        }
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        Objects.requireNonNull(application, "application must not be null");
        Objects.requireNonNull(window, "window must not be null");
        Objects.requireNonNull(thresholds, "thresholds must not be null");
        Objects.requireNonNull(operatorSummary, "operatorSummary must not be null");
        Objects.requireNonNull(dataQuality, "dataQuality must not be null");
        Objects.requireNonNull(starterConnection, "starterConnection must not be null");
        Objects.requireNonNull(signals, "signals must not be null");
        Objects.requireNonNull(state, "state must not be null");
        stateReasons = List.copyOf(Objects.requireNonNull(stateReasons, "stateReasons must not be null"));
        attentionEvidence = List.copyOf(Objects.requireNonNull(
                attentionEvidence,
                "attentionEvidence must not be null"));
        firstLookCandidates = List.copyOf(Objects.requireNonNull(
                firstLookCandidates,
                "firstLookCandidates must not be null"));
        if (firstLookCandidates.size() > 3) {
            throw new IllegalArgumentException("firstLookCandidates must not exceed 3");
        }
        Objects.requireNonNull(readSemantics, "readSemantics must not be null");
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
     * 기존 service/test가 사용하던 skeleton 생성자다.
     *
     * <p>legacy field에서 canonical field를 파생해 live dashboard response와 snapshot writer input이 같은
     * `dashboard_read_model.v1` shape를 갖도록 한다.</p>
     */
    public ApplicationDashboardReadModel(
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
            Object snapshot) {
        this(
                generatedAt,
                application,
                state,
                starterConnection,
                zeroInsight,
                recovery,
                metrics,
                sourceScopedPercentiles,
                histogramDistribution,
                triageCards,
                endpointPriority,
                instances,
                null,
                snapshot);
    }

    /**
     * service가 조회한 runtime ratio evidence까지 canonical USE signal에 반영하는 생성자다.
     */
    public ApplicationDashboardReadModel(
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
            RuntimeRatioEvidence runtimeRatio,
            Object snapshot) {
        this(
                SCHEMA_VERSION,
                LIVE_MODE,
                generatedAt,
                application,
                canonicalWindow(application),
                Thresholds.mvp(),
                operatorSummary(state, zeroInsight, triageCards, endpointPriority),
                dataQuality(state, application, metrics, histogramDistribution),
                starterConnection,
                signals(metrics, histogramDistribution, runtimeRatio),
                state,
                stateReasons(state, triageCards),
                attentionEvidence(state, triageCards, endpointPriority),
                firstLookCandidates(state, triageCards, endpointPriority),
                ReadSemantics.live(),
                zeroInsight,
                recovery,
                metrics,
                sourceScopedPercentiles,
                histogramDistribution,
                triageCards,
                endpointPriority,
                instances,
                snapshot);
    }

    /**
     * Source of Truth public naming을 갖는 dashboard 판단 window다.
     */
    public record CanonicalWindow(
            String type,
            OffsetDateTime startUtc,
            OffsetDateTime endUtc
    ) {

        /**
         * recent 30분 window type과 시간 boundary를 검증한다.
         */
        public CanonicalWindow {
            type = requireText(type, "type");
            if (!RECENT_30_MINUTES_WINDOW.equals(type)) {
                throw new IllegalArgumentException("type must be " + RECENT_30_MINUTES_WINDOW);
            }
            Objects.requireNonNull(startUtc, "startUtc must not be null");
            Objects.requireNonNull(endUtc, "endUtc must not be null");
            if (!endUtc.isAfter(startUtc)) {
                throw new IllegalArgumentException("endUtc must be after startUtc");
            }
        }
    }

    /**
     * Snapshot detail이 저장 당시 판단 기준을 재현할 수 있도록 MVP threshold를 명시한다.
     */
    public record Thresholds(
            long minimumRequestCount,
            BigDecimal errorRate,
            BigDecimal slowShareOver500ms,
            BigDecimal datasourcePoolUsage,
            BigDecimal cpuUsage,
            BigDecimal heapUsage
    ) {

        /**
         * Source of Truth MVP threshold 값을 가진 기본 block을 만든다.
         */
        public static Thresholds mvp() {
            return new Thresholds(
                    MINIMUM_REQUEST_COUNT,
                    ERROR_RATE_THRESHOLD,
                    SLOW_SHARE_OVER_500MS_THRESHOLD,
                    DATASOURCE_POOL_USAGE_THRESHOLD,
                    CPU_USAGE_THRESHOLD,
                    HEAP_USAGE_THRESHOLD);
        }

        /**
         * threshold 값이 public contract의 허용 범위를 벗어나지 않도록 검증한다.
         */
        public Thresholds {
            if (minimumRequestCount < 1L) {
                throw new IllegalArgumentException("minimumRequestCount must be positive");
            }
            validateFraction(Objects.requireNonNull(errorRate, "errorRate must not be null"), "errorRate");
            validateFraction(
                    Objects.requireNonNull(slowShareOver500ms, "slowShareOver500ms must not be null"),
                    "slowShareOver500ms");
            validateFraction(
                    Objects.requireNonNull(datasourcePoolUsage, "datasourcePoolUsage must not be null"),
                    "datasourcePoolUsage");
            validateFraction(Objects.requireNonNull(cpuUsage, "cpuUsage must not be null"), "cpuUsage");
            validateFraction(Objects.requireNonNull(heapUsage, "heapUsage must not be null"), "heapUsage");
        }
    }

    /**
     * 운영자가 첫 화면에서 읽는 요약과 첫 확인 후보 copy를 server-computed 값으로 제공한다.
     */
    public record OperatorSummary(
            String headline,
            String primaryProblemCode,
            String firstLookText
    ) {

        /**
         * headline과 first look copy가 비어 있지 않도록 검증한다.
         */
        public OperatorSummary {
            headline = requireText(headline, "headline");
            primaryProblemCode = trimNullable(primaryProblemCode);
            firstLookText = requireText(firstLookText, "firstLookText");
        }
    }

    /**
     * metric evidence를 얼마나 믿을 수 있는지 lifecycle state와 분리해 설명한다.
     */
    public record DataQuality(
            String state,
            long requestCount,
            long minimumRequestCount,
            OffsetDateTime lastObservedAt,
            List<String> limitations
    ) {

        /**
         * data quality state와 limitation 목록을 안정적으로 직렬화한다.
         */
        public DataQuality {
            state = requireText(state, "state");
            if (requestCount < 0L) {
                throw new IllegalArgumentException("requestCount must not be negative");
            }
            if (minimumRequestCount < 1L) {
                throw new IllegalArgumentException("minimumRequestCount must be positive");
            }
            limitations = List.copyOf(Objects.requireNonNull(limitations, "limitations must not be null"));
            if (limitations.stream().anyMatch(value -> value == null || value.isBlank())) {
                throw new IllegalArgumentException("limitations must contain non-blank values");
            }
        }
    }

    /**
     * RED/USE signal을 canonical field로 묶어 UI가 histogram과 resource evidence를 다시 해석하지 않게 한다.
     */
    public record Signals(RedSignals red, UseSignals use) {

        /**
         * RED와 USE block이 항상 존재하도록 검증한다.
         */
        public Signals {
            Objects.requireNonNull(red, "red must not be null");
            Objects.requireNonNull(use, "use must not be null");
        }
    }

    /**
     * recent 30분 요청량, 5xx 오류, 500ms 초과 요청 비율 evidence다.
     */
    public record RedSignals(
            long requestCount,
            long errorCount,
            String errorSemantic,
            BigDecimal errorRate,
            Long slowCountOver500ms,
            BigDecimal slowShareOver500ms,
            String latencyEvidenceStatus
    ) {

        /**
         * RED count/rate와 latency evidence availability를 검증한다.
         */
        public RedSignals {
            validateCount(requestCount, "requestCount");
            validateCount(errorCount, "errorCount");
            if (errorCount > requestCount) {
                throw new IllegalArgumentException("errorCount must not exceed requestCount");
            }
            errorSemantic = requireText(errorSemantic, "errorSemantic");
            validateNullableFraction(errorRate, "errorRate");
            validateNullableCount(slowCountOver500ms, "slowCountOver500ms");
            validateNullableFraction(slowShareOver500ms, "slowShareOver500ms");
            latencyEvidenceStatus = requireText(latencyEvidenceStatus, "latencyEvidenceStatus");
        }
    }

    /**
     * 공유 resource pressure hint를 root cause 확정 없이 표시하기 위한 USE signal block이다.
     */
    public record UseSignals(
            ResourceSignal datasourcePoolUsage,
            ResourceSignal cpuUsage,
            ResourceSignal heapUsage
    ) {

        /**
         * 세 resource block이 모두 존재하도록 검증한다.
         */
        public UseSignals {
            Objects.requireNonNull(datasourcePoolUsage, "datasourcePoolUsage must not be null");
            Objects.requireNonNull(cpuUsage, "cpuUsage must not be null");
            Objects.requireNonNull(heapUsage, "heapUsage must not be null");
        }

        public static UseSignals missing() {
            return new UseSignals(
                    ResourceSignal.missing(DATASOURCE_POOL_USAGE_THRESHOLD),
                    ResourceSignal.missing(CPU_USAGE_THRESHOLD),
                    ResourceSignal.missing(HEAP_USAGE_THRESHOLD));
        }
    }

    /**
     * 단일 resource usage signal의 threshold와 상태다.
     */
    public record ResourceSignal(
            BigDecimal max,
            BigDecimal threshold,
            String status,
            OffsetDateTime observedAt
    ) {

        public static ResourceSignal missing(BigDecimal threshold) {
            return new ResourceSignal(null, threshold, "missing", null);
        }

        /**
         * resource 값이 있으면 ratio 범위 안에 있는지 검증한다.
         */
        public ResourceSignal {
            validateNullableFraction(max, "max");
            validateFraction(Objects.requireNonNull(threshold, "threshold must not be null"), "threshold");
            status = requireText(status, "status");
        }
    }

    /**
     * lifecycle state를 만들거나 바꿀 수 있는 직접 근거다.
     */
    public record StateReason(
            String type,
            String severity,
            String scope,
            String target,
            String reasonCode,
            String operatorText
    ) {

        /**
         * state reason의 bounded type/scope/reason/copy를 검증한다.
         */
        public StateReason {
            type = requireText(type, "type");
            severity = requireText(severity, "severity");
            scope = requireText(scope, "scope");
            target = trimNullable(target);
            reasonCode = requireText(reasonCode, "reasonCode");
            operatorText = requireText(operatorText, "operatorText");
        }
    }

    /**
     * state를 바꾸지는 않지만 먼저 확인해야 하는 endpoint/resource/data-quality evidence다.
     */
    public record AttentionEvidence(
            String type,
            String severity,
            String scope,
            String target,
            String reasonCode,
            boolean affectsLifecycleState,
            String operatorText
    ) {

        /**
         * attention evidence가 lifecycle state source로 승격되지 않도록 검증한다.
         */
        public AttentionEvidence {
            type = requireText(type, "type");
            severity = requireText(severity, "severity");
            scope = requireText(scope, "scope");
            target = trimNullable(target);
            reasonCode = requireText(reasonCode, "reasonCode");
            if (affectsLifecycleState) {
                throw new IllegalArgumentException("attention evidence must not affect lifecycle state");
            }
            operatorText = requireText(operatorText, "operatorText");
        }
    }

    /**
     * 운영자가 먼저 볼 후보를 endpoint/resource/data-quality를 합친 bounded queue로 제공한다.
     */
    public record FirstLookCandidate(
            int rank,
            String type,
            String target,
            String reasonCode,
            String source,
            String operatorText
    ) {

        /**
         * candidate rank와 reason/source/copy가 public response에서 안정적으로 보이게 한다.
         */
        public FirstLookCandidate {
            if (rank < 1) {
                throw new IllegalArgumentException("rank must be positive");
            }
            type = requireText(type, "type");
            target = trimNullable(target);
            reasonCode = requireText(reasonCode, "reasonCode");
            source = requireText(source, "source");
            operatorText = requireText(operatorText, "operatorText");
        }
    }

    /**
     * live/snapshot 공통 read semantics와 non-source helper 의미를 명시한다.
     */
    public record ReadSemantics(
            String source,
            boolean snapshotDetailRecalculates,
            boolean markerIsStateSource,
            boolean baselineComparisonUsedForMvpDecision,
            boolean helperColumnsAreStateSource,
            boolean histogramBucketsUsedForPercentiles,
            String bucketDistributionSource,
            String bucketDistributionMeaning,
            String bucketEndBoundary
    ) {

        public static ReadSemantics live() {
            return new ReadSemantics(
                    ACCEPTED_METRIC_BUCKETS_SOURCE,
                    false,
                    false,
                    false,
                    false,
                    false,
                    ACCEPTED_BUCKET_DISTRIBUTION_SOURCE,
                    "accepted_metric_buckets.duration_buckets_json_distribution_display_only",
                    "bucket_end_utc > window.startUtc and bucket_end_utc <= window.endUtc");
        }

        public static ReadSemantics snapshot() {
            return new ReadSemantics(
                    DASHBOARD_SNAPSHOTS_READ_MODEL_SOURCE,
                    false,
                    false,
                    false,
                    false,
                    false,
                    ACCEPTED_BUCKET_DISTRIBUTION_SOURCE,
                    "stored_read_model.accepted_metric_buckets.duration_buckets_json_distribution_display_only",
                    "bucket_end_utc > window.startUtc and bucket_end_utc <= window.endUtc");
        }

        /**
         * source와 marker/helper/baseline non-source semantics를 response에 고정한다.
         */
        public ReadSemantics {
            source = requireText(source, "source");
            if (!ACCEPTED_METRIC_BUCKETS_SOURCE.equals(source)
                    && !DASHBOARD_SNAPSHOTS_READ_MODEL_SOURCE.equals(source)) {
                throw new IllegalArgumentException(
                        "source must be accepted_metric_buckets or dashboard_snapshots.read_model_json");
            }
            if (snapshotDetailRecalculates) {
                throw new IllegalArgumentException("snapshotDetailRecalculates must be false");
            }
            if (markerIsStateSource) {
                throw new IllegalArgumentException("markerIsStateSource must be false");
            }
            if (baselineComparisonUsedForMvpDecision) {
                throw new IllegalArgumentException("baselineComparisonUsedForMvpDecision must be false");
            }
            if (helperColumnsAreStateSource) {
                throw new IllegalArgumentException("helperColumnsAreStateSource must be false");
            }
            if (histogramBucketsUsedForPercentiles) {
                throw new IllegalArgumentException("histogramBucketsUsedForPercentiles must be false");
            }
            bucketDistributionSource = requireText(bucketDistributionSource, "bucketDistributionSource");
            if (!ACCEPTED_BUCKET_DISTRIBUTION_SOURCE.equals(bucketDistributionSource)) {
                throw new IllegalArgumentException("bucketDistributionSource must be accepted_bucket");
            }
            bucketDistributionMeaning = requireText(bucketDistributionMeaning, "bucketDistributionMeaning");
            bucketEndBoundary = requireText(bucketEndBoundary, "bucketEndBoundary");
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
     * query evaluationAt 기준 recent 30분 accepted bucket window와 legacy baseline compatibility field를 담는다.
     */
    public record SourceWindow(Window current, Window baseline) {

        /**
         * current는 recent 30분 호환 alias로 유지하고 baseline은 MVP primary 판단에서 쓰지 않으므로 null을 허용한다.
         */
        public SourceWindow {
            Objects.requireNonNull(current, "current must not be null");
        }

        /**
         * Source of Truth public naming을 노출하면서 기존 current 소비자와 같은 30분 window를 가리키게 한다.
         */
        @JsonProperty("recent_30_minutes")
        public Window recent30Minutes() {
            return current;
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
     * recent 30분 window의 request/error scalar만 담는다.
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
         * recent 30분 window에서 starter canonical percentile point를 찾지 못했을 때의 명시적 missing response를 만든다.
         */
        public static SourceScopedPercentiles empty() {
            return new SourceScopedPercentiles(
                    "starter_canonical_percentile",
                    "instance_bucket",
                    "source_scoped_points",
                    "no_average_no_max_no_merge_no_histogram_recalculation",
                    "missing",
                    "no_percentile_points_in_recent_30_minutes",
                    List.of());
        }

        /**
         * valid starter percentile point가 있을 때 available 상태의 source-scoped response를 만든다.
         */
        public static SourceScopedPercentiles available(List<PercentileItem> items) {
            return new SourceScopedPercentiles(
                    "starter_canonical_percentile",
                    "instance_bucket",
                    "source_scoped_points",
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
                    "starter_canonical_percentile",
                    "instance_bucket",
                    "source_scoped_points",
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
     * application-level summary duration histogram distribution evidence를 recent 30분 window 기준으로 담는다.
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
         * recent 30분 histogram evidence가 없는 기본 response와 baseline compatibility limitation을 만든다.
         */
        public static HistogramDistribution empty() {
            return new HistogramDistribution(
                    "accepted_bucket",
                    "application",
                    "cumulative_bucket_distribution",
                    "display_bucket_only_no_percentile_recalculation",
                    HistogramWindow.missing("no_histogram_buckets_in_recent_30_minutes"),
                    HistogramWindow.unavailable("baseline_comparison_not_used_for_mvp"));
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
        ERROR_SPIKE("error_rate_high"),
        LATENCY_SPIKE("latency_slow_share_high"),
        ERROR_AND_LATENCY("error_and_latency"),
        RECENT_ERROR("recent_server_error");

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
        INSUFFICIENT_BASELINE("baseline_comparison_not_used"),
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
     * <p>catalog UUID, 표시 이름, latest seen 시각, evidence link는 기존 navigation 계약으로 유지한다. `summary`는 SoT
     * Instance Summary row가 frontend 계산 없이 표시할 수 있는 server-computed scalar만 담으며, instance 독립 lifecycle
     * state나 root cause claim은 만들지 않는다.</p>
     */
    public record InstanceEntry(
            UUID instanceId,
            String instanceName,
            OffsetDateTime lastSeenAt,
            InstanceEntrySummary summary,
            InstanceEntryLinks links
    ) {

        /**
         * 기존 테스트/소비자가 쓰던 navigation-only 생성자를 보존한다.
         */
        public InstanceEntry(
                UUID instanceId,
                String instanceName,
                OffsetDateTime lastSeenAt,
                InstanceEntryLinks links) {
            this(
                    instanceId,
                    instanceName,
                    lastSeenAt,
                    InstanceEntrySummary.unavailable(),
                    links);
        }

        /**
         * instance UUID path identity와 evidence navigation link가 비어 있지 않도록 검증한다.
         */
        public InstanceEntry {
            Objects.requireNonNull(instanceId, "instanceId must not be null");
            instanceName = requireText(instanceName, "instanceName");
            Objects.requireNonNull(summary, "summary must not be null");
            Objects.requireNonNull(links, "links must not be null");
        }
    }

    /**
     * SoT Instance Summary row에 필요한 instance-scoped summary 묶음이다.
     *
     * <p>각 하위 block은 Instance Dashboard detail과 같은 source 의미를 쓰되, row 표시용으로 request/slow/heartbeat/
     * contribution scalar만 담는다.</p>
     */
    public record InstanceEntrySummary(
            InstanceEntryObservationStatus observationStatus,
            InstanceEntryStarterConnection starterConnection,
            InstanceEntryRedSignals red,
            InstanceEntryApplicationContribution applicationContribution
    ) {

        /**
         * frontend가 unavailable 상태를 명시적으로 표시할 수 있는 fallback summary다.
         */
        public static InstanceEntrySummary unavailable() {
            return new InstanceEntrySummary(
                    new InstanceEntryObservationStatus("metric_missing", "summary_not_available", null),
                    InstanceEntryStarterConnection.missing(),
                    new InstanceEntryRedSignals(0L, null, null),
                    new InstanceEntryApplicationContribution("insufficient", "summary_not_available"));
        }

        /**
         * row summary의 모든 표시 block이 존재하도록 검증한다.
         */
        public InstanceEntrySummary {
            Objects.requireNonNull(observationStatus, "observationStatus must not be null");
            Objects.requireNonNull(starterConnection, "starterConnection must not be null");
            Objects.requireNonNull(red, "red must not be null");
            Objects.requireNonNull(applicationContribution, "applicationContribution must not be null");
        }
    }

    /**
     * selected instance metric evidence가 current dashboard window에서 관측됐는지 설명한다.
     */
    public record InstanceEntryObservationStatus(
            String code,
            String reason,
            OffsetDateTime lastObservedBucketEndUtc
    ) {

        /**
         * observation code가 lifecycle state로 오해될 수 있는 값을 받지 않도록 제한한다.
         */
        public InstanceEntryObservationStatus {
            code = requireText(code, "code");
            if (!"observed".equals(code)
                    && !"not_observed_in_window".equals(code)
                    && !"metric_missing".equals(code)) {
                throw new IllegalArgumentException("code must be observed/not_observed_in_window/metric_missing");
            }
            reason = trimNullable(reason);
        }
    }

    /**
     * selected instance의 starter heartbeat control-plane 상태다.
     */
    public record InstanceEntryStarterConnection(
            OffsetDateTime lastHeartbeatAt,
            String lastHeartbeatStatus,
            String freshnessLabel
    ) {

        /**
         * heartbeat row가 없을 때 row가 명시적 missing 상태를 표시하게 한다.
         */
        public static InstanceEntryStarterConnection missing() {
            return new InstanceEntryStarterConnection(null, "missing", "missing");
        }

        /**
         * heartbeat code는 표시용 문자열로만 유지하고 metric state를 변경하지 않는다.
         */
        public InstanceEntryStarterConnection {
            lastHeartbeatStatus = requireText(lastHeartbeatStatus, "lastHeartbeatStatus");
            freshnessLabel = requireText(freshnessLabel, "freshnessLabel");
        }
    }

    /**
     * selected instance의 request count와 500ms 초과 지연 비율이다.
     */
    public record InstanceEntryRedSignals(
            long requestCount,
            Long slowCountOver500ms,
            BigDecimal slowShareOver500ms
    ) {

        /**
         * row metric scalar가 bounded count/rate 범위 안에 있는지 검증한다.
         */
        public InstanceEntryRedSignals {
            validateCount(requestCount, "requestCount");
            validateNullableCount(slowCountOver500ms, "slowCountOver500ms");
            validateNullableFraction(slowShareOver500ms, "slowShareOver500ms");
        }
    }

    /**
     * selected instance evidence가 application 판단을 설명하는 정도를 row badge용으로 담는다.
     */
    public record InstanceEntryApplicationContribution(
            String level,
            String reason
    ) {

        /**
         * contribution은 root cause가 아니라 표시 badge 수준의 bounded code로만 제한한다.
         */
        public InstanceEntryApplicationContribution {
            level = requireText(level, "level");
            if (!"none".equals(level)
                    && !"supporting".equals(level)
                    && !"attention".equals(level)
                    && !"contributing".equals(level)
                    && !"insufficient".equals(level)) {
                throw new IllegalArgumentException("level must be none/supporting/attention/contributing/insufficient");
            }
            reason = trimNullable(reason);
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
            if (!"accepted_bucket".equals(bucketDistributionSource)) {
                throw new IllegalArgumentException(
                        "bucketDistributionSource must be accepted_bucket");
            }
            Objects.requireNonNull(errorEvidenceStatus, "errorEvidenceStatus must not be null");
            Objects.requireNonNull(latencyEvidenceStatus, "latencyEvidenceStatus must not be null");
        }
    }

    private static CanonicalWindow canonicalWindow(Application application) {
        Application requiredApplication = Objects.requireNonNull(application, "application must not be null");
        Window current = requiredApplication.sourceWindow().current();
        return new CanonicalWindow(RECENT_30_MINUTES_WINDOW, current.startUtc(), current.endUtc());
    }

    private static OperatorSummary operatorSummary(
            State state,
            ZeroInsight zeroInsight,
            List<TriageCard> triageCards,
            List<EndpointPriorityItem> endpointPriority) {
        State requiredState = Objects.requireNonNull(state, "state must not be null");
        List<TriageCard> cards = List.copyOf(Objects.requireNonNullElse(triageCards, List.of()));
        List<EndpointPriorityItem> endpoints = List.copyOf(Objects.requireNonNullElse(endpointPriority, List.of()));
        String primaryProblemCode = cards.stream()
                .map(TriageCard::ruleId)
                .findFirst()
                .orElseGet(() -> endpoints.stream()
                        .findFirst()
                        .map(item -> item.reason().value())
                        .orElse(null));
        String firstLookText = endpoints.stream()
                .findFirst()
                .map(item -> "먼저 " + item.endpointKey() + " endpoint evidence를 확인하세요.")
                .orElseGet(() -> zeroInsight == null ? requiredState.recommendedAction() : zeroInsight.message());
        return new OperatorSummary(requiredState.rationale(), primaryProblemCode, firstLookText);
    }

    private static DataQuality dataQuality(
            State state,
            Application application,
            Metrics metrics,
            HistogramDistribution histogramDistribution) {
        State requiredState = Objects.requireNonNull(state, "state must not be null");
        Application requiredApplication = Objects.requireNonNull(application, "application must not be null");
        Metrics requiredMetrics = Objects.requireNonNull(metrics, "metrics must not be null");
        HistogramDistribution requiredHistogram = Objects.requireNonNull(
                histogramDistribution,
                "histogramDistribution must not be null");
        List<String> limitations = new java.util.ArrayList<>();
        limitations.add("baseline_comparison_not_used_for_mvp");
        if (requiredApplication.sourceWindow().baseline() == null) {
            limitations.add("sourceWindow.baseline_null_public_read_model");
        }
        if (!"available".equals(requiredHistogram.current().status())) {
            limitations.add("histogram_" + requiredHistogram.current().status());
        }
        return new DataQuality(
                dataQualityState(requiredState, requiredMetrics),
                requiredMetrics.requestCount(),
                MINIMUM_REQUEST_COUNT,
                requiredApplication.freshness().lastObservedAt(),
                limitations);
    }

    private static String dataQualityState(State state, Metrics metrics) {
        return switch (state.code()) {
            case "waiting_first_data" -> "waiting_first_data";
            case "stale" -> "stale";
            case "down" -> "down";
            case "unknown" -> metrics.requestCount() < MINIMUM_REQUEST_COUNT ? "sample_limited" : "partial";
            case "idle" -> "sample_limited";
            default -> metrics.requestCount() < MINIMUM_REQUEST_COUNT ? "sample_limited" : "sufficient";
        };
    }

    private static Signals signals(
            Metrics metrics,
            HistogramDistribution histogramDistribution,
            RuntimeRatioEvidence runtimeRatio) {
        Metrics requiredMetrics = Objects.requireNonNull(metrics, "metrics must not be null");
        HistogramDistribution requiredHistogram = Objects.requireNonNull(
                histogramDistribution,
                "histogramDistribution must not be null");
        SlowEvidence slowEvidence = slowEvidence(requiredHistogram.current());
        return new Signals(
                new RedSignals(
                        requiredMetrics.requestCount(),
                        requiredMetrics.errorCount(),
                        "server_error_5xx",
                        requiredMetrics.errorRate(),
                        slowEvidence.slowCountOver500ms(),
                        slowEvidence.slowShareOver500ms(),
                        slowEvidence.status()),
                useSignals(runtimeRatio));
    }

    private static UseSignals useSignals(RuntimeRatioEvidence runtimeRatio) {
        if (runtimeRatio == null) {
            return UseSignals.missing();
        }
        return new UseSignals(
                resourceSignal(runtimeRatio.datasourcePoolUsageRatio(), DATASOURCE_POOL_USAGE_THRESHOLD),
                resourceSignal(runtimeRatio.cpuUsageRatio(), CPU_USAGE_THRESHOLD),
                resourceSignal(runtimeRatio.heapUsedRatio(), HEAP_USAGE_THRESHOLD));
    }

    private static ResourceSignal resourceSignal(BigDecimal value, BigDecimal threshold) {
        if (value == null) {
            return ResourceSignal.missing(threshold);
        }
        String status = value.compareTo(threshold) >= 0 ? "threshold_exceeded" : "normal";
        return new ResourceSignal(value, threshold, status, null);
    }

    private static SlowEvidence slowEvidence(HistogramWindow window) {
        if (!"available".equals(window.status()) || window.totalCount() <= 0L) {
            return new SlowEvidence(null, null, window.status());
        }
        return window.buckets().stream()
                .filter(bucket -> bucket.leMs() == LATENCY_SLOW_BUCKET_LE_MS)
                .findFirst()
                .map(bucket -> {
                    long slowCount = Math.max(0L, window.totalCount() - bucket.count());
                    return new SlowEvidence(slowCount, fraction(slowCount, window.totalCount()), "available");
                })
                .orElseGet(() -> new SlowEvidence(null, null, "unavailable"));
    }

    private static List<StateReason> stateReasons(State state, List<TriageCard> triageCards) {
        State requiredState = Objects.requireNonNull(state, "state must not be null");
        List<TriageCard> cards = List.copyOf(Objects.requireNonNullElse(triageCards, List.of()));
        if ("degraded".equals(requiredState.code()) && !cards.isEmpty()) {
            List<StateReason> reasons = cards.stream()
                    .filter(ApplicationDashboardReadModel::affectsLifecycleState)
                    .map(card -> new StateReason(
                            "metric_rule",
                            card.severity().value(),
                            "application",
                            card.affectedEndpoint(),
                            card.ruleId(),
                            card.summary()))
                    .toList();
            if (!reasons.isEmpty()) {
                return reasons;
            }
        }
        if (!"active".equals(requiredState.code())) {
            return List.of(new StateReason(
                    "data_quality_guard",
                    "info",
                    "application",
                    null,
                    requiredState.code(),
                    requiredState.rationale()));
        }
        return List.of();
    }

    private static List<AttentionEvidence> attentionEvidence(
            State state,
            List<TriageCard> triageCards,
            List<EndpointPriorityItem> endpointPriority) {
        State requiredState = Objects.requireNonNull(state, "state must not be null");
        List<AttentionEvidence> items = new java.util.ArrayList<>();
        List.copyOf(Objects.requireNonNullElse(triageCards, List.of())).stream()
                .filter(card -> !"degraded".equals(requiredState.code()) || !affectsLifecycleState(card))
                .map(card -> new AttentionEvidence(
                        "triage_attention",
                        card.severity().value(),
                        "application",
                        card.affectedEndpoint(),
                        card.ruleId(),
                        false,
                        card.summary()))
                .forEach(items::add);
        List.copyOf(Objects.requireNonNullElse(endpointPriority, List.of())).stream()
                .map(item -> new AttentionEvidence(
                        "endpoint",
                        endpointSeverity(item.reason()),
                        "endpoint",
                        item.endpointKey(),
                        item.reason().value(),
                        false,
                        item.recommendedAction()))
                .forEach(items::add);
        return List.copyOf(items);
    }

    private static String endpointSeverity(EndpointPriorityReason reason) {
        return reason == EndpointPriorityReason.RECENT_ERROR ? "attention" : "warning";
    }

    private static boolean affectsLifecycleState(TriageCard card) {
        String ruleId = card.ruleId();
        return "application_error_rate_high".equals(ruleId)
                || "application_slow_share_high".equals(ruleId);
    }

    private static List<FirstLookCandidate> firstLookCandidates(
            State state,
            List<TriageCard> triageCards,
            List<EndpointPriorityItem> endpointPriority) {
        List<FirstLookCandidate> candidates = new java.util.ArrayList<>();
        State requiredState = Objects.requireNonNull(state, "state must not be null");
        List<TriageCard> cards = List.copyOf(Objects.requireNonNullElse(triageCards, List.of()));
        if ("degraded".equals(requiredState.code()) && !cards.isEmpty()) {
            TriageCard card = cards.get(0);
            candidates.add(new FirstLookCandidate(
                    1,
                    "application",
                    card.affectedEndpoint(),
                    card.ruleId(),
                    "stateReasons",
                    card.recommendation()));
        }
        for (EndpointPriorityItem item : List.copyOf(Objects.requireNonNullElse(endpointPriority, List.of()))) {
            if (candidates.size() >= 3) {
                break;
            }
            candidates.add(new FirstLookCandidate(
                    candidates.size() + 1,
                    "endpoint",
                    item.endpointKey(),
                    item.reason().value(),
                    "endpointPriority",
                    item.recommendedAction()));
        }
        if (candidates.isEmpty() && !"active".equals(requiredState.code())) {
            candidates.add(new FirstLookCandidate(
                    1,
                    "data_quality",
                    null,
                    requiredState.code(),
                    "dataQuality",
                    requiredState.recommendedAction()));
        }
        return List.copyOf(candidates);
    }

    private static BigDecimal fraction(long numerator, long denominator) {
        if (denominator <= 0L) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 6, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros();
    }

    private record SlowEvidence(
            Long slowCountOver500ms,
            BigDecimal slowShareOver500ms,
            String status
    ) {
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
