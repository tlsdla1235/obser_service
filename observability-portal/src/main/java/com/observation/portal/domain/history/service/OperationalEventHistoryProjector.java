package com.observation.portal.domain.history.service;

import com.observation.portal.domain.history.model.OperationalEventEvidence;
import com.observation.portal.domain.history.model.OperationalEventItem;
import com.observation.portal.domain.history.model.OperationalEventLinks;
import com.observation.portal.domain.history.model.OperationalEventSeverity;
import com.observation.portal.domain.history.model.OperationalEventType;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel.EndpointEvidenceItem;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel.SnapshotEndpointEvidence;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailRow;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotStoredReadModelProjection;
import com.observation.portal.domain.snapshot.service.DashboardSnapshotDetailProjectionParser;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Stored snapshot row를 operational event candidate로 바꾸는 projection boundary다.
 *
 * <p>current dashboard나 raw bucket을 다시 계산하지 않고 `dashboard_snapshots` row metadata, helper column,
 * stored `read_model_json`의 bounded projection만 사용해 promotion, suppression, period folding을 수행한다.</p>
 */
@Component
public class OperationalEventHistoryProjector {

    private static final BigDecimal HIGH_CONFIDENCE_THRESHOLD = new BigDecimal("0.82");
    private static final BigDecimal CONCERN_RESOLUTION_THRESHOLD = new BigDecimal("0.60");
    private static final Duration CONCERN_SUPPRESSION_WINDOW = Duration.ofMinutes(60);
    private static final String UNKNOWN_PREVIOUS_STATE = "unknown_previous";

    private final DashboardSnapshotDetailProjectionParser projectionParser;

    /**
     * stored read model parser를 주입해 snapshot detail과 같은 bounded JSON 해석 경계를 공유한다.
     */
    public OperationalEventHistoryProjector(DashboardSnapshotDetailProjectionParser projectionParser) {
        this.projectionParser = Objects.requireNonNull(projectionParser, "projectionParser must not be null");
    }

    /**
     * source row를 slot 시간순으로 평가해 operational event 후보를 만들고 시작 성격 event의 해소 시각을 채운다.
     */
    public List<OperationalEventItem> project(
            UUID projectId,
            UUID applicationId,
            List<DashboardSnapshotDetailRow> sourceRows) {
        UUID requiredProjectId = Objects.requireNonNull(projectId, "projectId must not be null");
        UUID requiredApplicationId = Objects.requireNonNull(applicationId, "applicationId must not be null");
        List<SourceSnapshot> snapshots = Objects.requireNonNull(sourceRows, "sourceRows must not be null").stream()
                .filter(row -> requiredProjectId.equals(row.projectId()) && requiredApplicationId.equals(row.applicationId()))
                .sorted(Comparator.comparing(DashboardSnapshotDetailRow::currentWindowEndUtc)
                        .thenComparing(DashboardSnapshotDetailRow::generatedAt)
                        .thenComparing(row -> row.snapshotId().toString()))
                .map(this::sourceSnapshot)
                .toList();

        List<ProjectedEvent> projectedEvents = new ArrayList<>();
        Map<String, OffsetDateTime> lastPromotedConcernAt = new HashMap<>();
        Map<String, List<ProjectedEvent>> openConcernEvents = new HashMap<>();
        OpenPeriods openPeriods = new OpenPeriods();
        SourceSnapshot previous = null;
        String lastFoldingKey = null;

        for (SourceSnapshot snapshot : snapshots) {
            List<ConcernSignal> concernSignals = concernSignals(snapshot);
            resolveOpenConcerns(openConcernEvents, concernSignals, snapshot.row().currentWindowEndUtc());

            List<ProjectedEvent> stateEvents = stateEvents(requiredProjectId, requiredApplicationId, previous, snapshot);
            for (ProjectedEvent stateEvent : stateEvents) {
                openPeriods.resolveFor(stateEvent, previous, snapshot);
                String foldingKey = stateEvent.foldingKey();
                if (!foldingKey.equals(lastFoldingKey)) {
                    projectedEvents.add(stateEvent);
                    openPeriods.rememberStart(stateEvent);
                    lastFoldingKey = foldingKey;
                }
            }

            if (stateEvents.isEmpty() && isHighConfidenceSeed(snapshot.normalizedCaptureReason())) {
                for (ConcernSignal signal : concernSignals) {
                    promoteHighConfidenceConcern(
                            requiredProjectId,
                            requiredApplicationId,
                            snapshot,
                            signal,
                            lastPromotedConcernAt,
                            openConcernEvents)
                            .ifPresent(projectedEvents::add);
                }
            }
            previous = snapshot;
        }

        return projectedEvents.stream()
                .map(ProjectedEvent::toItem)
                .toList();
    }

