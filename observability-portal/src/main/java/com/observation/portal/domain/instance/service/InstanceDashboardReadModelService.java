package com.observation.portal.domain.instance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.portal.common.time.TimeBucketWindowCalculator;
import com.observation.portal.common.time.UtcTimeInterval;
import com.observation.portal.domain.bucket.model.EndpointEvidenceRow;
import com.observation.portal.domain.bucket.model.HistogramBucketEvidenceRow;
import com.observation.portal.domain.bucket.model.RuntimeRatioEvidenceRow;
import com.observation.portal.domain.bucket.model.WindowBucketAggregate;
import com.observation.portal.domain.bucket.repository.MetricBucketRepository;
import com.observation.portal.domain.catalog.entity.ApplicationEntity;
import com.observation.portal.domain.catalog.entity.ApplicationInstanceEntity;
import com.observation.portal.domain.catalog.repository.ApplicationInstanceRepository;
import com.observation.portal.domain.catalog.repository.ApplicationRepository;
import com.observation.portal.domain.ingest.model.StarterHeartbeatTelemetryRecord;
import com.observation.portal.domain.ingest.repository.StarterHeartbeatTelemetryRepository;
import com.observation.portal.domain.instance.model.InstanceDashboardReadModel;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailRow;
import com.observation.portal.domain.snapshot.repository.DashboardSnapshotRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
 * Instance Dashboard live/snapshot mode의 catalog 검증, window 선택, metric evidence 재구성을 담당한다.
 *
 * <p>live mode는 Application Dashboard와 같은 recent 30분 window를 사용하고, snapshot mode는 selected
 * `dashboard_snapshots` row metadata의 window만 사용한다. 두 mode 모두 selected instance metric evidence는
 * `accepted_metric_buckets` non-cutoff query path로 읽는다.</p>
 */
@Service
public class InstanceDashboardReadModelService {

    private static final BigDecimal ERROR_RATE_THRESHOLD = new BigDecimal("0.05");
    private static final BigDecimal SLOW_SHARE_THRESHOLD = new BigDecimal("0.20");
    private static final BigDecimal CPU_THRESHOLD = new BigDecimal("0.85");
    private static final BigDecimal HEAP_THRESHOLD = new BigDecimal("0.90");
    private static final BigDecimal DATASOURCE_POOL_THRESHOLD = new BigDecimal("0.85");
    private static final long MINIMUM_SAMPLE_REQUEST_COUNT = 30L;
    private static final int MAX_ENDPOINT_ITEMS = 5;

    private final ApplicationRepository applicationRepository;
    private final ApplicationInstanceRepository instanceRepository;
    private final DashboardSnapshotRepository snapshotRepository;
    private final MetricBucketRepository metricBucketRepository;
    private final StarterHeartbeatTelemetryRepository heartbeatRepository;
    private final TimeBucketWindowCalculator timeBucketWindowCalculator;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final int retentionDays;

    /**
     * Instance Dashboard read model 조립에 필요한 repository와 time/parser dependency를 주입한다.
     */
    public InstanceDashboardReadModelService(
            ApplicationRepository applicationRepository,
            ApplicationInstanceRepository instanceRepository,
            DashboardSnapshotRepository snapshotRepository,
            MetricBucketRepository metricBucketRepository,
            StarterHeartbeatTelemetryRepository heartbeatRepository,
            TimeBucketWindowCalculator timeBucketWindowCalculator,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${portal.dashboard-snapshots.retention-days:14}") int retentionDays) {
        this.applicationRepository = Objects.requireNonNull(
                applicationRepository,
                "applicationRepository must not be null");
        this.instanceRepository = Objects.requireNonNull(instanceRepository, "instanceRepository must not be null");
        this.snapshotRepository = Objects.requireNonNull(snapshotRepository, "snapshotRepository must not be null");
        this.metricBucketRepository = Objects.requireNonNull(
                metricBucketRepository,
                "metricBucketRepository must not be null");
        this.heartbeatRepository = Objects.requireNonNull(
                heartbeatRepository,
                "heartbeatRepository must not be null");
        this.timeBucketWindowCalculator = Objects.requireNonNull(
                timeBucketWindowCalculator,
                "timeBucketWindowCalculator must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null").withZone(ZoneOffset.UTC);
        if (retentionDays <= 0) {
            throw new IllegalArgumentException("retentionDays must be positive");
        }
        this.retentionDays = retentionDays;
    }

    /**
     * live Instance Dashboard를 Application Dashboard와 같은 recent 30분 accepted bucket window로 반환한다.
     */
    @Transactional(readOnly = true)
    public Optional<InstanceDashboardReadModel> getLiveDashboard(
            UUID projectId,
            UUID applicationId,
            UUID instanceId) {
        Optional<CatalogContext> catalog = catalogContext(projectId, applicationId, instanceId);
        if (catalog.isEmpty()) {
            return Optional.empty();
        }
        Instant queryAt = clock.instant();
        Instant evaluationAt = timeBucketWindowCalculator.bucketContaining(queryAt).startUtc();
        UtcTimeInterval currentWindow = timeBucketWindowCalculator.dashboardWindowEndingAt(evaluationAt).current();
        return Optional.of(buildDashboard(
                catalog.orElseThrow(),
                "live",
                currentWindow.startUtc(),
                currentWindow.endUtc(),
                null));
    }

