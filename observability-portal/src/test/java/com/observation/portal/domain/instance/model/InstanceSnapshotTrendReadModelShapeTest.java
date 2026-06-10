package com.observation.portal.domain.instance.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstanceSnapshotTrendReadModelShapeTest {

    @Test
    void topLevelFieldsMatchSnapshotTrendContract() {
        List<String> fieldNames = componentNames(InstanceSnapshotTrendReadModel.class);

        assertThat(fieldNames).containsExactly(
                "generatedAt",
                "application",
                "instance",
                "source",
                "horizon",
                "points");
    }

    @Test
    void defaultShapeKeepsStoredSnapshotSourceAndHorizonMetadata() {
        InstanceSnapshotTrendReadModel readModel = readModel(List.of(point()));

        assertThat(readModel.source()).isEqualTo("dashboard_snapshots.read_model_json.instanceSummary.items");
        assertThat(readModel.horizon().requestedSince()).isEqualTo("7d");
        assertThat(readModel.horizon().defaultSince()).isEqualTo("7d");
        assertThat(readModel.horizon().maxSince()).isEqualTo("14d");
        assertThat(readModel.horizon().limit()).isEqualTo(336);
        assertThat(readModel.horizon().maxLimit()).isEqualTo(672);
        assertThat(readModel.horizon().order()).isEqualTo("currentWindowEndUtc_asc");
        assertThat(readModel.application().links().dashboard())
                .isEqualTo("/api/projects/%s/applications/%s/dashboard"
                        .formatted(projectId(), applicationId()));
        assertThat(readModel.instance().links().evidence())
                .isEqualTo("/api/projects/%s/applications/%s/instances/%s/evidence"
                        .formatted(projectId(), applicationId(), instanceId()));
        assertThat(readModel.points().get(0).storedApplicationStateCode()).isEqualTo("active");
        assertThat(readModel.points().get(0).captureReason()).isEqualTo("unknown_future_reason");
        assertThat(readModel.points().get(0).starterConnection().stateImpact()).isEqualTo("none");
        assertThat(readModel.points().get(0).endpointEvidenceRefs()).hasSize(1);
    }

    @Test
    void captureReasonPreservesOpaqueStoredStringAsIs() {
        InstanceSnapshotTrendReadModel.Point spaced = point(
                UUID.fromString("00000000-0000-0000-0000-000000005732"),
                "  unknown_future_reason  ");
        InstanceSnapshotTrendReadModel.Point blank = point(
                UUID.fromString("00000000-0000-0000-0000-000000005733"),
                "   ");

        assertThat(spaced.captureReason()).isEqualTo("  unknown_future_reason  ");
        assertThat(blank.captureReason()).isEqualTo("   ");
    }

    @Test
    void constructorsRejectUnboundedPointCountAndInvalidStoredBlocks() {
        InstanceSnapshotTrendReadModel.Horizon horizon = horizon(672);
        List<InstanceSnapshotTrendReadModel.Point> tooManyPoints = java.util.stream.IntStream.range(0, 673)
                .mapToObj(index -> point(UUID.randomUUID()))
                .toList();

        assertThatThrownBy(() -> new InstanceSnapshotTrendReadModel(
                OffsetDateTime.parse("2026-05-26T08:10:35Z"),
                application(),
                instance(),
                InstanceSnapshotTrendReadModel.SOURCE,
                horizon,
                tooManyPoints))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InstanceSnapshotTrendReadModel.MetricData(
                "starter_heartbeat",
                null,
                "current"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InstanceSnapshotTrendReadModel.StarterConnection(
                "starter_heartbeat",
                null,
                "received",
                "starter_connected",
                "impacts_state"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InstanceSnapshotTrendReadModel.ResourceHints(
                "accepted_bucket_latest_sample",
                "available",
                null,
                BigDecimal.valueOf(1.1d),
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InstanceSnapshotTrendReadModel.EndpointEvidenceRef(
                "GET /raw",
                "GET",
                "/raw",
                0,
                List.of(),
                null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publicShapeDoesNotExposeRawRecalculationOrRecoveryFields() {
        List<String> publicFieldNames = List.of(
                componentNames(InstanceSnapshotTrendReadModel.class),
                componentNames(InstanceSnapshotTrendReadModel.Point.class),
                componentNames(InstanceSnapshotTrendReadModel.EndpointEvidenceRef.class),
                componentNames(InstanceSnapshotTrendReadModel.ApplicationTriageContribution.class)).stream()
                .flatMap(List::stream)
                .toList();

        assertThat(publicFieldNames).doesNotContain(
                "rawBucketJson",
                "rawSnapshotJson",
                "endpointsJson",
                "endpointTimeseries",
                "previousState",
                "lastHealthyAt",
                "recoveryMarker",
                "recoveredAt",
                "lastRecoveredAt",
                "healthScore",
                "availabilityScore",
                "recommendedAction",
                "confidence",
                "score");
    }

    private static List<String> componentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }

    private static InstanceSnapshotTrendReadModel readModel(List<InstanceSnapshotTrendReadModel.Point> points) {
        return new InstanceSnapshotTrendReadModel(
                OffsetDateTime.parse("2026-05-26T08:10:35Z"),
                application(),
                instance(),
                InstanceSnapshotTrendReadModel.SOURCE,
                horizon(336),
                points);
    }

    private static InstanceSnapshotTrendReadModel.Application application() {
        return new InstanceSnapshotTrendReadModel.Application(
                projectId(),
                applicationId(),
                "orders-api",
                "prod",
                new InstanceSnapshotTrendReadModel.ApplicationLinks(
                        "/api/projects/%s/applications/%s/dashboard".formatted(projectId(), applicationId())));
    }

    private static InstanceSnapshotTrendReadModel.Instance instance() {
        return new InstanceSnapshotTrendReadModel.Instance(
                instanceId(),
                "pod-a",
                OffsetDateTime.parse("2026-05-26T05:00:05Z"),
                OffsetDateTime.parse("2026-05-26T08:00:05Z"),
                new InstanceSnapshotTrendReadModel.InstanceLinks(
                        "/api/projects/%s/applications/%s/instances/%s/evidence"
                                .formatted(projectId(), applicationId(), instanceId())));
    }

    private static InstanceSnapshotTrendReadModel.Horizon horizon(int limit) {
        return new InstanceSnapshotTrendReadModel.Horizon(
                OffsetDateTime.parse("2026-05-19T08:10:35Z"),
                OffsetDateTime.parse("2026-05-26T08:10:35Z"),
                "7d",
                "7d",
                "14d",
                limit,
                672,
                "currentWindowEndUtc_asc");
    }

    private static InstanceSnapshotTrendReadModel.Point point() {
        return point(UUID.fromString("00000000-0000-0000-0000-000000005731"));
    }

    private static InstanceSnapshotTrendReadModel.Point point(UUID snapshotId) {
        return point(snapshotId, "unknown_future_reason");
    }

    private static InstanceSnapshotTrendReadModel.Point point(UUID snapshotId, String captureReason) {
        return new InstanceSnapshotTrendReadModel.Point(
                snapshotId,
                OffsetDateTime.parse("2026-05-26T08:00:00Z"),
                OffsetDateTime.parse("2026-05-26T08:00:00Z"),
                "active",
                captureReason,
                "pod-a",
                "observed",
                new InstanceSnapshotTrendReadModel.MetricData(
                        "accepted_bucket",
                        OffsetDateTime.parse("2026-05-26T07:59:30Z"),
                        "current"),
                new InstanceSnapshotTrendReadModel.StarterConnection(
                        "starter_heartbeat",
                        OffsetDateTime.parse("2026-05-26T07:59:45Z"),
                        "received",
                        "starter_connected",
                        "none"),
                new InstanceSnapshotTrendReadModel.StarterPercentilePoint(
                        "starter_canonical_percentile",
                        "instance_bucket",
                        OffsetDateTime.parse("2026-05-26T07:59:00Z"),
                        OffsetDateTime.parse("2026-05-26T07:59:30Z"),
                        820L,
                        210L,
                        360L),
                new InstanceSnapshotTrendReadModel.ResourceHints(
                        "accepted_bucket_latest_sample",
                        "available",
                        OffsetDateTime.parse("2026-05-26T07:59:30Z"),
                        BigDecimal.valueOf(0.41d),
                        BigDecimal.valueOf(0.62d),
                        BigDecimal.valueOf(0.37d)),
                new InstanceSnapshotTrendReadModel.ApplicationTriageContribution(
                        "available",
                        false,
                        List.of(),
                        "no_action_needed"),
                List.of(new InstanceSnapshotTrendReadModel.EndpointEvidenceRef(
                        "POST /orders",
                        "POST",
                        "/orders",
                        1,
                        List.of("endpoint_error_spike"),
                        "snapshot:top-endpoint-1")));
    }

    private static UUID projectId() {
        return UUID.fromString("00000000-0000-0000-0000-000000005201");
    }

    private static UUID applicationId() {
        return UUID.fromString("00000000-0000-0000-0000-000000005211");
    }

    private static UUID instanceId() {
        return UUID.fromString("00000000-0000-0000-0000-000000005221");
    }
}