    private SourceSnapshot sourceSnapshot(DashboardSnapshotDetailRow row) {
        DashboardSnapshotStoredReadModelProjection projection = projectionParser.project(row.readModelJson());
        return new SourceSnapshot(
                row,
                projection,
                normalize(row.stateCode()).orElse("unknown"),
                normalize(row.captureReason()).orElse(""));
    }

    private List<ProjectedEvent> stateEvents(
            UUID projectId,
            UUID applicationId,
            SourceSnapshot previous,
            SourceSnapshot current) {
        String fromState = previous == null ? UNKNOWN_PREVIOUS_STATE : previous.normalizedState();
        String toState = current.normalizedState();
        boolean stateChanged = previous != null && !fromState.equals(toState);
        boolean firstStateChangeSeed = previous == null && isStateChangeReason(current.normalizedCaptureReason());
        boolean transitionSeed = stateChanged || firstStateChangeSeed;
        boolean recoveryObserved = previous != null && isRecoveryObservedAfterStaleOrDown(previous, current);
        List<ProjectedEvent> events = new ArrayList<>();

        if (transitionSeed && "degraded".equals(toState) && !"degraded".equals(fromState)) {
            events.add(stateEvent(
                    projectId,
                    applicationId,
                    current,
                    OperationalEventType.DEGRADED_ENTERED,
                    fromState,
                    toState,
                    primaryEvidence(current),
                    primaryConfidence(current)));
        }
        if (transitionSeed
                && "degraded".equals(fromState)
                && !"degraded".equals(toState)
                && concernResolutionObserved(current)) {
            events.add(stateEvent(
                    projectId,
                    applicationId,
                    current,
                    OperationalEventType.DEGRADED_RESOLVED,
                    fromState,
                    toState,
                    primaryEvidence(current),
                    primaryConfidence(current)));
        }
        if (transitionSeed && "stale".equals(toState) && !"stale".equals(fromState)) {
            events.add(stateEvent(
                    projectId,
                    applicationId,
                    current,
                    OperationalEventType.STALE_ENTERED,
                    fromState,
                    toState,
                    missingEvidence(),
                    null));
        }
        if (transitionSeed && "down".equals(toState) && !"down".equals(fromState)) {
            events.add(stateEvent(
                    projectId,
                    applicationId,
                    current,
                    OperationalEventType.DOWN_ENTERED,
                    fromState,
                    toState,
                    missingEvidence(),
                    null));
        }
        if (recoveryObserved) {
            events.add(stateEvent(
                    projectId,
                    applicationId,
                    current,
                    OperationalEventType.RECOVERY_OBSERVED,
                    fromState,
                    toState,
                    missingEvidence(),
                    null));
        }
        if (transitionSeed && events.isEmpty()) {
            events.add(stateEvent(
                    projectId,
                    applicationId,
                    current,
                    OperationalEventType.STATE_CHANGED,
                    fromState,
                    toState,
                    primaryEvidence(current),
                    primaryConfidence(current)));
        }
        return events;
    }

