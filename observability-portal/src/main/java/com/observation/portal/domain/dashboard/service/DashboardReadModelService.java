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
import com.observation.portal.domain.bucket.model.HistogramBucketEvidenceRow;
import com.observation.portal.domain.bucket.model.LocalPercentileEvidenceRow;
import com.observation.portal.domain.bucket.model.WindowBucketAggregate;
import com.observation.portal.domain.bucket.repository.MetricBucketRepository;
import com.observation.portal.domain.catalog.entity.ApplicationEntity;
import com.observation.portal.domain.catalog.repository.ApplicationRepository;
import com.observation.portal.domain.dashboard.model.ApplicationDashboardReadModel;
import com.observation.portal.domain.ingest.model.StarterHeartbeatTelemetryRecord;
import com.observation.portal.domain.ingest.repository.StarterHeartbeatTelemetryRepository;
import com.observation.portal.domain.state.model.DegradedHysteresisInput;
import com.observation.portal.domain.state.model.LifecycleStateDecision;
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
    private static final long MINIMUM_ACTIVE_SAMPLE_REQUEST_COUNT = 30L;
    private static final String APPLICATION_STATE_SCOPE = "application";
    private static final String STARTER_LOCAL_SOURCE = "starter_local";
    private static final String INSTANCE_BUCKET_SCOPE = "instance_bucket";
    private static final String HISTOGRAM_BOUNDARY_MISMATCH_REASON = "histogram_boundary_mismatch";

    private final ApplicationRepository applicationRepository;
    private final MetricBucketRepository metricBucketRepository;
    private final StarterHeartbeatTelemetryRepository heartbeatTelemetryRepository;
    private final AcceptedBucketFreshnessEvaluator freshnessEvaluator;
    private final TimeBucketWindowCalculator timeBucketWindowCalculator;
    private final LifecycleStateService lifecycleStateService;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    /**
     * dashboard read model 조립에 필요한 read-only repository와 state/time component를 주입한다.
     */
    public DashboardReadModelService(
            ApplicationRepository applicationRepository,
            MetricBucketRepository metricBucketRepository,
            StarterHeartbeatTelemetryRepository heartbeatTelemetryRepository,
            AcceptedBucketFreshnessEvaluator freshnessEvaluator,
            TimeBucketWindowCalculator timeBucketWindowCalculator,
            LifecycleStateService lifecycleStateService,
            Clock clock,
            ObjectMapper objectMapper) {
        this.applicationRepository = Objects.requireNonNull(
                applicationRepository,
                "applicationRepository must not be null");
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
        this.clock = Objects.requireNonNull(clock, "clock must not be null").withZone(ZoneOffset.UTC);
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
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
                .map(this::buildDashboard);
    }

    private ApplicationDashboardReadModel buildDashboard(ApplicationEntity application) {
        Instant queryAt = clock.instant();
        Instant evaluationAt = floorToBucketBoundary(queryAt);
        DashboardTimeWindow dashboardWindow = timeBucketWindowCalculator.dashboardWindowEndingAt(evaluationAt);
        Optional<OffsetDateTime> latestBucketEndUtc = metricBucketRepository
                .findLatestBucketEndUtcByApplicationIdAtOrBefore(application.id(), evaluationAt);
        WindowBucketAggregate aggregate = metricBucketRepository.findWindowAggregateByApplicationId(
                application.id(),
                dashboardWindow.current().startUtc(),
                dashboardWindow.current().endUtc());
        List<LocalPercentileEvidenceRow> currentPercentileRows =
                metricBucketRepository.findLocalPercentileEvidenceRowsByApplicationId(
                        application.id(),
                        dashboardWindow.current().startUtc(),
                        dashboardWindow.current().endUtc());
        List<HistogramBucketEvidenceRow> currentHistogramRows =
                metricBucketRepository.findSummaryDurationBucketEvidenceRowsByApplicationId(
                        application.id(),
                        dashboardWindow.current().startUtc(),
                        dashboardWindow.current().endUtc());
        List<HistogramBucketEvidenceRow> baselineHistogramRows =
                metricBucketRepository.findSummaryDurationBucketEvidenceRowsByApplicationId(
                        application.id(),
                        dashboardWindow.baseline().startUtc(),
                        dashboardWindow.baseline().endUtc());
        Optional<StarterHeartbeatTelemetryRecord> latestHeartbeat = heartbeatTelemetryRepository
                .findLatestByApplicationScope(application.projectId(), application.name(), application.environment());

        AcceptedBucketFreshness freshness = freshnessEvaluator.evaluateAt(
                evaluationAt,
                latestBucketEndUtc.map(OffsetDateTime::toInstant).orElse(null));
        MetricSampleReadiness sampleReadiness = sampleReadiness(aggregate);
        MetricTrafficActivity trafficActivity = trafficActivity(aggregate);
        StarterConnectionInput starterConnectionInput = starterConnectionInput(latestHeartbeat, queryAt);
        LifecycleStateDecision stateDecision = lifecycleStateService.decide(
                metricLifecycleInput(freshness, sampleReadiness, trafficActivity),
                starterConnectionInput);

        return new ApplicationDashboardReadModel(
                toUtcOffsetDateTime(clock.instant()),
                application(application, latestBucketEndUtc, dashboardWindow),
                state(stateDecision),
                starterConnection(stateDecision.starterConnection()),
                zeroInsight(freshness.status(), starterConnectionInput.freshness(), sampleReadiness, trafficActivity),
                recovery(stateDecision.recovery()),
                metrics(aggregate),
                sourceScopedPercentiles(application, currentPercentileRows),
                histogramDistribution(currentHistogramRows, baselineHistogramRows),
                List.of(),
                List.of(),
                null);
    }

    /**
     * persisted starter_local percentile point 중 current window의 instance별 latest valid item만 선택한다.
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
                    "no_valid_percentile_points_in_current_window");
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
                        percentiles.source(),
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
     * current/baseline histogram evidence를 독립 window로 조립하고, window 간 비교 판단은 만들지 않는다.
     */
    private ApplicationDashboardReadModel.HistogramDistribution histogramDistribution(
            List<HistogramBucketEvidenceRow> currentRows,
            List<HistogramBucketEvidenceRow> baselineRows) {
        return new ApplicationDashboardReadModel.HistogramDistribution(
                "histogram_bucket_distribution",
                "application",
                "bucket_distribution_evidence",
                "sum_cumulative_counts_only_when_boundary_set_matches",
                histogramWindow(currentRows, "current"),
                histogramWindow(baselineRows, "baseline"));
    }

    /**
     * 하나의 15분 window 안에서 boundary set이 모두 일치할 때만 cumulative count를 boundary별로 합산한다.
     */
    private ApplicationDashboardReadModel.HistogramWindow histogramWindow(
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
     * Story 5.2에서는 degraded 진입/해소와 recovery source를 새로 판단하지 않고 current aggregate 축만 전달한다.
     */
    private MetricLifecycleInput metricLifecycleInput(
            AcceptedBucketFreshness freshness,
            MetricSampleReadiness sampleReadiness,
            MetricTrafficActivity trafficActivity) {
        return new MetricLifecycleInput(
                freshness,
                sampleReadiness,
                trafficActivity,
                DegradedHysteresisInput.noConcern(),
                Optional.empty(),
                Optional.empty());
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
                window(dashboardWindow.baseline()));
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
            AcceptedBucketFreshnessStatus freshnessStatus,
            StarterConnectionFreshness starterFreshness,
            MetricSampleReadiness sampleReadiness,
            MetricTrafficActivity trafficActivity) {
        String reasonCode = zeroInsightReasonCode(
                freshnessStatus,
                starterFreshness,
                sampleReadiness,
                trafficActivity);
        return switch (reasonCode) {
            case "waiting_first_data" -> new ApplicationDashboardReadModel.ZeroInsight(
                    reasonCode,
                    "아직 accepted metric bucket이 없어 첫 데이터를 기다리고 있습니다.",
                    "Starter heartbeat가 최근 수신됐으므로 요청 traffic 발생 후 bucket 수용 여부를 확인하세요.");
            case "telemetry_unreachable" -> new ApplicationDashboardReadModel.ZeroInsight(
                    reasonCode,
                    "Starter heartbeat와 metric data freshness가 모두 최근 상태가 아닙니다.",
                    "Starter 설정, project key, portal/network 연결을 확인하되 host application down으로 단정하지 마세요.");
            case "insufficient_sample" -> new ApplicationDashboardReadModel.ZeroInsight(
                    reasonCode,
                    "현재 window의 요청 sample이 lifecycle 판단 기준보다 적습니다.",
                    "다음 bucket까지 더 많은 요청 sample을 관찰하세요.");
            case "metric_data_idle" -> new ApplicationDashboardReadModel.ZeroInsight(
                    reasonCode,
                    "현재 window에서 우선 조치가 필요한 metric data traffic이 없습니다.",
                    "요청이 다시 들어오면 다음 accepted bucket 이후 dashboard를 확인하세요.");
            case "no_action_needed" -> new ApplicationDashboardReadModel.ZeroInsight(
                    reasonCode,
                    "현재 우선 조치가 필요한 신호는 없습니다.",
                    "트래픽이 유지되는지 다음 bucket까지 관찰하세요.");
            default -> throw new IllegalStateException("unsupported zeroInsight reasonCode: " + reasonCode);
        };
    }

    /**
     * Story 5.2에서 triageCards가 비어 있을 때만 사용하는 zeroInsight reason mapping이다.
     */
    private static String zeroInsightReasonCode(
            AcceptedBucketFreshnessStatus freshnessStatus,
            StarterConnectionFreshness starterFreshness,
            MetricSampleReadiness sampleReadiness,
            MetricTrafficActivity trafficActivity) {
        boolean recentHeartbeat = starterFreshness == StarterConnectionFreshness.RECENT;
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

    private static OffsetDateTime toUtcOffsetDateTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
