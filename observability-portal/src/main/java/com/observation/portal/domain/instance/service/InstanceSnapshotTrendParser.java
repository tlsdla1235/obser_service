package com.observation.portal.domain.instance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.portal.domain.instance.model.InstanceSnapshotTrendReadModel;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotTrendRow;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * `dashboard_snapshots.read_model_json.instanceSummary.items[]`에서 target instance point만 추출하는 parser다.
 *
 * <p>Story 5.7은 이 minimum shape를 parser contract로 고정한다. 후속 Story 5.8 writer는 이 경로와 최소 field의 이름과
 * 의미를 rename/remove/reinterpret하지 않고 채워야 하며, 이 parser는 backward-compatible optional field만 무시한다.</p>
 */
@Component
public class InstanceSnapshotTrendParser {

    private static final String SUPPORTED_SCHEMA_VERSION = "1.0";
    private static final int MAX_INSTANCE_SUMMARY_ITEMS = 50;

    private final ObjectMapper objectMapper;

    /**
     * 저장된 dashboard read model JSON을 읽기 위한 ObjectMapper를 주입한다.
     */
    public InstanceSnapshotTrendParser(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * snapshot row의 `instanceSummary.items[]`에서 target `application_instances.id` UUID와 정확히 일치하는 item을
     * trend point로 projection한다.
     *
     * <p>missing source, unsupported schema, target item 없음, malformed item은 오류 대신 empty로 수렴한다. nullable/empty
     * `resourceHints`는 없는 block처럼 다루고 endpoint evidence ref는 유효한 항목 기준 최대 10개만 복사한다. `instances[]`,
     * `snapshot`, current dashboard generation 결과는 trend source로 읽지 않는다.</p>
     */
    public Optional<InstanceSnapshotTrendReadModel.Point> projectPoint(
            DashboardSnapshotTrendRow row,
            UUID targetInstanceId) {
        DashboardSnapshotTrendRow requiredRow = Objects.requireNonNull(row, "row must not be null");
        UUID requiredTargetInstanceId = Objects.requireNonNull(targetInstanceId, "targetInstanceId must not be null");

        Optional<JsonNode> root = readRoot(requiredRow.readModelJson());
        if (root.isEmpty() || !root.orElseThrow().isObject()) {
            return Optional.empty();
        }
        JsonNode instanceSummary = root.orElseThrow().get("instanceSummary");
        if (instanceSummary == null || !instanceSummary.isObject()) {
            return Optional.empty();
        }
        if (!SUPPORTED_SCHEMA_VERSION.equals(textValue(instanceSummary, "schemaVersion"))) {
            return Optional.empty();
        }
        JsonNode items = instanceSummary.get("items");
        if (items == null || !items.isArray()) {
            return Optional.empty();
        }

        int itemCount = Math.min(items.size(), MAX_INSTANCE_SUMMARY_ITEMS);
        for (int index = 0; index < itemCount; index++) {
            JsonNode item = items.get(index);
            if (!matchesTargetInstance(item, requiredTargetInstanceId)) {
                continue;
            }
            Optional<InstanceSnapshotTrendReadModel.Point> point = toPoint(requiredRow, item);
            if (point.isPresent()) {
                return point;
            }
        }
        return Optional.empty();
    }

    private Optional<JsonNode> readRoot(String readModelJson) {
        try {
            return Optional.ofNullable(objectMapper.readTree(readModelJson));
        } catch (JsonProcessingException exception) {
            return Optional.empty();
        }
    }

    private static boolean matchesTargetInstance(JsonNode item, UUID targetInstanceId) {
        if (item == null || !item.isObject()) {
            return false;
        }
        JsonNode instanceId = item.get("instanceId");
        if (instanceId == null || !instanceId.isTextual()) {
            return false;
        }
        String rawInstanceId = instanceId.asText();
        if (rawInstanceId == null || rawInstanceId.isBlank()) {
            return false;
        }
        try {
            UUID parsed = UUID.fromString(rawInstanceId);
            return targetInstanceId.equals(parsed) && targetInstanceId.toString().equals(rawInstanceId);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private Optional<InstanceSnapshotTrendReadModel.Point> toPoint(
            DashboardSnapshotTrendRow row,
            JsonNode item) {
        try {
            return Optional.of(new InstanceSnapshotTrendReadModel.Point(
                    row.snapshotId(),
                    row.generatedAt(),
                    row.currentWindowEndUtc(),
                    row.stateCode(),
                    row.captureReason(),
                    requiredText(item, "instanceName"),
                    requiredText(item, "observationStatus"),
                    metricData(requiredObject(item, "metricData")),
                    starterConnection(requiredObject(item, "starterConnection")),
                    starterPercentilePoint(item.get("starterPercentilePoint")),
                    resourceHints(item.get("resourceHints")),
                    applicationTriageContribution(requiredObject(item, "applicationTriageContribution")),
                    endpointEvidenceRefs(item.get("endpointEvidenceRefs"))));
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    private static InstanceSnapshotTrendReadModel.MetricData metricData(JsonNode metricData) {
        return new InstanceSnapshotTrendReadModel.MetricData(
                requiredText(metricData, "statusSource"),
                nullableOffsetDateTime(metricData, "lastAcceptedBucketAt"),
                requiredText(metricData, "freshnessLabel"));
    }

    private static InstanceSnapshotTrendReadModel.StarterConnection starterConnection(JsonNode starterConnection) {
        return new InstanceSnapshotTrendReadModel.StarterConnection(
                requiredText(starterConnection, "statusSource"),
                nullableOffsetDateTime(starterConnection, "lastHeartbeatAt"),
                requiredText(starterConnection, "lastHeartbeatStatus"),
                requiredText(starterConnection, "connectionMeaning"),
                requiredText(starterConnection, "stateImpact"));
    }

    private static InstanceSnapshotTrendReadModel.StarterPercentilePoint starterPercentilePoint(JsonNode point) {
        if (point == null || point.isNull()) {
            return null;
        }
        if (!point.isObject()) {
            throw new IllegalArgumentException("starterPercentilePoint must be an object");
        }
        return new InstanceSnapshotTrendReadModel.StarterPercentilePoint(
                requiredText(point, "source"),
                requiredText(point, "scope"),
                requiredOffsetDateTime(point, "bucketStartUtc"),
                requiredOffsetDateTime(point, "bucketEndUtc"),
                requiredLong(point, "requestCount"),
                requiredLong(point, "p95Ms"),
                requiredLong(point, "p99Ms"));
    }

    private static InstanceSnapshotTrendReadModel.ResourceHints resourceHints(JsonNode resourceHints) {
        if (resourceHints == null || resourceHints.isNull()) {
            return null;
        }
        if (!resourceHints.isObject()) {
            throw new IllegalArgumentException("resourceHints must be an object");
        }
        String source = textValue(resourceHints, "source");
        String status = textValue(resourceHints, "status");
        if (resourceHints.size() == 0 || source == null || status == null) {
            return null;
        }
        return new InstanceSnapshotTrendReadModel.ResourceHints(
                source,
                status,
                nullableOffsetDateTime(resourceHints, "bucketEndUtc"),
                nullableDecimal(resourceHints, "cpuUsageRatio"),
                nullableDecimal(resourceHints, "heapUsedRatio"),
                nullableDecimal(resourceHints, "datasourcePoolUsageRatio"));
    }

    private static InstanceSnapshotTrendReadModel.ApplicationTriageContribution applicationTriageContribution(
            JsonNode contribution) {
        return new InstanceSnapshotTrendReadModel.ApplicationTriageContribution(
                requiredText(contribution, "status"),
                requiredBoolean(contribution, "contributed"),
                stringList(contribution.get("relatedRuleIds")),
                textValue(contribution, "reason"));
    }

    private static List<InstanceSnapshotTrendReadModel.EndpointEvidenceRef> endpointEvidenceRefs(JsonNode refs) {
        if (refs == null || refs.isNull() || !refs.isArray()) {
            return List.of();
        }
        List<InstanceSnapshotTrendReadModel.EndpointEvidenceRef> parsedRefs = new ArrayList<>();
        for (JsonNode ref : refs) {
            if (ref == null || !ref.isObject()) {
                continue;
            }
            try {
                parsedRefs.add(new InstanceSnapshotTrendReadModel.EndpointEvidenceRef(
                        requiredText(ref, "endpointKey"),
                        textValue(ref, "method"),
                        textValue(ref, "route"),
                        nullableInteger(ref, "relatedApplicationPriorityRank"),
                        stringList(ref.get("relatedRuleIds")),
                        textValue(ref, "snapshotDetailAnchor")));
                if (parsedRefs.size() == 10) {
                    break;
                }
            } catch (IllegalArgumentException exception) {
                // endpointEvidenceRefs는 reference-only 목록이므로 malformed ref 하나만 건너뛴다.
            }
        }
        return parsedRefs;
    }

    private static JsonNode requiredObject(JsonNode parent, String fieldName) {
        JsonNode value = parent.get(fieldName);
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException(fieldName + " must be an object");
        }
        return value;
    }

    private static String requiredText(JsonNode parent, String fieldName) {
        String value = textValue(parent, fieldName);
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must be text");
        }
        return value;
    }

    private static String textValue(JsonNode parent, String fieldName) {
        JsonNode value = parent.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            return null;
        }
        String trimmed = value.asText().trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static OffsetDateTime requiredOffsetDateTime(JsonNode parent, String fieldName) {
        OffsetDateTime value = nullableOffsetDateTime(parent, fieldName);
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must be timestamp text");
        }
        return value;
    }

    private static OffsetDateTime nullableOffsetDateTime(JsonNode parent, String fieldName) {
        String value = textValue(parent, fieldName);
        return value == null ? null : OffsetDateTime.parse(value);
    }

    private static long requiredLong(JsonNode parent, String fieldName) {
        JsonNode value = parent.get(fieldName);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            throw new IllegalArgumentException(fieldName + " must be integer");
        }
        return value.asLong();
    }

    private static boolean requiredBoolean(JsonNode parent, String fieldName) {
        JsonNode value = parent.get(fieldName);
        if (value == null || !value.isBoolean()) {
            throw new IllegalArgumentException(fieldName + " must be boolean");
        }
        return value.asBoolean();
    }

    private static Integer nullableInteger(JsonNode parent, String fieldName) {
        JsonNode value = parent.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException(fieldName + " must be integer");
        }
        return value.asInt();
    }

    private static BigDecimal nullableDecimal(JsonNode parent, String fieldName) {
        JsonNode value = parent.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isNumber()) {
            throw new IllegalArgumentException(fieldName + " must be number");
        }
        return value.decimalValue();
    }

    private static List<String> stringList(JsonNode value) {
        if (value == null || value.isNull()) {
            return List.of();
        }
        if (!value.isArray()) {
            throw new IllegalArgumentException("value must be array");
        }
        List<String> strings = new ArrayList<>();
        for (JsonNode item : value) {
            if (item == null || !item.isTextual() || item.asText().isBlank()) {
                throw new IllegalArgumentException("value must contain text items");
            }
            strings.add(item.asText().trim());
        }
        return strings;
    }
}