    /**
     * selected Application Snapshot row의 metadata window 기준으로 Instance Dashboard snapshot mode를 반환한다.
     *
     * <p>Application Snapshot의 stored read model은 읽거나 재계산하지 않고, selected instance evidence만 현재 retention 안의
     * accepted bucket에서 cutoff 없이 재구성한다.</p>
     */
    @Transactional(readOnly = true)
    public Optional<InstanceDashboardReadModel> getSnapshotDashboard(
            UUID projectId,
            UUID applicationId,
            UUID snapshotId,
            UUID instanceId) {
        Optional<CatalogContext> catalog = catalogContext(projectId, applicationId, instanceId);
        if (catalog.isEmpty()) {
            return Optional.empty();
        }
        Optional<DashboardSnapshotDetailRow> snapshot = snapshotRepository.findDetailRow(
                Objects.requireNonNull(projectId, "projectId must not be null"),
                Objects.requireNonNull(applicationId, "applicationId must not be null"),
                Objects.requireNonNull(snapshotId, "snapshotId must not be null"));
        if (snapshot.isEmpty()) {
            return Optional.empty();
        }
        DashboardSnapshotDetailRow row = snapshot.orElseThrow();
        if (!snapshotInRetention(row)) {
            return Optional.empty();
        }
        return Optional.of(buildDashboard(
                catalog.orElseThrow(),
                "snapshot",
                row.currentWindowStartUtc().toInstant(),
                row.currentWindowEndUtc().toInstant(),
                row));
    }

    private Optional<CatalogContext> catalogContext(UUID projectId, UUID applicationId, UUID instanceId) {
        UUID requiredProjectId = Objects.requireNonNull(projectId, "projectId must not be null");
        UUID requiredApplicationId = Objects.requireNonNull(applicationId, "applicationId must not be null");
        UUID requiredInstanceId = Objects.requireNonNull(instanceId, "instanceId must not be null");
        Optional<ApplicationEntity> application = applicationRepository.findByIdAndProjectId(
                requiredApplicationId,
                requiredProjectId);
        if (application.isEmpty()) {
            return Optional.empty();
        }
        Optional<ApplicationInstanceEntity> instance = instanceRepository.findByIdAndApplicationId(
                requiredInstanceId,
                requiredApplicationId);
        if (instance.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new CatalogContext(application.orElseThrow(), instance.orElseThrow()));
    }

    private InstanceDashboardReadModel buildDashboard(
            CatalogContext catalog,
            String mode,
            Instant windowStartUtc,
            Instant windowEndUtc,
            DashboardSnapshotDetailRow snapshotRow) {
        ApplicationEntity application = catalog.application();
        ApplicationInstanceEntity instance = catalog.instance();
        UUID instanceId = instance.id();
        String windowSource = "snapshot".equals(mode)
                ? "selected_application_snapshot"
                : "live_recent_30_minutes";
        OffsetDateTime generatedAt = toUtcOffsetDateTime(clock.instant());
        OffsetDateTime windowStart = toUtcOffsetDateTime(windowStartUtc);
        OffsetDateTime windowEnd = toUtcOffsetDateTime(windowEndUtc);

        Optional<OffsetDateTime> latestBucketEnd =
                metricBucketRepository.findLatestBucketEndUtcByApplicationInstanceIdAtOrBefore(
                        instanceId,
                        windowEndUtc);
        WindowBucketAggregate aggregate = metricBucketRepository.findWindowAggregateByApplicationInstanceId(
                instanceId,
                windowStartUtc,
                windowEndUtc);
        List<HistogramBucketEvidenceRow> histogramRows =
                metricBucketRepository.findSummaryDurationBucketEvidenceRowsByApplicationInstanceId(
                        instanceId,
                        windowStartUtc,
                        windowEndUtc);
        SlowEvidence slowEvidence = slowEvidence(histogramRows);
        List<EndpointEvidenceRow> endpointRows = metricBucketRepository.findEndpointEvidenceRowsByApplicationInstanceId(
                instanceId,
                windowStartUtc,
                windowEndUtc);
        List<ApplicationEndpointAnchor> applicationEndpointAnchors = applicationEndpointAnchors(
                application.id(),
                windowStartUtc,
                windowEndUtc,
                snapshotRow);
        Optional<RuntimeRatioEvidenceRow> runtimeRatio =
                metricBucketRepository.findLatestRuntimeRatioEvidenceRowByApplicationInstanceId(
                        instanceId,
                        windowStartUtc,
                        windowEndUtc);
        Optional<StarterHeartbeatTelemetryRecord> heartbeat = heartbeatRepository.findByIdentity(
                application.projectId(),
                application.name(),
                application.environment(),
                instance.instanceName());

        InstanceDashboardReadModel.RedSignals redSignals = redSignals(aggregate, slowEvidence);
        InstanceDashboardReadModel.ObservationStatus observationStatus = observationStatus(
                latestBucketEnd,
                windowStart,
                windowEnd,
                windowSource);
        InstanceDashboardReadModel.EndpointEvidence endpointEvidence = endpointEvidence(
                endpointRows,
                applicationEndpointAnchors);
        InstanceDashboardReadModel.DataQuality dataQuality = dataQuality(
                observationStatus,
                aggregate,
                slowEvidence,
                endpointEvidence);
        InstanceDashboardReadModel.ResourceEvidence resourceEvidence = resourceEvidence(
                runtimeRatio,
                redSignals.requestSymptomPresent());
        List<InstanceDashboardReadModel.PatternEvidence> patterns = patterns(resourceEvidence);
        InstanceDashboardReadModel.Snapshot snapshot = snapshot(snapshotRow);

        return new InstanceDashboardReadModel(
                "instance_dashboard_read_model.v1",
                mode,
                generatedAt,
                application(application),
                instance(instance),
                new InstanceDashboardReadModel.Window(
                        "recent_30_minutes",
                        windowStart,
                        windowEnd,
                        (int) TimeBucketWindowCalculator.BUCKET_DURATION.toSeconds(),
                        windowSource),
                thresholds(),
                applicationStateRef(snapshotRow),
                observationStatus,
                applicationContribution(observationStatus, redSignals),
                dataQuality,
                starterConnection(heartbeat),
                new InstanceDashboardReadModel.Signals(redSignals),
                endpointEvidence,
                resourceEvidence,
                patterns,
                snapshot,
                readSemantics(windowSource, snapshotRow),
                links(application.projectId(), application.id(), instance.id(), snapshotRow),
                excludedCapabilities());
    }

