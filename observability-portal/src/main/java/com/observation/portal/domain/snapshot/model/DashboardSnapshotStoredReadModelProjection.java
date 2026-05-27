package com.observation.portal.domain.snapshot.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Stored `read_model_json`에서 detail/marker service가 재사용하는 bounded projection과 classifier signal을 묶은 값이다.
 *
 * <p>root JSON 전체는 보관하지 않고 UI-facing top-level block, endpoint evidence, instance summary, triage/recovery signal만
 * service에 전달한다.</p>
 */
public record DashboardSnapshotStoredReadModelProjection(
        DashboardSnapshotDetailReadModel.StoredReadModel readModel,
        DashboardSnapshotDetailReadModel.SnapshotEndpointEvidence snapshotEndpointEvidence,
        DashboardSnapshotDetailReadModel.InstanceSummary instanceSummary,
        JsonNode recovery,
        JsonNode zeroInsight,
        JsonNode triageCards,
        boolean recoveryObserved,
        boolean recoveryExpressionPresent,
        boolean criticalTriageSeverityPresent,
        boolean warningTriageSeverityPresent,
        BigDecimal maxTriageConfidence
) {

    /**
     * detail/marker service가 stored JSON root 없이 필요한 projection과 signal만 사용하도록 검증한다.
     */
    public DashboardSnapshotStoredReadModelProjection {
        Objects.requireNonNull(readModel, "readModel must not be null");
        Objects.requireNonNull(snapshotEndpointEvidence, "snapshotEndpointEvidence must not be null");
        Objects.requireNonNull(instanceSummary, "instanceSummary must not be null");
    }
}