    private ProjectedEvent stateEvent(
            UUID projectId,
            UUID applicationId,
            SourceSnapshot snapshot,
            OperationalEventType type,
            String fromState,
            String toState,
            OperationalEventEvidence evidence,
            BigDecimal confidence) {
        String normalizedKey = stateNormalizedKey(type, fromState, toState);
        return new ProjectedEvent(
                OperationalEventIdFactory.eventId(snapshot.row().snapshotId(), type, normalizedKey),
                type,
                severity(type),
                title(type, evidence),
                summary(type, fromState, toState, evidence),
                snapshot.row().currentWindowEndUtc(),
                null,
                snapshot.row().stateCode().trim(),
                confidence,
                snapshot.row().snapshotId(),
                evidence,
                links(projectId, applicationId, snapshot.row().snapshotId()),
                foldingKey(applicationId, type, fromState, toState),
                concernKey(evidence));
    }

    private Optional<ProjectedEvent> promoteHighConfidenceConcern(
            UUID projectId,
            UUID applicationId,
            SourceSnapshot snapshot,
            ConcernSignal signal,
            Map<String, OffsetDateTime> lastPromotedConcernAt,
            Map<String, List<ProjectedEvent>> openConcernEvents) {
        if (!signal.highConfidence()) {
            return Optional.empty();
        }
        String concernKey = signal.concernKey(applicationId);
        OffsetDateTime occurredAt = snapshot.row().currentWindowEndUtc();
        OffsetDateTime previousPromotedAt = lastPromotedConcernAt.get(concernKey);
        if (previousPromotedAt != null
                && occurredAt.isBefore(previousPromotedAt.plus(CONCERN_SUPPRESSION_WINDOW))) {
            return Optional.empty();
        }
        lastPromotedConcernAt.put(concernKey, occurredAt);
        OperationalEventType type = OperationalEventType.HIGH_CONFIDENCE_CONCERN;
        ProjectedEvent event = new ProjectedEvent(
                OperationalEventIdFactory.eventId(snapshot.row().snapshotId(), type, signal.normalizedKey()),
                type,
                severity(type),
                title(type, signal.evidence()),
                summary(type, null, null, signal.evidence()),
                occurredAt,
                null,
                snapshot.row().stateCode().trim(),
                signal.confidence(),
                snapshot.row().snapshotId(),
                signal.evidence(),
                links(projectId, applicationId, snapshot.row().snapshotId()),
                concernKey,
                concernKey);
        openConcernEvents.computeIfAbsent(signal.concernKey(), ignored -> new ArrayList<>()).add(event);
        return Optional.of(event);
    }

