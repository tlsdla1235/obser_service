package com.observation.portal.domain.history.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.portal.domain.history.model.OperationalEventItem;
import com.observation.portal.domain.history.model.OperationalEventSeverity;
import com.observation.portal.domain.history.model.OperationalEventType;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotDetailRow;
import com.observation.portal.domain.snapshot.service.DashboardSnapshotDetailProjectionParser;
import com.observation.portal.domain.snapshot.service.SnapshotEndpointEvidenceAnchorResolver;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OperationalEventHistoryProjectorTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000005901");
    private static final UUID APPLICATION_ID = UUID.fromString("00000000-0000-0000-0000-000000005911");

    private final OperationalEventHistoryProjector projector = new OperationalEventHistoryProjector(
            new DashboardSnapshotDetailProjectionParser(
                    new ObjectMapper().findAndRegisterModules(),
                    new SnapshotEndpointEvidenceAnchorResolver()));

    @Test
    void promotesStateEventsInChronologicalOrderAndFillsResolvedAtForStartEvents() {
        DashboardSnapshotDetailRow active = row(
                1,
                "2026-05-27T10:00:00Z",
                "active",
                "hourly_scheduled",
                null,
                null,
                null,
                emptyJson());
        DashboardSnapshotDetailRow degraded = row(
                2,
                "2026-05-27T10:10:00Z",
                "Degraded",
                "state_change",
                "endpoint_latency_spike",
                "POST /orders",
                "0.84",
                evidenceJson("endpoint_latency_spike", "POST", "/orders", "POST /orders", "0.84"));
        DashboardSnapshotDetailRow repeatedDegraded = row(
                3,
                "2026-05-27T10:20:00Z",
                " degraded ",
                "hourly_scheduled",
                "endpoint_latency_spike",
                "POST /orders",
                "0.80",
                evidenceJson("endpoint_latency_spike", "POST", "/orders", "POST /orders", "0.80"));
        DashboardSnapshotDetailRow degradedResolved = row(
                4,
                "2026-05-27T10:30:00Z",
                "active",
                "state_change",
                null,
                null,
                "0.20",
                emptyJson());
        DashboardSnapshotDetailRow stale = row(
                5,
                "2026-05-27T10:40:00Z",
                "stale",
                "state_change",
                null,
                null,
                null,
                emptyJson());
        DashboardSnapshotDetailRow recoveryAfterStale = row(
                6,
                "2026-05-27T10:50:00Z",
                "unknown",
                "state_change",
                null,
                null,
                null,
                recoveryJson());
        DashboardSnapshotDetailRow down = row(
                7,
                "2026-05-27T11:00:00Z",
                "down",
                "state_change",
                null,
                null,
                null,
                emptyJson());
        DashboardSnapshotDetailRow recoveryAfterDown = row(
                8,
                "2026-05-27T11:10:00Z",
                "unknown",
                "state_change",
                null,
                null,
                null,
                recoveryJson());
        DashboardSnapshotDetailRow stateChanged = row(
                9,
                "2026-05-27T11:20:00Z",
                "active",
                "state_change",
                null,
                null,
                null,
                emptyJson());

        List<OperationalEventItem> events = projector.project(
                PROJECT_ID,
                APPLICATION_ID,
                List.of(
                        stateChanged,
                        recoveryAfterDown,
                        down,
                        recoveryAfterStale,
                        stale,
                        degradedResolved,
                        repeatedDegraded,
                        degraded,
                        active));

        assertThat(events)
                .extracting(OperationalEventItem::type)
                .containsExactly(
                        OperationalEventType.DEGRADED_ENTERED,
                        OperationalEventType.DEGRADED_RESOLVED,
                        OperationalEventType.STALE_ENTERED,
                        OperationalEventType.RECOVERY_OBSERVED,
                        OperationalEventType.DOWN_ENTERED,
                        OperationalEventType.RECOVERY_OBSERVED,
                        OperationalEventType.STATE_CHANGED);

        OperationalEventItem degradedEntered = only(events, OperationalEventType.DEGRADED_ENTERED);
        assertThat(degradedEntered.occurredAt()).isEqualTo(offset("2026-05-27T10:10:00Z"));
        assertThat(degradedEntered.resolvedAt()).isEqualTo(offset("2026-05-27T10:30:00Z"));
        assertThat(degradedEntered.severity()).isEqualTo(OperationalEventSeverity.WARNING);
        assertThat(degradedEntered.evidence().snapshotDetailAnchor()).isEqualTo("endpoint-evidence-1");
        assertThat(degradedEntered.evidence().anchorStatus()).isEqualTo("resolved");

        OperationalEventItem degradedResolvedEvent = only(events, OperationalEventType.DEGRADED_RESOLVED);
        assertThat(degradedResolvedEvent.resolvedAt()).isNull();
        assertThat(degradedResolvedEvent.severity()).isEqualTo(OperationalEventSeverity.INFO);

        OperationalEventItem staleEntered = only(events, OperationalEventType.STALE_ENTERED);
        assertThat(staleEntered.resolvedAt()).isEqualTo(offset("2026-05-27T10:50:00Z"));
        assertThat(staleEntered.severity()).isEqualTo(OperationalEventSeverity.WARNING);

        OperationalEventItem downEntered = only(events, OperationalEventType.DOWN_ENTERED);
        assertThat(downEntered.resolvedAt()).isEqualTo(offset("2026-05-27T11:10:00Z"));
        assertThat(downEntered.severity()).isEqualTo(OperationalEventSeverity.CRITICAL);

        assertThat(events)
                .filteredOn(event -> event.type() == OperationalEventType.RECOVERY_OBSERVED)
                .allSatisfy(event -> {
                    assertThat(event.resolvedAt()).isNull();
                    assertThat(event.severity()).isEqualTo(OperationalEventSeverity.INFO);
                });
    }

    @Test
    void usesCurrentWindowSlotOrderWhenGeneratedAtDiffersFromSlot() {
        DashboardSnapshotDetailRow active = row(
                60,
                "2026-05-27T12:40:00Z",
                "2026-05-27T10:00:00Z",
                "active",
                "hourly_scheduled",
                null,
                null,
                null,
                emptyJson());
        DashboardSnapshotDetailRow degraded = row(
                61,
                "2026-05-27T10:05:00Z",
                "2026-05-27T10:30:00Z",
                "degraded",
                "state_change",
                "endpoint_latency_spike",
                "POST /orders",
                "0.84",
                evidenceJson("endpoint_latency_spike", "POST", "/orders", "POST /orders", "0.84"));
        DashboardSnapshotDetailRow resolved = row(
                62,
                "2026-05-27T10:07:00Z",
                "2026-05-27T11:00:00Z",
                "active",
                "state_change",
                null,
                null,
                "0.20",
                emptyJson());

        List<OperationalEventItem> events = projector.project(
                PROJECT_ID,
                APPLICATION_ID,
                List.of(resolved, degraded, active));

        assertThat(events)
                .extracting(OperationalEventItem::type)
                .containsExactly(
                        OperationalEventType.DEGRADED_ENTERED,
                        OperationalEventType.DEGRADED_RESOLVED);
        assertThat(only(events, OperationalEventType.DEGRADED_ENTERED).occurredAt())
                .isEqualTo(offset("2026-05-27T10:30:00Z"));
        assertThat(only(events, OperationalEventType.DEGRADED_ENTERED).resolvedAt())
                .isEqualTo(offset("2026-05-27T11:00:00Z"));
        assertThat(only(events, OperationalEventType.DEGRADED_RESOLVED).occurredAt())
                .isEqualTo(offset("2026-05-27T11:00:00Z"));
    }

    @Test
    void failureRecoveryEventCopyUsesStoredObservationLanguageWithoutCompletionClaims() {
        List<OperationalEventItem> events = projector.project(
                PROJECT_ID,
                APPLICATION_ID,
                List.of(
                        row(48, "2026-05-27T11:10:00Z", "unknown", "state_change", null, null, null, recoveryJson()),
                        row(47, "2026-05-27T11:00:00Z", "down", "state_change", null, null, null, emptyJson()),
                        row(46, "2026-05-27T10:50:00Z", "unknown", "state_change", null, null, null, recoveryJson()),
                        row(45, "2026-05-27T10:40:00Z", "stale", "state_change", null, null, null, emptyJson()),
                        row(44, "2026-05-27T10:30:00Z", "active", "state_change", null, null, "0.20", emptyJson()),
                        row(43, "2026-05-27T10:20:00Z", "degraded", "state_change", "endpoint_latency_spike", "POST /orders", "0.84",
                                evidenceJson("endpoint_latency_spike", "POST", "/orders", "POST /orders", "0.84")),
                        row(42, "2026-05-27T10:10:00Z", "active", "hourly_scheduled", null, null, null, emptyJson())));

        assertThat(events)
                .extracting(OperationalEventItem::type)
                .containsExactly(
                        OperationalEventType.DEGRADED_ENTERED,
                        OperationalEventType.DEGRADED_RESOLVED,
                        OperationalEventType.STALE_ENTERED,
                        OperationalEventType.RECOVERY_OBSERVED,
                        OperationalEventType.DOWN_ENTERED,
                        OperationalEventType.RECOVERY_OBSERVED);
        assertThat(events).allSatisfy(OperationalEventHistoryProjectorTest::assertSafeEventCopy);
        assertThat(only(events, OperationalEventType.DEGRADED_RESOLVED).summary())
                .contains("해소 조건")
                .doesNotContain("복구 완료", "장애 해결 완료", "앱 정상 확정");
        assertThat(only(events, OperationalEventType.STALE_ENTERED).summary())
                .contains("accepted bucket freshness 부족")
                .doesNotContain("host application down");
        assertThat(only(events, OperationalEventType.DOWN_ENTERED).summary())
                .contains("metric data freshness boundary")
                .doesNotContain("host application down", "host process down");
        assertThat(events)
                .filteredOn(event -> event.type() == OperationalEventType.RECOVERY_OBSERVED)
                .allSatisfy(event -> assertThat(event.summary())
                        .contains("회복 흐름")
                        .doesNotContain("복구 완료", "장애 해결 완료", "앱 정상 확정"));
    }

    @Test
    void queuePipelineDiagnosticsDoNotBecomeHostHealthCertaintyCopy() {
        List<OperationalEventItem> events = projector.project(
                PROJECT_ID,
                APPLICATION_ID,
                List.of(
                        row(58, "2026-05-27T11:00:00Z", "down", "state_change", null, null, null,
                                pipelineDiagnosticJson()),
                        row(57, "2026-05-27T10:50:00Z", "active", "hourly_scheduled", null, null, null,
                                emptyJson())));

        OperationalEventItem event = only(events, OperationalEventType.DOWN_ENTERED);

        assertSafeEventCopy(event);
        assertThat(event.summary()).doesNotContain(
                "telemetry unreachable",
                "starter unreachable",
                "host application down",
                "host process down",
                "앱 내려감");
    }

    @Test
    void promotesHighConfidenceConcernsWithGuardSuppressionBoundaryAndShortSpikeMapping() {
        DashboardSnapshotDetailRow firstConcern = row(
                10,
                "2026-05-27T10:05:00Z",
                "active",
                "high_confidence_concern",
                "endpoint_latency_spike",
                "POST /orders",
                "0.84",
                evidenceJson("endpoint_latency_spike", "POST", "/orders", "POST /orders", "0.84"));
        DashboardSnapshotDetailRow suppressedConcern = row(
                11,
                "2026-05-27T10:20:00Z",
                "active",
                "high_confidence_concern",
                "endpoint_latency_spike",
                "POST /orders",
                "0.91",
                evidenceJson("endpoint_latency_spike", "POST", "/orders", "POST /orders", "0.91"));
        DashboardSnapshotDetailRow simultaneousConcerns = row(
                12,
                "2026-05-27T10:30:00Z",
                "active",
                "high_confidence_concern",
                null,
                null,
                null,
                evidenceItemsJson("""
                        {"method":"GET","route":"/inventory","endpointKey":"GET /inventory","ruleIds":["endpoint_error_spike"],"confidence":0.88},
                        {"method":"POST","route":"/payments","endpointKey":"POST /payments","ruleIds":["payment_timeout"],"confidence":0.89}
                        """));
        DashboardSnapshotDetailRow boundaryConcern = row(
                13,
                "2026-05-27T11:05:00Z",
                "active",
                "high_confidence_concern",
                "endpoint_latency_spike",
                "POST /orders",
                "0.85",
                evidenceJson("endpoint_latency_spike", "POST", "/orders", "POST /orders", "0.85"));
        DashboardSnapshotDetailRow shortSpike = row(
                14,
                "2026-05-27T11:15:00Z",
                "active",
                "short_strong_spike",
                "endpoint_error_spike",
                "DELETE /carts",
                null,
                triageAndEvidenceJson("endpoint_error_spike", "DELETE", "/carts", "DELETE /carts", "0.90"));
        DashboardSnapshotDetailRow missingKey = row(
                15,
                "2026-05-27T11:25:00Z",
                "active",
                "high_confidence_concern",
                "endpoint_error_spike",
                null,
                "0.92",
                emptyJson());
        DashboardSnapshotDetailRow belowThreshold = row(
                16,
                "2026-05-27T11:35:00Z",
                "active",
                "short_strong_spike",
                "endpoint_latency_spike",
                "PATCH /orders",
                "0.81",
                evidenceJson("endpoint_latency_spike", "PATCH", "/orders", "PATCH /orders", "0.81"));
        DashboardSnapshotDetailRow resolvingSource = row(
                17,
                "2026-05-27T11:45:00Z",
                "active",
                "hourly_scheduled",
                null,
                null,
                null,
                emptyJson());

        List<OperationalEventItem> events = projector.project(
                PROJECT_ID,
                APPLICATION_ID,
                List.of(
                        resolvingSource,
                        belowThreshold,
                        missingKey,
                        shortSpike,
                        boundaryConcern,
                        simultaneousConcerns,
                        suppressedConcern,
                        firstConcern));

        assertThat(events)
                .extracting(OperationalEventItem::type)
                .containsOnly(OperationalEventType.HIGH_CONFIDENCE_CONCERN);
        assertThat(events).hasSize(5);
        assertThat(events)
                .extracting(event -> event.evidence().ruleId() + ":" + event.evidence().endpointKey())
                .containsExactly(
                        "endpoint_latency_spike:POST /orders",
                        "endpoint_error_spike:GET /inventory",
                        "payment_timeout:POST /payments",
                        "endpoint_latency_spike:POST /orders",
                        "endpoint_error_spike:DELETE /carts");

        assertThat(events)
                .allSatisfy(event -> {
                    assertThat(event.severity()).isEqualTo(OperationalEventSeverity.WARNING);
                    assertSafeEventCopy(event);
                });
        assertThat(events)
                .extracting(event -> event.evidence().endpointKey() + ":" + event.occurredAt() + ":" + event.resolvedAt())
                .containsExactly(
                        "POST /orders:2026-05-27T10:05Z:2026-05-27T10:30Z",
                        "GET /inventory:2026-05-27T10:30Z:2026-05-27T11:05Z",
                        "POST /payments:2026-05-27T10:30Z:2026-05-27T11:05Z",
                        "POST /orders:2026-05-27T11:05Z:2026-05-27T11:15Z",
                        "DELETE /carts:2026-05-27T11:15Z:2026-05-27T11:25Z");
        assertThat(events)
                .extracting(OperationalEventItem::eventId)
                .allSatisfy(eventId -> assertThat(eventId).contains(":high_confidence_concern:"));
    }

    @Test
    void resolvesAllOpenHighConfidenceConcernEventsForSameKeyAfterSuppressionBoundary() {
        DashboardSnapshotDetailRow firstConcern = row(
                30,
                "2026-05-27T10:00:00Z",
                "active",
                "high_confidence_concern",
                "endpoint_latency_spike",
                "POST /orders",
                "0.84",
                evidenceJson("endpoint_latency_spike", "POST", "/orders", "POST /orders", "0.84"));
        DashboardSnapshotDetailRow suppressedConcern = row(
                31,
                "2026-05-27T10:30:00Z",
                "active",
                "high_confidence_concern",
                "endpoint_latency_spike",
                "POST /orders",
                "0.91",
                evidenceJson("endpoint_latency_spike", "POST", "/orders", "POST /orders", "0.91"));
        DashboardSnapshotDetailRow boundaryConcern = row(
                32,
                "2026-05-27T11:00:00Z",
                "active",
                "high_confidence_concern",
                "endpoint_latency_spike",
                "POST /orders",
                "0.85",
                evidenceJson("endpoint_latency_spike", "POST", "/orders", "POST /orders", "0.85"));
        DashboardSnapshotDetailRow resolvingSource = row(
                33,
                "2026-05-27T11:15:00Z",
                "active",
                "hourly_scheduled",
                null,
                null,
                null,
                emptyJson());

        List<OperationalEventItem> events = projector.project(
                PROJECT_ID,
                APPLICATION_ID,
                List.of(resolvingSource, boundaryConcern, suppressedConcern, firstConcern));

        assertThat(events)
                .extracting(OperationalEventItem::occurredAt)
                .containsExactly(
                        offset("2026-05-27T10:00:00Z"),
                        offset("2026-05-27T11:00:00Z"));
        assertThat(events)
                .extracting(OperationalEventItem::resolvedAt)
                .containsExactly(
                        offset("2026-05-27T11:15:00Z"),
                        offset("2026-05-27T11:15:00Z"));
    }

    @Test
    void skipsLegacyObservationRowsWithoutProjectionFailure() {
        List<OperationalEventItem> events = projector.project(
                PROJECT_ID,
                APPLICATION_ID,
                List.of(
                        row(21, "2026-05-27T10:40:00Z", "active", "state_observation", null, null, null, emptyJson()),
                        row(20, "2026-05-27T10:30:00Z", "active", "stored_snapshot", null, null, null, emptyJson()),
                        row(19, "2026-05-27T10:20:00Z", "active", "query_fallback_snapshot", null, null, null, emptyJson()),
                        row(18, "2026-05-27T10:10:00Z", "active", "scheduled_snapshot", null, null, null, emptyJson())));

        assertThat(events).isEmpty();
    }

    private static OperationalEventItem only(List<OperationalEventItem> events, OperationalEventType type) {
        return events.stream()
                .filter(event -> event.type() == type)
                .findFirst()
                .orElseThrow();
    }

    private static void assertSafeEventCopy(OperationalEventItem event) {
        assertThat(event.summary()).doesNotContain(
                "복구 완료",
                "장애 해결 완료",
                "앱 정상 확정",
                "문제 없음",
                "현재 정상",
                "host application down",
                "host process down",
                "heartbeat");
        assertThat(event.title()).doesNotContain(
                "복구 완료",
                "장애 해결 완료",
                "앱 정상 확정",
                "문제 없음",
                "현재 정상",
                "host application down",
                "host process down",
                "heartbeat");
    }

    private static DashboardSnapshotDetailRow row(
            int id,
            String generatedAt,
            String stateCode,
            String captureReason,
            String primaryRuleId,
            String primaryEndpointKey,
            String maxConfidence,
            String readModelJson) {
        OffsetDateTime at = offset(generatedAt);
        return new DashboardSnapshotDetailRow(
                UUID.fromString("00000000-0000-0000-0000-%012d".formatted(id)),
                PROJECT_ID,
                APPLICATION_ID,
                at,
                at.minusMinutes(15),
                at,
                at.minusMinutes(30),
                at.minusMinutes(15),
                stateCode,
                captureReason,
                primaryRuleId,
                primaryEndpointKey,
                maxConfidence == null ? null : new BigDecimal(maxConfidence),
                readModelJson);
    }

    private static DashboardSnapshotDetailRow row(
            int id,
            String generatedAt,
            String currentWindowEndUtc,
            String stateCode,
            String captureReason,
            String primaryRuleId,
            String primaryEndpointKey,
            String maxConfidence,
            String readModelJson) {
        OffsetDateTime generated = offset(generatedAt);
        OffsetDateTime currentWindowEnd = offset(currentWindowEndUtc);
        return new DashboardSnapshotDetailRow(
                UUID.fromString("00000000-0000-0000-0000-%012d".formatted(id)),
                PROJECT_ID,
                APPLICATION_ID,
                generated,
                currentWindowEnd.minusMinutes(30),
                currentWindowEnd,
                currentWindowEnd.minusMinutes(60),
                currentWindowEnd.minusMinutes(30),
                stateCode,
                captureReason,
                primaryRuleId,
                primaryEndpointKey,
                maxConfidence == null ? null : new BigDecimal(maxConfidence),
                readModelJson);
    }

    private static String emptyJson() {
        return """
                {
                  "snapshotEndpointEvidence": {"items": []},
                  "triageCards": [],
                  "recovery": {"isRecovering": false},
                  "zeroInsight": null
                }
                """;
    }

    private static String pipelineDiagnosticJson() {
        return """
                {
                  "snapshotEndpointEvidence": {"items": []},
                  "triageCards": [],
                  "pipelineDiagnostics": {
                    "queueLag": "PT10M",
                    "queueBacklog": 42,
                    "workerFailure": "persist_timeout"
                  },
                  "recovery": {"isRecovering": false},
                  "zeroInsight": null
                }
                """;
    }

    private static String recoveryJson() {
        return """
                {
                  "snapshotEndpointEvidence": {"items": []},
                  "triageCards": [],
                  "recovery": {"isRecovering": true, "recommendedAction": "다음 bucket까지 관찰하세요."},
                  "zeroInsight": {"reasonCode": "observing_recovery"}
                }
                """;
    }

    private static String evidenceJson(
            String ruleId,
            String method,
            String route,
            String endpointKey,
            String confidence) {
        return evidenceItemsJson("""
                {"method":"%s","route":"%s","endpointKey":"%s","ruleIds":["%s"],"confidence":%s}
                """.formatted(method, route, endpointKey, ruleId, confidence));
    }

    private static String triageAndEvidenceJson(
            String ruleId,
            String method,
            String route,
            String endpointKey,
            String confidence) {
        return """
                {
                  "snapshotEndpointEvidence": {
                    "items": [
                      {"method":"%s","route":"%s","endpointKey":"%s","ruleIds":["%s"],"confidence":%s}
                    ]
                  },
                  "triageCards": [
                    {"ruleId":"%s","severity":"critical","confidence":%s,"affectedEndpoint":"%s"}
                  ],
                  "recovery": {"isRecovering": false},
                  "zeroInsight": null
                }
                """.formatted(method, route, endpointKey, ruleId, confidence, ruleId, confidence, endpointKey);
    }

    private static String evidenceItemsJson(String itemsJson) {
        return """
                {
                  "snapshotEndpointEvidence": {
                    "items": [%s]
                  },
                  "triageCards": [],
                  "recovery": {"isRecovering": false},
                  "zeroInsight": null
                }
                """.formatted(itemsJson);
    }

    private static OffsetDateTime offset(String instant) {
        return OffsetDateTime.parse(instant);
    }
}
