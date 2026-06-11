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
                  "schemaVersion": "dashboard_read_model.v1",
                  "mode": "snapshot",
                  "window": {"type": "recent_30_minutes"},
                  "thresholds": {"minimumRequestCount": 30},
                  "operatorSummary": {"headline": "저장된 요약"},
                  "dataQuality": {"limitations": ["baseline_comparison_not_used_for_mvp"]},
                  "signals": {"red": {"requestCount": 120}},
                  "stateReasons": [],
                  "attentionEvidence": [],
                  "firstLookCandidates": [],
                  "readSemantics": {
                    "source": "dashboard_snapshots.read_model_json",
                    "snapshotDetailRecalculates": false,
                    "markerIsStateSource": false,
                    "baselineComparisonUsedForMvpDecision": false,
                    "histogramBucketsUsedForPercentiles": false,
                    "bucketDistributionSource": "accepted_bucket"
                  },
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

        assertThat(projection.readModel().schemaVersion().asText()).isEqualTo("dashboard_read_model.v1");
        assertThat(projection.readModel().mode().asText()).isEqualTo("snapshot");
        assertThat(projection.readModel().window().path("type").asText()).isEqualTo("recent_30_minutes");
        assertThat(projection.readModel().thresholds().path("minimumRequestCount").asLong()).isEqualTo(30L);
        assertThat(projection.readModel().operatorSummary().path("headline").asText()).isEqualTo("저장된 요약");
        assertThat(projection.readModel().dataQuality().path("limitations").get(0).asText())
                .isEqualTo("baseline_comparison_not_used_for_mvp");
        assertThat(projection.readModel().signals().path("red").path("requestCount").asLong()).isEqualTo(120L);
        assertThat(projection.readModel().stateReasons()).isEmpty();
        assertThat(projection.readModel().attentionEvidence()).isEmpty();
        assertThat(projection.readModel().firstLookCandidates()).isEmpty();
        assertThat(projection.readModel().readSemantics().path("source").asText())
                .isEqualTo("dashboard_snapshots.read_model_json");
        assertThat(projection.snapshotEndpointEvidence().source())
                .isEqualTo("dashboard_snapshots.read_model_json.endpointPriority");
        assertThat(projection.snapshotEndpointEvidence().selectionPolicy()).isEqualTo("stored_read_model");
        assertThat(projection.instanceSummary().schemaVersion()).isEqualTo("dashboard_read_model.v1");
        assertThat(projection.instanceSummary().source())
                .isEqualTo("dashboard_snapshots.read_model_json.instanceSummary.items");
        assertThat(projection.instanceSummary().selectionPolicy()).isEqualTo("stored_read_model");
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
    void legacyStoredReadModelWithoutCanonicalFieldsProjectsSnapshotCanonicalFallbacks() {
        DashboardSnapshotStoredReadModelProjection projection = parser.project("""
                {
                  "application": {
                    "lastAcceptedBucketAt": "2026-05-25T10:31:30Z",
                    "sourceWindow": {
                      "current": {
                        "startUtc": "2026-05-25T10:02:30Z",
                        "endUtc": "2026-05-25T10:32:30Z"
                      }
                    }
                  },
                  "state": {
                    "code": "degraded",
                    "rationale": "legacy 저장 상태입니다.",
                    "recommendedAction": "legacy detail 확인"
                  },
                  "zeroInsight": {
                    "message": "legacy zero insight",
                    "recommendedAction": "legacy action"
                  },
                  "metrics": {
                    "requestCount": 91,
                    "errorCount": 7,
                    "errorRate": 0.076923
                  },
                  "triageCards": [
                    {"ruleId": "application_error_rate_high", "confidence": 0.9}
                  ],
                  "readSemantics": {
                    "source": "accepted_metric_buckets",
                    "snapshotDetailRecalculates": true
                  },
                  "instanceSummary": {
                    "schemaVersion": "1.0",
                    "items": []
                  }
                }
                """);

        assertThat(projection.readModel().schemaVersion().asText()).isEqualTo("dashboard_read_model.v1");
        assertThat(projection.readModel().mode().asText()).isEqualTo("snapshot");
        assertThat(projection.readModel().window().path("type").asText()).isEqualTo("recent_30_minutes");
        assertThat(projection.readModel().window().path("startUtc").asText())
                .isEqualTo("2026-05-25T10:02:30Z");
        assertThat(projection.readModel().thresholds().path("minimumRequestCount").asLong()).isEqualTo(30L);
        assertThat(projection.readModel().operatorSummary().path("primaryProblemCode").asText())
                .isEqualTo("application_error_rate_high");
        assertThat(projection.readModel().dataQuality().path("limitations").get(0).asText())
                .isEqualTo("legacy_snapshot_without_canonical_fields");
        assertThat(projection.readModel().dataQuality().path("limitations").get(1).asText())
                .isEqualTo("baseline_comparison_not_used_for_mvp");
        assertThat(projection.readModel().signals().path("red").path("requestCount").asLong()).isEqualTo(91L);
        assertThat(projection.readModel().readSemantics().path("source").asText())
                .isEqualTo("dashboard_snapshots.read_model_json");
        assertThat(projection.readModel().readSemantics().path("snapshotDetailRecalculates").asBoolean())
                .isFalse();
        assertThat(projection.instanceSummary().schemaVersion()).isEqualTo("dashboard_read_model.v1");
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
