package com.observation.portal.domain.instance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.portal.common.time.AcceptedBucketFreshness;
import com.observation.portal.common.time.AcceptedBucketFreshnessEvaluator;
import com.observation.portal.common.time.AcceptedBucketFreshnessStatus;
import com.observation.portal.common.time.DashboardTimeWindow;
import com.observation.portal.common.time.TimeBucketWindowCalculator;
import com.observation.portal.common.time.UtcTimeInterval;
import com.observation.portal.domain.bucket.model.EndpointEvidenceRow;
import com.observation.portal.domain.bucket.model.HistogramBucketEvidenceRow;
import com.observation.portal.domain.bucket.model.LocalPercentileEvidenceRow;
import com.observation.portal.domain.bucket.model.RecentBucketEvidenceRow;
import com.observation.portal.domain.bucket.model.RuntimeRatioEvidenceRow;
import com.observation.portal.domain.bucket.model.WindowBucketAggregate;
import com.observation.portal.domain.bucket.repository.MetricBucketRepository;
import com.observation.portal.domain.catalog.entity.ApplicationEntity;
import com.observation.portal.domain.catalog.entity.ApplicationInstanceEntity;
import com.observation.portal.domain.catalog.repository.ApplicationInstanceRepository;
import com.observation.portal.domain.catalog.repository.ApplicationRepository;
import com.observation.portal.domain.dashboard.model.ApplicationDashboardReadModel;
import com.observation.portal.domain.dashboard.service.EndpointEvidenceAggregationService;
import com.observation.portal.domain.dashboard.service.EndpointEvidenceAggregationService.EndpointAggregate;
import com.observation.portal.domain.dashboard.service.EndpointEvidenceAggregationService.WindowEndpointEvidence;
import com.observation.portal.domain.dashboard.service.EndpointPriorityService;
import com.observation.portal.domain.dashboard.service.TriageSummaryService;
import com.observation.portal.domain.dashboard.service.TriageSummaryService.TriageSummary;
import com.observation.portal.domain.dashboard.service.TriageSummaryService.TriageSummaryInput;
import com.observation.portal.domain.ingest.model.StarterHeartbeatTelemetryRecord;
import com.observation.portal.domain.ingest.repository.StarterHeartbeatTelemetryRepository;
import com.observation.portal.domain.instance.model.InstanceEvidenceReadModel;
import com.observation.portal.domain.state.model.DegradedHysteresisInput;
import com.observation.portal.domain.state.model.MetricLifecycleInput;
import com.observation.portal.domain.state.model.MetricSampleReadiness;
import com.observation.portal.domain.state.model.MetricTrafficActivity;
import com.observation.portal.domain.state.model.StarterConnectionFreshness;
import com.observation.portal.domain.state.model.StarterConnectionInput;
import com.observation.portal.domain.state.model.StarterConnectionSummary;
import com.observation.portal.domain.state.model.StarterHeartbeatStatus;
import com.observation.portal.domain.state.service.LifecycleStateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Instance Evidence API의 catalog path 정합성 검증과 current instance evidence read model 조립을 담당한다.
 *
 * <p>selected `application_instances.id` scope에서 accepted bucket metric axis와 starter heartbeat axis를 분리해 읽고,
 * endpoint detail selection이나 snapshot/history projection은 후속 phase에 남긴다.</p>
 */
@Service
public class InstanceEvidenceReadModelService {

    private static final Duration STARTER_HEARTBEAT_RECENT_WINDOW = Duration.ofSeconds(90);
    private static final long MINIMUM_ACTIVE_SAMPLE_REQUEST_COUNT = 30L;
    private static final int MAX_PERCENTILE_POINTS = 30;
    private static final String STARTER_LOCAL_SOURCE = "starter_local";
    private static final String INSTANCE_BUCKET_SCOPE = "instance_bucket";
    private static final String HISTOGRAM_BOUNDARY_MISMATCH_REASON = "histogram_boundary_mismatch";

    private final ApplicationRepository applicationRepository;
    private final ApplicationInstanceRepository applicationInstanceRepository;
    private final MetricBucketRepository metricBucketRepository;
    private final StarterHeartbeatTelemetryRepository heartbeatTelemetryRepository;
    private final AcceptedBucketFreshnessEvaluator freshnessEvaluator;
    private final TimeBucketWindowCalculator timeBucketWindowCalculator;
    private final LifecycleStateService lifecycleStateService;
    private final TriageSummaryService triageSummaryService;
    private final EndpointPriorityService endpointPriorityService;
    private final EndpointEvidenceAggregationService endpointEvidenceAggregationService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * catalog path 정합성 lookup repository와 current 15분 evidence 계산 component를 주입한다.
     */
    public InstanceEvidenceReadModelService(
            ApplicationRepository applicationRepository,
            ApplicationInstanceRepository applicationInstanceRepository,
            MetricBucketRepository metricBucketRepository,
            StarterHeartbeatTelemetryRepository heartbeatTelemetryRepository,
            AcceptedBucketFreshnessEvaluator freshnessEvaluator,
            TimeBucketWindowCalculator timeBucketWindowCalculator,
            LifecycleStateService lifecycleStateService,
            TriageSummaryService triageSummaryService,
            EndpointPriorityService endpointPriorityService,
            EndpointEvidenceAggregationService endpointEvidenceAggregationService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.applicationRepository = Objects.requireNonNull(
                applicationRepository,
                "applicationRepository must not be null");
        this.applicationInstanceRepository = Objects.requireNonNull(
                applicationInstanceRepository,
                "applicationInstanceRepository must not be null");
        this.metricBucketRepository = Objects.requireNonNull(
                metricBucketRepository,
                "metricBucketRepository must not be null");
        this.heartbeatTelemetryRepository = Objects.requireNonNull(
                heartbeatTelemetryRepository,
                "heartbeatTelemetryRepository must not be null");
        this.freshnessEvaluator = Objects.requireNonNull(freshnessEvaluator, "freshnessEvaluator must not be null");
        this.timeBucketWindowCalculator = Objects.requireNonNull(
                timeBucketWindowCalculator,
                "timeBucketWindowCalculator must not be null");
        this.lifecycleStateService = Objects.requireNonNull(
                lifecycleStateService,
                "lifecycleStateService must not be null");
        this.triageSummaryService = Objects.requireNonNull(
                triageSummaryService,
                "triageSummaryService must not be null");
        this.endpointPriorityService = Objects.requireNonNull(
                endpointPriorityService,
                "endpointPriorityService must not be null");
        this.endpointEvidenceAggregationService = Objects.requireNonNull(
                endpointEvidenceAggregationService,
                "endpointEvidenceAggregationService must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null").withZone(ZoneOffset.UTC);
    }

