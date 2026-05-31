package com.observation.portal.domain.instance.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Instance Detail 화면이 사용하는 bounded evidence bundle API response 모델이다.
 *
 * <p>metric data, starter heartbeat, percentile series, histogram, resource hint, triage bridge를 source별 bounded
 * block으로 분리해 UI가 lifecycle state나 p95/p99를 다시 계산하지 않게 한다.</p>
 */
public record InstanceEvidenceReadModel(
        OffsetDateTime generatedAt,
        Application application,
        Instance instance,
        MetricData metricData,
        StarterConnection starterConnection,
        StarterPercentiles starterPercentiles,
        HistogramDistribution histogramDistribution,
        ResourceHints resourceHints,
        ApplicationTriageContribution applicationTriageContribution,
        EndpointEvidence endpointEvidence,
        Links links
) {

    private static final Set<String> ENDPOINT_EVIDENCE_STATUSES = Set.of(
            "available",
            "missing",
            "insufficient",
            "suppressed",
            "unavailable");
    private static final Set<String> ENDPOINT_ITEM_STATUSES = Set.of(
            "available",
            "missing",
            "insufficient",
            "unavailable");
    private static final Set<String> ENDPOINT_PRESENCE_VALUES = Set.of(
            "observed",
            "not_observed",
            "insufficient");
    private static final Set<String> ENDPOINT_REASON_CODES = Set.of(
            "application_priority_endpoint_observed_on_selected_instance",
            "application_priority_endpoint_not_seen_on_selected_instance",
            "selected_instance_endpoint_observed",
            "endpoint_evidence_insufficient",
            "histogram_boundary_mismatch",
            "application_freshness_not_current");
    private static final Set<String> STARTER_PERCENTILE_STATUSES = Set.of(
            "available",
            "missing",
            "insufficient");

    /**
     * evidence response가 계약의 top-level block을 항상 포함하도록 필수 field를 검증한다.
     */
    public InstanceEvidenceReadModel {
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        Objects.requireNonNull(application, "application must not be null");
        Objects.requireNonNull(instance, "instance must not be null");
        Objects.requireNonNull(metricData, "metricData must not be null");
        Objects.requireNonNull(starterConnection, "starterConnection must not be null");
        Objects.requireNonNull(starterPercentiles, "starterPercentiles must not be null");
        Objects.requireNonNull(histogramDistribution, "histogramDistribution must not be null");
        Objects.requireNonNull(resourceHints, "resourceHints must not be null");
        Objects.requireNonNull(applicationTriageContribution, "applicationTriageContribution must not be null");
        Objects.requireNonNull(endpointEvidence, "endpointEvidence must not be null");
        Objects.requireNonNull(links, "links must not be null");
    }

    /**
     * 선택된 instance가 속한 application identity와 dashboard navigation link를 담는다.
     */
    public record Application(
            UUID projectId,
            UUID applicationId,
            String name,
            String environment,
            ApplicationLinks links
    ) {

        /**
         * API catalog path 정합성에 사용된 project/application UUID와 표시용 문자열 identity를 검증한다.
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
     * application dashboard로 되돌아갈 수 있는 application-scoped link block이다.
     */
    public record ApplicationLinks(String dashboard) {

        /**
         * dashboard link가 빈 문자열로 노출되지 않도록 검증한다.
         */
        public ApplicationLinks {
            dashboard = requireText(dashboard, "dashboard");
        }
    }

    /**
     * selected catalog instance의 UUID identity와 표시 이름, 관측 시각을 담는다.
     */
    public record Instance(
            UUID instanceId,
            String instanceName,
            OffsetDateTime firstSeenAt,
            OffsetDateTime lastSeenAt
    ) {

        /**
         * API path identity인 instance UUID와 catalog 표시 이름을 검증한다.
         */
        public Instance {
            Objects.requireNonNull(instanceId, "instanceId must not be null");
            instanceName = requireText(instanceName, "instanceName");
            Objects.requireNonNull(firstSeenAt, "firstSeenAt must not be null");
            Objects.requireNonNull(lastSeenAt, "lastSeenAt must not be null");
        }
    }

    /**
     * accepted bucket metric data axis의 current 15분 window와 bounded sample summary다.
     */
    public record MetricData(
            String statusSource,
            MetricWindow window,
            OffsetDateTime lastAcceptedBucketAt,
            String freshnessLabel,
            String sampleReadiness,
            long requestCount,
            long errorCount,
            BigDecimal errorRate,
            String reason
    ) {

        /**
         * metric data block이 accepted bucket source와 count/rate guard를 유지하도록 검증한다.
         */
        public MetricData {
            statusSource = requireText(statusSource, "statusSource");
            if (!"accepted_bucket".equals(statusSource)) {
                throw new IllegalArgumentException("statusSource must be accepted_bucket");
            }
            Objects.requireNonNull(window, "window must not be null");
            freshnessLabel = requireText(freshnessLabel, "freshnessLabel");
            sampleReadiness = requireText(sampleReadiness, "sampleReadiness");
            validateCount(requestCount, "requestCount");
            validateCount(errorCount, "errorCount");
            if (errorCount > requestCount) {
                throw new IllegalArgumentException("errorCount must not exceed requestCount");
            }
            validateNullableFraction(errorRate, "errorRate");
            if (requestCount == 0L && errorRate != null) {
                throw new IllegalArgumentException("errorRate must be null when requestCount is zero");
            }
            reason = trimNullable(reason);
        }

        /**
         * metric bucket aggregate를 계산할 수 없을 때의 안전한 empty block을 만든다.
         */
        public static MetricData missing(MetricWindow window) {
            return new MetricData(
                    "accepted_bucket",
                    window,
                    null,
                    "waiting_first_data",
                    "missing",
                    0L,
                    0L,
                    null,
                    "metric_data_not_loaded");
        }
    }

    /**
     * evidence API가 노출하는 current 15분 UTC bucket window다.
     */
    public record MetricWindow(
            String name,
            OffsetDateTime startUtc,
            OffsetDateTime endUtc,
            int bucketDurationSeconds
    ) {

        /**
         * current 15분 window와 30초 bucket duration 계약을 검증한다.
         */
        public MetricWindow {
            name = requireText(name, "name");
            if (!"current_15m".equals(name)) {
                throw new IllegalArgumentException("name must be current_15m");
            }
            Objects.requireNonNull(startUtc, "startUtc must not be null");
            Objects.requireNonNull(endUtc, "endUtc must not be null");
            if (!endUtc.isAfter(startUtc)) {
                throw new IllegalArgumentException("endUtc must be after startUtc");
            }
            if (bucketDurationSeconds != 30) {
                throw new IllegalArgumentException("bucketDurationSeconds must be 30");
            }
        }
    }

    /**
     * starter heartbeat control-plane axis를 metric data axis와 분리해 담는 block이다.
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
         * heartbeat source와 metric state 영향 없음 계약을 검증한다.
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
            if (!"none".equals(stateImpact)) {
                throw new IllegalArgumentException("stateImpact must be none");
            }
        }

        /**
         * heartbeat 상세 조회 결과가 없을 때의 bounded missing block을 만든다.
         */
        public static StarterConnection missing() {
            return new StarterConnection(
                    "starter_heartbeat",
                    null,
                    "missing",
                    "missing",
                    "unknown",
                    "none");
        }
    }

    /**
     * selected instance current 15분 starter percentile series 계약을 담는다.
     */
    public record StarterPercentiles(
            String source,
            String scope,
            String window,
            int bucketDurationSeconds,
            int maxPointCount,
            String displayPolicy,
            String aggregatePolicy,
            String status,
            String reason,
            List<PercentilePoint> points
    ) {

        /**
         * source-scoped series 정책과 최대 point 수를 검증한다.
         */
        public StarterPercentiles {
            source = requireText(source, "source");
            if (!"starter_canonical_percentile".equals(source)) {
                throw new IllegalArgumentException("source must be starter_canonical_percentile");
            }
            scope = requireText(scope, "scope");
            if (!"instance".equals(scope)) {
                throw new IllegalArgumentException("scope must be instance");
            }
            window = requireText(window, "window");
            if (!"current_15m".equals(window)) {
                throw new IllegalArgumentException("window must be current_15m");
            }
            if (bucketDurationSeconds != 30) {
                throw new IllegalArgumentException("bucketDurationSeconds must be 30");
            }
            if (maxPointCount != 30) {
                throw new IllegalArgumentException("maxPointCount must be 30");
            }
            displayPolicy = requireText(displayPolicy, "displayPolicy");
            if (!"source_scoped_series".equals(displayPolicy)) {
                throw new IllegalArgumentException("displayPolicy must be source_scoped_series");
            }
            aggregatePolicy = requireText(aggregatePolicy, "aggregatePolicy");
            if (!"no_average_no_max_no_merge_no_histogram_recalculation".equals(aggregatePolicy)) {
                throw new IllegalArgumentException(
                        "aggregatePolicy must be no_average_no_max_no_merge_no_histogram_recalculation");
            }
            status = requireText(status, "status");
            validateAllowed(status, STARTER_PERCENTILE_STATUSES, "status");
            reason = trimNullable(reason);
            points = List.copyOf(Objects.requireNonNull(points, "points must not be null"));
            if (points.size() > maxPointCount) {
                throw new IllegalArgumentException("points must not exceed maxPointCount");
            }
        }

        /**
         * current window에 percentile series가 없을 때의 empty block을 만든다.
         */
        public static StarterPercentiles missing() {
            return new StarterPercentiles(
                    "starter_canonical_percentile",
                    "instance",
                    "current_15m",
                    30,
                    30,
                    "source_scoped_series",
                    "no_average_no_max_no_merge_no_histogram_recalculation",
                    "missing",
                    "percentile_series_not_loaded",
                    List.of());
        }
    }

    /**
     * starter가 보낸 단일 30초 bucket percentile point를 그대로 담는 item이다.
     */
    public record PercentilePoint(
            OffsetDateTime bucketStartUtc,
            OffsetDateTime bucketEndUtc,
            long requestCount,
            long p95Ms,
            long p99Ms
    ) {

        /**
         * p95/p99를 새로 계산하지 않고 persisted point의 유효 범위만 검증한다.
         */
        public PercentilePoint {
            Objects.requireNonNull(bucketStartUtc, "bucketStartUtc must not be null");
            Objects.requireNonNull(bucketEndUtc, "bucketEndUtc must not be null");
            if (!bucketEndUtc.isAfter(bucketStartUtc)) {
                throw new IllegalArgumentException("bucketEndUtc must be after bucketStartUtc");
            }
            if (requestCount <= 0L) {
                throw new IllegalArgumentException("requestCount must be positive");
            }
            validateCount(p95Ms, "p95Ms");
            if (p99Ms < p95Ms) {
                throw new IllegalArgumentException("p99Ms must be greater than or equal to p95Ms");
            }
        }
    }

    /**
     * selected instance duration bucket distribution evidence block이다.
     */
    public record HistogramDistribution(
            String source,
            String scope,
            String status,
            String reason,
            long totalCount,
            List<HistogramBucket> buckets
    ) {

        /**
         * histogram distribution이 p95/p99 scalar 없이 bounded bucket만 갖도록 검증한다.
         */
        public HistogramDistribution {
            source = requireText(source, "source");
            if (!"histogram_bucket_distribution".equals(source)) {
                throw new IllegalArgumentException("source must be histogram_bucket_distribution");
            }
            scope = requireText(scope, "scope");
            status = requireText(status, "status");
            reason = trimNullable(reason);
            validateCount(totalCount, "totalCount");
            buckets = List.copyOf(Objects.requireNonNull(buckets, "buckets must not be null"));
        }

        /**
         * current window에 histogram evidence가 없을 때의 empty block을 만든다.
         */
        public static HistogramDistribution missing() {
            return new HistogramDistribution(
                    "histogram_bucket_distribution",
                    "selected_instance_current_15m",
                    "missing",
                    "histogram_distribution_not_loaded",
                    0L,
                    List.of());
        }
    }

    /**
     * cumulative histogram boundary와 해당 boundary 이하 count를 담는다.
     */
    public record HistogramBucket(long leMs, long count) {

        /**
         * histogram boundary/count가 음수가 아닌 bounded 값인지 검증한다.
         */
        public HistogramBucket {
            validateCount(leMs, "leMs");
            validateCount(count, "count");
        }
    }

    /**
     * selected instance latest accepted bucket sample 기반 runtime ratio hint block이다.
     */
    public record ResourceHints(
            String source,
            String status,
            String reason,
            OffsetDateTime bucketEndUtc,
            BigDecimal cpuUsageRatio,
            BigDecimal heapUsedRatio,
            BigDecimal datasourcePoolUsageRatio
    ) {

        /**
         * ratio hint가 nullable이어도 값이 있으면 0~1 범위 안에 있도록 검증한다.
         */
        public ResourceHints {
            source = requireText(source, "source");
            if (!"accepted_bucket_latest_sample".equals(source)) {
                throw new IllegalArgumentException("source must be accepted_bucket_latest_sample");
            }
            status = requireText(status, "status");
            reason = trimNullable(reason);
            validateNullableFraction(cpuUsageRatio, "cpuUsageRatio");
            validateNullableFraction(heapUsedRatio, "heapUsedRatio");
            validateNullableFraction(datasourcePoolUsageRatio, "datasourcePoolUsageRatio");
        }

        /**
         * current window에 latest runtime sample이 없을 때의 empty block을 만든다.
         */
        public static ResourceHints missing() {
            return new ResourceHints(
                    "accepted_bucket_latest_sample",
                    "missing",
                    "resource_hints_not_loaded",
                    null,
                    null,
                    null,
                    null);
        }
    }

    /**
     * application-level triage card와 selected instance evidence의 연결 여부를 담는 bridge block이다.
     */
    public record ApplicationTriageContribution(
            String status,
            boolean contributed,
            List<String> relatedRuleIds,
            String reason
    ) {

        /**
         * existing triage rule id만 담는 bounded list를 방어적으로 복사한다.
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

        /**
         * application triage card가 없거나 bridge할 evidence가 없을 때의 empty block을 만든다.
         */
        public static ApplicationTriageContribution missing() {
            return new ApplicationTriageContribution(
                    "missing",
                    false,
                    List.of(),
                    "application_triage_not_loaded");
        }
    }

    /**
     * selected instance endpoint evidence subset과 selection/display 정책을 담는다.
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
         * endpoint evidence item 수와 policy/source 값을 검증한다.
         */
        public EndpointEvidence {
            source = requireText(source, "source");
            if (!"accepted_metric_buckets.endpoints_json".equals(source)) {
                throw new IllegalArgumentException("source must be accepted_metric_buckets.endpoints_json");
            }
            scope = requireText(scope, "scope");
            if (!"instance_current_15m".equals(scope)) {
                throw new IllegalArgumentException("scope must be instance_current_15m");
            }
            selectionPolicy = requireText(selectionPolicy, "selectionPolicy");
            if (!"application_priority_presence_then_triage_then_instance_request_count".equals(selectionPolicy)) {
                throw new IllegalArgumentException(
                        "selectionPolicy must be application_priority_presence_then_triage_then_instance_request_count");
            }
            displayOrderingPolicy = requireText(displayOrderingPolicy, "displayOrderingPolicy");
            if (!"selected_instance_signal_then_application_priority_reference".equals(displayOrderingPolicy)) {
                throw new IllegalArgumentException(
                        "displayOrderingPolicy must be selected_instance_signal_then_application_priority_reference");
            }
            status = requireText(status, "status");
            validateAllowed(status, ENDPOINT_EVIDENCE_STATUSES, "status");
            reason = trimNullable(reason);
            validateNullableAllowed(reason, ENDPOINT_REASON_CODES, "reason");
            items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
            if (items.size() > 5) {
                throw new IllegalArgumentException("items must not exceed 5");
            }
        }

        /**
         * endpoint selection/merge 계산을 후속 phase로 남길 때의 empty block을 만든다.
         */
        public static EndpointEvidence missing() {
            return new EndpointEvidence(
                    "accepted_metric_buckets.endpoints_json",
                    "instance_current_15m",
                    "application_priority_presence_then_triage_then_instance_request_count",
                    "selected_instance_signal_then_application_priority_reference",
                    "missing",
                    null,
                    List.of());
        }
    }

    /**
     * endpoint evidence 목록의 bounded item shape다.
     *
     * <p>`relatedApplicationPriorityRank`는 Story 5.5 endpointPriority 참조 값이고, `localDisplayOrder`는 표시 순서일 뿐
     * 새 priority 판단이 아니다.</p>
     */
    public record EndpointEvidenceItem(
            String method,
            String route,
            String endpointKey,
            String presenceOnSelectedInstance,
            long instanceRequestCount,
            long instanceErrorCount,
            BigDecimal instanceErrorRate,
            Long applicationEndpointRequestCount,
            Long applicationEndpointErrorCount,
            BigDecimal applicationEndpointErrorRate,
            BigDecimal instanceRequestShare,
            BigDecimal instanceErrorShare,
            List<HistogramBucket> durationBuckets,
            String bucketDistributionSource,
            Integer relatedApplicationPriorityRank,
            int localDisplayOrder,
            List<String> relatedRuleIds,
            String status,
            String reason
    ) {

        /**
         * raw endpoint JSON 없이 selected instance와 application aggregate의 bounded scalar만 검증한다.
         */
        public EndpointEvidenceItem {
            method = requireText(method, "method");
            route = requireText(route, "route");
            endpointKey = requireText(endpointKey, "endpointKey");
            if (!endpointKey.equals(method + " " + route)) {
                throw new IllegalArgumentException("endpointKey must match method + ' ' + route");
            }
            presenceOnSelectedInstance = requireText(presenceOnSelectedInstance, "presenceOnSelectedInstance");
            validateAllowed(
                    presenceOnSelectedInstance,
                    ENDPOINT_PRESENCE_VALUES,
                    "presenceOnSelectedInstance");
            validateCount(instanceRequestCount, "instanceRequestCount");
            validateCount(instanceErrorCount, "instanceErrorCount");
            if (instanceErrorCount > instanceRequestCount) {
                throw new IllegalArgumentException("instanceErrorCount must not exceed instanceRequestCount");
            }
            validateNullableFraction(instanceErrorRate, "instanceErrorRate");
            validateNullableCount(applicationEndpointRequestCount, "applicationEndpointRequestCount");
            validateNullableCount(applicationEndpointErrorCount, "applicationEndpointErrorCount");
            validateNullableFraction(applicationEndpointErrorRate, "applicationEndpointErrorRate");
            validateNullableFraction(instanceRequestShare, "instanceRequestShare");
            validateNullableFraction(instanceErrorShare, "instanceErrorShare");
            durationBuckets = List.copyOf(Objects.requireNonNull(durationBuckets, "durationBuckets must not be null"));
            bucketDistributionSource = requireText(bucketDistributionSource, "bucketDistributionSource");
            if (!"histogram_bucket_distribution".equals(bucketDistributionSource)) {
                throw new IllegalArgumentException("bucketDistributionSource must be histogram_bucket_distribution");
            }
            if (relatedApplicationPriorityRank != null && relatedApplicationPriorityRank < 1) {
                throw new IllegalArgumentException(
                        "relatedApplicationPriorityRank must be greater than or equal to 1");
            }
            if (localDisplayOrder < 1) {
                throw new IllegalArgumentException("localDisplayOrder must be greater than or equal to 1");
            }
            relatedRuleIds = List.copyOf(Objects.requireNonNull(relatedRuleIds, "relatedRuleIds must not be null"));
            if (relatedRuleIds.stream().anyMatch(ruleId -> ruleId == null || ruleId.isBlank())) {
                throw new IllegalArgumentException("relatedRuleIds must contain non-blank values");
            }
            status = requireText(status, "status");
            validateAllowed(status, ENDPOINT_ITEM_STATUSES, "status");
            reason = trimNullable(reason);
            validateNullableAllowed(reason, ENDPOINT_REASON_CODES, "reason");
        }
    }

    /**
     * evidence API의 self/dashboard link와 후속 snapshot trend link 후보를 담는다.
     */
    public record Links(String self, String dashboard, String snapshotTrend) {

        /**
         * self/dashboard는 실제 API path로 고정하고 snapshotTrend는 후속 story 전까지 null을 허용한다.
         */
        public Links {
            self = requireText(self, "self");
            dashboard = requireText(dashboard, "dashboard");
            snapshotTrend = trimNullable(snapshotTrend);
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

    private static void validateNullableAllowed(String value, Set<String> allowedValues, String fieldName) {
        if (value != null) {
            validateAllowed(value, allowedValues, fieldName);
        }
    }
}