    private static InstanceDashboardReadModel.Application application(ApplicationEntity application) {
        return new InstanceDashboardReadModel.Application(
                application.projectId(),
                application.id(),
                application.name(),
                application.environment(),
                new InstanceDashboardReadModel.ApplicationLinks(
                        applicationDashboardLink(application.projectId(), application.id())));
    }

    private boolean snapshotInRetention(DashboardSnapshotDetailRow row) {
        OffsetDateTime cutoff = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).minusDays(retentionDays);
        return !row.currentWindowEndUtc()
                .withOffsetSameInstant(ZoneOffset.UTC)
                .isBefore(cutoff);
    }

    private static InstanceDashboardReadModel.Thresholds thresholds() {
        return new InstanceDashboardReadModel.Thresholds(
                MINIMUM_SAMPLE_REQUEST_COUNT,
                ERROR_RATE_THRESHOLD,
                SLOW_SHARE_THRESHOLD,
                DATASOURCE_POOL_THRESHOLD,
                CPU_THRESHOLD,
                HEAP_THRESHOLD);
    }

    private static InstanceDashboardReadModel.Instance instance(ApplicationInstanceEntity instance) {
        return new InstanceDashboardReadModel.Instance(
                instance.id(),
                instance.instanceName(),
                instance.firstSeenAt(),
                instance.lastSeenAt());
    }

    private static InstanceDashboardReadModel.ApplicationStateRef applicationStateRef(
            DashboardSnapshotDetailRow snapshotRow) {
        if (snapshotRow == null) {
            return new InstanceDashboardReadModel.ApplicationStateRef(
                    "application",
                    "application_dashboard_live",
                    null,
                    null);
        }
        return new InstanceDashboardReadModel.ApplicationStateRef(
                "application",
                "selected_application_snapshot",
                snapshotRow.stateCode(),
                snapshotRow.snapshotId());
    }

    private static InstanceDashboardReadModel.ObservationStatus observationStatus(
            Optional<OffsetDateTime> latestBucketEnd,
            OffsetDateTime windowStart,
            OffsetDateTime windowEnd,
            String windowSource) {
        if (latestBucketEnd.isEmpty()) {
            return new InstanceDashboardReadModel.ObservationStatus(
                    "metric_missing",
                    missingMetricReason(windowSource),
                    null);
        }
        OffsetDateTime lastObserved = latestBucketEnd.orElseThrow();
        boolean observedInWindow = lastObserved.isAfter(windowStart) && !lastObserved.isAfter(windowEnd);
        if (!observedInWindow) {
            return new InstanceDashboardReadModel.ObservationStatus(
                    "not_observed_in_window",
                    "latest_metric_bucket_outside_selected_window",
                    lastObserved);
        }
        return new InstanceDashboardReadModel.ObservationStatus(
                "observed",
                "selected_instance_metric_bucket_observed",
                lastObserved);
    }

    private static InstanceDashboardReadModel.DataQuality dataQuality(
            InstanceDashboardReadModel.ObservationStatus observationStatus,
            WindowBucketAggregate aggregate,
            SlowEvidence slowEvidence,
            InstanceDashboardReadModel.EndpointEvidence endpointEvidence) {
        List<String> limitations = new ArrayList<>();
        if ("metric_missing".equals(observationStatus.code())) {
            limitations.add(observationStatus.reason());
            return new InstanceDashboardReadModel.DataQuality(
                    "metric_missing",
                    limitations,
                    "accepted_metric_buckets");
        }
        if ("not_observed_in_window".equals(observationStatus.code())) {
            limitations.add("selected_instance_not_observed_in_window");
            return new InstanceDashboardReadModel.DataQuality(
                    "not_observed_in_window",
                    limitations,
                    "accepted_metric_buckets");
        }
        if (aggregate.requestCount() < MINIMUM_SAMPLE_REQUEST_COUNT) {
            limitations.add("request_sample_below_minimum");
        }
        if (slowEvidence.malformed()) {
            limitations.add("duration_bucket_malformed");
        }
        if ("insufficient".equals(endpointEvidence.status()) && "malformed_evidence".equals(endpointEvidence.reason())) {
            limitations.add("endpoint_evidence_malformed");
        }
        String state = limitations.isEmpty()
                ? "sufficient"
                : slowEvidence.malformed()
                        || limitations.contains("endpoint_evidence_malformed") ? "malformed" : "sample_limited";
        return new InstanceDashboardReadModel.DataQuality(
                state,
                limitations,
                "accepted_metric_buckets");
    }

    private static InstanceDashboardReadModel.ApplicationContribution applicationContribution(
            InstanceDashboardReadModel.ObservationStatus observationStatus,
            InstanceDashboardReadModel.RedSignals redSignals) {
        if (!"observed".equals(observationStatus.code())) {
            return new InstanceDashboardReadModel.ApplicationContribution(
                    "insufficient",
                    "selected_instance_evidence_not_observed",
                    List.of());
        }
        List<String> evidenceRefs = new ArrayList<>();
        if (redSignals.errorCount() > 0L) {
            evidenceRefs.add("server_error_5xx");
        }
        if (redSignals.slowCountOver500ms() != null && redSignals.slowCountOver500ms() > 0L) {
            evidenceRefs.add("slow_request_over_500ms");
        }
        if (evidenceRefs.isEmpty()) {
            return new InstanceDashboardReadModel.ApplicationContribution(
                    "none",
                    "no_request_symptom_observed",
                    List.of());
        }
        return new InstanceDashboardReadModel.ApplicationContribution(
                "attention",
                "request_symptom_observed_without_root_cause_claim",
                evidenceRefs);
    }

    private static InstanceDashboardReadModel.RedSignals redSignals(
            WindowBucketAggregate aggregate,
            SlowEvidence slowEvidence) {
        BigDecimal errorRate = aggregate.requestCount() == 0L
                ? null
                : ratio(aggregate.errorCount(), aggregate.requestCount());
        boolean errorSymptom = errorRate != null && errorRate.compareTo(ERROR_RATE_THRESHOLD) >= 0;
        boolean slowSymptom = slowEvidence.slowShareOver500ms() != null
                && slowEvidence.slowShareOver500ms().compareTo(SLOW_SHARE_THRESHOLD) >= 0;
        return new InstanceDashboardReadModel.RedSignals(
                aggregate.requestCount(),
                aggregate.errorCount(),
                errorRate,
                slowEvidence.slowCountOver500ms(),
                slowEvidence.slowShareOver500ms(),
                errorSymptom || slowSymptom || aggregate.errorCount() > 0L);
    }

    private InstanceDashboardReadModel.EndpointEvidence endpointEvidence(
            List<EndpointEvidenceRow> rows,
            List<ApplicationEndpointAnchor> applicationAnchors) {
        List<EndpointEvidenceRow> evidenceRows = List.copyOf(Objects.requireNonNullElse(rows, List.of()));
        Map<String, EndpointAggregate> aggregates = new LinkedHashMap<>();
        for (EndpointEvidenceRow row : evidenceRows) {
            Optional<List<EndpointItem>> parsed = parseEndpointItems(row.endpointsJson());
            if (parsed.isEmpty()) {
                return new InstanceDashboardReadModel.EndpointEvidence(
                        "accepted_metric_buckets.endpoints_json",
                        "instance_recent_30_minutes",
                        "selected_instance_metric_evidence",
                        "server_order",
                        "insufficient",
                        "malformed_evidence",
                        List.of());
            }
            for (EndpointItem item : parsed.orElseThrow()) {
                aggregates.compute(item.endpointKey(), (key, existing) -> {
                    if (existing == null) {
                        return new EndpointAggregate(item.method(), item.route(), item.requestCount(), item.errorCount());
                    }
                    return existing.plus(item.requestCount(), item.errorCount());
                });
            }
        }
        Map<String, ApplicationEndpointAnchor> anchorsByEndpointKey = new LinkedHashMap<>();
        for (ApplicationEndpointAnchor anchor : List.copyOf(Objects.requireNonNullElse(applicationAnchors, List.of()))) {
            anchorsByEndpointKey.putIfAbsent(anchor.endpointKey(), anchor);
        }
        List<String> orderedEndpointKeys = new ArrayList<>(anchorsByEndpointKey.keySet());
        List<String> selectedSymptomKeys = aggregates.values().stream()
                .filter(InstanceDashboardReadModelService::standaloneSelectedEndpointEvidenceCandidate)
                .sorted(Comparator.comparingLong(EndpointAggregate::errorCount)
                        .reversed()
                        .thenComparing(Comparator.comparingLong(EndpointAggregate::requestCount).reversed())
                        .thenComparing(EndpointAggregate::endpointKey))
                .map(EndpointAggregate::endpointKey)
                .filter(endpointKey -> !orderedEndpointKeys.contains(endpointKey))
                .toList();
        orderedEndpointKeys.addAll(selectedSymptomKeys);
        List<String> selected = orderedEndpointKeys.stream()
                .limit(MAX_ENDPOINT_ITEMS)
                .toList();
        if (selected.isEmpty()) {
            return InstanceDashboardReadModel.EndpointEvidence.missing();
        }
        List<InstanceDashboardReadModel.EndpointEvidenceItem> items = new ArrayList<>();
        for (int index = 0; index < selected.size(); index++) {
            String endpointKey = selected.get(index);
            EndpointAggregate endpoint = aggregates.get(endpointKey);
            ApplicationEndpointAnchor anchor = anchorsByEndpointKey.get(endpointKey);
            String method = endpoint == null ? anchor.method() : endpoint.method();
            String route = endpoint == null ? anchor.route() : endpoint.route();
            long requestCount = endpoint == null ? 0L : endpoint.requestCount();
            long errorCount = endpoint == null ? 0L : endpoint.errorCount();
            boolean observed = endpoint != null && endpoint.requestCount() > 0L;
            items.add(new InstanceDashboardReadModel.EndpointEvidenceItem(
                    method,
                    route,
                    endpointKey,
                    observed ? "observed" : "not_observed",
                    requestCount,
                    errorCount,
                    requestCount == 0L ? null : ratio(errorCount, requestCount),
                    index + 1,
                    observed ? "available" : "missing",
                    observed && errorCount > 0L
                            ? "selected_instance_endpoint_server_error_observed"
                            : observed
                                    ? "selected_instance_endpoint_observed"
                                    : "application_endpoint_not_observed_on_selected_instance",
                    anchor == null ? null : anchor.anchorId()));
        }
        return new InstanceDashboardReadModel.EndpointEvidence(
                "accepted_metric_buckets.endpoints_json",
                "instance_recent_30_minutes",
                "selected_instance_metric_evidence",
                "server_order",
                "available",
                null,
                items);
    }

    /**
     * Application anchor 없이 selected instance metric만으로 endpoint evidence 후보가 될 수 있는지 판단한다.
     */
    private static boolean standaloneSelectedEndpointEvidenceCandidate(EndpointAggregate endpoint) {
        if (endpoint.errorCount() > 0L) {
            return true;
        }
        if (endpoint.requestCount() < MINIMUM_SAMPLE_REQUEST_COUNT) {
            return false;
        }
        return ratio(endpoint.errorCount(), endpoint.requestCount()).compareTo(ERROR_RATE_THRESHOLD) >= 0;
    }

    private List<ApplicationEndpointAnchor> applicationEndpointAnchors(
            UUID applicationId,
            Instant windowStartUtc,
            Instant windowEndUtc,
            DashboardSnapshotDetailRow snapshotRow) {
        return snapshotRow == null
                ? liveApplicationEndpointAnchors(applicationId, windowStartUtc, windowEndUtc)
                : snapshotApplicationEndpointAnchors(snapshotRow.readModelJson());
    }

    private List<ApplicationEndpointAnchor> liveApplicationEndpointAnchors(
            UUID applicationId,
            Instant windowStartUtc,
            Instant windowEndUtc) {
        List<EndpointEvidenceRow> applicationRows = metricBucketRepository.findEndpointEvidenceRowsByApplicationId(
                applicationId,
                windowStartUtc,
                windowEndUtc);
        Map<String, EndpointAggregate> aggregates = new LinkedHashMap<>();
        for (EndpointEvidenceRow row : List.copyOf(Objects.requireNonNullElse(applicationRows, List.of()))) {
            Optional<List<EndpointItem>> parsed = parseEndpointItems(row.endpointsJson());
            if (parsed.isEmpty()) {
                return List.of();
            }
            for (EndpointItem item : parsed.orElseThrow()) {
                aggregates.compute(item.endpointKey(), (key, existing) -> existing == null
                        ? new EndpointAggregate(item.method(), item.route(), item.requestCount(), item.errorCount())
                        : existing.plus(item.requestCount(), item.errorCount()));
            }
        }
        List<EndpointAggregate> selected = aggregates.values().stream()
                .filter(endpoint -> endpoint.errorCount() > 0L
                        || endpoint.requestCount() >= MINIMUM_SAMPLE_REQUEST_COUNT)
                .sorted(Comparator.comparingLong(EndpointAggregate::errorCount)
                        .reversed()
                        .thenComparing(Comparator.comparingLong(EndpointAggregate::requestCount).reversed())
                        .thenComparing(EndpointAggregate::endpointKey))
                .limit(MAX_ENDPOINT_ITEMS)
                .toList();
        List<ApplicationEndpointAnchor> anchors = new ArrayList<>();
        for (int index = 0; index < selected.size(); index++) {
            EndpointAggregate endpoint = selected.get(index);
            anchors.add(new ApplicationEndpointAnchor(
                    endpoint.method(),
                    endpoint.route(),
                    endpoint.endpointKey(),
                    "application-endpoint-" + (index + 1)));
        }
        return anchors;
    }

    private List<ApplicationEndpointAnchor> snapshotApplicationEndpointAnchors(String readModelJson) {
        try {
            JsonNode root = objectMapper.readTree(readModelJson);
            if (root == null || !root.isObject()) {
                return List.of();
            }
            List<ApplicationEndpointAnchor> anchors = anchorsFromItems(root.path("snapshotEndpointEvidence").path("items"));
            if (!anchors.isEmpty()) {
                return anchors;
            }
            return anchorsFromItems(root.path("endpointPriority"));
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private List<ApplicationEndpointAnchor> anchorsFromItems(JsonNode items) {
        if (items == null || !items.isArray()) {
            return List.of();
        }
        List<ApplicationEndpointAnchor> anchors = new ArrayList<>();
        int maxItems = Math.min(items.size(), MAX_ENDPOINT_ITEMS);
        for (int index = 0; index < maxItems; index++) {
            JsonNode item = items.get(index);
            if (item == null || !item.isObject()) {
                continue;
            }
            String endpointKey = textValue(item, "endpointKey");
            EndpointIdentity identity = endpointIdentity(endpointKey, textValue(item, "method"), textValue(item, "route"));
            if (identity == null) {
                continue;
            }
            anchors.add(new ApplicationEndpointAnchor(
                    identity.method(),
                    identity.route(),
                    identity.endpointKey(),
                    normalizeAnchorId(textValue(item, "anchorId"), index + 1)));
        }
        return anchors;
    }

    private InstanceDashboardReadModel.ResourceEvidence resourceEvidence(
            Optional<RuntimeRatioEvidenceRow> runtimeRatio,
            boolean requestSymptomPresent) {
        if (runtimeRatio.isEmpty()) {
            return InstanceDashboardReadModel.ResourceEvidence.missing();
        }
        RuntimeRatioEvidenceRow row = runtimeRatio.orElseThrow();
        List<InstanceDashboardReadModel.ResourceEvidenceItem> items = new ArrayList<>();
        addResourceItem(
                items,
                "cpu_usage",
                row.cpuUsageRatio(),
                CPU_THRESHOLD,
                row.bucketEndUtc(),
                requestSymptomPresent);
        addResourceItem(
                items,
                "heap_usage",
                row.heapUsedRatio(),
                HEAP_THRESHOLD,
                row.bucketEndUtc(),
                requestSymptomPresent);
        addResourceItem(
                items,
                "datasource_pool_usage",
                row.datasourcePoolUsageRatio(),
                DATASOURCE_POOL_THRESHOLD,
                row.bucketEndUtc(),
                requestSymptomPresent);
        if (items.isEmpty()) {
            return InstanceDashboardReadModel.ResourceEvidence.missing();
        }
        return new InstanceDashboardReadModel.ResourceEvidence(
                "accepted_metric_buckets",
                "available",
                items);
    }

    private static void addResourceItem(
            List<InstanceDashboardReadModel.ResourceEvidenceItem> items,
            String resourceKey,
            BigDecimal usage,
            BigDecimal threshold,
            OffsetDateTime observedAt,
            boolean requestSymptomPresent) {
        if (usage == null) {
            return;
        }
        boolean thresholdExceeded = usage.compareTo(threshold) >= 0;
        String patternContribution = !thresholdExceeded
                ? "none"
                : requestSymptomPresent ? "shared_resource_pressure_pattern" : "attention_only";
        String operatorText = !thresholdExceeded
                ? "resource 사용률이 MVP 기준 이하입니다."
                : requestSymptomPresent
                        ? "resource 압박과 요청 증상이 같은 window에서 관찰됩니다."
                        : "resource 압박은 관찰됐지만 요청 증상은 함께 관찰되지 않았습니다.";
        items.add(new InstanceDashboardReadModel.ResourceEvidenceItem(
                resourceKey,
                "instance",
                usage,
                threshold,
                thresholdExceeded ? "threshold_exceeded" : "within_threshold",
                observedAt,
                requestSymptomPresent,
                patternContribution,
                operatorText));
    }

    private static List<InstanceDashboardReadModel.PatternEvidence> patterns(
            InstanceDashboardReadModel.ResourceEvidence resourceEvidence) {
        List<String> sharedResourceRefs = resourceEvidence.items().stream()
                .filter(item -> "shared_resource_pressure_pattern".equals(item.patternContribution()))
                .map(InstanceDashboardReadModel.ResourceEvidenceItem::resourceKey)
                .toList();
        if (sharedResourceRefs.isEmpty()) {
            return List.of();
        }
        return List.of(new InstanceDashboardReadModel.PatternEvidence(
                "shared_resource_pressure_pattern",
                "resource_pressure_with_request_symptom",
                sharedResourceRefs));
    }

    private static InstanceDashboardReadModel.StarterConnection starterConnection(
            Optional<StarterHeartbeatTelemetryRecord> heartbeat) {
        if (heartbeat.isEmpty()) {
            return InstanceDashboardReadModel.StarterConnection.missing();
        }
        StarterHeartbeatTelemetryRecord record = heartbeat.orElseThrow();
        return new InstanceDashboardReadModel.StarterConnection(
                "starter_heartbeat",
                record.lastReceivedAtUtc(),
                normalize(record.heartbeatStatus(), "unknown"),
                "observed",
                "control_plane_connection_observed",
                "does_not_change_metric_state");
    }

    private static InstanceDashboardReadModel.Snapshot snapshot(DashboardSnapshotDetailRow row) {
        if (row == null) {
            return null;
        }
        return new InstanceDashboardReadModel.Snapshot(
                row.snapshotId(),
                "dashboard_snapshots",
                row.generatedAt(),
                row.currentWindowStartUtc(),
                row.currentWindowEndUtc(),
                row.captureReason(),
                row.stateCode());
    }

    private static InstanceDashboardReadModel.ReadSemantics readSemantics(
            String windowSource,
            DashboardSnapshotDetailRow snapshotRow) {
        boolean snapshotMode = snapshotRow != null;
        return new InstanceDashboardReadModel.ReadSemantics(
                "accepted_metric_buckets",
                windowSource,
                snapshotMode ? "dashboard_snapshots" : null,
                false,
                snapshotMode,
                snapshotMode,
                false,
                snapshotMode,
                false);
    }

    private static InstanceDashboardReadModel.Links links(
            UUID projectId,
            UUID applicationId,
            UUID instanceId,
            DashboardSnapshotDetailRow snapshotRow) {
        String self = snapshotRow == null
                ? liveDashboardLink(projectId, applicationId, instanceId)
                : snapshotDashboardLink(projectId, applicationId, snapshotRow.snapshotId(), instanceId);
        return new InstanceDashboardReadModel.Links(
                self,
                applicationDashboardLink(projectId, applicationId),
                instanceEvidenceLink(projectId, applicationId, instanceId),
                snapshotTrendLink(projectId, applicationId, instanceId),
                snapshotRow == null ? null : applicationSnapshotDetailLink(projectId, applicationId, snapshotRow.snapshotId()));
    }

    private static List<String> excludedCapabilities() {
        return List.of(
                "instance_lifecycle_state",
                "instance_health_score",
                "root_cause",
                "recovery_proof",
                "marker_bucket_as_state_source",
                "accepted_at_cutoff_for_snapshot_instance_evidence",
                "application_snapshot_stored_read_model_recalculation");
    }

    private SlowEvidence slowEvidence(List<HistogramBucketEvidenceRow> rows) {
        List<HistogramBucketEvidenceRow> evidenceRows = List.copyOf(Objects.requireNonNullElse(rows, List.of()));
        if (evidenceRows.isEmpty()) {
            return SlowEvidence.missing();
        }
        long total = 0L;
        long atOrBelow500 = 0L;
        for (HistogramBucketEvidenceRow row : evidenceRows) {
            Optional<HistogramCounts> parsed = parseHistogramCounts(row.durationBucketsJson());
            if (parsed.isEmpty()) {
                return SlowEvidence.malformedEvidence();
            }
            total += parsed.orElseThrow().totalCount();
            atOrBelow500 += parsed.orElseThrow().countAtOrBelow500Ms();
        }
        if (total == 0L) {
            return SlowEvidence.missing();
        }
        long slowCount = Math.max(0L, total - atOrBelow500);
        return new SlowEvidence(slowCount, ratio(slowCount, total), false);
    }

    private Optional<HistogramCounts> parseHistogramCounts(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isArray() || root.isEmpty()) {
                return Optional.empty();
            }
            long total = -1L;
            long atOrBelow500 = 0L;
            long previousBoundary = -1L;
            long previousCount = -1L;
            for (JsonNode item : root) {
                Long boundary = longValue(item, "leMs");
                Long count = longValue(item, "count");
                if (boundary == null || count == null || boundary < 0L || count < 0L) {
                    return Optional.empty();
                }
                if (boundary <= previousBoundary || count < previousCount) {
                    return Optional.empty();
                }
                if (boundary <= 500L) {
                    atOrBelow500 = count;
                }
                previousBoundary = boundary;
                previousCount = count;
                total = count;
            }
            return total < 0L ? Optional.empty() : Optional.of(new HistogramCounts(total, atOrBelow500));
        } catch (JsonProcessingException exception) {
            return Optional.empty();
        }
    }

    private Optional<List<EndpointItem>> parseEndpointItems(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isArray()) {
                return Optional.empty();
            }
            List<EndpointItem> items = new ArrayList<>();
            for (JsonNode item : root) {
                String method = textValue(item, "method");
                String route = textValue(item, "route");
                Long requestCount = longValue(item, "requestCount");
                Long errorCount = longValue(item, "errorCount");
                if (method == null || route == null || requestCount == null || errorCount == null
                        || requestCount < 0L || errorCount < 0L || errorCount > requestCount) {
                    return Optional.empty();
                }
                if ("UNKNOWN".equalsIgnoreCase(route.trim())) {
                    continue;
                }
                items.add(new EndpointItem(
                        method.trim().toUpperCase(Locale.ROOT),
                        route.trim(),
                        requestCount,
                        errorCount));
            }
            return Optional.of(items);
        } catch (JsonProcessingException exception) {
            return Optional.empty();
        }
    }

    private static Long longValue(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        return value != null && value.isIntegralNumber() && value.canConvertToLong() ? value.asLong() : null;
    }

    private static String textValue(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        return value != null && value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
    }

    private static String missingMetricReason(String windowSource) {
        return "selected_application_snapshot".equals(windowSource)
                ? "no_metric_bucket_for_selected_snapshot_window"
                : "no_metric_bucket_for_live_window";
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeAnchorId(String value, int index) {
        return value == null || value.isBlank() ? "endpoint-evidence-" + index : value.trim();
    }

    private static EndpointIdentity endpointIdentity(String endpointKey, String method, String route) {
        String normalizedMethod = method == null ? null : method.trim().toUpperCase(Locale.ROOT);
        String normalizedRoute = route == null ? null : route.trim();
        String normalizedEndpointKey = endpointKey == null ? null : endpointKey.trim();
        if ((normalizedMethod == null || normalizedRoute == null) && normalizedEndpointKey != null) {
            int separator = normalizedEndpointKey.indexOf(' ');
            if (separator > 0 && separator < normalizedEndpointKey.length() - 1) {
                normalizedMethod = normalizedEndpointKey.substring(0, separator).trim().toUpperCase(Locale.ROOT);
                normalizedRoute = normalizedEndpointKey.substring(separator + 1).trim();
            }
        }
        if (normalizedMethod == null || normalizedRoute == null || normalizedRoute.isBlank()) {
            return null;
        }
        if (normalizedEndpointKey == null || normalizedEndpointKey.isBlank()) {
            normalizedEndpointKey = normalizedMethod + " " + normalizedRoute;
        }
        if (!normalizedEndpointKey.equals(normalizedMethod + " " + normalizedRoute)) {
            return null;
        }
        return new EndpointIdentity(normalizedMethod, normalizedRoute, normalizedEndpointKey);
    }

    private static BigDecimal ratio(long numerator, long denominator) {
        if (denominator == 0L) {
            return null;
        }
        int scale = Math.max(6, Long.toString(denominator).length() + 1);
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), scale, RoundingMode.HALF_UP)
                .stripTrailingZeros();
    }

    private static OffsetDateTime toUtcOffsetDateTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /**
     * live Instance Dashboard API path를 만든다.
     */
    public static String liveDashboardLink(UUID projectId, UUID applicationId, UUID instanceId) {
        return "/api/projects/%s/applications/%s/instances/%s/dashboard".formatted(
                projectId,
                applicationId,
                instanceId);
    }

    /**
     * snapshot Instance Dashboard API path를 만든다.
     */
    public static String snapshotDashboardLink(
            UUID projectId,
            UUID applicationId,
            UUID snapshotId,
            UUID instanceId) {
        return "/api/projects/%s/applications/%s/snapshots/%s/instances/%s/dashboard".formatted(
                projectId,
                applicationId,
                snapshotId,
                instanceId);
    }

    /**
     * Application Dashboard API path를 만든다.
     */
    public static String applicationDashboardLink(UUID projectId, UUID applicationId) {
        return "/api/projects/%s/applications/%s/dashboard".formatted(projectId, applicationId);
    }

    /**
     * 기존 Instance Evidence compatibility API path를 만든다.
     */
    public static String instanceEvidenceLink(UUID projectId, UUID applicationId, UUID instanceId) {
        return "/api/projects/%s/applications/%s/instances/%s/evidence".formatted(
                projectId,
                applicationId,
                instanceId);
    }

    /**
     * 기존 Instance Snapshot Trend API path를 만든다.
     */
    public static String snapshotTrendLink(UUID projectId, UUID applicationId, UUID instanceId) {
        return "/api/projects/%s/applications/%s/instances/%s/snapshot-trend".formatted(
                projectId,
                applicationId,
                instanceId);
    }

    /**
     * selected Application Snapshot Detail API path를 만든다.
     */
    public static String applicationSnapshotDetailLink(UUID projectId, UUID applicationId, UUID snapshotId) {
        return "/api/projects/%s/applications/%s/dashboard/snapshots/%s".formatted(
                projectId,
                applicationId,
                snapshotId);
    }

    private record CatalogContext(ApplicationEntity application, ApplicationInstanceEntity instance) {
    }

    private record HistogramCounts(long totalCount, long countAtOrBelow500Ms) {
    }

    private record SlowEvidence(Long slowCountOver500ms, BigDecimal slowShareOver500ms, boolean malformed) {

        private static SlowEvidence missing() {
            return new SlowEvidence(null, null, false);
        }

        private static SlowEvidence malformedEvidence() {
            return new SlowEvidence(null, null, true);
        }
    }

    private record EndpointItem(String method, String route, long requestCount, long errorCount) {

        private String endpointKey() {
            return method + " " + route;
        }
    }

    private record EndpointIdentity(String method, String route, String endpointKey) {
    }

    private record ApplicationEndpointAnchor(String method, String route, String endpointKey, String anchorId) {
    }

    private record EndpointAggregate(String method, String route, long requestCount, long errorCount) {

        private EndpointAggregate plus(long additionalRequestCount, long additionalErrorCount) {
            return new EndpointAggregate(
                    method,
                    route,
                    requestCount + additionalRequestCount,
                    errorCount + additionalErrorCount);
        }

        private String endpointKey() {
            return method + " " + route;
        }
    }
}