    /**
     * project/application/instance UUID catalog path 정합성이 모두 맞는 경우에만 instance evidence read model을 반환한다.
     *
     * <p>project 없음, application 없음, project/application mismatch, instance 없음, instance/application mismatch는 모두
     * empty로 수렴해 controller가 404로 매핑한다.</p>
     */
    @Transactional(readOnly = true)
    public Optional<InstanceEvidenceReadModel> getEvidence(
            UUID projectId,
            UUID applicationId,
            UUID instanceId) {
        UUID requiredProjectId = Objects.requireNonNull(projectId, "projectId must not be null");
        UUID requiredApplicationId = Objects.requireNonNull(applicationId, "applicationId must not be null");
        UUID requiredInstanceId = Objects.requireNonNull(instanceId, "instanceId must not be null");

        Optional<ApplicationEntity> application = applicationRepository.findByIdAndProjectId(
                requiredApplicationId,
                requiredProjectId);
        if (application.isEmpty()) {
            return Optional.empty();
        }

        Optional<ApplicationInstanceEntity> instance = applicationInstanceRepository.findByIdAndApplicationId(
                requiredInstanceId,
                requiredApplicationId);
        if (instance.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(buildReadModel(application.orElseThrow(), instance.orElseThrow()));
    }

    private InstanceEvidenceReadModel buildReadModel(
            ApplicationEntity application,
            ApplicationInstanceEntity instance) {
        Instant queryAt = clock.instant();
        Instant evaluationAt = timeBucketWindowCalculator.bucketContaining(queryAt).startUtc();
        DashboardTimeWindow dashboardWindow = timeBucketWindowCalculator.dashboardWindowEndingAt(evaluationAt);
        UtcTimeInterval currentWindow = dashboardWindow.current();

        Optional<OffsetDateTime> latestInstanceBucketEndUtc =
                metricBucketRepository.findLatestBucketEndUtcByApplicationInstanceIdAtOrBefore(
                        instance.id(),
                        evaluationAt);
        WindowBucketAggregate instanceAggregate = metricBucketRepository.findWindowAggregateByApplicationInstanceId(
                instance.id(),
                currentWindow.startUtc(),
                currentWindow.endUtc());
        AcceptedBucketFreshness instanceFreshness = freshnessEvaluator.evaluateAt(
                evaluationAt,
                latestInstanceBucketEndUtc.map(OffsetDateTime::toInstant).orElse(null));

        List<LocalPercentileEvidenceRow> instancePercentileRows =
                metricBucketRepository.findLocalPercentileEvidenceRowsByApplicationInstanceId(
                        instance.id(),
                        currentWindow.startUtc(),
                        currentWindow.endUtc());
        List<HistogramBucketEvidenceRow> instanceHistogramRows =
                metricBucketRepository.findSummaryDurationBucketEvidenceRowsByApplicationInstanceId(
                        instance.id(),
                        currentWindow.startUtc(),
                        currentWindow.endUtc());
        Optional<RuntimeRatioEvidenceRow> instanceRuntimeRatio =
                metricBucketRepository.findLatestRuntimeRatioEvidenceRowByApplicationInstanceId(
                        instance.id(),
                        currentWindow.startUtc(),
                        currentWindow.endUtc());
        Optional<StarterHeartbeatTelemetryRecord> heartbeat =
                heartbeatTelemetryRepository.findByIdentity(
                        application.projectId(),
                        application.name(),
                        application.environment(),
                        instance.instanceName());

        InstanceEvidenceReadModel.MetricWindow metricWindow = metricWindow(currentWindow);
        InstanceEvidenceReadModel.HistogramDistribution histogramDistribution =
                histogramDistribution(instanceHistogramRows);
        InstanceEvidenceReadModel.ResourceHints resourceHints = resourceHints(instanceRuntimeRatio);
        ApplicationEvidenceContext applicationContext = applicationEvidenceContext(
                application,
                dashboardWindow,
                evaluationAt);
        String dashboardLink = dashboardLink(application.projectId(), application.id());
        String selfLink = evidenceLink(application.projectId(), application.id(), instance.id());
        String snapshotTrendLink = snapshotTrendLink(application.projectId(), application.id(), instance.id());

        return new InstanceEvidenceReadModel(
                toUtcOffsetDateTime(queryAt),
                new InstanceEvidenceReadModel.Application(
                        application.projectId(),
                        application.id(),
                        application.name(),
                        application.environment(),
                        new InstanceEvidenceReadModel.ApplicationLinks(dashboardLink)),
                new InstanceEvidenceReadModel.Instance(
                        instance.id(),
                        instance.instanceName(),
                        instance.firstSeenAt(),
                        instance.lastSeenAt()),
                metricData(metricWindow, latestInstanceBucketEndUtc, instanceFreshness, instanceAggregate),
                starterConnection(heartbeat, queryAt, instanceFreshness, instanceAggregate),
                starterPercentiles(instancePercentileRows),
                histogramDistribution,
                resourceHints,
                applicationTriageContribution(
                        applicationContext,
                        instanceFreshness,
                        instanceAggregate,
                        histogramDistribution,
                        resourceHints),
                endpointEvidence(applicationContext, instance.id(), currentWindow),
                new InstanceEvidenceReadModel.Links(
                        selfLink,
                        dashboardLink,
                        snapshotTrendLink));
    }

    private InstanceEvidenceReadModel.MetricData metricData(
            InstanceEvidenceReadModel.MetricWindow metricWindow,
            Optional<OffsetDateTime> latestInstanceBucketEndUtc,
            AcceptedBucketFreshness freshness,
            WindowBucketAggregate aggregate) {
        SampleReadiness sampleReadiness = sampleReadiness(freshness.status(), aggregate);
        return new InstanceEvidenceReadModel.MetricData(
                "accepted_bucket",
                metricWindow,
                latestInstanceBucketEndUtc.orElse(null),
                freshness.status().name().toLowerCase(Locale.ROOT),
                sampleReadiness.status(),
                aggregate.requestCount(),
                aggregate.errorCount(),
                aggregate.requestCount() == 0L ? null : errorRate(aggregate.errorCount(), aggregate.requestCount()),
                sampleReadiness.reason());
    }

    private InstanceEvidenceReadModel.StarterConnection starterConnection(
            Optional<StarterHeartbeatTelemetryRecord> heartbeat,
            Instant queryAt,
            AcceptedBucketFreshness freshness,
            WindowBucketAggregate aggregate) {
        StarterConnectionInput starterConnectionInput = starterConnectionInput(heartbeat, queryAt);
        StarterConnectionSummary summary = lifecycleStateService.decide(
                        new MetricLifecycleInput(
                                freshness,
                                metricSampleReadiness(aggregate),
                                metricTrafficActivity(aggregate),
                                DegradedHysteresisInput.noConcern(),
                                Optional.empty(),
                                Optional.empty()),
                        starterConnectionInput)
                .starterConnection();
        return new InstanceEvidenceReadModel.StarterConnection(
                summary.statusSource(),
                summary.lastHeartbeatAt()
                        .map(InstanceEvidenceReadModelService::toUtcOffsetDateTime)
                        .orElse(null),
                summary.lastHeartbeatStatus().name().toLowerCase(Locale.ROOT),
                summary.freshness().name().toLowerCase(Locale.ROOT),
                summary.meaning().name().toLowerCase(Locale.ROOT),
                summary.stateImpact().code());
    }

    /**
     * selected instance heartbeat row를 dashboard와 같은 90초 recency policy의 typed input으로 변환한다.
     */
    private StarterConnectionInput starterConnectionInput(
            Optional<StarterHeartbeatTelemetryRecord> heartbeat,
            Instant queryAt) {
        if (heartbeat.isEmpty()) {
            return StarterConnectionInput.missing();
        }

        StarterHeartbeatTelemetryRecord record = heartbeat.orElseThrow();
        Instant lastReceivedAt = record.lastReceivedAtUtc().toInstant();
        StarterHeartbeatStatus heartbeatStatus = heartbeatStatus(record.heartbeatStatus());
        boolean recentReceivedHeartbeat = heartbeatStatus == StarterHeartbeatStatus.RECEIVED
                && Duration.between(lastReceivedAt, queryAt).compareTo(STARTER_HEARTBEAT_RECENT_WINDOW) <= 0;
        if (recentReceivedHeartbeat) {
            return StarterConnectionInput.recentHeartbeat(lastReceivedAt);
        }
        if (heartbeatStatus == StarterHeartbeatStatus.RECEIVED) {
            return StarterConnectionInput.staleHeartbeat(lastReceivedAt);
        }
        return new StarterConnectionInput(
                StarterConnectionInput.STARTER_HEARTBEAT_SOURCE,
                Optional.of(lastReceivedAt),
                heartbeatStatus,
                StarterConnectionFreshness.STALE);
    }

    private static StarterHeartbeatStatus heartbeatStatus(String heartbeatStatus) {
        String normalizedStatus = heartbeatStatus == null ? "" : heartbeatStatus.trim().toLowerCase(Locale.ROOT);
        return switch (normalizedStatus) {
            case "received" -> StarterHeartbeatStatus.RECEIVED;
            case "failed" -> StarterHeartbeatStatus.FAILED;
            default -> StarterHeartbeatStatus.UNKNOWN;
        };
    }

    private InstanceEvidenceReadModel.StarterPercentiles starterPercentiles(
            List<LocalPercentileEvidenceRow> rows) {
        List<LocalPercentileEvidenceRow> evidenceRows = List.copyOf(Objects.requireNonNullElse(rows, List.of()));
        if (evidenceRows.isEmpty()) {
            return new InstanceEvidenceReadModel.StarterPercentiles(
                    "starter_canonical_percentile",
                    "instance",
                    "current_15m",
                    30,
                    30,
                    "source_scoped_series",
                    "no_average_no_max_no_merge_no_histogram_recalculation",
                    "missing",
                    "no_percentile_points_in_current_window",
                    List.of());
        }

        List<InstanceEvidenceReadModel.PercentilePoint> validPoints = evidenceRows.stream()
                .flatMap(row -> toValidPercentilePoint(row).stream())
                .sorted(Comparator.comparing(InstanceEvidenceReadModel.PercentilePoint::bucketEndUtc))
                .toList();
        if (validPoints.isEmpty()) {
            return new InstanceEvidenceReadModel.StarterPercentiles(
                    "starter_canonical_percentile",
                    "instance",
                    "current_15m",
                    30,
                    30,
                    "source_scoped_series",
                    "no_average_no_max_no_merge_no_histogram_recalculation",
                    "insufficient",
                    "no_valid_percentile_points_in_current_window",
                    List.of());
        }

        int skipCount = Math.max(0, validPoints.size() - MAX_PERCENTILE_POINTS);
        List<InstanceEvidenceReadModel.PercentilePoint> boundedPoints = validPoints.stream()
                .skip(skipCount)
                .toList();
        return new InstanceEvidenceReadModel.StarterPercentiles(
                "starter_canonical_percentile",
                "instance",
                "current_15m",
                30,
                30,
                "source_scoped_series",
                "no_average_no_max_no_merge_no_histogram_recalculation",
                "available",
                null,
                boundedPoints);
    }

    /**
     * persisted local_percentiles_json의 source/scope와 bucket boundary를 검증해 public percentile point로 옮긴다.
     */
    private Optional<InstanceEvidenceReadModel.PercentilePoint> toValidPercentilePoint(
            LocalPercentileEvidenceRow row) {
        return parseLocalPercentiles(row.localPercentilesJson())
                .filter(percentiles -> INSTANCE_BUCKET_SCOPE.equals(percentiles.scope()))
                .filter(percentiles -> STARTER_LOCAL_SOURCE.equals(percentiles.source()))
                .filter(percentiles -> Boolean.FALSE.equals(percentiles.mergeable()))
                .filter(percentiles -> percentiles.requestCount() != null && percentiles.requestCount() > 0L)
                .filter(percentiles -> percentiles.p95Ms() != null && percentiles.p95Ms() >= 0L)
                .filter(percentiles -> percentiles.p99Ms() != null && percentiles.p99Ms() >= percentiles.p95Ms())
                .filter(percentiles -> matchesBoundary(percentiles, row))
                .map(percentiles -> new InstanceEvidenceReadModel.PercentilePoint(
                        row.bucketStartUtc(),
                        row.bucketEndUtc(),
                        percentiles.requestCount(),
                        percentiles.p95Ms(),
                        percentiles.p99Ms()));
    }

    private InstanceEvidenceReadModel.HistogramDistribution histogramDistribution(
            List<HistogramBucketEvidenceRow> rows) {
        List<HistogramBucketEvidenceRow> evidenceRows = List.copyOf(Objects.requireNonNullElse(rows, List.of()));
        if (evidenceRows.isEmpty()) {
            return new InstanceEvidenceReadModel.HistogramDistribution(
                    "histogram_bucket_distribution",
                    "selected_instance_current_15m",
                    "missing",
                    "no_histogram_buckets_in_current_window",
                    0L,
                    List.of());
        }

        List<Long> expectedBoundarySet = null;
        Map<Long, Long> mergedCounts = new LinkedHashMap<>();
        for (HistogramBucketEvidenceRow row : evidenceRows) {
            Optional<List<ParsedHistogramBucket>> parsedBuckets = parseDurationBuckets(row.durationBucketsJson());
            if (parsedBuckets.isEmpty() || parsedBuckets.orElseThrow().isEmpty()) {
                return insufficientHistogram();
            }
            List<ParsedHistogramBucket> sortedBuckets = sortedUniqueBuckets(parsedBuckets.orElseThrow());
            if (sortedBuckets.isEmpty()) {
                return insufficientHistogram();
            }
            List<Long> boundarySet = sortedBuckets.stream()
                    .map(ParsedHistogramBucket::leMs)
                    .toList();
            if (expectedBoundarySet == null) {
                expectedBoundarySet = boundarySet;
                boundarySet.forEach(boundary -> mergedCounts.put(boundary, 0L));
            } else if (!expectedBoundarySet.equals(boundarySet)) {
                return new InstanceEvidenceReadModel.HistogramDistribution(
                        "histogram_bucket_distribution",
                        "selected_instance_current_15m",
                        "unavailable",
                        HISTOGRAM_BOUNDARY_MISMATCH_REASON,
                        0L,
                        List.of());
            }
            for (ParsedHistogramBucket bucket : sortedBuckets) {
                mergedCounts.compute(bucket.leMs(), (boundary, count) -> count + bucket.count());
            }
        }

        if (mergedCounts.isEmpty()) {
            return insufficientHistogram();
        }
        List<InstanceEvidenceReadModel.HistogramBucket> buckets = mergedCounts.entrySet().stream()
                .map(entry -> new InstanceEvidenceReadModel.HistogramBucket(entry.getKey(), entry.getValue()))
                .toList();
        return new InstanceEvidenceReadModel.HistogramDistribution(
                "histogram_bucket_distribution",
                "selected_instance_current_15m",
                "available",
                null,
                buckets.get(buckets.size() - 1).count(),
                buckets);
    }

    private static InstanceEvidenceReadModel.HistogramDistribution insufficientHistogram() {
        return new InstanceEvidenceReadModel.HistogramDistribution(
                "histogram_bucket_distribution",
                "selected_instance_current_15m",
                "insufficient",
                "invalid_histogram_bucket_evidence",
                0L,
                List.of());
    }

    private static InstanceEvidenceReadModel.ResourceHints resourceHints(
            Optional<RuntimeRatioEvidenceRow> runtimeRatio) {
        return runtimeRatio
                .map(row -> new InstanceEvidenceReadModel.ResourceHints(
                        "accepted_bucket_latest_sample",
                        "available",
                        null,
                        row.bucketEndUtc(),
                        row.cpuUsageRatio(),
                        row.heapUsedRatio(),
                        row.datasourcePoolUsageRatio()))
                .orElseGet(() -> new InstanceEvidenceReadModel.ResourceHints(
                        "accepted_bucket_latest_sample",
                        "missing",
                        "no_runtime_ratio_sample_in_current_window",
                        null,
                        null,
                        null,
                        null));
    }

    private ApplicationEvidenceContext applicationEvidenceContext(
            ApplicationEntity application,
            DashboardTimeWindow dashboardWindow,
            Instant evaluationAt) {
        Optional<OffsetDateTime> latestApplicationBucketEndUtc =
                metricBucketRepository.findLatestBucketEndUtcByApplicationIdAtOrBefore(application.id(), evaluationAt);
        AcceptedBucketFreshness applicationFreshness = freshnessEvaluator.evaluateAt(
                evaluationAt,
                latestApplicationBucketEndUtc.map(OffsetDateTime::toInstant).orElse(null));
        WindowBucketAggregate currentAggregate = metricBucketRepository.findWindowAggregateByApplicationId(
                application.id(),
                dashboardWindow.current().startUtc(),
                dashboardWindow.current().endUtc());
        WindowBucketAggregate baselineAggregate = metricBucketRepository.findWindowAggregateByApplicationId(
                application.id(),
                dashboardWindow.baseline().startUtc(),
                dashboardWindow.baseline().endUtc());
        ApplicationDashboardReadModel.HistogramDistribution applicationHistogram =
                applicationHistogramDistribution(
                        metricBucketRepository.findSummaryDurationBucketEvidenceRowsByApplicationId(
                                application.id(),
                                dashboardWindow.current().startUtc(),
                                dashboardWindow.current().endUtc()),
                        metricBucketRepository.findSummaryDurationBucketEvidenceRowsByApplicationId(
                                application.id(),
                                dashboardWindow.baseline().startUtc(),
                                dashboardWindow.baseline().endUtc()));
        Optional<RuntimeRatioEvidenceRow> applicationRuntimeRatio =
                metricBucketRepository.findLatestRuntimeRatioEvidenceRowByApplicationId(
                        application.id(),
                        dashboardWindow.current().startUtc(),
                        dashboardWindow.current().endUtc());
        List<RecentBucketEvidenceRow> recentBuckets =
                metricBucketRepository.findRecentFiveBucketEvidenceRowsByApplicationIdAtOrBefore(
                        application.id(),
                        evaluationAt);
        List<EndpointEvidenceRow> currentEndpointRows = metricBucketRepository.findEndpointEvidenceRowsByApplicationId(
                application.id(),
                dashboardWindow.current().startUtc(),
                dashboardWindow.current().endUtc());
        List<EndpointEvidenceRow> baselineEndpointRows = metricBucketRepository.findEndpointEvidenceRowsByApplicationId(
                application.id(),
                dashboardWindow.baseline().startUtc(),
                dashboardWindow.baseline().endUtc());

        TriageSummary triageSummary = triageSummaryService.summarize(new TriageSummaryInput(
                currentAggregate,
                baselineAggregate,
                applicationHistogram,
                ApplicationDashboardReadModel.SourceScopedPercentiles.empty(),
                recentBuckets,
                applicationRuntimeRatio,
                applicationFreshness.status()));
        List<ApplicationDashboardReadModel.EndpointPriorityItem> endpointPriority =
                applicationEndpointPriority(
                        applicationFreshness.status(),
                        currentAggregate,
                        currentEndpointRows,
                        baselineEndpointRows,
                        latestApplicationBucketEndUtc);
        WindowEndpointEvidence endpointAggregate = endpointEvidenceAggregationService.mergeWindow(currentEndpointRows);
        return new ApplicationEvidenceContext(
                applicationFreshness,
                currentAggregate,
                triageSummary,
                endpointPriority,
                endpointAggregate);
    }

    private List<ApplicationDashboardReadModel.EndpointPriorityItem> applicationEndpointPriority(
            AcceptedBucketFreshnessStatus applicationFreshnessStatus,
            WindowBucketAggregate currentAggregate,
            List<EndpointEvidenceRow> currentEndpointRows,
            List<EndpointEvidenceRow> baselineEndpointRows,
            Optional<OffsetDateTime> latestApplicationBucketEndUtc) {
        if (metricSampleReadiness(currentAggregate) == MetricSampleReadiness.INSUFFICIENT) {
            return List.of();
        }
        return endpointPriorityService.endpointPriority(new EndpointPriorityService.EndpointPriorityInput(
                applicationFreshnessStatus,
                currentEndpointRows,
                baselineEndpointRows,
                latestApplicationBucketEndUtc));
    }

    private InstanceEvidenceReadModel.ApplicationTriageContribution applicationTriageContribution(
            ApplicationEvidenceContext applicationContext,
            AcceptedBucketFreshness instanceFreshness,
            WindowBucketAggregate instanceAggregate,
            InstanceEvidenceReadModel.HistogramDistribution instanceHistogram,
            InstanceEvidenceReadModel.ResourceHints resourceHints) {
        return bridgeApplicationTriage(
                applicationContext.triageSummary().triageCards(),
                instanceFreshness,
                instanceAggregate,
                instanceHistogram,
                resourceHints);
    }

    /**
     * application-level triage card의 existing rule id만 selected instance evidence와 연결한다.
     */
    private static InstanceEvidenceReadModel.ApplicationTriageContribution bridgeApplicationTriage(
            List<ApplicationDashboardReadModel.TriageCard> triageCards,
            AcceptedBucketFreshness instanceFreshness,
            WindowBucketAggregate instanceAggregate,
            InstanceEvidenceReadModel.HistogramDistribution instanceHistogram,
            InstanceEvidenceReadModel.ResourceHints resourceHints) {
        if (triageCards.isEmpty()) {
            return new InstanceEvidenceReadModel.ApplicationTriageContribution(
                    "missing",
                    false,
                    List.of(),
                    "no_application_triage_cards");
        }
        if (instanceFreshness.status() != AcceptedBucketFreshnessStatus.CURRENT
                || instanceAggregate.requestCount() == 0L) {
            return new InstanceEvidenceReadModel.ApplicationTriageContribution(
                    "insufficient",
                    false,
                    List.of(),
                    "selected_instance_evidence_insufficient");
        }

        List<RuleBridgeEvaluation> evaluations = triageCards.stream()
                .map(ApplicationDashboardReadModel.TriageCard::ruleId)
                .map(ruleId -> evaluateSelectedInstanceRuleEvidence(
                        ruleId,
                        instanceAggregate,
                        instanceHistogram,
                        resourceHints))
                .toList();
        List<String> relatedRuleIds = evaluations.stream()
                .filter(RuleBridgeEvaluation::supported)
                .map(RuleBridgeEvaluation::ruleId)
                .distinct()
                .toList();
        if (relatedRuleIds.isEmpty()) {
            boolean hasKnownInsufficientEvidence = evaluations.stream()
                    .anyMatch(RuleBridgeEvaluation::insufficientEvidence);
            if (hasKnownInsufficientEvidence) {
                return new InstanceEvidenceReadModel.ApplicationTriageContribution(
                        "insufficient",
                        false,
                        List.of(),
                        "selected_instance_evidence_insufficient");
            }
            return new InstanceEvidenceReadModel.ApplicationTriageContribution(
                    "available",
                    false,
                    List.of(),
                    "selected_instance_not_linked_to_application_triage");
        }
        return new InstanceEvidenceReadModel.ApplicationTriageContribution(
                "observed",
                true,
                relatedRuleIds,
                "selected_instance_has_evidence_for_application_triage");
    }

    /**
     * application endpoint priority 참조와 selected instance endpoint aggregate를 연결해 bounded subset을 만든다.
     */
    private InstanceEvidenceReadModel.EndpointEvidence endpointEvidence(
            ApplicationEvidenceContext applicationContext,
            UUID applicationInstanceId,
            UtcTimeInterval currentWindow) {
        if (applicationContext.applicationFreshness().status() != AcceptedBucketFreshnessStatus.CURRENT) {
            return new InstanceEvidenceReadModel.EndpointEvidence(
                    "accepted_metric_buckets.endpoints_json",
                    "instance_current_15m",
                    "application_priority_presence_then_triage_then_instance_request_count",
                    "selected_instance_signal_then_application_priority_reference",
                    "suppressed",
                    "application_freshness_not_current",
                    List.of());
        }

        List<EndpointEvidenceRow> selectedRows = metricBucketRepository.findEndpointEvidenceRowsByApplicationInstanceId(
                applicationInstanceId,
                currentWindow.startUtc(),
                currentWindow.endUtc());
        WindowEndpointEvidence selectedEndpointEvidence = endpointEvidenceAggregationService.mergeWindow(selectedRows);
        if (selectedEndpointEvidence.malformedEvidence()) {
            return new InstanceEvidenceReadModel.EndpointEvidence(
                    "accepted_metric_buckets.endpoints_json",
                    "instance_current_15m",
                    "application_priority_presence_then_triage_then_instance_request_count",
                    "selected_instance_signal_then_application_priority_reference",
                    "insufficient",
                    "endpoint_evidence_insufficient",
                    List.of());
        }

        Map<String, EndpointPriorityReference> priorityReferences = endpointPriorityReferences(
                applicationContext.endpointPriority());
        LinkedHashSet<String> selectedEndpointKeys = new LinkedHashSet<>();
        applicationContext.endpointPriority().stream()
                .sorted(Comparator.comparingInt(ApplicationDashboardReadModel.EndpointPriorityItem::rank))
                .map(ApplicationDashboardReadModel.EndpointPriorityItem::endpointKey)
                .filter(endpointKey -> !hasUnknownRoute(endpointKey))
                .forEach(selectedEndpointKeys::add);
        applicationContext.triageSummary().triageCards().stream()
                .map(ApplicationDashboardReadModel.TriageCard::affectedEndpoint)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(endpointKey -> !endpointKey.isEmpty())
                .filter(endpointKey -> !hasUnknownRoute(endpointKey))
                .forEach(selectedEndpointKeys::add);
        selectedEndpointEvidence.endpoints().values().stream()
                .filter(endpoint -> !isUnknownRoute(endpoint.route()))
                .filter(endpoint -> endpoint.requestCount() > 0L)
                .sorted(Comparator.comparingLong(EndpointAggregate::requestCount)
                        .reversed()
                        .thenComparing(EndpointAggregate::endpointKey))
                .map(EndpointAggregate::endpointKey)
                .forEach(selectedEndpointKeys::add);

        List<EndpointEvidenceDraft> selectedDrafts = new ArrayList<>();
        for (String endpointKey : selectedEndpointKeys) {
            toEndpointEvidenceDraft(
                    endpointKey,
                    applicationContext.applicationEndpointEvidence(),
                    selectedEndpointEvidence,
                    priorityReferences)
                    .ifPresent(selectedDrafts::add);
            if (selectedDrafts.size() == 5) {
                break;
            }
        }
        if (selectedDrafts.isEmpty()) {
            return InstanceEvidenceReadModel.EndpointEvidence.missing();
        }

        List<EndpointEvidenceDraft> displayDrafts = selectedDrafts.stream()
                .sorted(InstanceEvidenceReadModelService::compareEndpointEvidenceDrafts)
                .toList();
        List<InstanceEvidenceReadModel.EndpointEvidenceItem> items = new ArrayList<>();
        for (int index = 0; index < displayDrafts.size(); index++) {
            items.add(displayDrafts.get(index).toItem(index + 1));
        }
        return new InstanceEvidenceReadModel.EndpointEvidence(
                "accepted_metric_buckets.endpoints_json",
                "instance_current_15m",
                "application_priority_presence_then_triage_then_instance_request_count",
                "selected_instance_signal_then_application_priority_reference",
                "available",
                null,
                items);
    }

    private static Map<String, EndpointPriorityReference> endpointPriorityReferences(
            List<ApplicationDashboardReadModel.EndpointPriorityItem> endpointPriority) {
        Map<String, EndpointPriorityReference> references = new LinkedHashMap<>();
        for (ApplicationDashboardReadModel.EndpointPriorityItem item : endpointPriority) {
            references.putIfAbsent(
                    item.endpointKey(),
                    new EndpointPriorityReference(item.rank(), item.ruleIds()));
        }
        return references;
    }

    private static Optional<EndpointEvidenceDraft> toEndpointEvidenceDraft(
            String endpointKey,
            WindowEndpointEvidence applicationEndpointEvidence,
            WindowEndpointEvidence selectedEndpointEvidence,
            Map<String, EndpointPriorityReference> priorityReferences) {
        EndpointAggregate applicationAggregate = applicationEndpointEvidence.endpoints().get(endpointKey);
        EndpointAggregate selectedAggregate = selectedEndpointEvidence.endpoints().get(endpointKey);
        if (applicationAggregate == null && selectedAggregate == null) {
            return Optional.empty();
        }
        if (applicationAggregate == null && selectedAggregate != null && selectedAggregate.requestCount() == 0L) {
            return Optional.empty();
        }

        EndpointAggregate identitySource = selectedAggregate == null ? applicationAggregate : selectedAggregate;
        if (isUnknownRoute(identitySource.route())) {
            return Optional.empty();
        }
        long instanceRequestCount = selectedAggregate == null ? 0L : selectedAggregate.requestCount();
        long instanceErrorCount = selectedAggregate == null ? 0L : selectedAggregate.errorCount();
        Long applicationRequestCount = applicationAggregate == null ? null : applicationAggregate.requestCount();
        Long applicationErrorCount = applicationAggregate == null ? null : applicationAggregate.errorCount();
        boolean histogramBoundaryMismatch = selectedAggregate != null && selectedAggregate.durationBoundaryMismatch();
        String presence = presenceOnSelectedInstance(selectedAggregate, applicationAggregate, histogramBoundaryMismatch);
        EndpointPriorityReference priorityReference = priorityReferences.get(endpointKey);
        return Optional.of(new EndpointEvidenceDraft(
                identitySource.method(),
                identitySource.route(),
                identitySource.endpointKey(),
                presence,
                instanceRequestCount,
                instanceErrorCount,
                ratioOrNull(instanceErrorCount, instanceRequestCount),
                applicationRequestCount,
                applicationErrorCount,
                ratioOrNull(applicationErrorCount, applicationRequestCount),
                ratioOrNull(instanceRequestCount, applicationRequestCount),
                ratioOrNull(instanceErrorCount, applicationErrorCount),
                durationBuckets(selectedAggregate, histogramBoundaryMismatch),
                priorityReference == null ? null : priorityReference.rank(),
                priorityReference == null ? List.of() : priorityReference.ruleIds(),
                histogramBoundaryMismatch ? "unavailable" : "available",
                endpointEvidenceReason(presence, histogramBoundaryMismatch, priorityReference, applicationAggregate)));
    }

    private static String presenceOnSelectedInstance(
            EndpointAggregate selectedAggregate,
            EndpointAggregate applicationAggregate,
            boolean histogramBoundaryMismatch) {
        if (histogramBoundaryMismatch) {
            return "insufficient";
        }
        if (selectedAggregate != null && selectedAggregate.requestCount() > 0L) {
            return "observed";
        }
        if (applicationAggregate != null) {
            return "not_observed";
        }
        return "insufficient";
    }

    private static boolean hasUnknownRoute(String endpointKey) {
        if (endpointKey == null) {
            return false;
        }
        int firstSpace = endpointKey.indexOf(' ');
        return firstSpace >= 0 && isUnknownRoute(endpointKey.substring(firstSpace + 1));
    }

    private static boolean isUnknownRoute(String route) {
        return "UNKNOWN".equalsIgnoreCase(route == null ? "" : route.trim());
    }

    private static String endpointEvidenceReason(
            String presence,
            boolean histogramBoundaryMismatch,
            EndpointPriorityReference priorityReference,
            EndpointAggregate applicationAggregate) {
        if (histogramBoundaryMismatch) {
            return "histogram_boundary_mismatch";
        }
        if ("insufficient".equals(presence)) {
            return "endpoint_evidence_insufficient";
        }
        if (priorityReference != null && "observed".equals(presence)) {
            return "application_priority_endpoint_observed_on_selected_instance";
        }
        if ("not_observed".equals(presence) && applicationAggregate != null) {
            return "application_priority_endpoint_not_seen_on_selected_instance";
        }
        return "selected_instance_endpoint_observed";
    }

    private static List<InstanceEvidenceReadModel.HistogramBucket> durationBuckets(
            EndpointAggregate selectedAggregate,
            boolean histogramBoundaryMismatch) {
        if (selectedAggregate == null || histogramBoundaryMismatch || selectedAggregate.durationBuckets() == null) {
            return List.of();
        }
        return selectedAggregate.durationBuckets().stream()
                .map(bucket -> new InstanceEvidenceReadModel.HistogramBucket(bucket.leMs(), bucket.count()))
                .toList();
    }

    private static int compareEndpointEvidenceDrafts(EndpointEvidenceDraft first, EndpointEvidenceDraft second) {
        int presence = Boolean.compare(
                !"observed".equals(first.presenceOnSelectedInstance()),
                !"observed".equals(second.presenceOnSelectedInstance()));
        if (presence != 0) {
            return presence;
        }
        int errorShare = compareNullableDescending(first.instanceErrorShare(), second.instanceErrorShare());
        if (errorShare != 0) {
            return errorShare;
        }
        int errorRate = compareNullableDescending(first.instanceErrorRate(), second.instanceErrorRate());
        if (errorRate != 0) {
            return errorRate;
        }
        int requestCount = Long.compare(second.instanceRequestCount(), first.instanceRequestCount());
        if (requestCount != 0) {
            return requestCount;
        }
        int rank = compareNullableAscending(
                first.relatedApplicationPriorityRank(),
                second.relatedApplicationPriorityRank());
        if (rank != 0) {
            return rank;
        }
        return first.endpointKey().compareTo(second.endpointKey());
    }

    private static int compareNullableDescending(BigDecimal first, BigDecimal second) {
        if (first == null && second == null) {
            return 0;
        }
        if (first == null) {
            return 1;
        }
        if (second == null) {
            return -1;
        }
        return second.compareTo(first);
    }

    private static int compareNullableAscending(Integer first, Integer second) {
        if (first == null && second == null) {
            return 0;
        }
        if (first == null) {
            return 1;
        }
        if (second == null) {
            return -1;
        }
        return Integer.compare(first, second);
    }

    private static RuleBridgeEvaluation evaluateSelectedInstanceRuleEvidence(
            String ruleId,
            WindowBucketAggregate instanceAggregate,
            InstanceEvidenceReadModel.HistogramDistribution instanceHistogram,
            InstanceEvidenceReadModel.ResourceHints resourceHints) {
        return switch (ruleId) {
            case "global_error_spike" -> RuleBridgeEvaluation.known(
                    ruleId,
                    instanceAggregate.errorCount() > 0L,
                    false);
            case "global_latency_spike" -> RuleBridgeEvaluation.known(
                    ruleId,
                    "available".equals(instanceHistogram.status()) && instanceHistogram.totalCount() > 0L,
                    !"available".equals(instanceHistogram.status()));
            case "db_pool_high_with_latency" -> RuleBridgeEvaluation.known(
                    ruleId,
                    resourceHints.datasourcePoolUsageRatio() != null,
                    resourceHints.datasourcePoolUsageRatio() == null);
            case "cpu_high_with_latency" -> RuleBridgeEvaluation.known(
                    ruleId,
                    resourceHints.cpuUsageRatio() != null,
                    resourceHints.cpuUsageRatio() == null);
            case "heap_high_hint" -> RuleBridgeEvaluation.known(
                    ruleId,
                    resourceHints.heapUsedRatio() != null,
                    resourceHints.heapUsedRatio() == null);
            default -> RuleBridgeEvaluation.unknown(ruleId);
        };
    }

    private ApplicationDashboardReadModel.HistogramDistribution applicationHistogramDistribution(
            List<HistogramBucketEvidenceRow> currentRows,
            List<HistogramBucketEvidenceRow> baselineRows) {
        return new ApplicationDashboardReadModel.HistogramDistribution(
                "histogram_bucket_distribution",
                "application",
                "bucket_distribution_evidence",
                "sum_cumulative_counts_only_when_boundary_set_matches",
                applicationHistogramWindow(currentRows, "current"),
                applicationHistogramWindow(baselineRows, "baseline"));
    }

    private ApplicationDashboardReadModel.HistogramWindow applicationHistogramWindow(
            List<HistogramBucketEvidenceRow> rows,
            String windowName) {
        List<HistogramBucketEvidenceRow> evidenceRows = List.copyOf(Objects.requireNonNullElse(rows, List.of()));
        if (evidenceRows.isEmpty()) {
            return ApplicationDashboardReadModel.HistogramWindow.missing(
                    "no_histogram_buckets_in_" + windowName + "_window");
        }

        List<Long> expectedBoundarySet = null;
        Map<Long, Long> mergedCounts = new LinkedHashMap<>();
        for (HistogramBucketEvidenceRow row : evidenceRows) {
            Optional<List<ParsedHistogramBucket>> parsedBuckets = parseDurationBuckets(row.durationBucketsJson());
            if (parsedBuckets.isEmpty() || parsedBuckets.orElseThrow().isEmpty()) {
                return ApplicationDashboardReadModel.HistogramWindow.insufficient(
                        "invalid_histogram_bucket_evidence_in_" + windowName + "_window");
            }
            List<ParsedHistogramBucket> sortedBuckets = sortedUniqueBuckets(parsedBuckets.orElseThrow());
            if (sortedBuckets.isEmpty()) {
                return ApplicationDashboardReadModel.HistogramWindow.insufficient(
                        "invalid_histogram_bucket_evidence_in_" + windowName + "_window");
            }
            List<Long> boundarySet = sortedBuckets.stream()
                    .map(ParsedHistogramBucket::leMs)
                    .toList();
            if (expectedBoundarySet == null) {
                expectedBoundarySet = boundarySet;
                boundarySet.forEach(boundary -> mergedCounts.put(boundary, 0L));
            } else if (!expectedBoundarySet.equals(boundarySet)) {
                return ApplicationDashboardReadModel.HistogramWindow.unavailable(HISTOGRAM_BOUNDARY_MISMATCH_REASON);
            }
            for (ParsedHistogramBucket bucket : sortedBuckets) {
                mergedCounts.compute(bucket.leMs(), (boundary, count) -> count + bucket.count());
            }
        }
        if (mergedCounts.isEmpty()) {
            return ApplicationDashboardReadModel.HistogramWindow.insufficient(
                    "invalid_histogram_bucket_evidence_in_" + windowName + "_window");
        }
        List<ApplicationDashboardReadModel.HistogramBucket> buckets = mergedCounts.entrySet().stream()
                .map(entry -> new ApplicationDashboardReadModel.HistogramBucket(entry.getKey(), entry.getValue()))
                .toList();
        return ApplicationDashboardReadModel.HistogramWindow.available(
                buckets.get(buckets.size() - 1).count(),
                buckets);
    }

    private Optional<ParsedLocalPercentiles> parseLocalPercentiles(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isObject()) {
                return Optional.empty();
            }
            return Optional.of(new ParsedLocalPercentiles(
                    textValue(root, "scope"),
                    textValue(root, "source"),
                    textValue(root, "bucketStartUtc"),
                    textValue(root, "bucketEndUtc"),
                    longValue(root, "requestCount"),
                    longValue(root, "p95Ms"),
                    longValue(root, "p99Ms"),
                    booleanValue(root, "mergeable")));
        } catch (JsonProcessingException exception) {
            return Optional.empty();
        }
    }

    private Optional<List<ParsedHistogramBucket>> parseDurationBuckets(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isArray()) {
                return Optional.empty();
            }
            List<ParsedHistogramBucket> buckets = new ArrayList<>();
            for (JsonNode item : root) {
                Long leMs = longValue(item, "leMs");
                Long count = longValue(item, "count");
                if (leMs == null || count == null || leMs < 0L || count < 0L) {
                    return Optional.empty();
                }
                buckets.add(new ParsedHistogramBucket(leMs, count));
            }
            return Optional.of(buckets);
        } catch (JsonProcessingException exception) {
            return Optional.empty();
        }
    }

    private static List<ParsedHistogramBucket> sortedUniqueBuckets(List<ParsedHistogramBucket> buckets) {
        List<ParsedHistogramBucket> sortedBuckets = buckets.stream()
                .sorted(Comparator.comparingLong(ParsedHistogramBucket::leMs))
                .toList();
        Long previousBoundary = null;
        Long previousCount = null;
        for (ParsedHistogramBucket bucket : sortedBuckets) {
            if (Objects.equals(previousBoundary, bucket.leMs())) {
                return List.of();
            }
            if (previousCount != null && bucket.count() < previousCount) {
                return List.of();
            }
            previousBoundary = bucket.leMs();
            previousCount = bucket.count();
        }
        return sortedBuckets;
    }

    private static boolean matchesBoundary(
            ParsedLocalPercentiles percentiles,
            LocalPercentileEvidenceRow row) {
        if (percentiles.bucketStartUtc() == null || percentiles.bucketStartUtc().isBlank()
                || percentiles.bucketEndUtc() == null || percentiles.bucketEndUtc().isBlank()) {
            return false;
        }
        try {
            OffsetDateTime percentileStart = OffsetDateTime.parse(percentiles.bucketStartUtc());
            OffsetDateTime percentileEnd = OffsetDateTime.parse(percentiles.bucketEndUtc());
            return percentileStart.toInstant().equals(row.bucketStartUtc().toInstant())
                    && percentileEnd.toInstant().equals(row.bucketEndUtc().toInstant());
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    private static SampleReadiness sampleReadiness(
            AcceptedBucketFreshnessStatus freshnessStatus,
            WindowBucketAggregate aggregate) {
        if (aggregate.requestCount() == 0L) {
            if (freshnessStatus == AcceptedBucketFreshnessStatus.CURRENT) {
                return new SampleReadiness("sufficient", "metric_data_idle");
            }
            return new SampleReadiness("missing", "no_current_window_metric_bucket");
        }
        if (aggregate.requestCount() < MINIMUM_ACTIVE_SAMPLE_REQUEST_COUNT) {
            return new SampleReadiness("insufficient", "insufficient_request_sample");
        }
        return new SampleReadiness("sufficient", null);
    }

    private static MetricSampleReadiness metricSampleReadiness(WindowBucketAggregate aggregate) {
        long requestCount = aggregate.requestCount();
        if (requestCount == 0L || requestCount >= MINIMUM_ACTIVE_SAMPLE_REQUEST_COUNT) {
            return MetricSampleReadiness.SUFFICIENT;
        }
        return MetricSampleReadiness.INSUFFICIENT;
    }

    private static MetricTrafficActivity metricTrafficActivity(WindowBucketAggregate aggregate) {
        return aggregate.requestCount() == 0L ? MetricTrafficActivity.IDLE : MetricTrafficActivity.ACTIVE;
    }

    private static BigDecimal errorRate(long errorCount, long requestCount) {
        int scale = Math.max(6, Long.toString(requestCount).length() + 1);
        return BigDecimal.valueOf(errorCount)
                .divide(BigDecimal.valueOf(requestCount), scale, RoundingMode.HALF_UP)
                .stripTrailingZeros();
    }

    private static BigDecimal ratioOrNull(Long numerator, Long denominator) {
        if (numerator == null || denominator == null) {
            return null;
        }
        return ratioOrNull(numerator.longValue(), denominator.longValue());
    }

    private static BigDecimal ratioOrNull(long numerator, Long denominator) {
        if (denominator == null) {
            return null;
        }
        return ratioOrNull(numerator, denominator.longValue());
    }

    private static BigDecimal ratioOrNull(long numerator, long denominator) {
        if (denominator == 0L) {
            return null;
        }
        return errorRate(numerator, denominator);
    }

    private static String textValue(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private static Long longValue(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        return value != null && value.isIntegralNumber() && value.canConvertToLong() ? value.asLong() : null;
    }

    private static Boolean booleanValue(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        return value != null && value.isBoolean() ? value.asBoolean() : null;
    }

    private static InstanceEvidenceReadModel.MetricWindow metricWindow(UtcTimeInterval currentWindow) {
        return new InstanceEvidenceReadModel.MetricWindow(
                "current_15m",
                toUtcOffsetDateTime(currentWindow.startUtc()),
                toUtcOffsetDateTime(currentWindow.endUtc()),
                (int) TimeBucketWindowCalculator.BUCKET_DURATION.toSeconds());
    }

    /**
     * dashboard response와 evidence response가 같은 application dashboard path를 공유하도록 link를 만든다.
     */
    public static String dashboardLink(UUID projectId, UUID applicationId) {
        return "/api/projects/%s/applications/%s/dashboard".formatted(projectId, applicationId);
    }

    /**
     * dashboard instance entry와 evidence response self field가 같은 selected instance path를 공유하도록 link를 만든다.
     */
    public static String evidenceLink(UUID projectId, UUID applicationId, UUID instanceId) {
        return "/api/projects/%s/applications/%s/instances/%s/evidence".formatted(
                projectId,
                applicationId,
                instanceId);
    }

    /**
     * Story 5.7 instance snapshot trend endpoint로 이동하는 UUID 기반 link를 만든다.
     *
     * <p>이 link는 current evidence 계산 의미를 바꾸지 않고, `instanceName` fallback 없이 catalog instance UUID path만
     * 사용한다.</p>
     */
    public static String snapshotTrendLink(UUID projectId, UUID applicationId, UUID instanceId) {
        return "/api/projects/%s/applications/%s/instances/%s/snapshot-trend".formatted(
                projectId,
                applicationId,
                instanceId);
    }

    private static OffsetDateTime toUtcOffsetDateTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private record SampleReadiness(String status, String reason) {
    }

    private record ApplicationEvidenceContext(
            AcceptedBucketFreshness applicationFreshness,
            WindowBucketAggregate currentAggregate,
            TriageSummary triageSummary,
            List<ApplicationDashboardReadModel.EndpointPriorityItem> endpointPriority,
            WindowEndpointEvidence applicationEndpointEvidence
    ) {

        private ApplicationEvidenceContext {
            Objects.requireNonNull(applicationFreshness, "applicationFreshness must not be null");
            Objects.requireNonNull(currentAggregate, "currentAggregate must not be null");
            Objects.requireNonNull(triageSummary, "triageSummary must not be null");
            endpointPriority = List.copyOf(Objects.requireNonNull(
                    endpointPriority,
                    "endpointPriority must not be null"));
            Objects.requireNonNull(applicationEndpointEvidence, "applicationEndpointEvidence must not be null");
        }
    }

    private record EndpointPriorityReference(int rank, List<String> ruleIds) {

        private EndpointPriorityReference {
            if (rank < 1) {
                throw new IllegalArgumentException("rank must be greater than or equal to 1");
            }
            ruleIds = List.copyOf(Objects.requireNonNull(ruleIds, "ruleIds must not be null"));
        }
    }

    private record EndpointEvidenceDraft(
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
            List<InstanceEvidenceReadModel.HistogramBucket> durationBuckets,
            Integer relatedApplicationPriorityRank,
            List<String> relatedRuleIds,
            String status,
            String reason
    ) {

        private EndpointEvidenceDraft {
            durationBuckets = List.copyOf(Objects.requireNonNull(durationBuckets, "durationBuckets must not be null"));
            relatedRuleIds = List.copyOf(Objects.requireNonNull(relatedRuleIds, "relatedRuleIds must not be null"));
        }

        private InstanceEvidenceReadModel.EndpointEvidenceItem toItem(int localDisplayOrder) {
            return new InstanceEvidenceReadModel.EndpointEvidenceItem(
                    method,
                    route,
                    endpointKey,
                    presenceOnSelectedInstance,
                    instanceRequestCount,
                    instanceErrorCount,
                    instanceErrorRate,
                    applicationEndpointRequestCount,
                    applicationEndpointErrorCount,
                    applicationEndpointErrorRate,
                    instanceRequestShare,
                    instanceErrorShare,
                    durationBuckets,
                    "histogram_bucket_distribution",
                    relatedApplicationPriorityRank,
                    localDisplayOrder,
                    relatedRuleIds,
                    status,
                    reason);
        }
    }

    private record RuleBridgeEvaluation(String ruleId, boolean supported, boolean insufficientEvidence) {

        private static RuleBridgeEvaluation known(
                String ruleId,
                boolean supported,
                boolean insufficientEvidence) {
            return new RuleBridgeEvaluation(ruleId, supported, insufficientEvidence);
        }

        private static RuleBridgeEvaluation unknown(String ruleId) {
            return new RuleBridgeEvaluation(ruleId, false, false);
        }
    }

    private record ParsedLocalPercentiles(
            String scope,
            String source,
            String bucketStartUtc,
            String bucketEndUtc,
            Long requestCount,
            Long p95Ms,
            Long p99Ms,
            Boolean mergeable
    ) {
    }

    private record ParsedHistogramBucket(long leMs, long count) {
    }
}
