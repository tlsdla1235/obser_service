package com.observation.portal.domain.snapshot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.observation.portal.common.time.AcceptedBucketFreshness;
import com.observation.portal.common.time.AcceptedBucketFreshnessEvaluator;
import com.observation.portal.domain.bucket.model.EndpointEvidenceRow;
import com.observation.portal.domain.bucket.model.LocalPercentileEvidenceRow;
import com.observation.portal.domain.bucket.model.RuntimeRatioEvidenceRow;
import com.observation.portal.domain.bucket.model.WindowBucketAggregate;
import com.observation.portal.domain.bucket.repository.MetricBucketRepository;
import com.observation.portal.domain.catalog.entity.ApplicationInstanceEntity;
import com.observation.portal.domain.catalog.repository.ApplicationInstanceRepository;
import com.observation.portal.domain.dashboard.model.ApplicationDashboardReadModel;
import com.observation.portal.domain.dashboard.service.EndpointEvidenceAggregationService;
import com.observation.portal.domain.dashboard.service.EndpointEvidenceAggregationService.EndpointAggregate;
import com.observation.portal.domain.dashboard.service.EndpointEvidenceAggregationService.WindowEndpointEvidence;
import com.observation.portal.domain.ingest.model.StarterHeartbeatTelemetryRecord;
import com.observation.portal.domain.ingest.repository.StarterHeartbeatTelemetryRepository;
import com.observation.portal.domain.instance.service.InstanceEvidenceReadModelService;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotWriteCommand;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * dashboard current read model을 snapshot 저장용 bounded JSON으로 확장한다.
 *
 * <p>writer-owned top-level endpoint evidence block 이름은 `snapshotEndpointEvidence`다. Instance trend source는
 * Story 5.7이 고정한 `instanceSummary.items[]`를 그대로 채운다.</p>
 */
@Service
public class DashboardSnapshotReadModelEnricher {

    static final String SNAPSHOT_ENDPOINT_EVIDENCE_FIELD = "snapshotEndpointEvidence";
    static final int MAX_ENDPOINT_EVIDENCE_ITEMS = 10;
    static final int MAX_INSTANCE_SUMMARY_ITEMS = 50;
    private static final Duration STARTER_HEARTBEAT_RECENT_WINDOW = Duration.ofSeconds(90);
    private static final double HIGH_CONFIDENCE_THRESHOLD = 0.82d;

    private final ObjectMapper objectMapper;
    private final MetricBucketRepository metricBucketRepository;
    private final ApplicationInstanceRepository applicationInstanceRepository;
    private final StarterHeartbeatTelemetryRepository heartbeatTelemetryRepository;
    private final EndpointEvidenceAggregationService endpointEvidenceAggregationService;
    private final AcceptedBucketFreshnessEvaluator freshnessEvaluator;

