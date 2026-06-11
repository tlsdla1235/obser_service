package com.observation.portal.domain.instance.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Instance Dashboard live/snapshot API가 반환하는 bounded read model이다.
 *
 * <p>이 모델은 application 판단의 하위 evidence detail만 표현하며, instance 독립 lifecycle state, health score,
 * root cause, recovery proof를 top-level 계약으로 만들지 않는다.</p>
 */
public record InstanceDashboardReadModel(
        String schemaVersion,
        String mode,
        OffsetDateTime generatedAt,
        Application application,
        Instance instance,
        Window window,
        Thresholds thresholds,
        ApplicationStateRef applicationStateRef,
        ObservationStatus observationStatus,
        ApplicationContribution applicationContribution,
        DataQuality dataQuality,
        StarterConnection starterConnection,
        Signals signals,
        EndpointEvidence endpointEvidence,
        ResourceEvidence resourceEvidence,
        List<PatternEvidence> patterns,
        Snapshot snapshot,
        ReadSemantics readSemantics,
        Links links,
        List<String> excludedCapabilities
) {

    private static final String SCHEMA_VERSION = "instance_dashboard_read_model.v1";
    private static final Set<String> MODES = Set.of("live", "snapshot");
    private static final Set<String> WINDOW_SOURCES = Set.of(
            "live_recent_30_minutes",
            "selected_application_snapshot");
    private static final Set<String> OBSERVATION_CODES = Set.of(
            "observed",
            "not_observed_in_window",
            "metric_missing",
            "insufficient_evidence",
            "malformed_evidence");
    private static final Set<String> CONTRIBUTION_LEVELS = Set.of(
            "none",
            "attention",
            "supporting",
            "contributing",
            "insufficient");
    private static final Set<String> DATA_QUALITY_STATES = Set.of(
            "sufficient",
            "sample_limited",
            "not_observed_in_window",
            "metric_missing",
            "partial",
            "malformed");
    private static final Set<String> ENDPOINT_STATUSES = Set.of("available", "missing", "insufficient");
    private static final Set<String> RESOURCE_STATUSES = Set.of("available", "missing", "partial");
    private static final Set<String> RESOURCE_ITEM_STATUSES = Set.of(
            "within_threshold",
            "threshold_exceeded",
            "missing");
    private static final Set<String> PATTERN_CONTRIBUTIONS = Set.of(
            "none",
            "attention_only",
            "shared_resource_pressure_pattern");

    /**
     * API response의 source semantics와 mode별 snapshot block 존재 여부를 검증한다.
     */
    public InstanceDashboardReadModel {
        schemaVersion = requireText(schemaVersion, "schemaVersion");
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("schemaVersion must be " + SCHEMA_VERSION);
        }
        mode = requireText(mode, "mode");
        validateAllowed(mode, MODES, "mode");
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        Objects.requireNonNull(application, "application must not be null");
        Objects.requireNonNull(instance, "instance must not be null");
        Objects.requireNonNull(window, "window must not be null");
        Objects.requireNonNull(thresholds, "thresholds must not be null");
        Objects.requireNonNull(applicationStateRef, "applicationStateRef must not be null");
        Objects.requireNonNull(observationStatus, "observationStatus must not be null");
        Objects.requireNonNull(applicationContribution, "applicationContribution must not be null");
        Objects.requireNonNull(dataQuality, "dataQuality must not be null");
        Objects.requireNonNull(starterConnection, "starterConnection must not be null");
        Objects.requireNonNull(signals, "signals must not be null");
        Objects.requireNonNull(endpointEvidence, "endpointEvidence must not be null");
        Objects.requireNonNull(resourceEvidence, "resourceEvidence must not be null");
        patterns = List.copyOf(Objects.requireNonNull(patterns, "patterns must not be null"));
        Objects.requireNonNull(readSemantics, "readSemantics must not be null");
        Objects.requireNonNull(links, "links must not be null");
        excludedCapabilities = List.copyOf(Objects.requireNonNull(
                excludedCapabilities,
                "excludedCapabilities must not be null"));
        if (excludedCapabilities.stream().anyMatch(capability -> capability == null || capability.isBlank())) {
            throw new IllegalArgumentException("excludedCapabilities must contain non-blank values");
        }
        if ("live".equals(mode) && snapshot != null) {
            throw new IllegalArgumentException("snapshot must be null in live mode");
        }
        if ("snapshot".equals(mode) && snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null in snapshot mode");
        }
        if ("live".equals(mode) && !"live_recent_30_minutes".equals(window.windowSource())) {
            throw new IllegalArgumentException("live mode must use live_recent_30_minutes windowSource");
        }
        if ("snapshot".equals(mode) && !"selected_application_snapshot".equals(window.windowSource())) {
            throw new IllegalArgumentException("snapshot mode must use selected_application_snapshot windowSource");
        }
        if (!window.windowSource().equals(readSemantics.windowSource())) {
            throw new IllegalArgumentException("windowSource must match readSemantics.windowSource");
        }
        if (!stateRefSourceForMode(mode).equals(applicationStateRef.source())) {
            throw new IllegalArgumentException("applicationStateRef.source must match mode");
        }
        if ("snapshot".equals(mode) && !snapshot.snapshotId().equals(applicationStateRef.snapshotId())) {
            throw new IllegalArgumentException("applicationStateRef.snapshotId must match snapshot.snapshotId");
        }
    }

    /**
     * selected instance가 속한 application identity와 dashboard link를 담는다.
     */
    public record Application(
            UUID projectId,
            UUID applicationId,
            String name,
            String environment,
            ApplicationLinks links
    ) {

        /**
         * project/application catalog path identity와 표시 이름을 검증한다.
         */
        public Application {
            Objects.requireNonNull(projectId, "projectId must not be null");
            Objects.requireNonNull(applicationId, "applicationId must not be null");
            name = requireText(name, "name");
            environment = requireText(environment, "environment");
            Objects.requireNonNull(links, "links must not be null");
        }
    }

    /**
     * application dashboard로 이동하는 link block이다.
     */
    public record ApplicationLinks(String dashboard) {

        /**
         * dashboard link가 빈 문자열로 직렬화되지 않게 검증한다.
         */
        public ApplicationLinks {
            dashboard = requireText(dashboard, "dashboard");
        }
    }

    /**
     * selected catalog instance identity와 관측 시각 metadata를 담는다.
     */
    public record Instance(
            UUID instanceId,
            String instanceName,
            OffsetDateTime firstSeenAt,
            OffsetDateTime lastSeenAt
    ) {

        /**
         * instance path identity와 catalog 표시 이름을 검증한다.
         */
        public Instance {
            Objects.requireNonNull(instanceId, "instanceId must not be null");
            instanceName = requireText(instanceName, "instanceName");
            Objects.requireNonNull(firstSeenAt, "firstSeenAt must not be null");
            Objects.requireNonNull(lastSeenAt, "lastSeenAt must not be null");
        }
    }

    /**
     * live 또는 selected Application Snapshot 기준 recent 30분 bucket window다.
     */
    public record Window(
            String name,
            OffsetDateTime startUtc,
            OffsetDateTime endUtc,
            int bucketDurationSeconds,
            String windowSource
    ) {

        /**
         * Instance Dashboard가 `recent_30_minutes` public naming만 사용하도록 검증한다.
         */
        public Window {
            name = requireText(name, "name");
            if (!"recent_30_minutes".equals(name)) {
                throw new IllegalArgumentException("name must be recent_30_minutes");
            }
            Objects.requireNonNull(startUtc, "startUtc must not be null");
            Objects.requireNonNull(endUtc, "endUtc must not be null");
            if (!endUtc.isAfter(startUtc)) {
                throw new IllegalArgumentException("endUtc must be after startUtc");
            }
            if (bucketDurationSeconds != 30) {
                throw new IllegalArgumentException("bucketDurationSeconds must be 30");
            }
            windowSource = requireText(windowSource, "windowSource");
            validateAllowed(windowSource, WINDOW_SOURCES, "windowSource");
        }
    }

    /**
     * Instance Dashboard가 Application Dashboard와 공유하는 MVP 판단 기준값이다.
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
         * threshold 값이 public contract의 허용 범위에 있는지 검증한다.
         */
        public Thresholds {
            if (minimumRequestCount < 1L) {
                throw new IllegalArgumentException("minimumRequestCount must be positive");
            }
            validateNullableFraction(Objects.requireNonNull(errorRate, "errorRate must not be null"), "errorRate");
            validateNullableFraction(
                    Objects.requireNonNull(slowShareOver500ms, "slowShareOver500ms must not be null"),
                    "slowShareOver500ms");
            validateNullableFraction(
                    Objects.requireNonNull(datasourcePoolUsage, "datasourcePoolUsage must not be null"),
                    "datasourcePoolUsage");
            validateNullableFraction(Objects.requireNonNull(cpuUsage, "cpuUsage must not be null"), "cpuUsage");
            validateNullableFraction(Objects.requireNonNull(heapUsage, "heapUsage must not be null"), "heapUsage");
        }
    }

    /**
     * Application Dashboard 또는 Application Snapshot이 소유한 state 참조만 담는 block이다.
     */
    public record ApplicationStateRef(
            String lifecycleOwner,
            String source,
            String applicationStateCode,
            UUID snapshotId
    ) {

        /**
         * 이 block이 instance state가 아니라 application owner state 참조임을 강제한다.
         */
        public ApplicationStateRef {
            lifecycleOwner = requireText(lifecycleOwner, "lifecycleOwner");
            if (!"application".equals(lifecycleOwner)) {
                throw new IllegalArgumentException("lifecycleOwner must be application");
            }
            source = requireText(source, "source");
            if (!"application_dashboard_live".equals(source)
                    && !"selected_application_snapshot".equals(source)) {
                throw new IllegalArgumentException("source must be application dashboard or selected snapshot");
            }
            applicationStateCode = trimNullable(applicationStateCode);
            if ("application_dashboard_live".equals(source) && snapshotId != null) {
                throw new IllegalArgumentException("snapshotId must be null for live application state ref");
            }
            if ("selected_application_snapshot".equals(source) && snapshotId == null) {
                throw new IllegalArgumentException("snapshotId must not be null for snapshot application state ref");
            }
        }
    }

    /**
     * selected instance metric evidence가 window 안에서 관측 가능한지 설명한다.
     */
    public record ObservationStatus(
            String code,
            String reason,
            OffsetDateTime lastObservedBucketEndUtc
    ) {

        /**
         * observation status가 lifecycle state로 오해될 수 있는 값을 받지 않도록 제한한다.
         */
        public ObservationStatus {
            code = requireText(code, "code");
            validateAllowed(code, OBSERVATION_CODES, "code");
            reason = trimNullable(reason);
        }
    }

    /**
     * selected instance evidence가 application 판단을 설명하는 정도를 causality 없이 담는다.
     */
    public record ApplicationContribution(
            String level,
            String reason,
            List<String> evidenceRefs
    ) {

        /**
         * root cause claim 없이 bounded evidence reference만 담도록 검증한다.
         */
        public ApplicationContribution {
            level = requireText(level, "level");
            validateAllowed(level, CONTRIBUTION_LEVELS, "level");
            reason = trimNullable(reason);
            evidenceRefs = List.copyOf(Objects.requireNonNull(evidenceRefs, "evidenceRefs must not be null"));
            if (evidenceRefs.stream().anyMatch(ref -> ref == null || ref.isBlank())) {
                throw new IllegalArgumentException("evidenceRefs must contain non-blank values");
            }
        }
    }

    /**
     * metric evidence 품질과 제한 사항을 starter heartbeat와 분리해 표현한다.
     */
    public record DataQuality(
            String state,
            List<String> limitations,
            String source
    ) {

        /**
         * data quality source가 accepted metric bucket axis임을 검증한다.
         */
        public DataQuality {
            state = requireText(state, "state");
            validateAllowed(state, DATA_QUALITY_STATES, "state");
            limitations = List.copyOf(Objects.requireNonNull(limitations, "limitations must not be null"));
            source = requireText(source, "source");
            if (!"accepted_metric_buckets".equals(source)) {
                throw new IllegalArgumentException("source must be accepted_metric_buckets");
            }
        }
    }

    /**
     * starter heartbeat control-plane connection metadata다.
     */
    public record StarterConnection(
            String statusSource,
            OffsetDateTime lastHeartbeatAt,
            String lastHeartbeatStatus,
            String freshnessLabel,
            String connectionMeaning,
            String stateImpact
    ) {

        /**
         * heartbeat가 metric state를 바꾸지 않는 control-plane 정보로만 노출되게 검증한다.
         */
        public StarterConnection {
            statusSource = requireText(statusSource, "statusSource");
            if (!"starter_heartbeat".equals(statusSource)) {
                throw new IllegalArgumentException("statusSource must be starter_heartbeat");
            }
            lastHeartbeatStatus = requireText(lastHeartbeatStatus, "lastHeartbeatStatus");
            freshnessLabel = requireText(freshnessLabel, "freshnessLabel");
            connectionMeaning = requireText(connectionMeaning, "connectionMeaning");
            stateImpact = requireText(stateImpact, "stateImpact");
            if (!"does_not_change_metric_state".equals(stateImpact)
                    && !"control_plane_only".equals(stateImpact)) {
                throw new IllegalArgumentException("stateImpact must keep metric state unchanged");
            }
        }

        /**
         * heartbeat row가 없을 때의 bounded missing block을 만든다.
         */
        public static StarterConnection missing() {
            return new StarterConnection(
                    "starter_heartbeat",
                    null,
                    "missing",
                    "missing",
                    "unknown",
                    "does_not_change_metric_state");
        }
    }

    /**
     * Instance Dashboard가 노출하는 signal 묶음이다.
     */
    public record Signals(RedSignals red) {

        /**
         * RED signal block이 항상 존재하도록 검증한다.
         */
        public Signals {
            Objects.requireNonNull(red, "red must not be null");
        }
    }

    /**
     * selected instance의 요청량, 5xx 오류, 500ms 초과 지연 evidence다.
     */
    public record RedSignals(
            long requestCount,
            long errorCount,
            BigDecimal errorRate,
            Long slowCountOver500ms,
            BigDecimal slowShareOver500ms,
            boolean requestSymptomPresent
    ) {

        /**
         * count/rate가 bounded scalar 범위 안에 있는지 검증한다.
         */
        public RedSignals {
            validateCount(requestCount, "requestCount");
            validateCount(errorCount, "errorCount");
            if (errorCount > requestCount) {
                throw new IllegalArgumentException("errorCount must not exceed requestCount");
            }
            validateNullableFraction(errorRate, "errorRate");
            validateNullableCount(slowCountOver500ms, "slowCountOver500ms");
            validateNullableFraction(slowShareOver500ms, "slowShareOver500ms");
        }
    }

    /**
     * selected instance endpoint JSON을 bounded evidence item으로 해석한 block이다.
     */
    public record EndpointEvidence(
            String source,
            String scope,
            String selectionPolicy,
            String displayOrderingPolicy,
            String status,
            String reason,
            List<EndpointEvidenceItem> items
    ) {

        /**
         * raw endpoint JSON을 노출하지 않고 최대 5개 item만 담도록 검증한다.
         */
        public EndpointEvidence {
            source = requireText(source, "source");
            if (!"accepted_metric_buckets.endpoints_json".equals(source)) {
                throw new IllegalArgumentException("source must be accepted_metric_buckets.endpoints_json");
            }
            scope = requireText(scope, "scope");
            if (!"instance_recent_30_minutes".equals(scope)) {
                throw new IllegalArgumentException("scope must be instance_recent_30_minutes");
            }
            selectionPolicy = requireText(selectionPolicy, "selectionPolicy");
            displayOrderingPolicy = requireText(displayOrderingPolicy, "displayOrderingPolicy");
            status = requireText(status, "status");
            validateAllowed(status, ENDPOINT_STATUSES, "status");
            reason = trimNullable(reason);
            items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
            if (items.size() > 5) {
                throw new IllegalArgumentException("items must not exceed 5");
            }
        }

        /**
         * selected instance endpoint evidence가 없을 때의 empty block이다.
         */
        public static EndpointEvidence missing() {
            return new EndpointEvidence(
                    "accepted_metric_buckets.endpoints_json",
                    "instance_recent_30_minutes",
                    "selected_instance_metric_evidence",
                    "server_order",
                    "missing",
                    "no_endpoint_evidence_in_window",
                    List.of());
        }
    }

    /**
     * endpoint 단위 selected instance evidence item이다.
     */
    public record EndpointEvidenceItem(
            String method,
            String route,
            String endpointKey,
            String presenceOnSelectedInstance,
            long requestCount,
            long errorCount,
            BigDecimal errorRate,
            int localDisplayOrder,
            String status,
            String reason,
            String relatedApplicationEndpointEvidenceRef
    ) {

        /**
         * endpoint identity와 count/rate scalar를 검증한다.
         */
        public EndpointEvidenceItem {
            method = requireText(method, "method");
            route = requireText(route, "route");
            endpointKey = requireText(endpointKey, "endpointKey");
            if (!endpointKey.equals(method + " " + route)) {
                throw new IllegalArgumentException("endpointKey must match method + ' ' + route");
            }
            presenceOnSelectedInstance = requireText(presenceOnSelectedInstance, "presenceOnSelectedInstance");
            if (!"observed".equals(presenceOnSelectedInstance)
                    && !"not_observed".equals(presenceOnSelectedInstance)
                    && !"insufficient".equals(presenceOnSelectedInstance)) {
                throw new IllegalArgumentException("presenceOnSelectedInstance must be observed/not_observed/insufficient");
            }
            validateCount(requestCount, "requestCount");
            validateCount(errorCount, "errorCount");
            if (errorCount > requestCount) {
                throw new IllegalArgumentException("errorCount must not exceed requestCount");
            }
            validateNullableFraction(errorRate, "errorRate");
            if (localDisplayOrder < 1) {
                throw new IllegalArgumentException("localDisplayOrder must be positive");
            }
            status = requireText(status, "status");
            reason = trimNullable(reason);
            relatedApplicationEndpointEvidenceRef = trimNullable(relatedApplicationEndpointEvidenceRef);
        }
    }

    /**
     * instance-scoped resource ratio hint block이다.
     */
    public record ResourceEvidence(
            String source,
            String status,
            List<ResourceEvidenceItem> items
    ) {

        /**
         * resource evidence가 accepted bucket source의 bounded hint임을 검증한다.
         */
        public ResourceEvidence {
            source = requireText(source, "source");
            if (!"accepted_metric_buckets".equals(source)) {
                throw new IllegalArgumentException("source must be accepted_metric_buckets");
            }
            status = requireText(status, "status");
            validateAllowed(status, RESOURCE_STATUSES, "status");
            items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
        }

        /**
         * resource ratio sample이 없을 때의 bounded missing block이다.
         */
        public static ResourceEvidence missing() {
            return new ResourceEvidence("accepted_metric_buckets", "missing", List.of());
        }
    }

    /**
     * CPU/heap/DB pool 등 resource ratio hint item이다.
     */
    public record ResourceEvidenceItem(
            String resourceKey,
            String scope,
            BigDecimal usage,
            BigDecimal threshold,
            String status,
            OffsetDateTime observedAt,
            boolean requestSymptomPresent,
            String patternContribution,
            String operatorText
    ) {

        /**
         * resource threshold hint가 root cause claim으로 승격되지 않게 bounded value를 검증한다.
         */
        public ResourceEvidenceItem {
            resourceKey = requireText(resourceKey, "resourceKey");
            scope = requireText(scope, "scope");
            if (!"instance".equals(scope)) {
                throw new IllegalArgumentException("scope must be instance");
            }
            validateNullableFraction(usage, "usage");
            validateNullableFraction(threshold, "threshold");
            status = requireText(status, "status");
            validateAllowed(status, RESOURCE_ITEM_STATUSES, "status");
            patternContribution = requireText(patternContribution, "patternContribution");
            validateAllowed(patternContribution, PATTERN_CONTRIBUTIONS, "patternContribution");
            operatorText = requireText(operatorText, "operatorText");
        }
    }

    /**
     * resource pressure와 request symptom이 함께 관찰된 bounded pattern hint다.
     */
    public record PatternEvidence(
            String patternKey,
            String contribution,
            List<String> evidenceRefs
    ) {

        /**
         * pattern evidence는 원인 확정이 아니라 확인할 패턴 hint만 담는다.
         */
        public PatternEvidence {
            patternKey = requireText(patternKey, "patternKey");
            contribution = requireText(contribution, "contribution");
            evidenceRefs = List.copyOf(Objects.requireNonNull(evidenceRefs, "evidenceRefs must not be null"));
        }
    }

    /**
     * selected Application Snapshot row metadata와 window provenance를 담는다.
     */
    public record Snapshot(
            UUID snapshotId,
            String snapshotRowSource,
            OffsetDateTime generatedAt,
            OffsetDateTime currentWindowStartUtc,
            OffsetDateTime currentWindowEndUtc,
            String captureReason,
            String storedApplicationStateCode
    ) {

        /**
         * snapshot mode가 `dashboard_snapshots` row metadata만 source로 삼도록 검증한다.
         */
        public Snapshot {
            Objects.requireNonNull(snapshotId, "snapshotId must not be null");
            snapshotRowSource = requireText(snapshotRowSource, "snapshotRowSource");
            if (!"dashboard_snapshots".equals(snapshotRowSource)) {
                throw new IllegalArgumentException("snapshotRowSource must be dashboard_snapshots");
            }
            Objects.requireNonNull(generatedAt, "generatedAt must not be null");
            Objects.requireNonNull(currentWindowStartUtc, "currentWindowStartUtc must not be null");
            Objects.requireNonNull(currentWindowEndUtc, "currentWindowEndUtc must not be null");
            if (!currentWindowEndUtc.isAfter(currentWindowStartUtc)) {
                throw new IllegalArgumentException("currentWindowEndUtc must be after currentWindowStartUtc");
            }
            captureReason = trimNullable(captureReason);
            storedApplicationStateCode = trimNullable(storedApplicationStateCode);
        }
    }

    /**
     * live/snapshot mode별 source, cutoff, late metric semantics를 명시하는 block이다.
     */
    public record ReadSemantics(
            String source,
            String windowSource,
            String snapshotRowSource,
            boolean acceptedAtCutoffApplied,
            boolean includesLateAcceptedMetrics,
            boolean mayDifferFromStoredApplicationSnapshot,
            boolean applicationSnapshotRecalculated,
            boolean instanceEvidenceReconstructedFromMetrics,
            boolean markerIsStateSource
    ) {

        /**
         * snapshot mode에서 accepted_at cutoff와 stored Application Snapshot 재계산을 금지한다.
         */
        public ReadSemantics {
            source = requireText(source, "source");
            if (!"accepted_metric_buckets".equals(source)) {
                throw new IllegalArgumentException("source must be accepted_metric_buckets");
            }
            windowSource = requireText(windowSource, "windowSource");
            validateAllowed(windowSource, WINDOW_SOURCES, "windowSource");
            snapshotRowSource = trimNullable(snapshotRowSource);
            if (acceptedAtCutoffApplied) {
                throw new IllegalArgumentException("acceptedAtCutoffApplied must be false");
            }
            if (applicationSnapshotRecalculated) {
                throw new IllegalArgumentException("applicationSnapshotRecalculated must be false");
            }
            if (markerIsStateSource) {
                throw new IllegalArgumentException("markerIsStateSource must be false");
            }
            if ("selected_application_snapshot".equals(windowSource)) {
                if (!"dashboard_snapshots".equals(snapshotRowSource)) {
                    throw new IllegalArgumentException("snapshotRowSource must be dashboard_snapshots");
                }
                if (!includesLateAcceptedMetrics) {
                    throw new IllegalArgumentException("includesLateAcceptedMetrics must be true for snapshot mode");
                }
                if (!mayDifferFromStoredApplicationSnapshot) {
                    throw new IllegalArgumentException(
                            "mayDifferFromStoredApplicationSnapshot must be true for snapshot mode");
                }
                if (!instanceEvidenceReconstructedFromMetrics) {
                    throw new IllegalArgumentException(
                            "instanceEvidenceReconstructedFromMetrics must be true for snapshot mode");
                }
            } else {
                if (snapshotRowSource != null) {
                    throw new IllegalArgumentException("snapshotRowSource must be null for live mode");
                }
                if (includesLateAcceptedMetrics || mayDifferFromStoredApplicationSnapshot) {
                    throw new IllegalArgumentException("snapshot-only read semantics must be false for live mode");
                }
                if (instanceEvidenceReconstructedFromMetrics) {
                    throw new IllegalArgumentException(
                            "instanceEvidenceReconstructedFromMetrics must be false for live mode");
                }
            }
        }
    }

    /**
     * Instance Dashboard와 관련 하위 surface link를 담는다.
     */
    public record Links(
            String self,
            String applicationDashboard,
            String instanceEvidence,
            String snapshotTrend,
            String applicationSnapshotDetail
    ) {

        /**
         * 필수 navigation link와 optional snapshot detail link를 검증한다.
         */
        public Links {
            self = requireText(self, "self");
            applicationDashboard = requireText(applicationDashboard, "applicationDashboard");
            instanceEvidence = requireText(instanceEvidence, "instanceEvidence");
            snapshotTrend = requireText(snapshotTrend, "snapshotTrend");
            applicationSnapshotDetail = trimNullable(applicationSnapshotDetail);
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

    private static void validateNullableFraction(BigDecimal value, String fieldName) {
        if (value != null && (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0)) {
            throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0");
        }
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

    private static void validateAllowed(String value, Set<String> allowedValues, String fieldName) {
        if (!allowedValues.contains(value)) {
            throw new IllegalArgumentException(fieldName + " must be one of " + allowedValues);
        }
    }

    private static String stateRefSourceForMode(String mode) {
        return "snapshot".equals(mode)
                ? "selected_application_snapshot"
                : "application_dashboard_live";
    }
}
