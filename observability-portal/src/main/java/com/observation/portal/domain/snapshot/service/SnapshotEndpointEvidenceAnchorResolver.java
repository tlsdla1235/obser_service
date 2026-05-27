package com.observation.portal.domain.snapshot.service;

import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel.EndpointEvidenceItem;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel.InstanceSummary;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel.InstanceSummaryItem;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel.SnapshotEndpointEvidence;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel.SnapshotEndpointEvidenceRef;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Stored endpoint evidence items와 instance summary ref를 같은 snapshot 안에서 deterministic anchor로 연결한다.
 *
 * <p>`endpointKey`를 canonical match key로 사용하고, 같은 key가 여러 번 있으면 stored order의 첫 item anchor를 선택한다.
 * match 실패는 ref를 버리지 않고 `anchorStatus=missing`으로 bounded 표현한다.</p>
 */
@Component
public class SnapshotEndpointEvidenceAnchorResolver {

    /**
     * instance summary ref-only 목록에 `snapshotDetailAnchor`와 `anchorStatus`를 보강한다.
     */
    public InstanceSummary resolve(SnapshotEndpointEvidence evidence, InstanceSummary instanceSummary) {
        SnapshotEndpointEvidence requiredEvidence = Objects.requireNonNull(evidence, "evidence must not be null");
        InstanceSummary requiredInstanceSummary = Objects.requireNonNull(
                instanceSummary,
                "instanceSummary must not be null");
        Map<String, EndpointEvidenceItem> anchorsByEndpointKey = anchorsByEndpointKey(requiredEvidence.items());
        List<InstanceSummaryItem> resolvedItems = requiredInstanceSummary.items().stream()
                .map(item -> resolveItem(item, anchorsByEndpointKey))
                .toList();
        return new InstanceSummary(
                requiredInstanceSummary.schemaVersion(),
                requiredInstanceSummary.source(),
                requiredInstanceSummary.maxItems(),
                requiredInstanceSummary.selectionPolicy(),
                requiredInstanceSummary.unavailableReason(),
                resolvedItems);
    }

    private static Map<String, EndpointEvidenceItem> anchorsByEndpointKey(List<EndpointEvidenceItem> items) {
        Map<String, EndpointEvidenceItem> anchors = new HashMap<>();
        for (EndpointEvidenceItem item : items) {
            anchors.putIfAbsent(item.endpointKey(), item);
        }
        return anchors;
    }

    private static InstanceSummaryItem resolveItem(
            InstanceSummaryItem item,
            Map<String, EndpointEvidenceItem> anchorsByEndpointKey) {
        List<SnapshotEndpointEvidenceRef> resolvedRefs = item.endpointEvidenceRefs().stream()
                .map(ref -> resolveRef(ref, anchorsByEndpointKey.get(ref.endpointKey())))
                .toList();
        return new InstanceSummaryItem(
                item.instanceId(),
                item.instanceName(),
                item.observationStatus(),
                item.metricData(),
                item.starterConnection(),
                item.starterPercentilePoint(),
                item.resourceHints(),
                item.applicationTriageContribution(),
                resolvedRefs);
    }

    private static SnapshotEndpointEvidenceRef resolveRef(
            SnapshotEndpointEvidenceRef ref,
            EndpointEvidenceItem evidenceItem) {
        if (evidenceItem == null || !methodAndRouteCompatible(ref, evidenceItem)) {
            return new SnapshotEndpointEvidenceRef(
                    ref.endpointKey(),
                    ref.method(),
                    ref.route(),
                    ref.relatedApplicationPriorityRank(),
                    ref.relatedRuleIds(),
                    null,
                    "missing");
        }
        return new SnapshotEndpointEvidenceRef(
                ref.endpointKey(),
                ref.method(),
                ref.route(),
                ref.relatedApplicationPriorityRank(),
                ref.relatedRuleIds(),
                evidenceItem.anchorId(),
                "resolved");
    }

    private static boolean methodAndRouteCompatible(
            SnapshotEndpointEvidenceRef ref,
            EndpointEvidenceItem evidenceItem) {
        return compatibleNullable(ref.method(), evidenceItem.method())
                && compatibleNullable(ref.route(), evidenceItem.route());
    }

    private static boolean compatibleNullable(String refValue, String evidenceValue) {
        return refValue == null || evidenceValue == null || refValue.equals(evidenceValue);
    }
}