    /**
     * bounded evidence fill에 필요한 JSON mapper와 neutral repository projections를 주입한다.
     */
    public DashboardSnapshotReadModelEnricher(
            ObjectMapper objectMapper,
            MetricBucketRepository metricBucketRepository,
            ApplicationInstanceRepository applicationInstanceRepository,
            StarterHeartbeatTelemetryRepository heartbeatTelemetryRepository,
            EndpointEvidenceAggregationService endpointEvidenceAggregationService,
            AcceptedBucketFreshnessEvaluator freshnessEvaluator) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.metricBucketRepository = Objects.requireNonNull(
                metricBucketRepository,
                "metricBucketRepository must not be null");
        this.applicationInstanceRepository = Objects.requireNonNull(
                applicationInstanceRepository,
                "applicationInstanceRepository must not be null");
        this.heartbeatTelemetryRepository = Objects.requireNonNull(
                heartbeatTelemetryRepository,
                "heartbeatTelemetryRepository must not be null");
        this.endpointEvidenceAggregationService = Objects.requireNonNull(
                endpointEvidenceAggregationService,
                "endpointEvidenceAggregationService must not be null");
        this.freshnessEvaluator = Objects.requireNonNull(freshnessEvaluator, "freshnessEvaluator must not be null");
    }

    /**
     * writer command의 read model에 endpoint evidence와 instance summary를 추가하고 helper column 값을 추출한다.
     */
    public EnrichedSnapshotReadModel enrich(DashboardSnapshotWriteCommand command) throws JsonProcessingException {
        DashboardSnapshotWriteCommand requiredCommand = Objects.requireNonNull(command, "command must not be null");
        ApplicationDashboardReadModel readModel = requiredCommand.readModel();
        ObjectNode root = objectMapper.valueToTree(readModel);
        List<StoredEndpointEvidence> endpointEvidence = endpointEvidence(readModel);
        root.set(SNAPSHOT_ENDPOINT_EVIDENCE_FIELD, endpointEvidenceBlock(endpointEvidence));
        root.set("instanceSummary", instanceSummary(requiredCommand, endpointEvidence));

        HelperColumns helperColumns = helperColumns(readModel);
        return new EnrichedSnapshotReadModel(
                objectMapper.writeValueAsString(root),
                helperColumns.primaryRuleId(),
                helperColumns.primaryEndpointKey(),
                helperColumns.maxConfidence());
    }

    private List<StoredEndpointEvidence> endpointEvidence(ApplicationDashboardReadModel readModel) {
        Map<String, StoredEndpointEvidence> selected = new LinkedHashMap<>();
        readModel.endpointPriority().stream()
                .sorted(Comparator.comparingInt(ApplicationDashboardReadModel.EndpointPriorityItem::rank))
                .map(this::fromEndpointPriority)
                .forEach(item -> selected.putIfAbsent(item.endpointKey(), item));

        readModel.triageCards().stream()
                .filter(card -> card.confidence() >= HIGH_CONFIDENCE_THRESHOLD)
                .flatMap(card -> fromTriageCard(card, "high_confidence_concern").stream())
                .forEach(item -> selected.putIfAbsent(item.endpointKey(), item));

        readModel.triageCards().stream()
                .flatMap(card -> fromTriageCard(card, "triage_affected_endpoint").stream())
                .forEach(item -> selected.putIfAbsent(item.endpointKey(), item));

        return selected.values().stream()
                .limit(MAX_ENDPOINT_EVIDENCE_ITEMS)
                .toList();
    }

    private StoredEndpointEvidence fromEndpointPriority(ApplicationDashboardReadModel.EndpointPriorityItem item) {
        ApplicationDashboardReadModel.EndpointPriorityEvidence evidence = item.evidence();
        return new StoredEndpointEvidence(
                item.method(),
                item.route(),
                item.endpointKey(),
                item.rank(),
                item.reason().value(),
                item.ruleIds(),
                item.confidence(),
                item.score(),
                evidence.requestCount(),
                evidence.errorRate(),
                evidence.durationBuckets(),
                evidence.baselineDurationBuckets(),
                evidence.bucketDistributionSource(),
                objectMapper.valueToTree(item.freshness()),
                item.recommendedAction());
    }

    private Optional<StoredEndpointEvidence> fromTriageCard(
            ApplicationDashboardReadModel.TriageCard card,
            String evidenceReason) {
        if (card.affectedEndpoint() == null || card.affectedEndpoint().isBlank()) {
            return Optional.empty();
        }
        EndpointIdentity identity = EndpointIdentity.fromEndpointKey(card.affectedEndpoint());
        return Optional.of(new StoredEndpointEvidence(
                identity.method(),
                identity.route(),
                identity.endpointKey(),
                null,
                evidenceReason,
                List.of(card.ruleId()),
                card.confidence(),
                card.score(),
                null,
                null,
                null,
                null,
                null,
                null,
                card.recommendation()));
    }

    private ObjectNode endpointEvidenceBlock(List<StoredEndpointEvidence> endpointEvidence) {
        ObjectNode block = objectMapper.createObjectNode();
        block.put("source", "bounded_endpoint_evidence");
        block.put("maxItems", MAX_ENDPOINT_EVIDENCE_ITEMS);
        block.put("selectionPolicy", "endpoint_priority_rank_then_high_confidence_concern_then_triage_affected_endpoint");
        ArrayNode items = block.putArray("items");
        endpointEvidence.forEach(item -> items.add(item.toJson(objectMapper)));
        return block;
    }

    private ObjectNode instanceSummary(
            DashboardSnapshotWriteCommand command,
            List<StoredEndpointEvidence> endpointEvidence) {
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("schemaVersion", "1.0");
        summary.put("source", "bounded_instance_summary");
        summary.put("maxItems", MAX_INSTANCE_SUMMARY_ITEMS);
        summary.put("selectionPolicy", "triage_contributors_then_freshness_attention_then_high_request_count");
        ArrayNode items = summary.putArray("items");
        List<InstanceSummaryDraft> drafts = instanceSummaryCandidates(command).stream()
                .map(instance -> instanceSummaryDraft(command, instance, endpointEvidence))
                .sorted(InstanceSummaryDraft.ORDERING)
                .limit(MAX_INSTANCE_SUMMARY_ITEMS)
                .toList();
        drafts.forEach(draft -> items.add(draft.toJson(objectMapper)));
        return summary;
    }

    /**
     * navigation list의 50개 cap과 분리해 snapshot summary selection policy가 평가할 전체 catalog 후보를 만든다.
     */
    private List<ApplicationDashboardReadModel.InstanceEntry> instanceSummaryCandidates(
            DashboardSnapshotWriteCommand command) {
        List<ApplicationInstanceEntity> catalogInstances = applicationInstanceRepository.findByApplicationId(
                command.applicationId());
        if (catalogInstances.isEmpty()) {
            return command.readModel().instances();
        }
        return catalogInstances.stream()
                .map(instance -> new ApplicationDashboardReadModel.InstanceEntry(
                        instance.id(),
                        instance.instanceName(),
                        instance.lastSeenAt(),
                        new ApplicationDashboardReadModel.InstanceEntryLinks(
                                InstanceEvidenceReadModelService.evidenceLink(
                                        command.projectId(),
                                        command.applicationId(),
                                        instance.id()))))
                .toList();
    }

    private InstanceSummaryDraft instanceSummaryDraft(
            DashboardSnapshotWriteCommand command,
            ApplicationDashboardReadModel.InstanceEntry instance,
            List<StoredEndpointEvidence> endpointEvidence) {
        OffsetDateTime windowStart = command.readModel().application().sourceWindow().current().startUtc();
        OffsetDateTime windowEnd = command.readModel().application().sourceWindow().current().endUtc();
        Optional<OffsetDateTime> latestBucketEndUtc =
                metricBucketRepository.findLatestBucketEndUtcByApplicationInstanceIdAtOrBeforeAcceptedAt(
                        instance.instanceId(),
                        command.currentWindowEndUtc().toInstant(),
                        command.snapshotCutoffAt());
        AcceptedBucketFreshness freshness = freshnessEvaluator.evaluateAt(
                command.currentWindowEndUtc().toInstant(),
                latestBucketEndUtc.map(OffsetDateTime::toInstant).orElse(null));
        WindowBucketAggregate aggregate =
                metricBucketRepository.findWindowAggregateByApplicationInstanceIdAcceptedAtOrBefore(
                        instance.instanceId(),
                        windowStart.toInstant(),
                        windowEnd.toInstant(),
                        command.snapshotCutoffAt());
        Optional<RuntimeRatioEvidenceRow> runtimeRatio =
                metricBucketRepository.findLatestRuntimeRatioEvidenceRowByApplicationInstanceIdAcceptedAtOrBefore(
                        instance.instanceId(),
                        windowStart.toInstant(),
                        windowEnd.toInstant(),
                        command.snapshotCutoffAt());
        Optional<StarterHeartbeatTelemetryRecord> heartbeat =
                heartbeatTelemetryRepository.findByIdentityAtOrBeforeReceivedAt(
                        command.projectId(),
                        command.readModel().application().name(),
                        command.readModel().application().environment(),
                        instance.instanceName(),
                        command.currentWindowEndUtc());
        Optional<PercentilePoint> percentilePoint = latestPercentilePoint(
                metricBucketRepository.findLocalPercentileEvidenceRowsByApplicationInstanceIdAcceptedAtOrBefore(
                        instance.instanceId(),
                        windowStart.toInstant(),
                        windowEnd.toInstant(),
                        command.snapshotCutoffAt()));
        List<EndpointEvidenceRef> refs = endpointEvidenceRefs(
                instance.instanceId(),
                windowStart,
                windowEnd,
                command.snapshotCutoffAt(),
                endpointEvidence);
        List<String> relatedRuleIds = refs.stream()
                .flatMap(ref -> ref.relatedRuleIds().stream())
                .distinct()
                .toList();
        boolean contributed = !relatedRuleIds.isEmpty();
        boolean freshnessAttention = freshness.status().name().toLowerCase(Locale.ROOT).contains("stale")
                || freshness.status().name().toLowerCase(Locale.ROOT).contains("down")
                || freshness.status().name().toLowerCase(Locale.ROOT).contains("waiting");
        return new InstanceSummaryDraft(
                instance.instanceId(),
                instance.instanceName(),
                instance.lastSeenAt(),
                freshnessAttention,
                aggregate.requestCount(),
                contributed,
                metricData(latestBucketEndUtc, freshness),
                starterConnection(heartbeat, command.currentWindowEndUtc()),
                percentilePoint.map(point -> point.toJson(objectMapper)).orElse(null),
                resourceHints(runtimeRatio),
                applicationTriageContribution(contributed, relatedRuleIds),
                refs);
    }

    private List<EndpointEvidenceRef> endpointEvidenceRefs(
            UUID instanceId,
            OffsetDateTime windowStart,
            OffsetDateTime windowEnd,
            OffsetDateTime snapshotCutoffAt,
            List<StoredEndpointEvidence> endpointEvidence) {
        if (endpointEvidence.isEmpty()) {
            return List.of();
        }
        List<EndpointEvidenceRow> rows =
                metricBucketRepository.findEndpointEvidenceRowsByApplicationInstanceIdAcceptedAtOrBefore(
                        instanceId,
                        windowStart.toInstant(),
                        windowEnd.toInstant(),
                        snapshotCutoffAt);
        WindowEndpointEvidence selected = endpointEvidenceAggregationService.mergeWindow(rows);
        if (selected.malformedEvidence()) {
            return List.of();
        }
        Map<String, EndpointAggregate> observed = selected.endpoints();
        return endpointEvidence.stream()
                .filter(item -> observed.containsKey(item.endpointKey()))
                .filter(item -> observed.get(item.endpointKey()).requestCount() > 0L)
                .map(item -> new EndpointEvidenceRef(
                        item.endpointKey(),
                        item.method(),
                        item.route(),
                        item.rank(),
                        item.ruleIds()))
                .limit(MAX_ENDPOINT_EVIDENCE_ITEMS)
                .toList();
    }

    private ObjectNode metricData(
            Optional<OffsetDateTime> latestBucketEndUtc,
            AcceptedBucketFreshness freshness) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("statusSource", "accepted_bucket");
        putIfNotNull(node, "lastAcceptedBucketAt", latestBucketEndUtc.map(OffsetDateTime::toString).orElse(null));
        node.put("freshnessLabel", freshness.status().name().toLowerCase(Locale.ROOT));
        return node;
    }

    private ObjectNode starterConnection(
            Optional<StarterHeartbeatTelemetryRecord> heartbeat,
            OffsetDateTime currentWindowEndUtc) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("statusSource", "starter_heartbeat");
        if (heartbeat.isEmpty()) {
            node.putNull("lastHeartbeatAt");
            node.put("lastHeartbeatStatus", "missing");
            node.put("connectionMeaning", "unknown");
            node.put("stateImpact", "none");
            return node;
        }

        StarterHeartbeatTelemetryRecord record = heartbeat.orElseThrow();
        node.put("lastHeartbeatAt", record.lastReceivedAtUtc().toString());
        String status = record.heartbeatStatus() == null
                ? "unknown"
                : record.heartbeatStatus().trim().toLowerCase(Locale.ROOT);
        node.put("lastHeartbeatStatus", status.isBlank() ? "unknown" : status);
        boolean recentReceived = "received".equals(status)
                && !record.lastReceivedAtUtc().isAfter(currentWindowEndUtc)
                && Duration.between(record.lastReceivedAtUtc(), currentWindowEndUtc)
                .compareTo(STARTER_HEARTBEAT_RECENT_WINDOW) <= 0;
        node.put("connectionMeaning", recentReceived ? "starter_connected" : "starter_disconnected");
        node.put("stateImpact", "none");
        return node;
    }

    private Optional<PercentilePoint> latestPercentilePoint(List<LocalPercentileEvidenceRow> rows) {
        return List.copyOf(Objects.requireNonNullElse(rows, List.of())).stream()
                .flatMap(row -> toPercentilePoint(row).stream())
                .max(Comparator.comparing(PercentilePoint::bucketEndUtc));
    }

    private Optional<PercentilePoint> toPercentilePoint(LocalPercentileEvidenceRow row) {
        try {
            JsonNode root = objectMapper.readTree(row.localPercentilesJson());
            if (root == null || !root.isObject()) {
                return Optional.empty();
            }
            if (!"instance_bucket".equals(text(root, "scope"))
                    || !"starter_local".equals(text(root, "source"))
                    || !Boolean.FALSE.equals(booleanValue(root, "mergeable"))) {
                return Optional.empty();
            }
            Long requestCount = longValue(root, "requestCount");
            Long p95Ms = longValue(root, "p95Ms");
            Long p99Ms = longValue(root, "p99Ms");
            if (requestCount == null || requestCount <= 0L
                    || p95Ms == null || p95Ms < 0L
                    || p99Ms == null || p99Ms < p95Ms) {
                return Optional.empty();
            }
            return Optional.of(new PercentilePoint(
                    row.bucketStartUtc(),
                    row.bucketEndUtc(),
                    requestCount,
                    p95Ms,
                    p99Ms));
        } catch (JsonProcessingException exception) {
            return Optional.empty();
        }
    }

    private ObjectNode resourceHints(Optional<RuntimeRatioEvidenceRow> runtimeRatio) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("source", "accepted_bucket_latest_sample");
        if (runtimeRatio.isEmpty()) {
            node.put("status", "missing");
            node.putNull("bucketEndUtc");
            return node;
        }
        RuntimeRatioEvidenceRow row = runtimeRatio.orElseThrow();
        node.put("status", "available");
        node.put("bucketEndUtc", row.bucketEndUtc().toString());
        putBigDecimalIfNotNull(node, "cpuUsageRatio", row.cpuUsageRatio());
        putBigDecimalIfNotNull(node, "heapUsedRatio", row.heapUsedRatio());
        putBigDecimalIfNotNull(node, "datasourcePoolUsageRatio", row.datasourcePoolUsageRatio());
        return node;
    }

    private ObjectNode applicationTriageContribution(boolean contributed, List<String> relatedRuleIds) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("status", "available");
        node.put("contributed", contributed);
        ArrayNode ruleIds = node.putArray("relatedRuleIds");
        relatedRuleIds.forEach(ruleIds::add);
        node.put("reason", contributed ? "endpoint_evidence_ref_related_to_application_triage" : "no_action_needed");
        return node;
    }

    private HelperColumns helperColumns(ApplicationDashboardReadModel readModel) {
        String primaryRuleId = readModel.triageCards().stream()
                .map(ApplicationDashboardReadModel.TriageCard::ruleId)
                .findFirst()
                .orElseGet(() -> readModel.endpointPriority().stream()
                        .flatMap(item -> item.ruleIds().stream())
                        .findFirst()
                        .orElse(null));
        String primaryEndpointKey = readModel.endpointPriority().stream()
                .map(ApplicationDashboardReadModel.EndpointPriorityItem::endpointKey)
                .findFirst()
                .orElseGet(() -> readModel.triageCards().stream()
                        .map(ApplicationDashboardReadModel.TriageCard::affectedEndpoint)
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .findFirst()
                        .orElse(null));
        Optional<Double> maxTriageConfidence = readModel.triageCards().stream()
                .map(ApplicationDashboardReadModel.TriageCard::confidence)
                .max(Double::compareTo);
        Optional<Double> maxEndpointConfidence = readModel.endpointPriority().stream()
                .map(ApplicationDashboardReadModel.EndpointPriorityItem::confidence)
                .max(Double::compareTo);
        BigDecimal maxConfidence = maxOf(maxTriageConfidence, maxEndpointConfidence)
                .map(value -> BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP))
                .orElse(null);
        return new HelperColumns(primaryRuleId, primaryEndpointKey, maxConfidence);
    }

    private static Optional<Double> maxOf(Optional<Double> first, Optional<Double> second) {
        if (first.isEmpty()) {
            return second;
        }
        if (second.isEmpty()) {
            return first;
        }
        return Optional.of(Math.max(first.orElseThrow(), second.orElseThrow()));
    }

    private static String text(JsonNode root, String fieldName) {
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

    private static void putIfNotNull(ObjectNode node, String fieldName, String value) {
        if (value == null) {
            node.putNull(fieldName);
        } else {
            node.put(fieldName, value);
        }
    }

    private static void putBigDecimalIfNotNull(ObjectNode node, String fieldName, BigDecimal value) {
        if (value != null) {
            node.put(fieldName, value);
        }
    }

    /**
     * writer가 저장할 최종 JSON과 helper column 값을 함께 반환한다.
     */
    public record EnrichedSnapshotReadModel(
            String readModelJson,
            String primaryRuleId,
            String primaryEndpointKey,
            BigDecimal maxConfidence
    ) {
    }

    private record HelperColumns(
            String primaryRuleId,
            String primaryEndpointKey,
            BigDecimal maxConfidence
    ) {
    }

    private record EndpointIdentity(String method, String route, String endpointKey) {

        private static EndpointIdentity fromEndpointKey(String endpointKey) {
            String trimmed = endpointKey.trim();
            int separator = trimmed.indexOf(' ');
            if (separator <= 0 || separator >= trimmed.length() - 1) {
                return new EndpointIdentity(null, null, trimmed);
            }
            return new EndpointIdentity(trimmed.substring(0, separator), trimmed.substring(separator + 1), trimmed);
        }
    }

    private record StoredEndpointEvidence(
            String method,
            String route,
            String endpointKey,
            Integer rank,
            String reason,
            List<String> ruleIds,
            Double confidence,
            Integer score,
            Long requestCount,
            BigDecimal errorRate,
            List<ApplicationDashboardReadModel.HistogramBucket> durationBuckets,
            List<ApplicationDashboardReadModel.HistogramBucket> baselineDurationBuckets,
            String bucketDistributionSource,
            JsonNode freshness,
            String recommendedAction
    ) {

        private StoredEndpointEvidence {
            endpointKey = Objects.requireNonNull(endpointKey, "endpointKey must not be null").trim();
            ruleIds = List.copyOf(Objects.requireNonNullElse(ruleIds, List.of()));
        }

        private ObjectNode toJson(ObjectMapper objectMapper) {
            ObjectNode node = objectMapper.createObjectNode();
            if (method != null) {
                node.put("method", method);
            }
            if (route != null) {
                node.put("route", route);
            }
            node.put("endpointKey", endpointKey);
            if (rank != null) {
                node.put("rank", rank);
            }
            if (reason != null) {
                node.put("reason", reason);
            }
            if (!ruleIds.isEmpty()) {
                ArrayNode values = node.putArray("ruleIds");
                ruleIds.forEach(values::add);
            }
            if (confidence != null) {
                node.put("confidence", confidence);
            }
            if (score != null) {
                node.put("score", score);
            }
            if (requestCount != null) {
                node.put("requestCount", requestCount);
            }
            if (errorRate != null) {
                node.put("errorRate", errorRate);
            }
            if (durationBuckets != null) {
                node.set("durationBuckets", objectMapper.valueToTree(durationBuckets));
            }
            if (baselineDurationBuckets != null) {
                node.set("baselineDurationBuckets", objectMapper.valueToTree(baselineDurationBuckets));
            }
            if (bucketDistributionSource != null) {
                node.put("bucketDistributionSource", bucketDistributionSource);
            }
            if (freshness != null) {
                node.set("freshness", freshness);
            }
            if (recommendedAction != null) {
                node.put("recommendedAction", recommendedAction);
            }
            return node;
        }
    }

    private record PercentilePoint(
            OffsetDateTime bucketStartUtc,
            OffsetDateTime bucketEndUtc,
            long requestCount,
            long p95Ms,
            long p99Ms
    ) {

        private ObjectNode toJson(ObjectMapper objectMapper) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("source", "starter_canonical_percentile");
            node.put("scope", "instance_bucket");
            node.put("bucketStartUtc", bucketStartUtc.toString());
            node.put("bucketEndUtc", bucketEndUtc.toString());
            node.put("requestCount", requestCount);
            node.put("p95Ms", p95Ms);
            node.put("p99Ms", p99Ms);
            return node;
        }
    }

    private record EndpointEvidenceRef(
            String endpointKey,
            String method,
            String route,
            Integer relatedApplicationPriorityRank,
            List<String> relatedRuleIds
    ) {

        private EndpointEvidenceRef {
            relatedRuleIds = List.copyOf(Objects.requireNonNullElse(relatedRuleIds, List.of()));
        }

        private ObjectNode toJson(ObjectMapper objectMapper) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("endpointKey", endpointKey);
            if (method != null) {
                node.put("method", method);
            }
            if (route != null) {
                node.put("route", route);
            }
            if (relatedApplicationPriorityRank != null) {
                node.put("relatedApplicationPriorityRank", relatedApplicationPriorityRank);
            }
            if (!relatedRuleIds.isEmpty()) {
                ArrayNode values = node.putArray("relatedRuleIds");
                relatedRuleIds.forEach(values::add);
            }
            return node;
        }
    }

    private record InstanceSummaryDraft(
            UUID instanceId,
            String instanceName,
            OffsetDateTime lastSeenAt,
            boolean freshnessAttention,
            long requestCount,
            boolean triageContributor,
            ObjectNode metricData,
            ObjectNode starterConnection,
            ObjectNode starterPercentilePoint,
            ObjectNode resourceHints,
            ObjectNode applicationTriageContribution,
            List<EndpointEvidenceRef> endpointEvidenceRefs
    ) {

        private static final Comparator<InstanceSummaryDraft> ORDERING =
                Comparator.comparing(InstanceSummaryDraft::triageContributor, Comparator.reverseOrder())
                        .thenComparing(InstanceSummaryDraft::freshnessAttention, Comparator.reverseOrder())
                        .thenComparing(Comparator.comparingLong(InstanceSummaryDraft::requestCount).reversed())
                        .thenComparing(InstanceSummaryDraft::lastSeenAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(InstanceSummaryDraft::instanceId);

        private InstanceSummaryDraft {
            endpointEvidenceRefs = List.copyOf(Objects.requireNonNullElse(endpointEvidenceRefs, List.of()));
        }

        private ObjectNode toJson(ObjectMapper objectMapper) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("instanceId", instanceId.toString());
            node.put("instanceName", instanceName);
            node.put("observationStatus", "observed");
            node.set("metricData", metricData);
            node.set("starterConnection", starterConnection);
            if (starterPercentilePoint == null) {
                node.putNull("starterPercentilePoint");
            } else {
                node.set("starterPercentilePoint", starterPercentilePoint);
            }
            node.set("resourceHints", resourceHints);
            node.set("applicationTriageContribution", applicationTriageContribution);
            ArrayNode refs = node.putArray("endpointEvidenceRefs");
            endpointEvidenceRefs.forEach(ref -> refs.add(ref.toJson(objectMapper)));
            return node;
        }
    }
}
