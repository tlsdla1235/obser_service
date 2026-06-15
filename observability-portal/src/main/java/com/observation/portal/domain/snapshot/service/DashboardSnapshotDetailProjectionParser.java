package com.observation.portal.domain.snapshot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.observation.portal.domain.dashboard.model.ApplicationDashboardReadModel;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel.EndpointEvidenceItem;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel.InstanceSummary;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel.InstanceSummaryItem;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel.SnapshotEndpointEvidence;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel.SnapshotEndpointEvidenceRef;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel.StoredReadModel;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotStoredReadModelProjection;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Stored `dashboard_snapshots.read_model_json`을 snapshot detail/marker용 bounded projection으로 변환한다.
 *
 * <p>root JSON이 깨졌거나 object가 아니면 projection 실패로 처리하지만, optional top-level block이 없거나 malformed이면
 * unavailable/empty block으로 수렴하고 current source에서 다시 채우지 않는다.</p>
 */
@Component
public class DashboardSnapshotDetailProjectionParser {

    private static final BigDecimal HIGH_CONFIDENCE_THRESHOLD = new BigDecimal("0.82");
    private static final String STORED_READ_MODEL_SELECTION_POLICY = "stored_read_model";
    private static final String STORED_INSTANCE_SUMMARY_SCHEMA_VERSION = "1.0";
    private static final long MINIMUM_REQUEST_COUNT = 30L;

    private final ObjectMapper objectMapper;
    private final SnapshotEndpointEvidenceAnchorResolver anchorResolver;

