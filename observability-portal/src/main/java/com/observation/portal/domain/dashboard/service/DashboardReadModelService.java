package com.observation.portal.domain.dashboard.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.portal.common.time.AcceptedBucketFreshness;
import com.observation.portal.common.time.AcceptedBucketFreshnessEvaluator;
import com.observation.portal.common.time.AcceptedBucketFreshnessStatus;
import com.observation.portal.common.time.DashboardTimeWindow;
import com.observation.portal.common.time.TimeBucketWindowCalculator;
import com.observation.portal.common.time.UtcTimeInterval;
import com.observation.portal.domain.bucket.model.AcceptedBucketGapEvidence;
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
import com.observation.portal.domain.dashboard.service.TriageSummaryService.TriageSummary;
import com.observation.portal.domain.dashboard.service.TriageSummaryService.TriageSummaryInput;
import com.observation.portal.domain.ingest.model.StarterHeartbeatTelemetryRecord;
import com.observation.portal.domain.ingest.repository.StarterHeartbeatTelemetryRepository;
import com.observation.portal.domain.instance.service.InstanceEvidenceReadModelService;
import com.observation.portal.domain.snapshot.service.DashboardSnapshotFallbackCaptureService;
import com.observation.portal.domain.state.model.DegradedHysteresisInput;
import com.observation.portal.domain.state.model.LifecycleStateDecision;
import com.observation.portal.domain.state.model.LifecycleStateCode;
import com.observation.portal.domain.state.model.MetricLifecycleInput;
import com.observation.portal.domain.state.model.MetricSampleReadiness;
import com.observation.portal.domain.state.model.MetricTrafficActivity;
import com.observation.portal.domain.state.model.RecoveryGuidance;
import com.observation.portal.domain.state.model.StarterConnectionFreshness;
import com.observation.portal.domain.state.model.StarterConnectionInput;
import com.observation.portal.domain.state.model.StarterConnectionMeaning;
import com.observation.portal.domain.state.model.StarterConnectionSummary;
import com.observation.portal.domain.state.model.StarterHeartbeatStatus;
import com.observation.portal.domain.state.service.LifecycleStateService;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Application Dashboard current read model을 repository 조회와 lifecycle state 결정 결과로 조립한다.
 *
 * <p>Controller와 UI가 state, starter connection, zeroInsight, recovery 의미를 다시 계산하지 않도록 server-computed
 * contract shape를 만든다.</p>
 */
@Service
public class DashboardReadModelService {

    private static final Duration STARTER_HEARTBEAT_RECENT_WINDOW = Duration.ofSeconds(90);
    private static final int INSTANCE_ENTRY_LIMIT = 50;
    private static final long MINIMUM_ACTIVE_SAMPLE_REQUEST_COUNT = 30L;
    private static final String APPLICATION_STATE_SCOPE = "application";
    private static final String ACCEPTED_BUCKET_SOURCE = "accepted_bucket";
    private static final String STARTER_CANONICAL_PERCENTILE_SOURCE = "starter_canonical_percentile";
    private static final String STARTER_LOCAL_SOURCE = "starter_local";
    private static final String INSTANCE_BUCKET_SCOPE = "instance_bucket";
    private static final String HISTOGRAM_BOUNDARY_MISMATCH_REASON = "histogram_boundary_mismatch";
    private static final String BASELINE_NOT_USED_REASON = "baseline_comparison_not_used_for_mvp";
    private static final long LATENCY_SLOW_BUCKET_LE_MS = 500L;

    private final ApplicationRepository applicationRepository;
    private final ApplicationInstanceRepository applicationInstanceRepository;
    private final MetricBucketRepository metricBucketRepository;
    private final StarterHeartbeatTelemetryRepository heartbeatTelemetryRepository;
    private final AcceptedBucketFreshnessEvaluator freshnessEvaluator;
    private final TimeBucketWindowCalculator timeBucketWindowCalculator;
    private final LifecycleStateService lifecycleStateService;
    private final TriageSummaryService triageSummaryService;
    private final EndpointPriorityService endpointPriorityService;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<DashboardSnapshotFallbackCaptureService> fallbackCaptureServiceProvider;

    /**
     * dashboard read model 조립에 필요한 read-only repository와 state/time component를 주입한다.
     */
    @Autowired
    public DashboardReadModelService(
            ApplicationRepository applicationRepository,
            ApplicationInstanceRepository applicationInstanceRepository,
            MetricBucketRepository metricBucketRepository,
            StarterHeartbeatTelemetryRepository heartbeatTelemetryRepository,
            AcceptedBucketFreshnessEvaluator freshnessEvaluator,
            TimeBucketWindowCalculator timeBucketWindowCalculator,
            LifecycleStateService lifecycleStateService,
            TriageSummaryService triageSummaryService,
            EndpointPriorityService endpointPriorityService,
            Clock clock,
            ObjectMapper objectMapper,
            ObjectProvider<DashboardSnapshotFallbackCaptureService> fallbackCaptureServiceProvider) {
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
        this.clock = Objects.requireNonNull(clock, "clock must not be null").withZone(ZoneOffset.UTC);
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.fallbackCaptureServiceProvider = Objects.requireNonNull(
                fallbackCaptureServiceProvider,
                "fallbackCaptureServiceProvider must not be null");
    }

    /**
     * focused unit test에서 fallback bean 없이 기존 dashboard orchestration만 검증할 때 사용하는 생성자다.
     */
    public DashboardReadModelService(
            ApplicationRepository applicationRepository,
            ApplicationInstanceRepository applicationInstanceRepository,
            MetricBucketRepository metricBucketRepository,
            StarterHeartbeatTelemetryRepository heartbeatTelemetryRepository,
            AcceptedBucketFreshnessEvaluator freshnessEvaluator,
            TimeBucketWindowCalculator timeBucketWindowCalculator,
            LifecycleStateService lifecycleStateService,
            TriageSummaryService triageSummaryService,
            EndpointPriorityService endpointPriorityService,
            Clock clock,
            ObjectMapper objectMapper) {
        this(
                applicationRepository,
                applicationInstanceRepository,
                metricBucketRepository,
                heartbeatTelemetryRepository,
                freshnessEvaluator,
                timeBucketWindowCalculator,
                lifecycleStateService,
                triageSummaryService,
                endpointPriorityService,
                clock,
                objectMapper,
                new NoopFallbackCaptureServiceProvider());
    }

