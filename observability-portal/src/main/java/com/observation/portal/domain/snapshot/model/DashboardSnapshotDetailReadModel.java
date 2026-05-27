package com.observation.portal.domain.snapshot.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Snapshot detail API가 반환하는 typed/bounded wrapper다.
 *
 * <p>source는 `dashboard_snapshots` row metadata와 stored `read_model_json` projection으로 제한하며, raw JSON 전체를
 * escape hatch field로 노출하지 않는다.</p>
 */
public record DashboardSnapshotDetailReadModel(
        OffsetDateTime generatedAt,
        String source,
        SnapshotReadSemantics readSemantics,
        SnapshotMetadata snapshot,
        DashboardSnapshotMarkerItem marker,
        PreviousState previousState,
        LastHealthyAt lastHealthyAt,
        RecoveryMarker recoveryMarker,
        StoredReadModel readModel,
        SnapshotEndpointEvidence snapshotEndpointEvidence,
        InstanceSummary instanceSummary,
        SnapshotLinks links
) {

    public static final String SOURCE = "dashboard_snapshots";

    /**
     * detail response의 source/read semantics와 bounded projection block이 항상 존재하도록 검증한다.
     */
    public DashboardSnapshotDetailReadModel {
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        source = requireText(source, "source");
        if (!SOURCE.equals(source)) {
            throw new IllegalArgumentException("source must be " + SOURCE);
        }
        Objects.requireNonNull(readSemantics, "readSemantics must not be null");
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(marker, "marker must not be null");
        Objects.requireNonNull(previousState, "previousState must not be null");
        Objects.requireNonNull(lastHealthyAt, "lastHealthyAt must not be null");
        Objects.requireNonNull(readModel, "readModel must not be null");
        Objects.requireNonNull(snapshotEndpointEvidence, "snapshotEndpointEvidence must not be null");
        Objects.requireNonNull(instanceSummary, "instanceSummary must not be null");
        Objects.requireNonNull(links, "links must not be null");
    }

    /**
     * detail API가 current read model 재계산이나 live source join 없이 stored snapshot을 읽었음을 명시한다.
     */
    public record SnapshotReadSemantics(
            String mode,
            boolean currentStateRecalculated,
            List<String> liveSourcesJoined,
            boolean rawReadModelJsonExposed
    ) {

        public static SnapshotReadSemantics storedSnapshotDetail() {
            return new SnapshotReadSemantics(
                    "stored_snapshot_detail",
                    false,
                    List.of(),
                    false);
        }

        /**
         * recalculation 금지와 raw JSON 미노출 계약을 response shape에 고정한다.
         */
        public SnapshotReadSemantics {
            mode = requireText(mode, "mode");
            if (!"stored_snapshot_detail".equals(mode)) {
                throw new IllegalArgumentException("mode must be stored_snapshot_detail");
            }
            if (currentStateRecalculated) {
                throw new IllegalArgumentException("currentStateRecalculated must be false");
            }
            liveSourcesJoined = List.copyOf(Objects.requireNonNull(
                    liveSourcesJoined,
                    "liveSourcesJoined must not be null"));
            if (!liveSourcesJoined.isEmpty()) {
                throw new IllegalArgumentException("liveSourcesJoined must be empty");
            }
            if (rawReadModelJsonExposed) {
                throw new IllegalArgumentException("rawReadModelJsonExposed must be false");
            }
        }
    }

    /**
     * `dashboard_snapshots` row metadata와 5.8-a helper column 값을 API-safe field로 복사한 block이다.
     */
    public record SnapshotMetadata(
            UUID snapshotId,
            OffsetDateTime capturedAt,
            OffsetDateTime generatedAt,
            Window currentWindow,
            Window baselineWindow,
            String captureReason,
            String storedApplicationStateCode,
            String primaryRuleId,
            String primaryEndpointKey,
            BigDecimal maxConfidence
    ) {

        /**
         * stored row에서 온 identity/window/state/helper column을 검증한다.
         */
        public SnapshotMetadata {
            Objects.requireNonNull(snapshotId, "snapshotId must not be null");
            Objects.requireNonNull(capturedAt, "capturedAt must not be null");
            Objects.requireNonNull(generatedAt, "generatedAt must not be null");
            Objects.requireNonNull(currentWindow, "currentWindow must not be null");
            Objects.requireNonNull(baselineWindow, "baselineWindow must not be null");
            storedApplicationStateCode = requireText(storedApplicationStateCode, "storedApplicationStateCode");
            captureReason = trimNullable(captureReason);
            primaryRuleId = trimNullable(primaryRuleId);
            primaryEndpointKey = trimNullable(primaryEndpointKey);
        }
    }

    /**
     * 저장 당시 dashboard current/baseline window boundary다.
     */
    public record Window(OffsetDateTime startUtc, OffsetDateTime endUtc) {

        /**
         * window boundary가 시간 순서를 지키는지 검증한다.
         */
        public Window {
            Objects.requireNonNull(startUtc, "startUtc must not be null");
            Objects.requireNonNull(endUtc, "endUtc must not be null");
            if (!endUtc.isAfter(startUtc)) {
                throw new IllegalArgumentException("endUtc must be after startUtc");
            }
        }
    }

    /**
     * 같은 application의 strictly earlier stored snapshot에서 읽은 이전 state source다.
     */
    public record PreviousState(
            String stateCode,
            String source,
            UUID snapshotId,
            OffsetDateTime capturedAt
    ) {

        public static PreviousState none() {
            return new PreviousState(null, "no_previous_snapshot_in_retention", null, null);
        }

        /**
         * previousState가 snapshot source 또는 retention gap source 중 하나로 표현되도록 검증한다.
         */
        public PreviousState {
            source = requireText(source, "source");
            stateCode = trimNullable(stateCode);
        }
    }

    /**
     * normalized lastHealthyAt source를 previous active stored snapshot으로 제한한 block이다.
     */
    public record LastHealthyAt(
            OffsetDateTime value,
            String source,
            UUID snapshotId
    ) {

        public static LastHealthyAt none() {
            return new LastHealthyAt(null, "no_previous_active_snapshot_in_retention", null);
        }

        /**
         * lastHealthyAt source code가 비어 있지 않도록 검증한다.
         */
        public LastHealthyAt {
            source = requireText(source, "source");
        }
    }

    /**
     * 회복 완료가 아니라 "회복 관찰 중" marker를 detail에 보강하는 optional block이다.
     */
    public record RecoveryMarker(
            String markerId,
            DashboardSnapshotMarkerType type,
            DashboardSnapshotMarkerSeverity severity,
            String title,
            String summary,
            String recommendedAction,
            PreviousState previousState,
            LastHealthyAt lastHealthyAt
    ) {

        /**
         * recovery marker가 resolved/recovered semantics 없이 observing copy만 갖도록 검증한다.
         */
        public RecoveryMarker {
            markerId = requireText(markerId, "markerId");
            Objects.requireNonNull(type, "type must not be null");
            if (type != DashboardSnapshotMarkerType.RECOVERY_OBSERVED) {
                throw new IllegalArgumentException("type must be recovery_observed");
            }
            Objects.requireNonNull(severity, "severity must not be null");
            if (severity != DashboardSnapshotMarkerSeverity.WARNING) {
                throw new IllegalArgumentException("severity must be warning");
            }
            title = requireText(title, "title");
            summary = requireText(summary, "summary");
            recommendedAction = requireText(recommendedAction, "recommendedAction");
            Objects.requireNonNull(previousState, "previousState must not be null");
            Objects.requireNonNull(lastHealthyAt, "lastHealthyAt must not be null");
        }
    }

    /**
     * stored `read_model_json`의 UI-facing top-level block만 담는 bounded projection이다.
     */
    public record StoredReadModel(
            JsonNode application,
            JsonNode state,
            JsonNode starterConnection,
            JsonNode zeroInsight,
            JsonNode recovery,
            JsonNode metrics,
            JsonNode sourceScopedPercentiles,
            JsonNode triageCards,
            JsonNode endpointPriority
    ) {
    }

    /**
     * stored `snapshotEndpointEvidence.items[]`에 detail anchor를 붙인 bounded endpoint evidence block이다.
     */
    public record SnapshotEndpointEvidence(
            String source,
            int maxItems,
            String selectionPolicy,
            String unavailableReason,
            List<EndpointEvidenceItem> items
    ) {

        public static final String DEFAULT_SOURCE = "bounded_endpoint_evidence";
        public static final int DEFAULT_MAX_ITEMS = 10;

        /**
         * endpoint evidence item 수와 source field가 5.8-a bounded block 의미를 유지하는지 검증한다.
         */
        public SnapshotEndpointEvidence {
            source = requireText(source, "source");
            if (!DEFAULT_SOURCE.equals(source)) {
                throw new IllegalArgumentException("source must be " + DEFAULT_SOURCE);
            }
            if (maxItems != DEFAULT_MAX_ITEMS) {
                throw new IllegalArgumentException("maxItems must be " + DEFAULT_MAX_ITEMS);
            }
            selectionPolicy = trimNullable(selectionPolicy);
            unavailableReason = trimNullable(unavailableReason);
            items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
            if (items.size() > DEFAULT_MAX_ITEMS) {
                throw new IllegalArgumentException("items must not exceed maxItems");
            }
        }
    }

    /**
     * 한 snapshot detail 안에서만 stable한 endpoint evidence anchor와 stored evidence field를 담는다.
     */
    public record EndpointEvidenceItem(
            String anchorId,
            String method,
            String route,
            String endpointKey,
            Integer rank,
            String reason,
            List<String> ruleIds,
            BigDecimal confidence,
            Integer score,
            Long requestCount,
            BigDecimal errorRate,
            JsonNode durationBuckets,
            JsonNode baselineDurationBuckets,
            String bucketDistributionSource,
            JsonNode freshness,
            String recommendedAction
    ) {

        /**
         * anchor id와 canonical match key를 검증하고 나머지 stored field는 bounded optional 값으로 보존한다.
         */
        public EndpointEvidenceItem {
            anchorId = requireText(anchorId, "anchorId");
            endpointKey = requireText(endpointKey, "endpointKey");
            method = trimNullable(method);
            route = trimNullable(route);
            reason = trimNullable(reason);
            ruleIds = List.copyOf(Objects.requireNonNull(ruleIds, "ruleIds must not be null"));
            bucketDistributionSource = trimNullable(bucketDistributionSource);
            recommendedAction = trimNullable(recommendedAction);
        }
    }

    /**
     * Story 5.7이 고정한 `instanceSummary` minimum shape에 detail anchor resolution만 더한 block이다.
     */
    public record InstanceSummary(
            String schemaVersion,
            String source,
            int maxItems,
            String selectionPolicy,
            String unavailableReason,
            List<InstanceSummaryItem> items
    ) {

        public static final String DEFAULT_SCHEMA_VERSION = "1.0";
        public static final String DEFAULT_SOURCE = "bounded_instance_summary";
        public static final int DEFAULT_MAX_ITEMS = 50;

        /**
         * instance summary source path와 max item 계약을 유지하는지 검증한다.
         */
        public InstanceSummary {
            schemaVersion = requireText(schemaVersion, "schemaVersion");
            if (!DEFAULT_SCHEMA_VERSION.equals(schemaVersion)) {
                throw new IllegalArgumentException("schemaVersion must be " + DEFAULT_SCHEMA_VERSION);
            }
            source = requireText(source, "source");
            if (!DEFAULT_SOURCE.equals(source)) {
                throw new IllegalArgumentException("source must be " + DEFAULT_SOURCE);
            }
            if (maxItems != DEFAULT_MAX_ITEMS) {
                throw new IllegalArgumentException("maxItems must be " + DEFAULT_MAX_ITEMS);
            }
            selectionPolicy = trimNullable(selectionPolicy);
            unavailableReason = trimNullable(unavailableReason);
            items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
            if (items.size() > DEFAULT_MAX_ITEMS) {
                throw new IllegalArgumentException("items must not exceed maxItems");
            }
        }
    }

    /**
     * stored instance summary item의 minimum field와 endpoint evidence ref-only 목록을 보존한다.
     */
    public record InstanceSummaryItem(
            String instanceId,
            String instanceName,
            String observationStatus,
            JsonNode metricData,
            JsonNode starterConnection,
            JsonNode starterPercentilePoint,
            JsonNode resourceHints,
            JsonNode applicationTriageContribution,
            List<SnapshotEndpointEvidenceRef> endpointEvidenceRefs
    ) {

        /**
         * Story 5.7 minimum shape의 이름과 의미를 유지하고 ref-only 목록만 방어적으로 복사한다.
         */
        public InstanceSummaryItem {
            instanceId = requireText(instanceId, "instanceId");
            instanceName = requireText(instanceName, "instanceName");
            observationStatus = requireText(observationStatus, "observationStatus");
            endpointEvidenceRefs = List.copyOf(Objects.requireNonNull(
                    endpointEvidenceRefs,
                    "endpointEvidenceRefs must not be null"));
        }
    }

    /**
     * stored endpoint evidence ref에 detail anchor resolution 상태를 read-side로 보강한 값이다.
     */
    public record SnapshotEndpointEvidenceRef(
            String endpointKey,
            String method,
            String route,
            Integer relatedApplicationPriorityRank,
            List<String> relatedRuleIds,
            String snapshotDetailAnchor,
            String anchorStatus
    ) {

        /**
         * endpoint evidence ref가 body 없이 식별/anchor field만 포함하도록 검증한다.
         */
        public SnapshotEndpointEvidenceRef {
            endpointKey = requireText(endpointKey, "endpointKey");
            method = trimNullable(method);
            route = trimNullable(route);
            if (relatedApplicationPriorityRank != null && relatedApplicationPriorityRank < 1) {
                throw new IllegalArgumentException("relatedApplicationPriorityRank must be positive");
            }
            relatedRuleIds = List.copyOf(Objects.requireNonNull(relatedRuleIds, "relatedRuleIds must not be null"));
            snapshotDetailAnchor = trimNullable(snapshotDetailAnchor);
            anchorStatus = requireText(anchorStatus, "anchorStatus");
            if (!"resolved".equals(anchorStatus) && !"missing".equals(anchorStatus)) {
                throw new IllegalArgumentException("anchorStatus must be resolved or missing");
            }
        }
    }

    /**
     * detail API와 marker list API 사이를 오가는 link block이다.
     */
    public record SnapshotLinks(String self, String markers) {

        /**
         * detail self link와 marker list link가 실제 API path로 채워졌는지 검증한다.
         */
        public SnapshotLinks {
            self = requireText(self, "self");
            markers = requireText(markers, "markers");
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
