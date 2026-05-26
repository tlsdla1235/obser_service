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

class InstanceEvidenceReadModelShapeTest {

    @Test
    void topLevelFieldsMatchInstanceEvidenceContract() {
        List<String> fieldNames = Arrays.stream(InstanceEvidenceReadModel.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(fieldNames).containsExactly(
                "generatedAt",
                "application",
                "instance",
                "metricData",
                "starterConnection",
                "starterPercentiles",
                "histogramDistribution",
                "resourceHints",
                "applicationTriageContribution",
                "endpointEvidence",
                "links");
    }

    @Test
    void defaultBlocksKeepBoundedMissingShape() {
        InstanceEvidenceReadModel readModel = readModel();

        assertThat(readModel.metricData().statusSource()).isEqualTo("accepted_bucket");
        assertThat(readModel.metricData().window().name()).isEqualTo("current_15m");
        assertThat(readModel.metricData().window().bucketDurationSeconds()).isEqualTo(30);
        assertThat(readModel.metricData().errorRate()).isNull();
        assertThat(readModel.starterConnection().statusSource()).isEqualTo("starter_heartbeat");
        assertThat(readModel.starterConnection().stateImpact()).isEqualTo("none");
        assertThat(readModel.starterPercentiles().source()).isEqualTo("starter_canonical_percentile");
        assertThat(readModel.starterPercentiles().points()).isEmpty();
        assertThat(readModel.histogramDistribution().source()).isEqualTo("histogram_bucket_distribution");
        assertThat(readModel.histogramDistribution().buckets()).isEmpty();
        assertThat(readModel.resourceHints().source()).isEqualTo("accepted_bucket_latest_sample");
        assertThat(readModel.applicationTriageContribution().relatedRuleIds()).isEmpty();
        assertThat(readModel.endpointEvidence().source()).isEqualTo("accepted_metric_buckets.endpoints_json");
        assertThat(readModel.endpointEvidence().reason()).isNull();
        assertThat(readModel.endpointEvidence().items()).isEmpty();
        assertThat(readModel.links().snapshotTrend()).isNull();
    }

    @Test
    void rejectsUnboundedCountsRatiosAndItemCounts() {
        InstanceEvidenceReadModel.MetricWindow window = metricWindow();

        assertThatThrownBy(() -> new InstanceEvidenceReadModel.MetricData(
                "accepted_bucket",
                window,
                null,
                "waiting_first_data",
                "missing",
                0L,
                0L,
                BigDecimal.ZERO,
                null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InstanceEvidenceReadModel.ResourceHints(
                "accepted_bucket_latest_sample",
                "available",
                null,
                null,
                BigDecimal.valueOf(1.1d),
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InstanceEvidenceReadModel.EndpointEvidence(
                "accepted_metric_buckets.endpoints_json",
                "instance_current_15m",
                "application_priority_presence_then_triage_then_instance_request_count",
                "selected_instance_signal_then_application_priority_reference",
                "available",
                null,
                List.of(
                        endpointItem(1),
                        endpointItem(2),
                        endpointItem(3),
                        endpointItem(4),
                        endpointItem(5),
                        endpointItem(6))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEndpointEvidenceValuesOutsideBoundedContract() {
        assertThatThrownBy(() -> new InstanceEvidenceReadModel.StarterPercentiles(
                "starter_canonical_percentile",
                "application",
                "current_15m",
                30,
                30,
                "source_scoped_series",
                "no_average_no_max_no_merge_no_histogram_recalculation",
                "missing",
                null,
                List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InstanceEvidenceReadModel.StarterPercentiles(
                "starter_canonical_percentile",
                "instance",
                "current_15m",
                30,
                30,
                "source_scoped_points",
                "no_average_no_max_no_merge_no_histogram_recalculation",
                "missing",
                null,
                List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InstanceEvidenceReadModel.StarterPercentiles(
                "starter_canonical_percentile",
                "instance",
                "current_15m",
                30,
                30,
                "source_scoped_series",
                "no_average_no_max_no_merge_no_histogram_recalculation",
                "stale",
                null,
                List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InstanceEvidenceReadModel.EndpointEvidence(
                "accepted_metric_buckets.endpoints_json",
                "instance_current_15m",
                "application_priority_presence_then_triage_then_instance_request_count",
                "selected_instance_signal_then_application_priority_reference",
                "stale",
                null,
                List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InstanceEvidenceReadModel.EndpointEvidence(
                "accepted_metric_buckets.endpoints_json",
                "instance_current_15m",
                "application_priority_presence_then_triage_then_instance_request_count",
                "selected_instance_signal_then_application_priority_reference",
                "available",
                "raw_json_parse_failed",
                List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InstanceEvidenceReadModel.EndpointEvidence(
                "accepted_metric_buckets.endpoints_json",
                "application_current_15m",
                "application_priority_presence_then_triage_then_instance_request_count",
                "selected_instance_signal_then_application_priority_reference",
                "available",
                null,
                List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InstanceEvidenceReadModel.EndpointEvidence(
                "accepted_metric_buckets.endpoints_json",
                "instance_current_15m",
                "selected_instance_request_count",
                "selected_instance_signal_then_application_priority_reference",
                "available",
                null,
                List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InstanceEvidenceReadModel.EndpointEvidence(
                "accepted_metric_buckets.endpoints_json",
                "instance_current_15m",
                "application_priority_presence_then_triage_then_instance_request_count",
                "priority_rank",
                "available",
                null,
                List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InstanceEvidenceReadModel.EndpointEvidenceItem(
                "GET",
                "/orders",
                "GET /orders",
                "maybe_observed",
                1L,
                0L,
                BigDecimal.ZERO,
                1L,
                0L,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                null,
                List.of(),
                "histogram_bucket_distribution",
                null,
                1,
                List.of(),
                "available",
                "selected_instance_endpoint_observed"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InstanceEvidenceReadModel.EndpointEvidenceItem(
                "GET",
                "/orders",
                "GET /orders",
                "observed",
                1L,
                0L,
                BigDecimal.ZERO,
                1L,
                0L,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                null,
                List.of(),
                "summary_duration_buckets",
                null,
                1,
                List.of(),
                "available",
                "selected_instance_endpoint_observed"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publicShapeDoesNotExposeForbiddenRawOrHealthFields() {
        List<String> nestedFieldNames = List.of(
                componentNames(InstanceEvidenceReadModel.MetricData.class),
                componentNames(InstanceEvidenceReadModel.StarterConnection.class),
                componentNames(InstanceEvidenceReadModel.EndpointEvidence.class),
                componentNames(InstanceEvidenceReadModel.EndpointEvidenceItem.class)).stream()
                .flatMap(List::stream)
                .toList();

        assertThat(nestedFieldNames).doesNotContain(
                "rawBucketJson",
                "rawJson",
                "endpointsJson",
                "rawPath",
                "queryString",
                "traceId",
                "healthScore",
                "availabilityScore",
                "hostStatus",
                "connectedAndHealthy",
                "endpointP95Ms",
                "endpointP99Ms",
                "recommendedAction");
    }

    private static List<String> componentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }

    private static InstanceEvidenceReadModel readModel() {
        UUID projectId = UUID.fromString("00000000-0000-0000-0000-000000005201");
        UUID applicationId = UUID.fromString("00000000-0000-0000-0000-000000005211");
        UUID instanceId = UUID.fromString("00000000-0000-0000-0000-000000005221");
        String dashboard = "/api/projects/%s/applications/%s/dashboard".formatted(projectId, applicationId);
        String self = "/api/projects/%s/applications/%s/instances/%s/evidence"
                .formatted(projectId, applicationId, instanceId);
        return new InstanceEvidenceReadModel(
                OffsetDateTime.parse("2026-05-26T06:10:35Z"),
                new InstanceEvidenceReadModel.Application(
                        projectId,
                        applicationId,
                        "orders-api",
                        "prod",
                        new InstanceEvidenceReadModel.ApplicationLinks(dashboard)),
                new InstanceEvidenceReadModel.Instance(
                        instanceId,
                        "pod-a",
                        OffsetDateTime.parse("2026-05-26T05:00:05Z"),
                        OffsetDateTime.parse("2026-05-26T06:10:05Z")),
                InstanceEvidenceReadModel.MetricData.missing(metricWindow()),
                InstanceEvidenceReadModel.StarterConnection.missing(),
                InstanceEvidenceReadModel.StarterPercentiles.missing(),
                InstanceEvidenceReadModel.HistogramDistribution.missing(),
                InstanceEvidenceReadModel.ResourceHints.missing(),
                InstanceEvidenceReadModel.ApplicationTriageContribution.missing(),
                InstanceEvidenceReadModel.EndpointEvidence.missing(),
                new InstanceEvidenceReadModel.Links(self, dashboard, null));
    }

    private static InstanceEvidenceReadModel.MetricWindow metricWindow() {
        return new InstanceEvidenceReadModel.MetricWindow(
                "current_15m",
                OffsetDateTime.parse("2026-05-26T05:55:30Z"),
                OffsetDateTime.parse("2026-05-26T06:10:30Z"),
                30);
    }

    private static InstanceEvidenceReadModel.EndpointEvidenceItem endpointItem(int displayOrder) {
        return new InstanceEvidenceReadModel.EndpointEvidenceItem(
                "GET",
                "/orders/%d".formatted(displayOrder),
                "GET /orders/%d".formatted(displayOrder),
                "observed",
                1L,
                0L,
                BigDecimal.ZERO,
                1L,
                0L,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                null,
                List.of(),
                "histogram_bucket_distribution",
                null,
                displayOrder,
                List.of(),
                "available",
                "selected_instance_endpoint_observed");
    }
}