    /**
     * project/application path scope에 맞는 current dashboard read model을 반환한다.
     *
     * <p>application row가 없거나 project mismatch이면 empty를 반환해 controller가 404로 매핑할 수 있게 한다.</p>
     */
    @Transactional(readOnly = true)
    public Optional<ApplicationDashboardReadModel> getDashboard(UUID projectId, UUID applicationId) {
        UUID requiredProjectId = Objects.requireNonNull(projectId, "projectId must not be null");
        UUID requiredApplicationId = Objects.requireNonNull(applicationId, "applicationId must not be null");
        return applicationRepository.findByIdAndProjectId(requiredApplicationId, requiredProjectId)
                .map(application -> {
                    Instant queryAt = clock.instant();
                    ApplicationDashboardReadModel readModel = buildDashboard(
                            application,
                            queryAt,
                            floorToBucketBoundary(queryAt),
                            BucketQueryContext.current());
                    DashboardSnapshotFallbackCaptureService fallbackCaptureService =
                            fallbackCaptureServiceProvider.getIfAvailable();
                    if (fallbackCaptureService != null) {
                        fallbackCaptureService.captureIfNeeded(readModel, toUtcOffsetDateTime(queryAt));
                    }
                    return readModel;
                });
    }

    /**
     * scheduled snapshot capture가 target current window end와 accepted_at cutoff를 고정해 read model을 생성하는 내부 경로다.
     *
     * <p>이 method는 query fallback capture를 호출하지 않아 scheduler/capture/writer recursion을 만들지 않는다. current dashboard
     * path와 같은 recent 30분 window 계산을 쓰되, bucket repository 조회만 snapshot cutoff 기준으로 제한한다.</p>
     */
    @Transactional(readOnly = true)
    public Optional<ApplicationDashboardReadModel> getDashboardForSnapshot(
            UUID projectId,
            UUID applicationId,
            OffsetDateTime currentWindowEndUtc,
            OffsetDateTime snapshotCutoffAt) {
        UUID requiredProjectId = Objects.requireNonNull(projectId, "projectId must not be null");
        UUID requiredApplicationId = Objects.requireNonNull(applicationId, "applicationId must not be null");
        OffsetDateTime requiredCurrentWindowEndUtc = Objects.requireNonNull(
                currentWindowEndUtc,
                "currentWindowEndUtc must not be null")
                .withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime requiredSnapshotCutoffAt = Objects.requireNonNull(
                snapshotCutoffAt,
                "snapshotCutoffAt must not be null")
                .withOffsetSameInstant(ZoneOffset.UTC);
        if (requiredSnapshotCutoffAt.isBefore(requiredCurrentWindowEndUtc)) {
            throw new IllegalArgumentException("snapshotCutoffAt must not be before currentWindowEndUtc");
        }
        return applicationRepository.findByIdAndProjectId(requiredApplicationId, requiredProjectId)
                .map(application -> buildDashboard(
                        application,
                        requiredCurrentWindowEndUtc.toInstant(),
                        requiredCurrentWindowEndUtc.toInstant(),
                        BucketQueryContext.snapshot(requiredSnapshotCutoffAt)));
    }

