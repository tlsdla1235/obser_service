package com.observation.portal.domain.instance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.portal.domain.instance.model.InstanceSnapshotTrendReadModel;
import com.observation.portal.domain.snapshot.model.DashboardSnapshotTrendRow;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class InstanceSnapshotTrendParserTest {

    private static final UUID TARGET_INSTANCE_ID = UUID.fromString("00000000-0000-0000-0000-000000005221");
    private static final UUID OTHER_INSTANCE_ID = UUID.fromString("00000000-0000-0000-0000-000000005222");
    private static final UUID SNAPSHOT_ID = UUID.fromString("00000000-0000-0000-0000-000000005731");

    private final InstanceSnapshotTrendParser parser = new InstanceSnapshotTrendParser(new ObjectMapper());

    @Test
    void projectsTargetItemOnlyByExactInstanceUuidAndCopiesStoredBlocks() {
        String json = """
                {
                  "instances": [
                    {"instanceId": "%s", "instanceName": "pod-a"}
                  ],
                  "snapshot": {
                    "instanceId": "%s"
                  },
                  "instanceSummary": {
                    "schemaVersion": "1.0",
                    "items": [
                      %s,
                      %s
                    ],
                    "unknownFutureField": true
                  }
                }
                """.formatted(TARGET_INSTANCE_ID, TARGET_INSTANCE_ID,
                item(OTHER_INSTANCE_ID, "pod-a", "\"endpointEvidenceRefs\": []"),
                item(TARGET_INSTANCE_ID, "pod-a", """
                        "endpointEvidenceRefs": [
                          {
                            "endpointKey": "POST /orders",
                            "method": "POST",
                            "route": "/orders",
                            "relatedApplicationPriorityRank": 1,
                            "relatedRuleIds": ["endpoint_error_spike"],
                            "snapshotDetailAnchor": "snapshot:endpoint-1",
                            "requestCount": 999
                          }
                        ],
                        "extra": {"ignored": true}
                        """));

        InstanceSnapshotTrendReadModel.Point point = parser.projectPoint(row(json), TARGET_INSTANCE_ID)
                .orElseThrow();

        assertThat(point.snapshotId()).isEqualTo(SNAPSHOT_ID);
        assertThat(point.capturedAt()).isEqualTo(OffsetDateTime.parse("2026-05-26T08:00:00Z"));
        assertThat(point.currentWindowEndUtc()).isEqualTo(OffsetDateTime.parse("2026-05-26T08:00:00Z"));
        assertThat(point.storedApplicationStateCode()).isEqualTo("active");
        assertThat(point.captureReason()).isEqualTo("hourly_scheduled");
        assertThat(point.instanceName()).isEqualTo("pod-a");
        assertThat(point.observationStatus()).isEqualTo("observed");
        assertThat(point.metricData().statusSource()).isEqualTo("accepted_bucket");
        assertThat(point.metricData().lastAcceptedBucketAt())
                .isEqualTo(OffsetDateTime.parse("2026-05-26T07:59:30Z"));
        assertThat(point.starterConnection().statusSource()).isEqualTo("starter_heartbeat");
        assertThat(point.starterConnection().stateImpact()).isEqualTo("none");
        assertThat(point.starterPercentilePoint().source()).isEqualTo("starter_canonical_percentile");
        assertThat(point.resourceHints().source()).isEqualTo("accepted_bucket_latest_sample");
        assertThat(point.applicationTriageContribution().relatedRuleIds()).containsExactly("global_error_spike");
        assertThat(point.endpointEvidenceRefs()).hasSize(1);
        assertThat(point.endpointEvidenceRefs().get(0).endpointKey()).isEqualTo("POST /orders");
        assertThat(point.endpointEvidenceRefs().get(0).relatedApplicationPriorityRank()).isEqualTo(1);
        assertThat(point.endpointEvidenceRefs().get(0).relatedRuleIds()).containsExactly("endpoint_error_spike");
    }

    @Test
    void skipsMissingUnsupportedAndMalformedSourcesAsEmptyProjection() {
        assertEmpty("{\"instances\":[{\"instanceId\":\"%s\"}]}".formatted(TARGET_INSTANCE_ID));
        assertEmpty("{\"snapshot\":{\"instanceId\":\"%s\"}}".formatted(TARGET_INSTANCE_ID));
        assertEmpty("{\"instanceSummary\":{\"items\":[]}}");
        assertEmpty("{\"instanceSummary\":{\"schemaVersion\":\"2.0\",\"items\":[]}}");
        assertEmpty("{\"instanceSummary\":{\"schemaVersion\":\"1.0\",\"items\":[]}}");
        assertEmpty("{\"instanceSummary\":{\"schemaVersion\":\"1.0\",\"items\":[%s]}}"
                .formatted(item(OTHER_INSTANCE_ID, "pod-a", "\"endpointEvidenceRefs\": []")));
        assertEmpty("""
                {
                  "instanceSummary": {
                    "schemaVersion": "1.0",
                    "items": [
                      {
                        "instanceId": "not-a-uuid",
                        "instanceName": "pod-a"
                      },
                      %s
                    ]
                  }
                }
                """.formatted(item(OTHER_INSTANCE_ID, "pod-a", "\"endpointEvidenceRefs\": []")));
        assertEmpty("""
                {
                  "instanceSummary": {
                    "schemaVersion": "1.0",
                    "items": [
                      {
                        "instanceId": "%s",
                        "instanceName": "pod-a",
                        "observationStatus": "observed",
                        "metricData": {"statusSource": "starter_heartbeat"}
                      }
                    ]
                  }
                }
                """.formatted(TARGET_INSTANCE_ID));
    }

    @Test
    void ignoresUnknownFieldsCapsItemsAndSkipsMalformedEndpointRefs() {
        List<String> firstFifty = IntStream.range(0, 50)
                .mapToObj(index -> index == 49
                        ? item(TARGET_INSTANCE_ID, "pod-target", """
                                "endpointEvidenceRefs": [
                                  {"endpointKey": "", "method": "GET"},
                                  {"endpointKey": "GET /orders", "relatedApplicationPriorityRank": 0},
                                  {"endpointKey": "GET /health", "method": "GET", "route": "/health"}
                                ]
                                """)
                        : item(UUID.fromString("00000000-0000-0000-0000-%012d".formatted(index + 1)),
                                "pod-" + index,
                                "\"endpointEvidenceRefs\": []"))
                .toList();
        String json = """
                {
                  "instanceSummary": {
                    "schemaVersion": "1.0",
                    "items": [%s]
                  }
                }
                """.formatted(String.join(",", firstFifty));

        InstanceSnapshotTrendReadModel.Point point = parser.projectPoint(row(json), TARGET_INSTANCE_ID)
                .orElseThrow();

        assertThat(point.instanceName()).isEqualTo("pod-target");
        assertThat(point.endpointEvidenceRefs())
                .extracting(InstanceSnapshotTrendReadModel.EndpointEvidenceRef::endpointKey)
                .containsExactly("GET /health");

        String targetAfterCap = """
                {
                  "instanceSummary": {
                    "schemaVersion": "1.0",
                    "items": [%s,%s]
                  }
                }
                """.formatted(String.join(",", firstFifty),
                item(UUID.fromString("00000000-0000-0000-0000-000000009999"),
                        "pod-after-cap",
                        "\"endpointEvidenceRefs\": []"));

        Optional<InstanceSnapshotTrendReadModel.Point> cappedPoint = parser.projectPoint(
                row(targetAfterCap),
                UUID.fromString("00000000-0000-0000-0000-000000009999"));

        assertThat(cappedPoint).isEmpty();
    }

    @Test
    void projectsTargetItemWhenResourceHintsIsEmptyObject() {
        String json = """
                {
                  "instanceSummary": {
                    "schemaVersion": "1.0",
                    "items": [
                      %s
                    ]
                  }
                }
                """.formatted(itemWithResourceHints(
                TARGET_INSTANCE_ID,
                "pod-a",
                "\"resourceHints\": {}",
                "\"endpointEvidenceRefs\": []"));

        InstanceSnapshotTrendReadModel.Point point = parser.projectPoint(row(json), TARGET_INSTANCE_ID)
                .orElseThrow();

        assertThat(point.instanceName()).isEqualTo("pod-a");
        assertThat(point.resourceHints()).isNull();
    }

    @Test
    void boundsEndpointEvidenceRefsToFirstTenValidItems() {
        String refs = IntStream.rangeClosed(1, 11)
                .mapToObj(index -> """
                        {
                          "endpointKey": "GET /endpoint-%d",
                          "method": "GET",
                          "route": "/endpoint-%d",
                          "relatedRuleIds": []
                        }
                        """.formatted(index, index))
                .collect(java.util.stream.Collectors.joining(","));
        String json = """
                {
                  "instanceSummary": {
                    "schemaVersion": "1.0",
                    "items": [
                      %s
                    ]
                  }
                }
                """.formatted(item(TARGET_INSTANCE_ID, "pod-a", "\"endpointEvidenceRefs\": [%s]".formatted(refs)));

        InstanceSnapshotTrendReadModel.Point point = parser.projectPoint(row(json), TARGET_INSTANCE_ID)
                .orElseThrow();

        assertThat(point.endpointEvidenceRefs()).hasSize(10);
        assertThat(point.endpointEvidenceRefs())
                .extracting(InstanceSnapshotTrendReadModel.EndpointEvidenceRef::endpointKey)
                .containsExactly(
                        "GET /endpoint-1",
                        "GET /endpoint-2",
                        "GET /endpoint-3",
                        "GET /endpoint-4",
                        "GET /endpoint-5",
                        "GET /endpoint-6",
                        "GET /endpoint-7",
                        "GET /endpoint-8",
                        "GET /endpoint-9",
                        "GET /endpoint-10");
    }

    private void assertEmpty(String json) {
        assertThat(parser.projectPoint(row(json), TARGET_INSTANCE_ID)).isEmpty();
    }

    private static DashboardSnapshotTrendRow row(String readModelJson) {
        return new DashboardSnapshotTrendRow(
                SNAPSHOT_ID,
                OffsetDateTime.parse("2026-05-26T08:00:00Z"),
                OffsetDateTime.parse("2026-05-26T08:00:00Z"),
                "active",
                "hourly_scheduled",
                readModelJson);
    }

    private static String item(UUID instanceId, String instanceName, String endpointRefsJsonProperty) {
        return itemWithResourceHints(
                instanceId,
                instanceName,
                """
                  "resourceHints": {
                    "source": "accepted_bucket_latest_sample",
                    "status": "available",
                    "bucketEndUtc": "2026-05-26T07:59:30Z",
                    "cpuUsageRatio": 0.41,
                    "heapUsedRatio": 0.62,
                    "datasourcePoolUsageRatio": 0.37
                  }
                """,
                endpointRefsJsonProperty);
    }

    private static String itemWithResourceHints(
            UUID instanceId,
            String instanceName,
            String resourceHintsJsonProperty,
            String endpointRefsJsonProperty) {
        return """
                {
                  "instanceId": "%s",
                  "instanceName": "%s",
                  "observationStatus": "observed",
                  "metricData": {
                    "statusSource": "accepted_bucket",
                    "lastAcceptedBucketAt": "2026-05-26T07:59:30Z",
                    "freshnessLabel": "current"
                  },
                  "starterConnection": {
                    "statusSource": "starter_heartbeat",
                    "lastHeartbeatAt": "2026-05-26T07:59:45Z",
                    "lastHeartbeatStatus": "received",
                    "connectionMeaning": "starter_connected",
                    "stateImpact": "none"
                  },
                  "starterPercentilePoint": {
                    "source": "starter_canonical_percentile",
                    "scope": "instance_bucket",
                    "bucketStartUtc": "2026-05-26T07:59:00Z",
                    "bucketEndUtc": "2026-05-26T07:59:30Z",
                    "requestCount": 820,
                    "p95Ms": 210,
                    "p99Ms": 360
                  },
                  %s,
                  "applicationTriageContribution": {
                    "status": "observed",
                    "contributed": true,
                    "relatedRuleIds": ["global_error_spike"],
                    "reason": "selected_instance_has_evidence_for_application_triage"
                  },
                  %s
                }
                """.formatted(instanceId, instanceName, resourceHintsJsonProperty, endpointRefsJsonProperty);
    }
}
