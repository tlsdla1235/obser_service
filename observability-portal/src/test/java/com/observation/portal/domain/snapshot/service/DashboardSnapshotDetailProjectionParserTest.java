package com.observation.portal.domain.snapshot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel.InstanceSummaryItem;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailReadModel.SnapshotEndpointEvidenceRef;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotStoredReadModelProjection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashboardSnapshotDetailProjectionParserTest {

    private final DashboardSnapshotDetailProjectionParser parser = new DashboardSnapshotDetailProjectionParser(
            new ObjectMapper().findAndRegisterModules(),
            new SnapshotEndpointEvidenceAnchorResolver());

    @Test
    void projectsEndpointEvidenceAnchorsAndResolvesInstanceSummaryRefsByEndpointKey() {
        DashboardSnapshotStoredReadModelProjection projection = parser.project("""
                {
                  "application": {"applicationId": "app-1"},
                  "state": {"code": "degraded"},
                  "recovery": {"isRecovering": false},
                  "triageCards": [],
                  "endpointPriority": [],
                  "snapshotEndpointEvidence": {
                    "source": "bounded_endpoint_evidence",
                    "maxItems": 10,
                    "selectionPolicy": "endpoint_priority_rank_then_high_confidence_concern_then_triage_affected_endpoint",
                    "items": [
                      {"method": "POST", "route": "/orders", "endpointKey": "POST /orders", "rank": 1},
                      {"method": "POST", "route": "/orders", "endpointKey": "POST /orders", "rank": 2},
                      {"method": "GET", "route": "/health", "endpointKey": "GET /health", "rank": 3}
                    ]
                  },
                  "instanceSummary": {
                    "schemaVersion": "1.0",
                    "source": "bounded_instance_summary",
                    "maxItems": 50,
                    "items": [
                      {
                        "instanceId": "00000000-0000-0000-0000-000000005721",
                        "instanceName": "pod-a",
                        "observationStatus": "observed",
                        "endpointEvidenceRefs": [
                          {"endpointKey": "POST /orders", "method": "POST", "route": "/orders"},
                          {"endpointKey": "DELETE /orders", "method": "DELETE", "route": "/orders"}
                        ]
                      }
                    ]
                  }
                }
                """);

        assertThat(projection.snapshotEndpointEvidence().items())
                .extracting("anchorId")
                .containsExactly("endpoint-evidence-1", "endpoint-evidence-2", "endpoint-evidence-3");
        InstanceSummaryItem item = projection.instanceSummary().items().get(0);
        assertThat(item.endpointEvidenceRefs())
                .extracting(SnapshotEndpointEvidenceRef::snapshotDetailAnchor)
                .containsExactly("endpoint-evidence-1", null);
        assertThat(item.endpointEvidenceRefs())
                .extracting(SnapshotEndpointEvidenceRef::anchorStatus)
                .containsExactly("resolved", "missing");
    }

    @Test
    void malformedOptionalBlocksBecomeUnavailableInsteadOfBeingRehydrated() {
        DashboardSnapshotStoredReadModelProjection projection = parser.project("""
                {
                  "snapshotEndpointEvidence": {"items": "not-array"},
                  "instanceSummary": {"schemaVersion": "legacy", "items": []},
                  "triageCards": {"not": "array"}
                }
                """);

        assertThat(projection.snapshotEndpointEvidence().unavailableReason())
                .isEqualTo("stored_snapshot_endpoint_evidence_unavailable");
        assertThat(projection.snapshotEndpointEvidence().items()).isEmpty();
        assertThat(projection.instanceSummary().unavailableReason())
                .isEqualTo("stored_instance_summary_unavailable");
        assertThat(projection.instanceSummary().items()).isEmpty();
        assertThat(projection.readModel().triageCards().isArray()).isTrue();
        assertThat(projection.readModel().triageCards()).isEmpty();
    }

    @Test
    void malformedRootFailsProjection() {
        assertThatThrownBy(() -> parser.project("[]"))
                .isInstanceOf(DashboardSnapshotProjectionException.class)
                .hasMessageContaining("root must be an object");
        assertThatThrownBy(() -> parser.project("{"))
                .isInstanceOf(DashboardSnapshotProjectionException.class)
                .hasMessageContaining("projection failed");
    }
}
