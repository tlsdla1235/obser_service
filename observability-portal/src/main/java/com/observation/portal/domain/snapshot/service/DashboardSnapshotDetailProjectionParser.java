package com.observation.portal.domain.snapshot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
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
    private static final String DEFAULT_ENDPOINT_SELECTION_POLICY =
            "endpoint_priority_rank_then_high_confidence_concern_then_triage_affected_endpoint";
    private static final String DEFAULT_INSTANCE_SELECTION_POLICY =
            "triage_contributors_then_freshness_attention_then_high_request_count";

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
                objectOrNull(root.get("application")),
                objectOrNull(root.get("state")),
                objectOrNull(root.get("starterConnection")),
                objectOrNull(root.get("zeroInsight")),
                objectOrNull(root.get("recovery")),
                objectOrNull(root.get("metrics")),
                objectOrNull(root.get("sourceScopedPercentiles")),
                arrayOrEmpty(root.get("triageCards")),
                arrayOrEmpty(root.get("endpointPriority")));
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
                    arrayOrNull(item.get("durationBuckets")),
                    arrayOrNull(item.get("baselineDurationBuckets")),
                    text(item, "bucketDistributionSource"),
                    objectOrNull(item.get("freshness")),
                    text(item, "recommendedAction")));
        }
        return new SnapshotEndpointEvidence(
                SnapshotEndpointEvidence.DEFAULT_SOURCE,
                SnapshotEndpointEvidence.DEFAULT_MAX_ITEMS,
                textOrDefault(block, "selectionPolicy", DEFAULT_ENDPOINT_SELECTION_POLICY),
                null,
                parsedItems);
    }

    private static SnapshotEndpointEvidence unavailableEndpointEvidence(String reason) {
        return new SnapshotEndpointEvidence(
                SnapshotEndpointEvidence.DEFAULT_SOURCE,
                SnapshotEndpointEvidence.DEFAULT_MAX_ITEMS,
                DEFAULT_ENDPOINT_SELECTION_POLICY,
                reason,
                List.of());
    }

    private InstanceSummary instanceSummary(JsonNode block) {
        if (block == null || !block.isObject()) {
            return unavailableInstanceSummary("stored_instance_summary_unavailable");
        }
        if (!InstanceSummary.DEFAULT_SCHEMA_VERSION.equals(text(block, "schemaVersion"))) {
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
                textOrDefault(block, "selectionPolicy", DEFAULT_INSTANCE_SELECTION_POLICY),
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
                objectOrNull(item.get("metricData")),
                objectOrNull(item.get("starterConnection")),
                objectOrNull(item.get("starterPercentilePoint")),
                objectOrNull(item.get("resourceHints")),
                objectOrNull(item.get("applicationTriageContribution")),
                endpointEvidenceRefs(item.get("endpointEvidenceRefs"))));
    }

    private static InstanceSummary unavailableInstanceSummary(String reason) {
        return new InstanceSummary(
                InstanceSummary.DEFAULT_SCHEMA_VERSION,
                InstanceSummary.DEFAULT_SOURCE,
                InstanceSummary.DEFAULT_MAX_ITEMS,
                DEFAULT_INSTANCE_SELECTION_POLICY,
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

    private static JsonNode objectOrNull(JsonNode value) {
        return value != null && value.isObject() ? value : NullNode.getInstance();
    }

    private ArrayNode arrayOrEmpty(JsonNode value) {
        return value != null && value.isArray() ? value.deepCopy() : objectMapper.createArrayNode();
    }

    private JsonNode arrayOrNull(JsonNode value) {
        return value != null && value.isArray() ? value : NullNode.getInstance();
    }

    private static String textOrDefault(JsonNode parent, String fieldName, String defaultValue) {
        String value = text(parent, fieldName);
        return value == null ? defaultValue : value;
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