    /**
     * stored JSON parser와 endpoint evidence anchor resolver를 주입한다.
     */
    public DashboardSnapshotDetailProjectionParser(
            ObjectMapper objectMapper,
            SnapshotEndpointEvidenceAnchorResolver anchorResolver) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.anchorResolver = Objects.requireNonNull(anchorResolver, "anchorResolver must not be null");
    }

    /**
     * stored JSON root를 읽고 detail wrapper에 들어갈 bounded block과 classifier signal을 반환한다.
     */
    public DashboardSnapshotStoredReadModelProjection project(String readModelJson) {
        JsonNode root = readRoot(readModelJson);
        StoredReadModel readModel = new StoredReadModel(
                jsonValue(schemaVersion(root)),
                jsonValue(mode(root)),
                jsonValue(window(root)),
                jsonValue(thresholds(root)),
                jsonValue(operatorSummary(root)),
                jsonValue(dataQuality(root)),
                jsonValue(signals(root)),
                arrayValueOrEmpty(root.get("stateReasons")),
                arrayValueOrEmpty(root.get("attentionEvidence")),
                arrayValueOrEmpty(root.get("firstLookCandidates")),
                jsonValue(readSemantics()),
                objectValueOrNull(root.get("application")),
                objectValueOrNull(root.get("state")),
                objectValueOrNull(root.get("starterConnection")),
                objectValueOrNull(root.get("zeroInsight")),
                objectValueOrNull(root.get("recovery")),
                objectValueOrNull(root.get("metrics")),
                objectValueOrNull(root.get("sourceScopedPercentiles")),
                arrayValueOrEmpty(root.get("triageCards")),
                arrayValueOrEmpty(root.get("endpointPriority")));
        SnapshotEndpointEvidence endpointEvidence = endpointEvidence(root.get("snapshotEndpointEvidence"));
        InstanceSummary instanceSummary = anchorResolver.resolve(endpointEvidence, instanceSummary(root.get("instanceSummary")));
        JsonNode recovery = objectOrNull(root.get("recovery"));
        JsonNode zeroInsight = objectOrNull(root.get("zeroInsight"));
        JsonNode triageCards = arrayOrEmpty(root.get("triageCards"));
        return new DashboardSnapshotStoredReadModelProjection(
                readModel,
                endpointEvidence,
                instanceSummary,
                recovery,
                zeroInsight,
                triageCards,
                recoveryObserved(recovery, zeroInsight),
                recoveryExpressionPresent(recovery),
                triageSeverityPresent(triageCards, "critical"),
                triageSeverityPresent(triageCards, "warning"),
                maxTriageConfidence(triageCards));
    }

    private JsonNode readRoot(String readModelJson) {
        try {
            JsonNode root = objectMapper.readTree(readModelJson);
            if (root == null || !root.isObject()) {
                throw new DashboardSnapshotProjectionException("stored read_model_json root must be an object");
            }
            return root;
        } catch (JsonProcessingException exception) {
            throw new DashboardSnapshotProjectionException("stored read_model_json projection failed", exception);
        }
    }

    private JsonNode schemaVersion(JsonNode root) {
        JsonNode value = root.get("schemaVersion");
        return value == null
                ? objectMapper.getNodeFactory().textNode(ApplicationDashboardReadModel.SCHEMA_VERSION)
                : value;
    }

    private JsonNode mode(JsonNode root) {
        JsonNode value = root.get("mode");
        return value == null
                ? objectMapper.getNodeFactory().textNode(ApplicationDashboardReadModel.SNAPSHOT_MODE)
                : value;
    }

    private JsonNode window(JsonNode root) {
        JsonNode value = root.get("window");
        if (value != null && value.isObject()) {
            return value;
        }
        JsonNode sourceWindow = root.path("application").path("sourceWindow");
        JsonNode current = sourceWindow.path("recent_30_minutes");
        if (!current.isObject()) {
            current = sourceWindow.path("current");
        }
        String startUtc = text(current, "startUtc");
        String endUtc = text(current, "endUtc");
        if (startUtc == null || endUtc == null) {
            return NullNode.getInstance();
        }
        ObjectNode fallback = objectMapper.createObjectNode();
        fallback.put("type", "recent_30_minutes");
        fallback.put("startUtc", startUtc);
        fallback.put("endUtc", endUtc);
        return fallback;
    }

    private JsonNode thresholds(JsonNode root) {
        JsonNode value = root.get("thresholds");
        return value != null && value.isObject()
                ? value
                : objectMapper.valueToTree(ApplicationDashboardReadModel.Thresholds.mvp());
    }

    private JsonNode operatorSummary(JsonNode root) {
        JsonNode value = root.get("operatorSummary");
        if (value != null && value.isObject()) {
            return value;
        }
        JsonNode state = root.path("state");
        String headline = firstText(
                text(state, "rationale"),
                text(root.path("zeroInsight"), "message"),
                "저장된 snapshot read model입니다.");
        ObjectNode fallback = objectMapper.createObjectNode();
        fallback.put("headline", headline);
        String primaryProblemCode = firstTriageRuleId(root.path("triageCards"));
        if (primaryProblemCode == null) {
            fallback.putNull("primaryProblemCode");
        } else {
            fallback.put("primaryProblemCode", primaryProblemCode);
        }
        fallback.put("firstLookText", firstText(
                text(root.path("zeroInsight"), "recommendedAction"),
                text(state, "recommendedAction"),
                "저장된 snapshot detail을 확인하세요."));
        return fallback;
    }

    private JsonNode dataQuality(JsonNode root) {
        JsonNode value = root.get("dataQuality");
        if (value != null && value.isObject()) {
            return value;
        }
        JsonNode metrics = root.path("metrics");
        JsonNode application = root.path("application");
        ObjectNode fallback = objectMapper.createObjectNode();
        Long requestCount = longValue(metrics, "requestCount");
        fallback.put("state", "legacy_snapshot_projection");
        fallback.put("requestCount", requestCount == null ? 0L : requestCount);
        fallback.put("minimumRequestCount", MINIMUM_REQUEST_COUNT);
        String lastObservedAt = text(application, "lastAcceptedBucketAt");
        if (lastObservedAt == null) {
            fallback.putNull("lastObservedAt");
        } else {
            fallback.put("lastObservedAt", lastObservedAt);
        }
        ArrayNode limitations = fallback.putArray("limitations");
        limitations.add("legacy_snapshot_without_canonical_fields");
        limitations.add("baseline_comparison_not_used_for_mvp");
        return fallback;
    }

    private JsonNode signals(JsonNode root) {
        JsonNode value = root.get("signals");
        if (value != null && value.isObject()) {
            return value;
        }
        JsonNode metrics = root.path("metrics");
        ObjectNode signals = objectMapper.createObjectNode();
        ObjectNode red = signals.putObject("red");
        Long requestCount = longValue(metrics, "requestCount");
        Long errorCount = longValue(metrics, "errorCount");
        red.put("requestCount", requestCount == null ? 0L : requestCount);
        red.put("errorCount", errorCount == null ? 0L : errorCount);
        red.put("errorSemantic", "server_error_5xx");
        putDecimalOrNull(red, "errorRate", decimal(metrics, "errorRate"));
        red.putNull("slowCountOver500ms");
        red.putNull("slowShareOver500ms");
        red.put("latencyEvidenceStatus", "unavailable");
        ObjectNode use = signals.putObject("use");
        use.set("datasourcePoolUsage", missingResourceSignal("0.85"));
        use.set("cpuUsage", missingResourceSignal("0.85"));
        use.set("heapUsage", missingResourceSignal("0.90"));
        return signals;
    }

    private JsonNode readSemantics() {
        return objectMapper.valueToTree(ApplicationDashboardReadModel.ReadSemantics.snapshot());
    }

    private SnapshotEndpointEvidence endpointEvidence(JsonNode block) {
        if (block == null || !block.isObject()) {
            return unavailableEndpointEvidence("stored_snapshot_endpoint_evidence_unavailable");
        }
        JsonNode items = block.get("items");
        if (items == null || !items.isArray()) {
            return unavailableEndpointEvidence("stored_snapshot_endpoint_evidence_unavailable");
        }
        List<EndpointEvidenceItem> parsedItems = new ArrayList<>();
        int maxItems = Math.min(items.size(), SnapshotEndpointEvidence.DEFAULT_MAX_ITEMS);
        for (int index = 0; index < maxItems; index++) {
            JsonNode item = items.get(index);
            if (item == null || !item.isObject()) {
                continue;
            }
            String endpointKey = text(item, "endpointKey");
            if (endpointKey == null) {
                continue;
            }
            parsedItems.add(new EndpointEvidenceItem(
                    "endpoint-evidence-" + (index + 1),
                    text(item, "method"),
                    text(item, "route"),
                    endpointKey,
                    integer(item, "rank"),
                    text(item, "reason"),
                    stringList(item.get("ruleIds")),
                    decimal(item, "confidence"),
                    integer(item, "score"),
                    longValue(item, "requestCount"),
                    decimal(item, "errorRate"),
                    arrayValueOrNull(item.get("durationBuckets")),
                    arrayValueOrNull(item.get("baselineDurationBuckets")),
                    text(item, "bucketDistributionSource"),
                    objectValueOrNull(item.get("freshness")),
                    text(item, "recommendedAction")));
        }
        return new SnapshotEndpointEvidence(
                SnapshotEndpointEvidence.DEFAULT_SOURCE,
                SnapshotEndpointEvidence.DEFAULT_MAX_ITEMS,
                STORED_READ_MODEL_SELECTION_POLICY,
                null,
                parsedItems);
    }

    private static SnapshotEndpointEvidence unavailableEndpointEvidence(String reason) {
        return new SnapshotEndpointEvidence(
                SnapshotEndpointEvidence.DEFAULT_SOURCE,
                SnapshotEndpointEvidence.DEFAULT_MAX_ITEMS,
                STORED_READ_MODEL_SELECTION_POLICY,
                reason,
                List.of());
    }

    private InstanceSummary instanceSummary(JsonNode block) {
        if (block == null || !block.isObject()) {
            return unavailableInstanceSummary("stored_instance_summary_unavailable");
        }
        if (!STORED_INSTANCE_SUMMARY_SCHEMA_VERSION.equals(text(block, "schemaVersion"))) {
            return unavailableInstanceSummary("stored_instance_summary_unavailable");
        }
        JsonNode items = block.get("items");
        if (items == null || !items.isArray()) {
            return unavailableInstanceSummary("stored_instance_summary_unavailable");
        }
        List<InstanceSummaryItem> parsedItems = new ArrayList<>();
        int maxItems = Math.min(items.size(), InstanceSummary.DEFAULT_MAX_ITEMS);
        for (int index = 0; index < maxItems; index++) {
            JsonNode item = items.get(index);
            parseInstanceSummaryItem(item).ifPresent(parsedItems::add);
        }
        return new InstanceSummary(
                InstanceSummary.DEFAULT_SCHEMA_VERSION,
                InstanceSummary.DEFAULT_SOURCE,
                InstanceSummary.DEFAULT_MAX_ITEMS,
                STORED_READ_MODEL_SELECTION_POLICY,
                null,
                parsedItems);
    }

    private java.util.Optional<InstanceSummaryItem> parseInstanceSummaryItem(JsonNode item) {
        if (item == null || !item.isObject()) {
            return java.util.Optional.empty();
        }
        String instanceId = text(item, "instanceId");
        String instanceName = text(item, "instanceName");
        String observationStatus = text(item, "observationStatus");
        if (instanceId == null || instanceName == null || observationStatus == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new InstanceSummaryItem(
                instanceId,
                instanceName,
                observationStatus,
                objectValueOrNull(item.get("metricData")),
                objectValueOrNull(item.get("starterConnection")),
                objectValueOrNull(item.get("starterPercentilePoint")),
                objectValueOrNull(item.get("resourceHints")),
                objectValueOrNull(item.get("applicationTriageContribution")),
                endpointEvidenceRefs(item.get("endpointEvidenceRefs"))));
    }

    private static InstanceSummary unavailableInstanceSummary(String reason) {
        return new InstanceSummary(
                InstanceSummary.DEFAULT_SCHEMA_VERSION,
                InstanceSummary.DEFAULT_SOURCE,
                InstanceSummary.DEFAULT_MAX_ITEMS,
                STORED_READ_MODEL_SELECTION_POLICY,
                reason,
                List.of());
    }

    private List<SnapshotEndpointEvidenceRef> endpointEvidenceRefs(JsonNode refs) {
        if (refs == null || !refs.isArray()) {
            return List.of();
        }
        List<SnapshotEndpointEvidenceRef> parsedRefs = new ArrayList<>();
        int maxRefs = Math.min(refs.size(), SnapshotEndpointEvidence.DEFAULT_MAX_ITEMS);
        for (int index = 0; index < maxRefs; index++) {
            JsonNode ref = refs.get(index);
            if (ref == null || !ref.isObject()) {
                continue;
            }
            String endpointKey = text(ref, "endpointKey");
            if (endpointKey == null) {
                continue;
            }
            parsedRefs.add(new SnapshotEndpointEvidenceRef(
                    endpointKey,
                    text(ref, "method"),
                    text(ref, "route"),
                    integer(ref, "relatedApplicationPriorityRank"),
                    stringList(ref.get("relatedRuleIds")),
                    text(ref, "snapshotDetailAnchor"),
                    "missing"));
        }
        return parsedRefs;
    }

    private static boolean recoveryObserved(JsonNode recovery, JsonNode zeroInsight) {
        return booleanValue(recovery, "isRecovering")
                || "observing_recovery".equals(text(zeroInsight, "reasonCode"));
    }

    private static boolean recoveryExpressionPresent(JsonNode recovery) {
        return recovery != null
                && recovery.isObject()
                && (booleanValue(recovery, "isRecovering")
                || text(recovery, "recommendedAction") != null
                || recovery.hasNonNull("retryAfterSeconds"));
    }

    private static boolean triageSeverityPresent(JsonNode triageCards, String severity) {
        if (triageCards == null || !triageCards.isArray()) {
            return false;
        }
        for (JsonNode card : triageCards) {
            String storedSeverity = text(card, "severity");
            if (storedSeverity != null && severity.equals(storedSeverity.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static BigDecimal maxTriageConfidence(JsonNode triageCards) {
        if (triageCards == null || !triageCards.isArray()) {
            return null;
        }
        BigDecimal max = null;
        for (JsonNode card : triageCards) {
            BigDecimal confidence = decimal(card, "confidence");
            if (confidence != null && (max == null || confidence.compareTo(max) > 0)) {
                max = confidence;
            }
        }
        return max;
    }

    /**
     * stored triage confidence 중 high-confidence threshold를 넘는 값이 있는지 판단한다.
     */
    static boolean hasHighConfidenceTriage(DashboardSnapshotStoredReadModelProjection projection) {
        BigDecimal maxConfidence = projection.maxTriageConfidence();
        return maxConfidence != null && maxConfidence.compareTo(HIGH_CONFIDENCE_THRESHOLD) >= 0;
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        throw new IllegalArgumentException("at least one fallback text must be provided");
    }

    private static String firstTriageRuleId(JsonNode triageCards) {
        if (triageCards == null || !triageCards.isArray()) {
            return null;
        }
        for (JsonNode card : triageCards) {
            String ruleId = text(card, "ruleId");
            if (ruleId != null) {
                return ruleId;
            }
        }
        return null;
    }

    private ObjectNode missingResourceSignal(String threshold) {
        ObjectNode node = objectMapper.createObjectNode();
        node.putNull("max");
        node.put("threshold", new BigDecimal(threshold));
        node.put("status", "missing");
        node.putNull("observedAt");
        return node;
    }

    private static void putDecimalOrNull(ObjectNode node, String fieldName, BigDecimal value) {
        if (value == null) {
            node.putNull(fieldName);
        } else {
            node.put(fieldName, value);
        }
    }

    private static JsonNode objectOrNull(JsonNode value) {
        return value != null && value.isObject() ? value : NullNode.getInstance();
    }

    private ArrayNode arrayOrEmpty(JsonNode value) {
        return value != null && value.isArray() ? value.deepCopy() : objectMapper.createArrayNode();
    }

    private JsonNode arrayOrNull(JsonNode value) {
        return value != null && value.isArray() ? value : NullNode.getInstance();
    }

    /**
     * API response DTO에는 Jackson tree type을 싣지 않고 Spring/Jackson이 그대로 JSON으로 직렬화할 수 있는 값만 전달한다.
     */
    private Object jsonValue(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        return objectMapper.convertValue(value, Object.class);
    }

    private Object objectValueOrNull(JsonNode value) {
        return jsonValue(objectOrNull(value));
    }

    private Object arrayValueOrEmpty(JsonNode value) {
        return jsonValue(arrayOrEmpty(value));
    }

    private Object arrayValueOrNull(JsonNode value) {
        return jsonValue(arrayOrNull(value));
    }

    private static String text(JsonNode parent, String fieldName) {
        if (parent == null || !parent.isObject()) {
            return null;
        }
        JsonNode value = parent.get(fieldName);
        if (value == null || !value.isTextual()) {
            return null;
        }
        String trimmed = value.asText().trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Boolean booleanObjectValue(JsonNode parent, String fieldName) {
        if (parent == null || !parent.isObject()) {
            return null;
        }
        JsonNode value = parent.get(fieldName);
        return value != null && value.isBoolean() ? value.asBoolean() : null;
    }

    private static boolean booleanValue(JsonNode parent, String fieldName) {
        return Boolean.TRUE.equals(booleanObjectValue(parent, fieldName));
    }

    private static Integer integer(JsonNode parent, String fieldName) {
        if (parent == null || !parent.isObject()) {
            return null;
        }
        JsonNode value = parent.get(fieldName);
        return value != null && value.isIntegralNumber() && value.canConvertToInt() ? value.asInt() : null;
    }

    private static Long longValue(JsonNode parent, String fieldName) {
        if (parent == null || !parent.isObject()) {
            return null;
        }
        JsonNode value = parent.get(fieldName);
        return value != null && value.isIntegralNumber() && value.canConvertToLong() ? value.asLong() : null;
    }

    private static BigDecimal decimal(JsonNode parent, String fieldName) {
        if (parent == null || !parent.isObject()) {
            return null;
        }
        JsonNode value = parent.get(fieldName);
        return value != null && value.isNumber() ? value.decimalValue() : null;
    }

    private static List<String> stringList(JsonNode value) {
        if (value == null || !value.isArray()) {
            return List.of();
        }
        List<String> strings = new ArrayList<>();
        for (JsonNode item : value) {
            if (item != null && item.isTextual() && !item.asText().isBlank()) {
                strings.add(item.asText().trim().toLowerCase(Locale.ROOT).isEmpty()
                        ? item.asText().trim()
                        : item.asText().trim());
            }
        }
        return strings;
    }
}