    private ApplicationDashboardReadModel buildDashboard(
            ApplicationEntity application,
            Instant queryAt,
            Instant evaluationAt,
            BucketQueryContext bucketQueryContext) {
        DashboardTimeWindow dashboardWindow = timeBucketWindowCalculator.dashboardWindowEndingAt(evaluationAt);
        Optional<OffsetDateTime> latestBucketEndUtc =
                findLatestBucketEndUtcByApplicationId(application.id(), evaluationAt, bucketQueryContext);
        WindowBucketAggregate aggregate = findWindowAggregateByApplicationId(
                application.id(),
                dashboardWindow.current().startUtc(),
                dashboardWindow.current().endUtc(),
                bucketQueryContext);
        List<LocalPercentileEvidenceRow> currentPercentileRows =
                findLocalPercentileEvidenceRowsByApplicationId(
                        application.id(),
                        dashboardWindow.current().startUtc(),
                        dashboardWindow.current().endUtc(),
                        bucketQueryContext);
        List<HistogramBucketEvidenceRow> currentHistogramRows =
                findSummaryDurationBucketEvidenceRowsByApplicationId(
                        application.id(),
                        dashboardWindow.current().startUtc(),
                        dashboardWindow.current().endUtc(),
                        bucketQueryContext);
        List<EndpointEvidenceRow> currentEndpointRows =
                findEndpointEvidenceRowsByApplicationId(
                        application.id(),
                        dashboardWindow.current().startUtc(),
                        dashboardWindow.current().endUtc(),
                        bucketQueryContext);
        Optional<AcceptedBucketGapEvidence> bucketGapEvidence =
                findAcceptedBucketGapEvidenceByApplicationIdAtOrBefore(
                        application.id(),
                        evaluationAt,
                        bucketQueryContext);
        List<RecentBucketEvidenceRow> recentBuckets =
                findRecentFiveBucketEvidenceRowsByApplicationIdAtOrBefore(
                        application.id(),
                        evaluationAt,
                        bucketQueryContext);
        Optional<RuntimeRatioEvidenceRow> runtimeRatio =
                findLatestRuntimeRatioEvidenceRowByApplicationId(
                        application.id(),
                        dashboardWindow.current().startUtc(),
                        dashboardWindow.current().endUtc(),
                        bucketQueryContext);
        Optional<StarterHeartbeatTelemetryRecord> latestHeartbeat = findLatestHeartbeat(application, queryAt, bucketQueryContext);

        AcceptedBucketFreshness freshness = freshnessEvaluator.evaluateAt(
                evaluationAt,
                latestBucketEndUtc.map(OffsetDateTime::toInstant).orElse(null));
        MetricSampleReadiness sampleReadiness = sampleReadiness(aggregate);
        MetricTrafficActivity trafficActivity = trafficActivity(aggregate);
        StarterConnectionInput starterConnectionInput = starterConnectionInput(latestHeartbeat, queryAt);
        ApplicationDashboardReadModel.SourceScopedPercentiles sourceScopedPercentiles =
                sourceScopedPercentiles(application, currentPercentileRows);
        ApplicationDashboardReadModel.HistogramDistribution histogramDistribution =
                histogramDistribution(currentHistogramRows);
        TriageSummary triageSummary = triageSummaryService.summarize(new TriageSummaryInput(
                aggregate,
                histogramDistribution,
                sourceScopedPercentiles,
                recentBuckets,
                runtimeRatio,
                freshness.status()));
        LifecycleStateDecision stateDecision = lifecycleStateService.decide(
                metricLifecycleInput(
                        freshness,
                        sampleReadiness,
                        trafficActivity,
                        triageSummary.degradedInput(),
                        previousStateCandidate(freshness, bucketGapEvidence)),
                starterConnectionInput);
        List<ApplicationDashboardReadModel.EndpointPriorityItem> endpointPriority =
                endpointPriority(freshness.status(), currentEndpointRows, latestBucketEndUtc);

        return new ApplicationDashboardReadModel(
                toUtcOffsetDateTime(queryAt),
                application(application, latestBucketEndUtc, dashboardWindow),
                state(stateDecision),
                starterConnection(stateDecision.starterConnection()),
                zeroInsight(
                        triageSummary.triageCards(),
                        endpointPriority,
                        stateDecision.recovery(),
                        freshness.status(),
                        starterConnectionInput.freshness(),
                        sampleReadiness,
                        trafficActivity),
                recovery(stateDecision.recovery()),
                metrics(aggregate),
                sourceScopedPercentiles,
                histogramDistribution,
                triageSummary.triageCards(),
                endpointPriority,
                instanceEntries(application, queryAt, dashboardWindow, bucketQueryContext),
                runtimeRatioEvidence(runtimeRatio),
                null);
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

    private Optional<OffsetDateTime> findLatestBucketEndUtcByApplicationId(
            UUID applicationId,
            Instant evaluationAt,
            BucketQueryContext bucketQueryContext) {
        return bucketQueryContext.acceptedAtCutoffUtc()
                .map(cutoff -> metricBucketRepository.findLatestBucketEndUtcByApplicationIdAtOrBeforeAcceptedAt(
                        applicationId,
                        evaluationAt,
                        cutoff))
                .orElseGet(() -> metricBucketRepository.findLatestBucketEndUtcByApplicationIdAtOrBefore(
                        applicationId,
                        evaluationAt));
    }

    private WindowBucketAggregate findWindowAggregateByApplicationId(
            UUID applicationId,
            Instant windowStartUtc,
            Instant windowEndUtc,
            BucketQueryContext bucketQueryContext) {
        return bucketQueryContext.acceptedAtCutoffUtc()
                .map(cutoff -> metricBucketRepository.findWindowAggregateByApplicationIdAcceptedAtOrBefore(
                        applicationId,
                        windowStartUtc,
                        windowEndUtc,
                        cutoff))
                .orElseGet(() -> metricBucketRepository.findWindowAggregateByApplicationId(
                        applicationId,
                        windowStartUtc,
                        windowEndUtc));
    }

    private List<LocalPercentileEvidenceRow> findLocalPercentileEvidenceRowsByApplicationId(
            UUID applicationId,
            Instant windowStartUtc,
            Instant windowEndUtc,
            BucketQueryContext bucketQueryContext) {
        return bucketQueryContext.acceptedAtCutoffUtc()
                .map(cutoff -> metricBucketRepository.findLocalPercentileEvidenceRowsByApplicationIdAcceptedAtOrBefore(
                        applicationId,
                        windowStartUtc,
                        windowEndUtc,
                        cutoff))
                .orElseGet(() -> metricBucketRepository.findLocalPercentileEvidenceRowsByApplicationId(
                        applicationId,
                        windowStartUtc,
                        windowEndUtc));
    }

    private List<HistogramBucketEvidenceRow> findSummaryDurationBucketEvidenceRowsByApplicationId(
            UUID applicationId,
            Instant windowStartUtc,
            Instant windowEndUtc,
            BucketQueryContext bucketQueryContext) {
        return bucketQueryContext.acceptedAtCutoffUtc()
                .map(cutoff -> metricBucketRepository
                        .findSummaryDurationBucketEvidenceRowsByApplicationIdAcceptedAtOrBefore(
                                applicationId,
                                windowStartUtc,
                                windowEndUtc,
                                cutoff))
                .orElseGet(() -> metricBucketRepository.findSummaryDurationBucketEvidenceRowsByApplicationId(
                        applicationId,
                        windowStartUtc,
                        windowEndUtc));
    }

    private List<EndpointEvidenceRow> findEndpointEvidenceRowsByApplicationId(
            UUID applicationId,
            Instant windowStartUtc,
            Instant windowEndUtc,
            BucketQueryContext bucketQueryContext) {
        return bucketQueryContext.acceptedAtCutoffUtc()
                .map(cutoff -> metricBucketRepository.findEndpointEvidenceRowsByApplicationIdAcceptedAtOrBefore(
                        applicationId,
                        windowStartUtc,
                        windowEndUtc,
                        cutoff))
                .orElseGet(() -> metricBucketRepository.findEndpointEvidenceRowsByApplicationId(
                        applicationId,
                        windowStartUtc,
                        windowEndUtc));
    }

    private Optional<AcceptedBucketGapEvidence> findAcceptedBucketGapEvidenceByApplicationIdAtOrBefore(
            UUID applicationId,
            Instant evaluationAt,
            BucketQueryContext bucketQueryContext) {
        return bucketQueryContext.acceptedAtCutoffUtc()
                .map(cutoff -> metricBucketRepository.findAcceptedBucketGapEvidenceByApplicationIdAtOrBeforeAcceptedAt(
                        applicationId,
                        evaluationAt,
                        cutoff))
                .orElseGet(() -> metricBucketRepository.findAcceptedBucketGapEvidenceByApplicationIdAtOrBefore(
                        applicationId,
                        evaluationAt));
    }

    private List<RecentBucketEvidenceRow> findRecentFiveBucketEvidenceRowsByApplicationIdAtOrBefore(
            UUID applicationId,
            Instant evaluationAt,
            BucketQueryContext bucketQueryContext) {
        return bucketQueryContext.acceptedAtCutoffUtc()
                .map(cutoff -> metricBucketRepository
                        .findRecentFiveBucketEvidenceRowsByApplicationIdAtOrBeforeAcceptedAt(
                                applicationId,
                                evaluationAt,
                                cutoff))
                .orElseGet(() -> metricBucketRepository.findRecentFiveBucketEvidenceRowsByApplicationIdAtOrBefore(
                        applicationId,
                        evaluationAt));
    }

    private Optional<RuntimeRatioEvidenceRow> findLatestRuntimeRatioEvidenceRowByApplicationId(
            UUID applicationId,
            Instant windowStartUtc,
            Instant windowEndUtc,
            BucketQueryContext bucketQueryContext) {
        return bucketQueryContext.acceptedAtCutoffUtc()
                .map(cutoff -> metricBucketRepository
                        .findLatestRuntimeRatioEvidenceRowByApplicationIdAcceptedAtOrBefore(
                                applicationId,
                                windowStartUtc,
                                windowEndUtc,
                                cutoff))
                .orElseGet(() -> metricBucketRepository.findLatestRuntimeRatioEvidenceRowByApplicationId(
                        applicationId,
                        windowStartUtc,
                        windowEndUtc));
    }

    private Optional<StarterHeartbeatTelemetryRecord> findLatestHeartbeat(
            ApplicationEntity application,
            Instant queryAt,
            BucketQueryContext bucketQueryContext) {
        return bucketQueryContext.acceptedAtCutoffUtc()
                .map(ignored -> heartbeatTelemetryRepository.findLatestByApplicationScopeAtOrBeforeReceivedAt(
                        application.projectId(),
                        application.name(),
                        application.environment(),
                        toUtcOffsetDateTime(queryAt)))
                .orElseGet(() -> heartbeatTelemetryRepository.findLatestByApplicationScope(
                        application.projectId(),
                        application.name(),
                        application.environment()));
    }

    /**
     * Application Dashboard에서 selected instance evidence API로 이동할 수 있는 최대 50개 entry를 만든다.
     *
     * <p>row summary는 SoT Instance Summary가 frontend 재계산 없이 표시할 수 있는 request/slow/heartbeat/contribution
     * scalar만 포함한다. instance lifecycle state, health score, endpoint priority, percentile summary는 만들지 않는다.</p>
     */
    private List<ApplicationDashboardReadModel.InstanceEntry> instanceEntries(
            ApplicationEntity application,
            Instant queryAt,
            DashboardTimeWindow dashboardWindow,
            BucketQueryContext bucketQueryContext) {
        List<ApplicationInstanceEntity> instances = Objects.requireNonNullElse(
                applicationInstanceRepository.findByApplicationIdOrderByLastSeenAtDescInstanceNameAsc(
                        application.id(),
                        PageRequest.of(0, INSTANCE_ENTRY_LIMIT)),
                List.of());
        return instances.stream()
                .limit(INSTANCE_ENTRY_LIMIT)
                .map(instance -> new ApplicationDashboardReadModel.InstanceEntry(
                        instance.id(),
                        instance.instanceName(),
                        instance.lastSeenAt(),
                        instanceEntrySummary(
                                application,
                                instance,
                                queryAt,
                                dashboardWindow,
                                bucketQueryContext),
                        new ApplicationDashboardReadModel.InstanceEntryLinks(
                                InstanceEvidenceReadModelService.evidenceLink(
                                        application.projectId(),
                                        application.id(),
                                        instance.id()))))
                .toList();
    }

    /**
     * Instance Summary row가 필요한 서버 산출 값을 current dashboard window 기준으로 축약한다.
     */
    private ApplicationDashboardReadModel.InstanceEntrySummary instanceEntrySummary(
            ApplicationEntity application,
            ApplicationInstanceEntity instance,
            Instant queryAt,
            DashboardTimeWindow dashboardWindow,
            BucketQueryContext bucketQueryContext) {
        UtcTimeInterval currentWindow = dashboardWindow.current();
        Optional<OffsetDateTime> latestBucketEnd = findLatestBucketEndUtcByApplicationInstanceIdAtOrBefore(
                instance.id(),
                currentWindow.endUtc(),
                bucketQueryContext);
        WindowBucketAggregate aggregate = findWindowAggregateByApplicationInstanceId(
                instance.id(),
                currentWindow.startUtc(),
                currentWindow.endUtc(),
                bucketQueryContext);
        InstanceEntrySlowEvidence slowEvidence = instanceEntrySlowEvidence(
                findSummaryDurationBucketEvidenceRowsByApplicationInstanceId(
                        instance.id(),
                        currentWindow.startUtc(),
                        currentWindow.endUtc(),
                        bucketQueryContext));
        Optional<StarterHeartbeatTelemetryRecord> heartbeat = findLatestHeartbeat(
                application,
                instance,
                queryAt,
                bucketQueryContext);
        ApplicationDashboardReadModel.InstanceEntryObservationStatus observationStatus = instanceEntryObservationStatus(
                latestBucketEnd,
                toUtcOffsetDateTime(currentWindow.startUtc()),
                toUtcOffsetDateTime(currentWindow.endUtc()),
                bucketQueryContext);
        ApplicationDashboardReadModel.InstanceEntryRedSignals redSignals =
                new ApplicationDashboardReadModel.InstanceEntryRedSignals(
                        aggregate.requestCount(),
                        slowEvidence.slowCountOver500ms(),
                        slowEvidence.slowShareOver500ms());
        return new ApplicationDashboardReadModel.InstanceEntrySummary(
                observationStatus,
                instanceEntryStarterConnection(heartbeat),
                redSignals,
                instanceEntryContribution(observationStatus, aggregate, slowEvidence));
    }

    private Optional<OffsetDateTime> findLatestBucketEndUtcByApplicationInstanceIdAtOrBefore(
            UUID applicationInstanceId,
            Instant evaluationAt,
            BucketQueryContext bucketQueryContext) {
        return bucketQueryContext.acceptedAtCutoffUtc()
                .map(cutoff -> metricBucketRepository.findLatestBucketEndUtcByApplicationInstanceIdAtOrBeforeAcceptedAt(
                        applicationInstanceId,
                        evaluationAt,
                        cutoff))
                .orElseGet(() -> metricBucketRepository.findLatestBucketEndUtcByApplicationInstanceIdAtOrBefore(
                        applicationInstanceId,
                        evaluationAt));
    }

    private WindowBucketAggregate findWindowAggregateByApplicationInstanceId(
            UUID applicationInstanceId,
            Instant windowStartUtc,
            Instant windowEndUtc,
            BucketQueryContext bucketQueryContext) {
        return bucketQueryContext.acceptedAtCutoffUtc()
                .map(cutoff -> metricBucketRepository.findWindowAggregateByApplicationInstanceIdAcceptedAtOrBefore(
                        applicationInstanceId,
                        windowStartUtc,
                        windowEndUtc,
                        cutoff))
                .orElseGet(() -> metricBucketRepository.findWindowAggregateByApplicationInstanceId(
                        applicationInstanceId,
                        windowStartUtc,
                        windowEndUtc));
    }

    private List<HistogramBucketEvidenceRow> findSummaryDurationBucketEvidenceRowsByApplicationInstanceId(
            UUID applicationInstanceId,
            Instant windowStartUtc,
            Instant windowEndUtc,
            BucketQueryContext bucketQueryContext) {
        return bucketQueryContext.acceptedAtCutoffUtc()
                .map(cutoff -> metricBucketRepository
                        .findSummaryDurationBucketEvidenceRowsByApplicationInstanceIdAcceptedAtOrBefore(
                                applicationInstanceId,
                                windowStartUtc,
                                windowEndUtc,
                                cutoff))
                .orElseGet(() -> metricBucketRepository.findSummaryDurationBucketEvidenceRowsByApplicationInstanceId(
                        applicationInstanceId,
                        windowStartUtc,
                        windowEndUtc));
    }

    private Optional<StarterHeartbeatTelemetryRecord> findLatestHeartbeat(
            ApplicationEntity application,
            ApplicationInstanceEntity instance,
            Instant queryAt,
            BucketQueryContext bucketQueryContext) {
        return bucketQueryContext.acceptedAtCutoffUtc()
                .map(ignored -> heartbeatTelemetryRepository.findByIdentityAtOrBeforeReceivedAt(
                        application.projectId(),
                        application.name(),
                        application.environment(),
                        instance.instanceName(),
                        toUtcOffsetDateTime(queryAt)))
                .orElseGet(() -> heartbeatTelemetryRepository.findByIdentity(
                        application.projectId(),
                        application.name(),
                        application.environment(),
                        instance.instanceName()));
    }

    private ApplicationDashboardReadModel.InstanceEntryObservationStatus instanceEntryObservationStatus(
            Optional<OffsetDateTime> latestBucketEnd,
            OffsetDateTime windowStart,
            OffsetDateTime windowEnd,
            BucketQueryContext bucketQueryContext) {
        String windowSource = bucketQueryContext.acceptedAtCutoffUtc().isPresent()
                ? "selected_application_snapshot"
                : "live_recent_30_minutes";
        if (latestBucketEnd.isEmpty()) {
            return new ApplicationDashboardReadModel.InstanceEntryObservationStatus(
                    "metric_missing",
                    "selected_application_snapshot".equals(windowSource)
                            ? "no_metric_bucket_for_selected_snapshot_window"
                            : "no_metric_bucket_for_live_window",
                    null);
        }
        OffsetDateTime lastObserved = latestBucketEnd.orElseThrow();
        boolean observedInWindow = lastObserved.isAfter(windowStart) && !lastObserved.isAfter(windowEnd);
        if (!observedInWindow) {
            return new ApplicationDashboardReadModel.InstanceEntryObservationStatus(
                    "not_observed_in_window",
                    "latest_metric_bucket_outside_selected_window",
                    lastObserved);
        }
        return new ApplicationDashboardReadModel.InstanceEntryObservationStatus(
                "observed",
                "selected_instance_metric_bucket_observed",
                lastObserved);
    }

    private static ApplicationDashboardReadModel.InstanceEntryStarterConnection instanceEntryStarterConnection(
            Optional<StarterHeartbeatTelemetryRecord> heartbeat) {
        if (heartbeat.isEmpty()) {
            return ApplicationDashboardReadModel.InstanceEntryStarterConnection.missing();
        }
        StarterHeartbeatTelemetryRecord record = heartbeat.orElseThrow();
        return new ApplicationDashboardReadModel.InstanceEntryStarterConnection(
                record.lastReceivedAtUtc(),
                normalize(record.heartbeatStatus(), "unknown"),
                "observed");
    }

    private static ApplicationDashboardReadModel.InstanceEntryApplicationContribution instanceEntryContribution(
            ApplicationDashboardReadModel.InstanceEntryObservationStatus observationStatus,
            WindowBucketAggregate aggregate,
            InstanceEntrySlowEvidence slowEvidence) {
        if (!"observed".equals(observationStatus.code())) {
            return new ApplicationDashboardReadModel.InstanceEntryApplicationContribution(
                    "insufficient",
                    "selected_instance_evidence_not_observed");
        }
        boolean errorSymptom = aggregate.errorCount() > 0L;
        boolean slowSymptom = slowEvidence.slowCountOver500ms() != null && slowEvidence.slowCountOver500ms() > 0L;
        if (errorSymptom || slowSymptom) {
            return new ApplicationDashboardReadModel.InstanceEntryApplicationContribution(
                    "attention",
                    "request_symptom_observed_without_root_cause_claim");
        }
        return new ApplicationDashboardReadModel.InstanceEntryApplicationContribution(
                "supporting",
                "observed_without_request_symptom");
    }

    private InstanceEntrySlowEvidence instanceEntrySlowEvidence(List<HistogramBucketEvidenceRow> rows) {
        ApplicationDashboardReadModel.HistogramWindow window = histogramWindow(rows, "instance_summary");
        if (!"available".equals(window.status()) || window.totalCount() <= 0L) {
            return new InstanceEntrySlowEvidence(null, null);
        }
        return window.buckets().stream()
                .filter(bucket -> bucket.leMs() == LATENCY_SLOW_BUCKET_LE_MS)
                .findFirst()
                .map(bucket -> {
                    long slowCount = Math.max(0L, window.totalCount() - bucket.count());
                    return new InstanceEntrySlowEvidence(slowCount, errorRate(slowCount, window.totalCount()));
                })
                .orElseGet(() -> new InstanceEntrySlowEvidence(null, null));
    }

    /**
     * application state sample이 부족해도 current endpoint 오류 후보는 숨기지 않고 priority service로 전달한다.
     */
    private List<ApplicationDashboardReadModel.EndpointPriorityItem> endpointPriority(
            AcceptedBucketFreshnessStatus freshnessStatus,
            List<EndpointEvidenceRow> currentEndpointRows,
            Optional<OffsetDateTime> latestBucketEndUtc) {
        return endpointPriorityService.endpointPriority(new EndpointPriorityService.EndpointPriorityInput(
                freshnessStatus,
                currentEndpointRows,
                List.of(),
                latestBucketEndUtc));
    }

    /**
     * persisted starter_local percentile point 중 recent 30분 window의 instance별 latest valid item만 선택한다.
     */
    private ApplicationDashboardReadModel.SourceScopedPercentiles sourceScopedPercentiles(
            ApplicationEntity application,
            List<LocalPercentileEvidenceRow> rows) {
        List<LocalPercentileEvidenceRow> evidenceRows = List.copyOf(Objects.requireNonNullElse(rows, List.of()));
        if (evidenceRows.isEmpty()) {
            return ApplicationDashboardReadModel.SourceScopedPercentiles.empty();
        }

        Map<UUID, ApplicationDashboardReadModel.PercentileItem> latestByInstance = new LinkedHashMap<>();
        for (LocalPercentileEvidenceRow row : evidenceRows) {
            toValidPercentileItem(application, row)
                    .ifPresent(item -> latestByInstance.merge(
                            row.applicationInstanceId(),
                            item,
                            DashboardReadModelService::latestPercentileItem));
        }
        if (latestByInstance.isEmpty()) {
            return ApplicationDashboardReadModel.SourceScopedPercentiles.insufficient(
                    "no_valid_percentile_points_in_recent_30_minutes");
        }

        List<ApplicationDashboardReadModel.PercentileItem> items = latestByInstance.values().stream()
                .sorted(Comparator.comparing(ApplicationDashboardReadModel.PercentileItem::instance)
                        .thenComparing(ApplicationDashboardReadModel.PercentileItem::bucketEndUtc))
                .toList();
        return ApplicationDashboardReadModel.SourceScopedPercentiles.available(items);
    }

    /**
     * local_percentiles_json의 source/scope와 persisted bucket boundary를 검증하고 API item으로 변환한다.
     */
    private Optional<ApplicationDashboardReadModel.PercentileItem> toValidPercentileItem(
            ApplicationEntity application,
            LocalPercentileEvidenceRow row) {
        return parseLocalPercentiles(row.localPercentilesJson())
                .filter(percentiles -> INSTANCE_BUCKET_SCOPE.equals(percentiles.scope()))
                .filter(percentiles -> STARTER_LOCAL_SOURCE.equals(percentiles.source()))
                .filter(percentiles -> Boolean.FALSE.equals(percentiles.mergeable()))
                .filter(percentiles -> percentiles.requestCount() != null && percentiles.requestCount() > 0L)
                .filter(percentiles -> percentiles.p95Ms() != null && percentiles.p95Ms() >= 0L)
                .filter(percentiles -> percentiles.p99Ms() != null && percentiles.p99Ms() >= percentiles.p95Ms())
                .filter(percentiles -> matchesBoundary(percentiles, row))
                .map(percentiles -> new ApplicationDashboardReadModel.PercentileItem(
                        STARTER_CANONICAL_PERCENTILE_SOURCE,
                        application.name(),
                        application.environment(),
                        row.instanceName(),
                        row.bucketStartUtc(),
                        row.bucketEndUtc(),
                        percentiles.requestCount(),
                        percentiles.p95Ms(),
                        percentiles.p99Ms()));
    }

    private static ApplicationDashboardReadModel.PercentileItem latestPercentileItem(
            ApplicationDashboardReadModel.PercentileItem first,
            ApplicationDashboardReadModel.PercentileItem second) {
        return second.bucketEndUtc().isAfter(first.bucketEndUtc()) ? second : first;
    }

    /**
     * recent 30분 histogram evidence를 display source로 조립하고, baseline 비교 판단은 만들지 않는다.
     */
    private ApplicationDashboardReadModel.HistogramDistribution histogramDistribution(
            List<HistogramBucketEvidenceRow> currentRows) {
        return new ApplicationDashboardReadModel.HistogramDistribution(
                ACCEPTED_BUCKET_SOURCE,
                "application",
                "cumulative_bucket_distribution",
                "display_bucket_only_no_percentile_recalculation",
                histogramWindow(currentRows, "recent_30_minutes"),
                ApplicationDashboardReadModel.HistogramWindow.unavailable(BASELINE_NOT_USED_REASON));
    }

    /**
     * 하나의 recent 30분 window 안에서 boundary set이 모두 일치할 때만 cumulative count를 boundary별로 합산한다.
     */
    private ApplicationDashboardReadModel.HistogramWindow histogramWindow(
            List<HistogramBucketEvidenceRow> rows,
            String windowName) {
        List<HistogramBucketEvidenceRow> evidenceRows = List.copyOf(Objects.requireNonNullElse(rows, List.of()));
        if (evidenceRows.isEmpty()) {
            return ApplicationDashboardReadModel.HistogramWindow.missing(
                    "no_histogram_buckets_in_" + windowName);
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
        long totalCount = buckets.get(buckets.size() - 1).count();
        return ApplicationDashboardReadModel.HistogramWindow.available(totalCount, buckets);
    }

    /**
     * persisted local_percentiles_json을 lenient하게 읽어 invalid evidence를 response item에서 제외할 수 있게 한다.
     */
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

    /**
     * persisted duration_buckets_json 배열을 histogram merge 전 중립 bucket 목록으로 변환한다.
     */
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

    /**
     * 한 row 안의 histogram boundary를 정렬하고 중복 boundary가 있으면 invalid evidence로 취급한다.
     */
    private static List<ParsedHistogramBucket> sortedUniqueBuckets(List<ParsedHistogramBucket> buckets) {
        List<ParsedHistogramBucket> sortedBuckets = buckets.stream()
                .sorted(Comparator.comparingLong(ParsedHistogramBucket::leMs))
                .toList();
        Long previousBoundary = null;
        for (ParsedHistogramBucket bucket : sortedBuckets) {
            if (Objects.equals(previousBoundary, bucket.leMs())) {
                return List.of();
            }
            previousBoundary = bucket.leMs();
        }
        return sortedBuckets;
    }

    /**
     * local percentile JSON에 적힌 bucket boundary가 persisted bucket boundary와 같은 instant인지 확인한다.
     */
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

    private static String textValue(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private static Long longValue(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        return value != null && value.canConvertToLong() ? value.asLong() : null;
    }

    private static Boolean booleanValue(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        return value != null && value.isBoolean() ? value.asBoolean() : null;
    }

    private Instant floorToBucketBoundary(Instant queryAt) {
        return timeBucketWindowCalculator.bucketContaining(queryAt).startUtc();
    }

    /**
     * triage summary가 계산한 degraded 입력과 accepted bucket gap 기반 previous state 후보를 lifecycle 판단에 연결한다.
     */
    private MetricLifecycleInput metricLifecycleInput(
            AcceptedBucketFreshness freshness,
            MetricSampleReadiness sampleReadiness,
            MetricTrafficActivity trafficActivity,
            DegradedHysteresisInput degradedInput,
            Optional<LifecycleStateCode> previousState) {
        return new MetricLifecycleInput(
                freshness,
                sampleReadiness,
                trafficActivity,
                degradedInput,
                previousState,
                Optional.empty());
    }

    /**
     * Story 5.4의 lightweight recovery source인 latest/current bucket 직전 gap으로 previous stale/down 후보를 만든다.
     *
     * <p>snapshot/previous read model source가 없는 동안 lastHealthyAt은 만들지 않으며, Story 5.8에서 이 fallback과
     * snapshot source 우선순위를 다시 닫는다.</p>
     */
    private static Optional<LifecycleStateCode> previousStateCandidate(
            AcceptedBucketFreshness freshness,
            Optional<AcceptedBucketGapEvidence> bucketGapEvidence) {
        if (freshness.status() != AcceptedBucketFreshnessStatus.CURRENT || bucketGapEvidence.isEmpty()) {
            return Optional.empty();
        }
        AcceptedBucketGapEvidence evidence = bucketGapEvidence.orElseThrow();
        if (evidence.previousBucketEndUtc().isEmpty()) {
            return Optional.empty();
        }
        Duration gap = Duration.between(
                evidence.previousBucketEndUtc().orElseThrow().toInstant(),
                evidence.latestBucketEndUtc().toInstant());
        if (gap.compareTo(AcceptedBucketFreshnessEvaluator.DOWN_AFTER) >= 0) {
            return Optional.of(LifecycleStateCode.DOWN);
        }
        if (gap.compareTo(AcceptedBucketFreshnessEvaluator.STALE_AFTER) >= 0) {
            return Optional.of(LifecycleStateCode.STALE);
        }
        return Optional.empty();
    }

    /**
     * latest heartbeat row를 LifecycleStateService가 소비하는 starter connection typed input으로 변환한다.
     */
    private StarterConnectionInput starterConnectionInput(
            Optional<StarterHeartbeatTelemetryRecord> latestHeartbeat,
            Instant queryAt) {
        if (latestHeartbeat.isEmpty()) {
            return StarterConnectionInput.missing();
        }

        StarterHeartbeatTelemetryRecord heartbeat = latestHeartbeat.orElseThrow();
        Instant lastReceivedAt = heartbeat.lastReceivedAtUtc().toInstant();
        StarterHeartbeatStatus heartbeatStatus = heartbeatStatus(heartbeat.heartbeatStatus());
        boolean recentReceivedHeartbeat = heartbeatStatus == StarterHeartbeatStatus.RECEIVED
                && !lastReceivedAt.isAfter(queryAt)
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

    /**
     * heartbeat status 문자열을 starter connection 축의 제한된 enum으로 정규화한다.
     */
    private static StarterHeartbeatStatus heartbeatStatus(String heartbeatStatus) {
        String normalizedStatus = heartbeatStatus == null ? "" : heartbeatStatus.trim().toLowerCase(Locale.ROOT);
        return switch (normalizedStatus) {
            case "received" -> StarterHeartbeatStatus.RECEIVED;
            case "failed" -> StarterHeartbeatStatus.FAILED;
            default -> StarterHeartbeatStatus.UNKNOWN;
        };
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * current window 요청 수로 sample 부족과 idle traffic을 분리해 lifecycle input을 만든다.
     */
    private static MetricSampleReadiness sampleReadiness(WindowBucketAggregate aggregate) {
        long requestCount = aggregate.requestCount();
        if (requestCount == 0L || requestCount >= MINIMUM_ACTIVE_SAMPLE_REQUEST_COUNT) {
            return MetricSampleReadiness.SUFFICIENT;
        }
        return MetricSampleReadiness.INSUFFICIENT;
    }

    /**
     * requestCount가 0인 window를 "오류 없음"이 아니라 traffic idle 축으로 전달한다.
     */
    private static MetricTrafficActivity trafficActivity(WindowBucketAggregate aggregate) {
        return aggregate.requestCount() == 0L ? MetricTrafficActivity.IDLE : MetricTrafficActivity.ACTIVE;
    }

    private static ApplicationDashboardReadModel.Application application(
            ApplicationEntity application,
            Optional<OffsetDateTime> latestBucketEndUtc,
            DashboardTimeWindow dashboardWindow) {
        return new ApplicationDashboardReadModel.Application(
                application.projectId(),
                application.id(),
                application.name(),
                application.environment(),
                latestBucketEndUtc.orElse(null),
                null,
                sourceWindow(dashboardWindow),
                freshness(latestBucketEndUtc));
    }

    private static ApplicationDashboardReadModel.SourceWindow sourceWindow(DashboardTimeWindow dashboardWindow) {
        return new ApplicationDashboardReadModel.SourceWindow(
                window(dashboardWindow.current()),
                null);
    }

    private static ApplicationDashboardReadModel.Window window(UtcTimeInterval interval) {
        return new ApplicationDashboardReadModel.Window(
                toUtcOffsetDateTime(interval.startUtc()),
                toUtcOffsetDateTime(interval.endUtc()));
    }

    private static ApplicationDashboardReadModel.Freshness freshness(Optional<OffsetDateTime> latestBucketEndUtc) {
        return latestBucketEndUtc
                .map(lastObservedAt -> new ApplicationDashboardReadModel.Freshness(
                        lastObservedAt,
                        lastObservedAt.plus(AcceptedBucketFreshnessEvaluator.STALE_AFTER),
                        lastObservedAt.plus(AcceptedBucketFreshnessEvaluator.DOWN_AFTER)))
                .orElseGet(() -> new ApplicationDashboardReadModel.Freshness(null, null, null));
    }

    private static ApplicationDashboardReadModel.State state(LifecycleStateDecision stateDecision) {
        return new ApplicationDashboardReadModel.State(
                stateDecision.metricState().code().code(),
                stateDecision.metricState().label(),
                stateDecision.metricState().rationale(),
                stateDecision.metricState().recommendedAction(),
                APPLICATION_STATE_SCOPE);
    }

    private static ApplicationDashboardReadModel.StarterConnection starterConnection(
            StarterConnectionSummary starterConnection) {
        return new ApplicationDashboardReadModel.StarterConnection(
                starterConnection.statusSource(),
                starterConnection.lastHeartbeatAt()
                        .map(DashboardReadModelService::toUtcOffsetDateTime)
                        .orElse(null),
                heartbeatStatusCode(starterConnection.lastHeartbeatStatus()),
                starterMeaningCode(starterConnection.meaning()),
                starterConnection.stateImpact().code());
    }

    private static String heartbeatStatusCode(StarterHeartbeatStatus heartbeatStatus) {
        return heartbeatStatus.name().toLowerCase(Locale.ROOT);
    }

    private static String starterMeaningCode(StarterConnectionMeaning meaning) {
        return meaning.name().toLowerCase(Locale.ROOT);
    }

    private static ApplicationDashboardReadModel.ZeroInsight zeroInsight(
            List<ApplicationDashboardReadModel.TriageCard> triageCards,
            List<ApplicationDashboardReadModel.EndpointPriorityItem> endpointPriority,
            RecoveryGuidance recovery,
            AcceptedBucketFreshnessStatus freshnessStatus,
            StarterConnectionFreshness starterFreshness,
            MetricSampleReadiness sampleReadiness,
            MetricTrafficActivity trafficActivity) {
        if (!triageCards.isEmpty()) {
            return null;
        }
        String reasonCode = zeroInsightReasonCode(
                recovery,
                freshnessStatus,
                starterFreshness,
                sampleReadiness,
                trafficActivity);
        return switch (reasonCode) {
            case "waiting_first_data" -> new ApplicationDashboardReadModel.ZeroInsight(
                    reasonCode,
                    "아직 accepted metric bucket이 없어 첫 데이터를 기다리고 있습니다.",
                    "Starter heartbeat가 최근 수신됐으므로 요청 traffic 발생 후 bucket 수용 여부를 확인하세요.");
            case "observing_recovery" -> new ApplicationDashboardReadModel.ZeroInsight(
                    reasonCode,
                    "새 accepted bucket이 들어왔지만 회복 여부를 판단할 sample은 아직 부족합니다.",
                    "회복 여부를 확정하지 말고 다음 bucket까지 accepted bucket 수용과 sample 증가를 관찰하세요.");
            case "telemetry_unreachable" -> new ApplicationDashboardReadModel.ZeroInsight(
                    reasonCode,
                    "Starter heartbeat와 metric data freshness가 모두 최근 상태가 아닙니다.",
                    "Starter 설정, project key, portal/network 연결을 확인하되 host application 상태를 이 신호만으로 확정하지 마세요.");
            case "insufficient_sample" -> new ApplicationDashboardReadModel.ZeroInsight(
                    reasonCode,
                    "현재 window의 요청 sample이 lifecycle 판단 기준보다 적습니다.",
                    "다음 bucket까지 더 많은 요청 sample을 관찰하세요.");
            case "metric_data_idle" -> new ApplicationDashboardReadModel.ZeroInsight(
                    reasonCode,
                    "현재 window에서 우선 조치가 필요한 metric data traffic이 없습니다.",
                    "요청이 다시 들어오면 다음 accepted bucket 이후 dashboard를 확인하세요.");
            case "no_action_needed" -> zeroInsightNoActionNeeded(endpointPriority);
            default -> throw new IllegalStateException("unsupported zeroInsight reasonCode: " + reasonCode);
        };
    }

    /**
     * application-level triage가 없더라도 endpoint priority가 있으면 zeroInsight copy가 이를 부정하지 않게 한다.
     */
    private static ApplicationDashboardReadModel.ZeroInsight zeroInsightNoActionNeeded(
            List<ApplicationDashboardReadModel.EndpointPriorityItem> endpointPriority) {
        if (!endpointPriority.isEmpty()) {
            return new ApplicationDashboardReadModel.ZeroInsight(
                    "no_action_needed",
                    "Application 단위 triage card는 없습니다.",
                    "낮은 우선순위 endpoint 후보가 있으면 해당 API의 최근 오류 로그를 함께 확인하세요.");
        }
        return new ApplicationDashboardReadModel.ZeroInsight(
                "no_action_needed",
                "현재 우선 조치가 필요한 신호는 없습니다.",
                "트래픽이 유지되는지 다음 bucket까지 관찰하세요.");
    }

    /**
     * Story 5.4에서 triageCards가 비어 있을 때만 사용하는 zeroInsight precedence mapping이다.
     */
    private static String zeroInsightReasonCode(
            RecoveryGuidance recovery,
            AcceptedBucketFreshnessStatus freshnessStatus,
            StarterConnectionFreshness starterFreshness,
            MetricSampleReadiness sampleReadiness,
            MetricTrafficActivity trafficActivity) {
        boolean recentHeartbeat = starterFreshness == StarterConnectionFreshness.RECENT;
        if (recovery.isRecovering()) {
            return "observing_recovery";
        }
        if (freshnessStatus == AcceptedBucketFreshnessStatus.WAITING_FIRST_DATA) {
            return recentHeartbeat ? "waiting_first_data" : "telemetry_unreachable";
        }
        if (freshnessStatus == AcceptedBucketFreshnessStatus.STALE_CANDIDATE
                || freshnessStatus == AcceptedBucketFreshnessStatus.DOWN_CANDIDATE) {
            return recentHeartbeat ? "metric_data_idle" : "telemetry_unreachable";
        }
        if (sampleReadiness == MetricSampleReadiness.INSUFFICIENT) {
            return "insufficient_sample";
        }
        if (trafficActivity == MetricTrafficActivity.IDLE) {
            return "metric_data_idle";
        }
        return "no_action_needed";
    }

    private static ApplicationDashboardReadModel.Recovery recovery(RecoveryGuidance recovery) {
        return new ApplicationDashboardReadModel.Recovery(
                recovery.isRecovering(),
                recovery.lastHealthyAt()
                        .map(DashboardReadModelService::toUtcOffsetDateTime)
                        .orElse(null),
                recovery.retryAfterSeconds().orElse(null),
                recovery.recommendedAction().orElse(null));
    }

    private static ApplicationDashboardReadModel.Metrics metrics(WindowBucketAggregate aggregate) {
        BigDecimal errorRate = aggregate.requestCount() == 0L
                ? null
                : errorRate(aggregate.errorCount(), aggregate.requestCount());
        return new ApplicationDashboardReadModel.Metrics(
                aggregate.requestCount(),
                aggregate.errorCount(),
                errorRate);
    }

    /**
     * errorCount/requestCount 의미를 보존하되 작은 non-zero 비율이 0으로 반올림되지 않도록 scale을 정한다.
     */
    private static BigDecimal errorRate(long errorCount, long requestCount) {
        int scale = Math.max(6, Long.toString(requestCount).length() + 1);
        return BigDecimal.valueOf(errorCount)
                .divide(BigDecimal.valueOf(requestCount), scale, RoundingMode.HALF_UP)
                .stripTrailingZeros();
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

    private record InstanceEntrySlowEvidence(
            Long slowCountOver500ms,
            BigDecimal slowShareOver500ms
    ) {
    }

    private static OffsetDateTime toUtcOffsetDateTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /**
     * current dashboard와 snapshot read model이 같은 window 계산을 공유하되 snapshot만 accepted_at cutoff를 쓰게 구분한다.
     */
    private record BucketQueryContext(Optional<OffsetDateTime> acceptedAtCutoffUtc) {

        private BucketQueryContext {
            acceptedAtCutoffUtc = Objects.requireNonNull(
                    acceptedAtCutoffUtc,
                    "acceptedAtCutoffUtc must not be null")
                    .map(cutoff -> cutoff.withOffsetSameInstant(ZoneOffset.UTC));
        }

        private static BucketQueryContext current() {
            return new BucketQueryContext(Optional.empty());
        }

        private static BucketQueryContext snapshot(OffsetDateTime snapshotCutoffAt) {
            return new BucketQueryContext(Optional.of(Objects.requireNonNull(
                    snapshotCutoffAt,
                    "snapshotCutoffAt must not be null")));
        }
    }

    private static final class NoopFallbackCaptureServiceProvider
            implements ObjectProvider<DashboardSnapshotFallbackCaptureService> {

        @Override
        public DashboardSnapshotFallbackCaptureService getObject(Object... args) {
            return null;
        }

        @Override
        public DashboardSnapshotFallbackCaptureService getIfAvailable() {
            return null;
        }

        @Override
        public DashboardSnapshotFallbackCaptureService getIfUnique() {
            return null;
        }

        @Override
        public DashboardSnapshotFallbackCaptureService getObject() {
            return null;
        }
    }
}
