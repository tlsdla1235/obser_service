package com.observation.portal.domain.instance.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Instance Snapshot Trend API가 반환하는 stored dashboard snapshot 기반 read model이다.
 *
 * <p>trend source는 `dashboard_snapshots.read_model_json.instanceSummary.items[]`로 고정되며, 각 point는 저장 당시
 * bounded instance summary와 snapshot row metadata만 복사한다. 이 모델은 current state, health score, endpoint priority,
 * p95/p99, recovery marker를 새로 계산하거나 표현하지 않는다.</p>
 */
public record InstanceSnapshotTrendReadModel(
        OffsetDateTime generatedAt,
        Application application,
        Instance instance,
        String source,
        Horizon horizon,
        List<Point> points
) {

    public static final String SOURCE = "dashboard_snapshots.read_model_json.instanceSummary.items";
    public static final String DEFAULT_SINCE = "7d";
    public static final String MAX_SINCE = "14d";
    public static final int DEFAULT_LIMIT = 168;
    public static final int MAX_LIMIT = 336;
    public static final String ORDER = "capturedAt_asc";

    /**
     * top-level response가 source, horizon, bounded point list를 항상 포함하도록 검증한다.
     */
    public InstanceSnapshotTrendReadModel {
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        Objects.requireNonNull(application, "application must not be null");
        Objects.requireNonNull(instance, "instance must not be null");
        source = requireText(source, "source");
        if (!SOURCE.equals(source)) {
            throw new IllegalArgumentException("source must be " + SOURCE);
        }
        Objects.requireNonNull(horizon, "horizon must not be null");
        points = List.copyOf(Objects.requireNonNull(points, "points must not be null"));
        if (points.size() > MAX_LIMIT) {
            throw new IllegalArgumentException("points must not exceed maxLimit");
        }
        if (points.size() > horizon.limit()) {
            throw new IllegalArgumentException("points must not exceed horizon limit");
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
         * project/application UUID path identity와 표시용 application metadata를 검증한다.
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
     * Application Dashboard API로 되돌아가는 link block이다.
     */
    public record ApplicationLinks(String dashboard) {

        /**
         * dashboard link가 실제 API path로 채워졌는지 검증한다.
         */
        public ApplicationLinks {
            dashboard = requireText(dashboard, "dashboard");
        }
    }

    /**
     * selected catalog instance의 UUID identity, 표시 이름, catalog seen 시각과 evidence link를 담는다.
     */
    public record Instance(
            UUID instanceId,
            String instanceName,
            OffsetDateTime firstSeenAt,
            OffsetDateTime lastSeenAt,
            InstanceLinks links
    ) {

        /**
         * matching key인 instance UUID와 표시 metadata를 검증한다.
         */
        public Instance {
            Objects.requireNonNull(instanceId, "instanceId must not be null");
            instanceName = requireText(instanceName, "instanceName");
            Objects.requireNonNull(links, "links must not be null");
        }
    }

    /**
     * Instance Evidence API로 좁혀 들어가는 link block이다.
     */
    public record InstanceLinks(String evidence) {

        /**
         * evidence link가 실제 UUID path 기반 API path로 채워졌는지 검증한다.
         */
        public InstanceLinks {
            evidence = requireText(evidence, "evidence");
        }
    }

    /**
     * trend 조회에 적용된 effective horizon, 요청 token, limit clamp, 정렬 정책을 담는다.
     */
    public record Horizon(
            OffsetDateTime since,
            OffsetDateTime until,
            String requestedSince,
            String defaultSince,
            String maxSince,
            int limit,
            int maxLimit,
            String order
    ) {

        /**
         * `since`/`limit`가 Story 5.7의 bounded query 계약 안에 있는지 검증한다.
         */
        public Horizon {
            Objects.requireNonNull(since, "since must not be null");
            Objects.requireNonNull(until, "until must not be null");
            if (!until.isAfter(since)) {
                throw new IllegalArgumentException("until must be after since");
            }
            requestedSince = requireText(requestedSince, "requestedSince");
            defaultSince = requireText(defaultSince, "defaultSince");
            if (!DEFAULT_SINCE.equals(defaultSince)) {
                throw new IllegalArgumentException("defaultSince must be " + DEFAULT_SINCE);
            }
            maxSince = requireText(maxSince, "maxSince");
            if (!MAX_SINCE.equals(maxSince)) {
                throw new IllegalArgumentException("maxSince must be " + MAX_SINCE);
            }
            if (limit <= 0 || limit > MAX_LIMIT) {
                throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
            }
            if (maxLimit != MAX_LIMIT) {
                throw new IllegalArgumentException("maxLimit must be " + MAX_LIMIT);
            }
            order = requireText(order, "order");
            if (!ORDER.equals(order)) {
                throw new IllegalArgumentException("order must be " + ORDER);
            }
        }
    }

    /**
     * 하나의 dashboard snapshot row와 target instance stored summary item을 결합한 trend point다.
     *
     * <p>`storedApplicationStateCode`는 application-level stored state copy일 뿐 instance lifecycle state나 health score가
     * 아니며, `captureReason`은 trimming이나 blank-to-null 변환 없이 opaque metadata로만 복사된다.</p>
     */
    public record Point(
            UUID snapshotId,
            OffsetDateTime capturedAt,
            OffsetDateTime currentWindowEndUtc,
            String storedApplicationStateCode,
            String captureReason,
            String instanceName,
            String observationStatus,
            MetricData metricData,
            StarterConnection starterConnection,
            StarterPercentilePoint starterPercentilePoint,
            ResourceHints resourceHints,
            ApplicationTriageContribution applicationTriageContribution,
            List<EndpointEvidenceRef> endpointEvidenceRefs
    ) {

        /**
         * row metadata와 stored instance summary block을 bounded public point로 검증한다.
         *
         * <p>`captureReason`은 ordering/filtering/marker 의미로 쓰지 않는 저장 문자열이라 null 여부만 보존한다.</p>
         */
        public Point {
            Objects.requireNonNull(snapshotId, "snapshotId must not be null");
            Objects.requireNonNull(capturedAt, "capturedAt must not be null");
            Objects.requireNonNull(currentWindowEndUtc, "currentWindowEndUtc must not be null");
            storedApplicationStateCode = requireText(storedApplicationStateCode, "storedApplicationStateCode");
            instanceName = requireText(instanceName, "instanceName");
            observationStatus = requireText(observationStatus, "observationStatus");
            Objects.requireNonNull(metricData, "metricData must not be null");
            Objects.requireNonNull(starterConnection, "starterConnection must not be null");
            Objects.requireNonNull(applicationTriageContribution, "applicationTriageContribution must not be null");
            endpointEvidenceRefs = List.copyOf(Objects.requireNonNull(
                    endpointEvidenceRefs,
                    "endpointEvidenceRefs must not be null"));
            if (endpointEvidenceRefs.size() > 10) {
                throw new IllegalArgumentException("endpointEvidenceRefs must not exceed 10");
            }
        }
    }

    /**
     * accepted bucket axis의 저장된 freshness summary block이다.
     */
    public record MetricData(
            String statusSource,
            OffsetDateTime lastAcceptedBucketAt,
            String freshnessLabel
    ) {

        /**
         * metricData가 accepted bucket source 의미를 유지하도록 검증한다.
         */
        public MetricData {
            statusSource = requireText(statusSource, "statusSource");
            if (!"accepted_bucket".equals(statusSource)) {
                throw new IllegalArgumentException("statusSource must be accepted_bucket");
            }
            freshnessLabel = requireText(freshnessLabel, "freshnessLabel");
        }
    }

    /**
     * starter heartbeat axis의 저장된 connection summary block이다.
     */
    public record StarterConnection(
            String statusSource,
            OffsetDateTime lastHeartbeatAt,
            String lastHeartbeatStatus,
            String connectionMeaning,
            String stateImpact
    ) {

        /**
         * heartbeat source와 `stateImpact=none` 의미가 유지되는지 검증한다.
         */
        public StarterConnection {
            statusSource = requireText(statusSource, "statusSource");
            if (!"starter_heartbeat".equals(statusSource)) {
                throw new IllegalArgumentException("statusSource must be starter_heartbeat");
            }
            lastHeartbeatStatus = requireText(lastHeartbeatStatus, "lastHeartbeatStatus");
            connectionMeaning = requireText(connectionMeaning, "connectionMeaning");
            stateImpact = requireText(stateImpact, "stateImpact");
            if (!"none".equals(stateImpact)) {
                throw new IllegalArgumentException("stateImpact must be none");
            }
        }
    }

    /**
     * snapshot에 저장된 단일 starter canonical percentile point다.
     *
     * <p>series, average, max, merge, histogram-derived percentile이 아니라 저장된 latest point 하나만 표현한다.</p>
     */
    public record StarterPercentilePoint(
            String source,
            String scope,
            OffsetDateTime bucketStartUtc,
            OffsetDateTime bucketEndUtc,
            long requestCount,
            long p95Ms,
            long p99Ms
    ) {

        /**
         * stored percentile point의 source/scope와 count/latency 범위를 검증한다.
         */
        public StarterPercentilePoint {
            source = requireText(source, "source");
            if (!"starter_canonical_percentile".equals(source)) {
                throw new IllegalArgumentException("source must be starter_canonical_percentile");
            }
            scope = requireText(scope, "scope");
            if (!"instance_bucket".equals(scope)) {
                throw new IllegalArgumentException("scope must be instance_bucket");
            }
            Objects.requireNonNull(bucketStartUtc, "bucketStartUtc must not be null");
            Objects.requireNonNull(bucketEndUtc, "bucketEndUtc must not be null");
            if (!bucketEndUtc.isAfter(bucketStartUtc)) {
                throw new IllegalArgumentException("bucketEndUtc must be after bucketStartUtc");
            }
            validateCount(requestCount, "requestCount");
            validateCount(p95Ms, "p95Ms");
            if (p99Ms < p95Ms) {
                throw new IllegalArgumentException("p99Ms must be greater than or equal to p95Ms");
            }
        }
    }

    /**
     * latest accepted bucket sample에서 저장된 runtime hint block이다.
     *
     * <p>resource hint는 state, score, root cause 입력이 아니라 저장된 표시 metadata로만 사용한다.</p>
     */
    public record ResourceHints(
            String source,
            String status,
            OffsetDateTime bucketEndUtc,
            BigDecimal cpuUsageRatio,
            BigDecimal heapUsedRatio,
            BigDecimal datasourcePoolUsageRatio
    ) {

        /**
         * runtime ratio 값이 있으면 0~1 범위 안에 있도록 검증한다.
         */
        public ResourceHints {
            source = requireText(source, "source");
            if (!"accepted_bucket_latest_sample".equals(source)) {
                throw new IllegalArgumentException("source must be accepted_bucket_latest_sample");
            }
            status = requireText(status, "status");
            validateNullableFraction(cpuUsageRatio, "cpuUsageRatio");
            validateNullableFraction(heapUsedRatio, "heapUsedRatio");
            validateNullableFraction(datasourcePoolUsageRatio, "datasourcePoolUsageRatio");
        }
    }

    /**
     * selected instance가 저장 당시 application triage evidence에 기여했는지 나타내는 bounded bridge block이다.
     */
    public record ApplicationTriageContribution(
            String status,
            boolean contributed,
            List<String> relatedRuleIds,
            String reason
    ) {

        /**
         * 저장된 related rule id만 방어적으로 복사하고 새 rule/confidence/action을 만들지 않도록 검증한다.
         */
        public ApplicationTriageContribution {
            status = requireText(status, "status");
            relatedRuleIds = List.copyOf(Objects.requireNonNull(relatedRuleIds, "relatedRuleIds must not be null"));
            if (relatedRuleIds.stream().anyMatch(ruleId -> ruleId == null || ruleId.isBlank())) {
                throw new IllegalArgumentException("relatedRuleIds must contain non-blank values");
            }
            if (!contributed && !relatedRuleIds.isEmpty()) {
                throw new IllegalArgumentException("relatedRuleIds must be empty when contributed is false");
            }
            reason = trimNullable(reason);
        }
    }

    /**
     * snapshot detail의 bounded endpoint evidence를 가리키는 reference-only item이다.
     *
     * <p>request/error body, duration buckets, confidence/score/recommended action, endpoint p95/p99, raw endpoint JSON을
     * 포함하지 않는다.</p>
     */
    public record EndpointEvidenceRef(
            String endpointKey,
            String method,
            String route,
            Integer relatedApplicationPriorityRank,
            List<String> relatedRuleIds,
            String snapshotDetailAnchor
    ) {

        /**
         * endpoint reference가 허용된 식별/참조 field만 갖고 rank/list 범위를 지키는지 검증한다.
         */
        public EndpointEvidenceRef {
            endpointKey = requireText(endpointKey, "endpointKey");
            method = trimNullable(method);
            route = trimNullable(route);
            if (relatedApplicationPriorityRank != null && relatedApplicationPriorityRank < 1) {
                throw new IllegalArgumentException(
                        "relatedApplicationPriorityRank must be greater than or equal to 1");
            }
            relatedRuleIds = List.copyOf(Objects.requireNonNull(relatedRuleIds, "relatedRuleIds must not be null"));
            if (relatedRuleIds.stream().anyMatch(ruleId -> ruleId == null || ruleId.isBlank())) {
                throw new IllegalArgumentException("relatedRuleIds must contain non-blank values");
            }
            snapshotDetailAnchor = trimNullable(snapshotDetailAnchor);
        }
    }

    private static void validateCount(long value, String fieldName) {
        if (value < 0L) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
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
}