    private void resolveOpenConcerns(
            Map<String, List<ProjectedEvent>> openConcernEvents,
            List<ConcernSignal> currentSignals,
            OffsetDateTime resolvingAt) {
        if (openConcernEvents.isEmpty()) {
            return;
        }
        Map<String, ConcernSignal> signalsByKey = new HashMap<>();
        currentSignals.forEach(signal -> signalsByKey.put(signal.concernKey(), signal));
        for (Iterator<Map.Entry<String, List<ProjectedEvent>>> iterator = openConcernEvents.entrySet().iterator();
             iterator.hasNext(); ) {
            Map.Entry<String, List<ProjectedEvent>> entry = iterator.next();
            ConcernSignal signal = signalsByKey.get(entry.getKey());
            for (ProjectedEvent openEvent : entry.getValue()) {
                if (openEvent.resolvedAt() != null || !resolvingAt.isAfter(openEvent.occurredAt())) {
                    continue;
                }
                if (signal == null || signal.confidenceBelowResolutionThreshold()) {
                    openEvent.resolveAt(resolvingAt);
                }
            }
            entry.getValue().removeIf(openEvent -> openEvent.resolvedAt() != null);
            if (entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
    }

    private List<ConcernSignal> concernSignals(SourceSnapshot snapshot) {
        LinkedHashMap<String, ConcernSignal> signals = new LinkedHashMap<>();
        ConcernSignal helperSignal = helperConcernSignal(snapshot);
        if (helperSignal != null) {
            putSignal(signals, helperSignal);
        }
        for (EndpointEvidenceItem item : snapshot.projection().snapshotEndpointEvidence().items()) {
            normalize(item.endpointKey()).ifPresent(endpointKey -> item.ruleIds().stream()
                    .map(OperationalEventHistoryProjector::normalize)
                    .flatMap(Optional::stream)
                    .forEach(ruleId -> putSignal(signals, new ConcernSignal(
                            ruleId,
                            item.ruleIds().stream()
                                    .filter(rule -> normalize(rule).orElse("").equals(ruleId))
                                    .findFirst()
                                    .orElse(ruleId),
                            endpointKey,
                            item.endpointKey(),
                            item.confidence(),
                            evidence(item, item.ruleIds().stream()
                                    .filter(rule -> normalize(rule).orElse("").equals(ruleId))
                                    .findFirst()
                                    .orElse(ruleId))))));
        }
        return List.copyOf(signals.values());
    }

    private static void putSignal(LinkedHashMap<String, ConcernSignal> signals, ConcernSignal incoming) {
        ConcernSignal existing = signals.get(incoming.concernKey());
        if (existing == null) {
            signals.put(incoming.concernKey(), incoming);
            return;
        }
        signals.put(incoming.concernKey(), existing.merge(incoming));
    }

    private ConcernSignal helperConcernSignal(SourceSnapshot snapshot) {
        Optional<String> normalizedRuleId = normalize(snapshot.row().primaryRuleId());
        Optional<String> normalizedEndpointKey = normalize(snapshot.row().primaryEndpointKey());
        if (normalizedRuleId.isEmpty() || normalizedEndpointKey.isEmpty()) {
            return null;
        }
        EndpointEvidenceItem matchingEvidence = matchingEvidence(
                snapshot.projection().snapshotEndpointEvidence(),
                normalizedEndpointKey.orElseThrow(),
                normalizedRuleId.orElseThrow());
        BigDecimal confidence = firstPresent(
                snapshot.row().maxConfidence(),
                snapshot.projection().maxTriageConfidence(),
                matchingEvidence == null ? null : matchingEvidence.confidence());
        OperationalEventEvidence evidence = matchingEvidence == null
                ? new OperationalEventEvidence(
                snapshot.row().primaryRuleId(),
                snapshot.row().primaryEndpointKey(),
                null,
                null,
                null,
                "missing")
                : evidence(matchingEvidence, snapshot.row().primaryRuleId());
        return new ConcernSignal(
                normalizedRuleId.orElseThrow(),
                snapshot.row().primaryRuleId(),
                normalizedEndpointKey.orElseThrow(),
                snapshot.row().primaryEndpointKey(),
                confidence,
                evidence);
    }

    private OperationalEventEvidence primaryEvidence(SourceSnapshot snapshot) {
        ConcernSignal signal = helperConcernSignal(snapshot);
        return signal == null ? missingEvidence() : signal.evidence();
    }

    private BigDecimal primaryConfidence(SourceSnapshot snapshot) {
        return firstPresent(snapshot.row().maxConfidence(), snapshot.projection().maxTriageConfidence());
    }

    private static EndpointEvidenceItem matchingEvidence(
            SnapshotEndpointEvidence endpointEvidence,
            String normalizedEndpointKey,
            String normalizedRuleId) {
        EndpointEvidenceItem endpointOnlyMatch = null;
        for (EndpointEvidenceItem item : endpointEvidence.items()) {
            if (!normalize(item.endpointKey()).orElse("").equals(normalizedEndpointKey)) {
                continue;
            }
            if (endpointOnlyMatch == null) {
                endpointOnlyMatch = item;
            }
            boolean ruleMatches = item.ruleIds().stream()
                    .map(OperationalEventHistoryProjector::normalize)
                    .flatMap(Optional::stream)
                    .anyMatch(normalizedRuleId::equals);
            if (ruleMatches) {
                return item;
            }
        }
        return endpointOnlyMatch;
    }

    private static OperationalEventEvidence evidence(EndpointEvidenceItem item, String ruleId) {
        return new OperationalEventEvidence(
                ruleId,
                item.endpointKey(),
                item.method(),
                item.route(),
                item.anchorId(),
                "resolved");
    }

    private static OperationalEventEvidence missingEvidence() {
        return new OperationalEventEvidence(null, null, null, null, null, "missing");
    }

    private static boolean concernResolutionObserved(SourceSnapshot snapshot) {
        BigDecimal confidence = firstPresent(snapshot.row().maxConfidence(), snapshot.projection().maxTriageConfidence());
        return concernSignalsPresent(snapshot).isEmpty()
                || (confidence != null && confidence.compareTo(CONCERN_RESOLUTION_THRESHOLD) < 0);
    }

    private static List<String> concernSignalsPresent(SourceSnapshot snapshot) {
        List<String> signals = new ArrayList<>();
        normalize(snapshot.row().primaryRuleId()).ifPresent(signals::add);
        normalize(snapshot.row().primaryEndpointKey()).ifPresent(signals::add);
        snapshot.projection().snapshotEndpointEvidence().items().stream()
                .filter(item -> !item.ruleIds().isEmpty())
                .map(EndpointEvidenceItem::endpointKey)
                .map(OperationalEventHistoryProjector::normalize)
                .flatMap(Optional::stream)
                .forEach(signals::add);
        return signals;
    }

    private static boolean isRecoveryObservedAfterStaleOrDown(SourceSnapshot previous, SourceSnapshot current) {
        if (!isStaleOrDown(previous.normalizedState())) {
            return false;
        }
        return current.projection().recoveryObserved()
                || ("unknown".equals(current.normalizedState()) && current.projection().recoveryExpressionPresent());
    }

    private static boolean isStaleOrDown(String state) {
        return "stale".equals(state) || "down".equals(state);
    }

    private static boolean isStateChangeReason(String captureReason) {
        return "state_change".equals(captureReason);
    }

    private static boolean isHighConfidenceSeed(String captureReason) {
        return "high_confidence_concern".equals(captureReason)
                || "short_strong_spike".equals(captureReason);
    }

    private static OperationalEventSeverity severity(OperationalEventType type) {
        return switch (type) {
            case DOWN_ENTERED -> OperationalEventSeverity.CRITICAL;
            case DEGRADED_ENTERED, HIGH_CONFIDENCE_CONCERN, STALE_ENTERED -> OperationalEventSeverity.WARNING;
            case DEGRADED_RESOLVED, RECOVERY_OBSERVED, STATE_CHANGED -> OperationalEventSeverity.INFO;
        };
    }

    private static String title(OperationalEventType type, OperationalEventEvidence evidence) {
        return switch (type) {
            case DEGRADED_ENTERED -> "성능 저하 관찰";
            case DEGRADED_RESOLVED -> "성능 저하 concern 해소 조건 관찰";
            case STALE_ENTERED -> "Metric freshness 부족 관찰";
            case DOWN_ENTERED -> "Metric data down boundary 관찰";
            case RECOVERY_OBSERVED -> "회복 흐름 관찰";
            case HIGH_CONFIDENCE_CONCERN -> highConfidenceTitle(evidence);
            case STATE_CHANGED -> "Application state 변화 관찰";
        };
    }

    private static String highConfidenceTitle(OperationalEventEvidence evidence) {
        if (evidence.ruleId() == null) {
            return "High-confidence concern 관찰";
        }
        if (evidence.endpointKey() == null) {
            return evidence.ruleId() + " concern 관찰";
        }
        return evidence.endpointKey() + " " + evidence.ruleId() + " concern 관찰";
    }

    private static String summary(
            OperationalEventType type,
            String fromState,
            String toState,
            OperationalEventEvidence evidence) {
        return switch (type) {
            case DEGRADED_ENTERED -> "저장된 snapshot에서 성능 저하가 관찰됐습니다.";
            case DEGRADED_RESOLVED -> "성능 저하 concern 해소 조건이 저장된 snapshot에서 확인됐습니다.";
            case STALE_ENTERED -> "저장된 snapshot에서 accepted bucket freshness 부족으로 stale 관찰이 시작됐습니다.";
            case DOWN_ENTERED -> "저장된 snapshot에서 metric data freshness boundary가 down 상태로 관찰됐습니다.";
            case RECOVERY_OBSERVED -> "새 metric bucket이 다시 관찰되며 회복 흐름이 시작됐습니다.";
            case HIGH_CONFIDENCE_CONCERN -> highConfidenceSummary(evidence);
            case STATE_CHANGED -> "저장된 snapshot에서 application state가 %s에서 %s로 바뀐 것이 관찰됐습니다."
                    .formatted(fromState, toState);
        };
    }

    private static String highConfidenceSummary(OperationalEventEvidence evidence) {
        String rule = evidence.ruleId() == null ? "stored" : evidence.ruleId();
        return "저장된 snapshot에서 " + rule + " concern이 high confidence로 관찰됐습니다.";
    }

    private static OperationalEventLinks links(UUID projectId, UUID applicationId, UUID snapshotId) {
        return new OperationalEventLinks(
                "/api/projects/%s/applications/%s/dashboard/snapshots/%s".formatted(
                        projectId,
                        applicationId,
                        snapshotId));
    }

    private static String stateNormalizedKey(OperationalEventType type, String fromState, String toState) {
        return switch (type) {
            case STALE_ENTERED, DOWN_ENTERED, RECOVERY_OBSERVED -> type.value() + ":" + toState;
            case DEGRADED_ENTERED, DEGRADED_RESOLVED, STATE_CHANGED -> fromState + ":" + toState;
            case HIGH_CONFIDENCE_CONCERN -> throw new IllegalArgumentException("concern event has a concern key");
        };
    }

    private static String foldingKey(
            UUID applicationId,
            OperationalEventType type,
            String fromState,
            String toState) {
        return switch (type) {
            case STALE_ENTERED, DOWN_ENTERED, RECOVERY_OBSERVED -> applicationId + ":" + type.value() + ":" + toState;
            case DEGRADED_ENTERED, DEGRADED_RESOLVED, STATE_CHANGED ->
                    applicationId + ":" + fromState + ":" + toState + ":" + type.value();
            case HIGH_CONFIDENCE_CONCERN -> throw new IllegalArgumentException("concern event has a concern key");
        };
    }

    private static String concernKey(OperationalEventEvidence evidence) {
        Optional<String> ruleId = normalize(evidence.ruleId());
        Optional<String> endpointKey = normalize(evidence.endpointKey());
        return ruleId.isPresent() && endpointKey.isPresent()
                ? ruleId.orElseThrow() + ":" + endpointKey.orElseThrow()
                : null;
    }

    private static BigDecimal firstPresent(BigDecimal first, BigDecimal second) {
        return first != null ? first : second;
    }

    private static BigDecimal firstPresent(BigDecimal first, BigDecimal second, BigDecimal third) {
        return first != null ? first : firstPresent(second, third);
    }

    private static Optional<String> normalize(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.trim().toLowerCase(Locale.ROOT));
    }

    private record SourceSnapshot(
            DashboardSnapshotDetailRow row,
            DashboardSnapshotStoredReadModelProjection projection,
            String normalizedState,
            String normalizedCaptureReason
    ) {
    }

    private record ConcernSignal(
            String normalizedRuleId,
            String ruleId,
            String normalizedEndpointKey,
            String endpointKey,
            BigDecimal confidence,
            OperationalEventEvidence evidence
    ) {

        private String normalizedKey() {
            return normalizedRuleId + ":" + normalizedEndpointKey;
        }

        private String concernKey() {
            return normalizedKey();
        }

        private String concernKey(UUID applicationId) {
            return applicationId + ":" + normalizedKey();
        }

        private boolean highConfidence() {
            return confidence != null && confidence.compareTo(HIGH_CONFIDENCE_THRESHOLD) >= 0;
        }

        private boolean confidenceBelowResolutionThreshold() {
            return confidence != null && confidence.compareTo(CONCERN_RESOLUTION_THRESHOLD) < 0;
        }

        private ConcernSignal merge(ConcernSignal other) {
            BigDecimal mergedConfidence = confidence != null ? confidence : other.confidence();
            OperationalEventEvidence mergedEvidence = "resolved".equals(evidence.anchorStatus())
                    ? evidence
                    : other.evidence();
            return new ConcernSignal(
                    normalizedRuleId,
                    ruleId,
                    normalizedEndpointKey,
                    endpointKey,
                    mergedConfidence,
                    mergedEvidence);
        }
    }

    private static final class OpenPeriods {

        private ProjectedEvent degraded;
        private ProjectedEvent stale;
        private ProjectedEvent down;

        private void rememberStart(ProjectedEvent event) {
            switch (event.type()) {
                case DEGRADED_ENTERED -> degraded = event;
                case STALE_ENTERED -> stale = event;
                case DOWN_ENTERED -> down = event;
                default -> {
                }
            }
        }

        private void resolveFor(ProjectedEvent event, SourceSnapshot previous, SourceSnapshot current) {
            if (event.type() == OperationalEventType.DEGRADED_RESOLVED && degraded != null) {
                degraded.resolveAt(current.row().currentWindowEndUtc());
                degraded = null;
            }
            if (previous != null && stale != null && "stale".equals(previous.normalizedState())
                    && (!"stale".equals(current.normalizedState())
                    || event.type() == OperationalEventType.RECOVERY_OBSERVED)) {
                stale.resolveAt(current.row().currentWindowEndUtc());
                stale = null;
            }
            if (previous != null && down != null && "down".equals(previous.normalizedState())
                    && (!"down".equals(current.normalizedState())
                    || event.type() == OperationalEventType.RECOVERY_OBSERVED)) {
                down.resolveAt(current.row().currentWindowEndUtc());
                down = null;
            }
        }
    }

    private static final class ProjectedEvent {

        private final String eventId;
        private final OperationalEventType type;
        private final OperationalEventSeverity severity;
        private final String title;
        private final String summary;
        private final OffsetDateTime occurredAt;
        private OffsetDateTime resolvedAt;
        private final String stateCode;
        private final BigDecimal confidence;
        private final UUID snapshotId;
        private final OperationalEventEvidence evidence;
        private final OperationalEventLinks links;
        private final String foldingKey;
        private final String concernKey;

        private ProjectedEvent(
                String eventId,
                OperationalEventType type,
                OperationalEventSeverity severity,
                String title,
                String summary,
                OffsetDateTime occurredAt,
                OffsetDateTime resolvedAt,
                String stateCode,
                BigDecimal confidence,
                UUID snapshotId,
                OperationalEventEvidence evidence,
                OperationalEventLinks links,
                String foldingKey,
                String concernKey) {
            this.eventId = eventId;
            this.type = type;
            this.severity = severity;
            this.title = title;
            this.summary = summary;
            this.occurredAt = occurredAt;
            this.resolvedAt = resolvedAt;
            this.stateCode = stateCode;
            this.confidence = confidence;
            this.snapshotId = snapshotId;
            this.evidence = evidence;
            this.links = links;
            this.foldingKey = foldingKey;
            this.concernKey = concernKey;
        }

        private OperationalEventType type() {
            return type;
        }

        private OffsetDateTime occurredAt() {
            return occurredAt;
        }

        private OffsetDateTime resolvedAt() {
            return resolvedAt;
        }

        private String foldingKey() {
            return foldingKey;
        }

        private void resolveAt(OffsetDateTime value) {
            if (resolvedAt == null) {
                resolvedAt = value;
            }
        }

        private OperationalEventItem toItem() {
            return new OperationalEventItem(
                    eventId,
                    type,
                    severity,
                    title,
                    summary,
                    occurredAt,
                    resolvedAt,
                    stateCode,
                    confidence,
                    snapshotId,
                    evidence,
                    links);
        }
    }
}
