package com.observation.portal.domain.instance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.portal.common.time.AcceptedBucketFreshnessEvaluator;
import com.observation.portal.common.time.TimeBucketWindowCalculator;
import com.observation.portal.domain.bucket.model.EndpointEvidenceRow;
import com.observation.portal.domain.bucket.model.HistogramBucketEvidenceRow;
import com.observation.portal.domain.bucket.model.LocalPercentileEvidenceRow;
import com.observation.portal.domain.bucket.model.RuntimeRatioEvidenceRow;
import com.observation.portal.domain.bucket.model.WindowBucketAggregate;
import com.observation.portal.domain.bucket.repository.MetricBucketRepository;
import com.observation.portal.domain.catalog.entity.ApplicationEntity;
import com.observation.portal.domain.catalog.entity.ApplicationInstanceEntity;
import com.observation.portal.domain.catalog.repository.ApplicationInstanceRepository;
import com.observation.portal.domain.catalog.repository.ApplicationRepository;
import com.observation.portal.domain.dashboard.model.ApplicationDashboardReadModel;
import com.observation.portal.domain.dashboard.service.EndpointEvidenceAggregationService;
import com.observation.portal.domain.dashboard.service.EndpointPriorityService;
import com.observation.portal.domain.dashboard.service.TriageSummaryService;
import com.observation.portal.domain.ingest.model.StarterHeartbeatTelemetryRecord;
import com.observation.portal.domain.ingest.repository.StarterHeartbeatTelemetryRepository;
import com.observation.portal.domain.instance.model.InstanceEvidenceReadModel;
import com.observation.portal.domain.state.model.DegradedHysteresisInput;
import com.observation.portal.domain.state.service.LifecycleStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InstanceEvidenceReadModelServiceTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000005201");
    private static final UUID OTHER_PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000009201");
    private static final UUID APPLICATION_ID = UUID.fromString("00000000-0000-0000-0000-000000005211");
    private static final UUID INSTANCE_ID = UUID.fromString("00000000-0000-0000-0000-000000005221");
    private static final Instant QUERY_AT = Instant.parse("2026-05-26T06:10:35Z");
    private static final Instant EVALUATION_AT = Instant.parse("2026-05-26T06:10:30Z");
    private static final Instant CURRENT_START = Instant.parse("2026-05-26T05:55:30Z");
    private static final Instant BASELINE_START = Instant.parse("2026-05-26T05:40:30Z");
    private static final Clock CLOCK = Clock.fixed(QUERY_AT, ZoneOffset.UTC);

    private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
    private final ApplicationInstanceRepository applicationInstanceRepository =
            mock(ApplicationInstanceRepository.class);
    private final MetricBucketRepository metricBucketRepository = mock(MetricBucketRepository.class);
    private final StarterHeartbeatTelemetryRepository heartbeatRepository =
            mock(StarterHeartbeatTelemetryRepository.class);
    private final TriageSummaryService triageSummaryService = mock(TriageSummaryService.class);
    private final EndpointEvidenceAggregationService endpointEvidenceAggregationService =
            new EndpointEvidenceAggregationService(new ObjectMapper());
    private final EndpointPriorityService endpointPriorityService =
            new EndpointPriorityService(endpointEvidenceAggregationService);

    private InstanceEvidenceReadModelService service;

    @BeforeEach
    void setUp() {
        service = new InstanceEvidenceReadModelService(
                applicationRepository,
                applicationInstanceRepository,
                metricBucketRepository,
                heartbeatRepository,
                new AcceptedBucketFreshnessEvaluator(CLOCK),
                new TimeBucketWindowCalculator(CLOCK),
                new LifecycleStateService(),
                triageSummaryService,
                endpointPriorityService,
                endpointEvidenceAggregationService,
                new ObjectMapper(),
                CLOCK);
        stubCatalogPathConsistency();
        stubEmptyEvidence();
    }

    @Test
    void returnsFoundationModelWhenProjectApplicationAndInstanceCatalogPathMatches() {
        InstanceEvidenceReadModel evidence = service.getEvidence(PROJECT_ID, APPLICATION_ID, INSTANCE_ID)
                .orElseThrow();

        assertThat(evidence.generatedAt()).isEqualTo(offset(QUERY_AT));
        assertThat(evidence.application().projectId()).isEqualTo(PROJECT_ID);
        assertThat(evidence.application().applicationId()).isEqualTo(APPLICATION_ID);
        assertThat(evidence.application().name()).isEqualTo("orders-api");
        assertThat(evidence.application().environment()).isEqualTo("prod");
        assertThat(evidence.application().links().dashboard())
                .isEqualTo("/api/projects/%s/applications/%s/dashboard".formatted(PROJECT_ID, APPLICATION_ID));
        assertThat(evidence.instance().instanceId()).isEqualTo(INSTANCE_ID);
        assertThat(evidence.instance().instanceName()).isEqualTo("pod-a");
        assertThat(evidence.metricData().window().startUtc()).isEqualTo(offset(CURRENT_START));
        assertThat(evidence.metricData().window().endUtc()).isEqualTo(offset(EVALUATION_AT));
        assertThat(evidence.metricData().statusSource()).isEqualTo("accepted_bucket");
        assertThat(evidence.metricData().freshnessLabel()).isEqualTo("waiting_first_data");
        assertThat(evidence.metricData().requestCount()).isZero();
        assertThat(evidence.metricData().errorRate()).isNull();
        assertThat(evidence.starterConnection().stateImpact()).isEqualTo("none");
        assertThat(evidence.starterPercentiles().status()).isEqualTo("missing");
        assertThat(evidence.histogramDistribution().status()).isEqualTo("missing");
        assertThat(evidence.resourceHints().status()).isEqualTo("missing");
        assertThat(evidence.applicationTriageContribution().reason()).isEqualTo("no_application_triage_cards");
        assertThat(evidence.endpointEvidence().items()).isEmpty();
        assertThat(evidence.links().self())
                .isEqualTo("/api/projects/%s/applications/%s/instances/%s/evidence"
                        .formatted(PROJECT_ID, APPLICATION_ID, INSTANCE_ID));
        assertThat(evidence.links().snapshotTrend())
                .isEqualTo("/api/projects/%s/applications/%s/instances/%s/snapshot-trend"
                        .formatted(PROJECT_ID, APPLICATION_ID, INSTANCE_ID));
        verify(applicationRepository).findByIdAndProjectId(APPLICATION_ID, PROJECT_ID);
        verify(applicationInstanceRepository).findByIdAndApplicationId(INSTANCE_ID, APPLICATION_ID);
        verify(applicationInstanceRepository, never()).findByApplicationIdAndInstanceName(APPLICATION_ID, "pod-a");
    }

    @Test
    void returnsEmptyWhenApplicationIsMissingOrProjectMismatched() {
        when(applicationRepository.findByIdAndProjectId(APPLICATION_ID, OTHER_PROJECT_ID))
                .thenReturn(Optional.empty());

        assertThat(service.getEvidence(OTHER_PROJECT_ID, APPLICATION_ID, INSTANCE_ID)).isEmpty();

        verify(applicationRepository).findByIdAndProjectId(APPLICATION_ID, OTHER_PROJECT_ID);
        verifyNoInteractions(applicationInstanceRepository, metricBucketRepository, heartbeatRepository);
    }

    @Test
    void returnsEmptyWhenInstanceIsMissingOrApplicationMismatched() {
        when(applicationInstanceRepository.findByIdAndApplicationId(INSTANCE_ID, APPLICATION_ID))
                .thenReturn(Optional.empty());

        assertThat(service.getEvidence(PROJECT_ID, APPLICATION_ID, INSTANCE_ID)).isEmpty();

        verify(applicationRepository).findByIdAndProjectId(APPLICATION_ID, PROJECT_ID);
        verify(applicationInstanceRepository).findByIdAndApplicationId(INSTANCE_ID, APPLICATION_ID);
        verify(applicationInstanceRepository, never()).findByApplicationIdAndInstanceName(APPLICATION_ID, "pod-a");
        verifyNoInteractions(metricBucketRepository, heartbeatRepository);
    }

    @Test
    void metricDataUsesSelectedInstanceLatestBucketAndFlooredCurrentWindow() {
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationInstanceIdAtOrBefore(
                INSTANCE_ID,
                EVALUATION_AT))
                .thenReturn(Optional.of(offset("2026-05-26T06:10:00Z")));
        when(metricBucketRepository.findWindowAggregateByApplicationInstanceId(
                INSTANCE_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(new WindowBucketAggregate(120L, 12L));

        InstanceEvidenceReadModel evidence = service.getEvidence(PROJECT_ID, APPLICATION_ID, INSTANCE_ID)
                .orElseThrow();

        assertThat(evidence.metricData().window().endUtc()).isEqualTo(offset(EVALUATION_AT));
        assertThat(evidence.metricData().lastAcceptedBucketAt()).isEqualTo(offset("2026-05-26T06:10:00Z"));
        assertThat(evidence.metricData().freshnessLabel()).isEqualTo("current");
        assertThat(evidence.metricData().sampleReadiness()).isEqualTo("sufficient");
        assertThat(evidence.metricData().requestCount()).isEqualTo(120L);
        assertThat(evidence.metricData().errorCount()).isEqualTo(12L);
        assertThat(evidence.metricData().errorRate()).isEqualByComparingTo("0.1");
        verify(metricBucketRepository).findWindowAggregateByApplicationInstanceId(
                INSTANCE_ID,
                CURRENT_START,
                EVALUATION_AT);
    }

    @Test
    void metricDataKeepsErrorRateNullWhenRequestCountIsZero() {
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationInstanceIdAtOrBefore(
                INSTANCE_ID,
                EVALUATION_AT))
                .thenReturn(Optional.of(offset("2026-05-26T06:10:00Z")));
        when(metricBucketRepository.findWindowAggregateByApplicationInstanceId(
                INSTANCE_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(new WindowBucketAggregate(0L, 0L));

        InstanceEvidenceReadModel evidence = service.getEvidence(PROJECT_ID, APPLICATION_ID, INSTANCE_ID)
                .orElseThrow();

        assertThat(evidence.metricData().freshnessLabel()).isEqualTo("current");
        assertThat(evidence.metricData().sampleReadiness()).isEqualTo("sufficient");
        assertThat(evidence.metricData().reason()).isEqualTo("metric_data_idle");
        assertThat(evidence.metricData().errorRate()).isNull();
    }

    @Test
    void starterConnectionUsesHeartbeatIdentityAndDoesNotChangeMetricFreshness() {
        when(heartbeatRepository.findByIdentity(PROJECT_ID, "orders-api", "prod", "pod-a"))
                .thenReturn(Optional.of(heartbeat("2026-05-26T06:10:20Z", "received")));

        InstanceEvidenceReadModel evidence = service.getEvidence(PROJECT_ID, APPLICATION_ID, INSTANCE_ID)
                .orElseThrow();

        assertThat(evidence.metricData().freshnessLabel()).isEqualTo("waiting_first_data");
        assertThat(evidence.starterConnection().statusSource()).isEqualTo("starter_heartbeat");
        assertThat(evidence.starterConnection().lastHeartbeatStatus()).isEqualTo("received");
        assertThat(evidence.starterConnection().freshnessLabel()).isEqualTo("recent");
        assertThat(evidence.starterConnection().connectionMeaning()).isEqualTo("starter_connected");
        assertThat(evidence.starterConnection().stateImpact()).isEqualTo("none");
        verify(heartbeatRepository).findByIdentity(PROJECT_ID, "orders-api", "prod", "pod-a");
    }

    @Test
    void staleHeartbeatDoesNotDowngradeCurrentMetricData() {
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationInstanceIdAtOrBefore(
                INSTANCE_ID,
                EVALUATION_AT))
                .thenReturn(Optional.of(offset("2026-05-26T06:10:00Z")));
        when(metricBucketRepository.findWindowAggregateByApplicationInstanceId(
                INSTANCE_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(new WindowBucketAggregate(60L, 0L));
        when(heartbeatRepository.findByIdentity(PROJECT_ID, "orders-api", "prod", "pod-a"))
                .thenReturn(Optional.of(heartbeat("2026-05-26T06:07:00Z", "received")));

        InstanceEvidenceReadModel evidence = service.getEvidence(PROJECT_ID, APPLICATION_ID, INSTANCE_ID)
                .orElseThrow();

        assertThat(evidence.metricData().freshnessLabel()).isEqualTo("current");
        assertThat(evidence.starterConnection().freshnessLabel()).isEqualTo("stale");
        assertThat(evidence.starterConnection().connectionMeaning()).isEqualTo("starter_disconnected");
    }

    @Test
    void starterPercentilesExposeValidOrderedSeriesWithCanonicalPublicSource() {
        when(metricBucketRepository.findLocalPercentileEvidenceRowsByApplicationInstanceId(
                INSTANCE_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(List.of(
                        localPercentileRow("2026-05-26T06:00:00Z", 20L, 220L, 440L),
                        localPercentileRow("2026-05-26T05:56:00Z", 10L, 120L, 240L),
                        localPercentileRow("2026-05-26T06:01:00Z", 10L, 300L, 200L)));

        InstanceEvidenceReadModel evidence = service.getEvidence(PROJECT_ID, APPLICATION_ID, INSTANCE_ID)
                .orElseThrow();

        assertThat(evidence.starterPercentiles().source()).isEqualTo("starter_canonical_percentile");
        assertThat(evidence.starterPercentiles().status()).isEqualTo("available");
        assertThat(evidence.starterPercentiles().points())
                .extracting(InstanceEvidenceReadModel.PercentilePoint::bucketEndUtc)
                .containsExactly(
                        offset("2026-05-26T05:56:30Z"),
                        offset("2026-05-26T06:00:30Z"));
    }

    @Test
    void starterPercentilesDistinguishMissingInsufficientAndMaxPointCount() {
        assertThat(service.getEvidence(PROJECT_ID, APPLICATION_ID, INSTANCE_ID)
                .orElseThrow()
                .starterPercentiles()
                .status())
                .isEqualTo("missing");

        when(metricBucketRepository.findLocalPercentileEvidenceRowsByApplicationInstanceId(
                INSTANCE_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(List.of(localPercentileRow(
                        "2026-05-26T05:56:00Z",
                        10L,
                        120L,
                        240L,
                        "application_window",
                        "starter_local",
                        false)));

        assertThat(service.getEvidence(PROJECT_ID, APPLICATION_ID, INSTANCE_ID)
                .orElseThrow()
                .starterPercentiles()
                .status())
                .isEqualTo("insufficient");

        when(metricBucketRepository.findLocalPercentileEvidenceRowsByApplicationInstanceId(
                INSTANCE_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(List.of(rawLocalPercentileRow(
                        "2026-05-26T05:56:00Z",
                        """
                                {
                                  "scope": "instance_bucket",
                                  "source": "starter_local",
                                  "bucketStartUtc": "2026-05-26T05:56:00Z",
                                  "bucketEndUtc": "2026-05-26T05:56:30Z",
                                  "requestCount": 10.5,
                                  "p95Ms": 120,
                                  "p99Ms": 240,
                                  "mergeable": false
                                }
                                """)));

        assertThat(service.getEvidence(PROJECT_ID, APPLICATION_ID, INSTANCE_ID)
                .orElseThrow()
                .starterPercentiles()
                .status())
                .isEqualTo("insufficient");

        List<LocalPercentileEvidenceRow> thirtyOneRows = IntStream.range(0, 31)
                .mapToObj(index -> localPercentileRow(
                        OffsetDateTime.parse("2026-05-26T05:55:00Z").plusSeconds(index * 30L).toString(),
                        10L,
                        100L + index,
                        200L + index))
                .toList();
        when(metricBucketRepository.findLocalPercentileEvidenceRowsByApplicationInstanceId(
                INSTANCE_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(thirtyOneRows);

        InstanceEvidenceReadModel.StarterPercentiles percentiles = service.getEvidence(
                        PROJECT_ID,
                        APPLICATION_ID,
                        INSTANCE_ID)
                .orElseThrow()
                .starterPercentiles();

        assertThat(percentiles.points()).hasSize(30);
        assertThat(percentiles.points().get(0).bucketEndUtc()).isEqualTo(offset("2026-05-26T05:56:00Z"));
        assertThat(percentiles.points().get(29).bucketEndUtc()).isEqualTo(offset("2026-05-26T06:10:30Z"));
    }

    @Test
    void histogramDistributionAggregatesOnlyMatchingBoundaries() {
        when(metricBucketRepository.findSummaryDurationBucketEvidenceRowsByApplicationInstanceId(
                INSTANCE_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(List.of(
                        histogramRow("2026-05-26T06:00:00Z", "[{\"leMs\":50,\"count\":1},{\"leMs\":100,\"count\":2}]"),
                        histogramRow("2026-05-26T06:00:30Z", "[{\"leMs\":50,\"count\":3},{\"leMs\":100,\"count\":4}]")));

        InstanceEvidenceReadModel.HistogramDistribution histogram = service.getEvidence(
                        PROJECT_ID,
                        APPLICATION_ID,
                        INSTANCE_ID)
                .orElseThrow()
                .histogramDistribution();

        assertThat(histogram.status()).isEqualTo("available");
        assertThat(histogram.reason()).isNull();
        assertThat(histogram.totalCount()).isEqualTo(6L);
        assertThat(histogram.buckets())
                .extracting(
                        InstanceEvidenceReadModel.HistogramBucket::leMs,
                        InstanceEvidenceReadModel.HistogramBucket::count)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(50L, 4L),
                        org.assertj.core.groups.Tuple.tuple(100L, 6L));
    }

    @Test
    void histogramDistributionHandlesInvalidJsonAndBoundaryMismatch() {
        when(metricBucketRepository.findSummaryDurationBucketEvidenceRowsByApplicationInstanceId(
                INSTANCE_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(List.of(histogramRow("2026-05-26T06:00:00Z", "not-json")));

        assertThat(service.getEvidence(PROJECT_ID, APPLICATION_ID, INSTANCE_ID)
                .orElseThrow()
                .histogramDistribution()
                .status())
                .isEqualTo("insufficient");

        when(metricBucketRepository.findSummaryDurationBucketEvidenceRowsByApplicationInstanceId(
                INSTANCE_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(List.of(histogramRow(
                        "2026-05-26T06:00:00Z",
                        "[{\"leMs\":50,\"count\":10},{\"leMs\":100,\"count\":5}]")));

        assertThat(service.getEvidence(PROJECT_ID, APPLICATION_ID, INSTANCE_ID)
                .orElseThrow()
                .histogramDistribution()
                .status())
                .isEqualTo("insufficient");

        when(metricBucketRepository.findSummaryDurationBucketEvidenceRowsByApplicationInstanceId(
                INSTANCE_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(List.of(histogramRow("2026-05-26T06:00:00Z", "[{\"leMs\":50,\"count\":1.5}]")));

        assertThat(service.getEvidence(PROJECT_ID, APPLICATION_ID, INSTANCE_ID)
                .orElseThrow()
                .histogramDistribution()
                .status())
                .isEqualTo("insufficient");

        when(metricBucketRepository.findSummaryDurationBucketEvidenceRowsByApplicationInstanceId(
                INSTANCE_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(List.of(
                        histogramRow("2026-05-26T06:00:00Z", "[{\"leMs\":50,\"count\":1}]"),
                        histogramRow("2026-05-26T06:00:30Z", "[{\"leMs\":100,\"count\":1}]")));

        InstanceEvidenceReadModel.HistogramDistribution histogram = service.getEvidence(
                        PROJECT_ID,
                        APPLICATION_ID,
                        INSTANCE_ID)
                .orElseThrow()
                .histogramDistribution();

        assertThat(histogram.status()).isEqualTo("unavailable");
        assertThat(histogram.reason()).isEqualTo("histogram_boundary_mismatch");
        assertThat(histogram.buckets()).isEmpty();
    }

    @Test
    void resourceHintsUseOnlyLatestSelectedInstanceRuntimeSample() {
        when(metricBucketRepository.findLatestRuntimeRatioEvidenceRowByApplicationInstanceId(
                INSTANCE_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(Optional.of(new RuntimeRatioEvidenceRow(
                        APPLICATION_ID,
                        offset("2026-05-26T06:09:30Z"),
                        offset("2026-05-26T06:10:00Z"),
                        BigDecimal.valueOf(0.41d),
                        BigDecimal.valueOf(0.62d),
                        BigDecimal.valueOf(0.37d))));

        InstanceEvidenceReadModel.ResourceHints resourceHints = service.getEvidence(
                        PROJECT_ID,
                        APPLICATION_ID,
                        INSTANCE_ID)
                .orElseThrow()
                .resourceHints();

        assertThat(resourceHints.status()).isEqualTo("available");
        assertThat(resourceHints.bucketEndUtc()).isEqualTo(offset("2026-05-26T06:10:00Z"));
        assertThat(resourceHints.cpuUsageRatio()).isEqualByComparingTo("0.41");
        assertThat(resourceHints.heapUsedRatio()).isEqualByComparingTo("0.62");
        assertThat(resourceHints.datasourcePoolUsageRatio()).isEqualByComparingTo("0.37");
    }

    @Test
    void applicationTriageContributionUsesOnlyExistingApplicationRuleIds() {
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationInstanceIdAtOrBefore(
                INSTANCE_ID,
                EVALUATION_AT))
                .thenReturn(Optional.of(offset("2026-05-26T06:10:00Z")));
        when(metricBucketRepository.findWindowAggregateByApplicationInstanceId(
                INSTANCE_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(new WindowBucketAggregate(90L, 9L));
        when(triageSummaryService.summarize(any(TriageSummaryService.TriageSummaryInput.class)))
                .thenReturn(new TriageSummaryService.TriageSummary(
                        List.of(
                                triageCard("global_error_spike"),
                                triageCard("synthetic_new_rule")),
                        DegradedHysteresisInput.noConcern()));

        InstanceEvidenceReadModel.ApplicationTriageContribution contribution = service.getEvidence(
                        PROJECT_ID,
                        APPLICATION_ID,
                        INSTANCE_ID)
                .orElseThrow()
                .applicationTriageContribution();

        assertThat(contribution.status()).isEqualTo("observed");
        assertThat(contribution.contributed()).isTrue();
        assertThat(contribution.relatedRuleIds()).containsExactly("global_error_spike");
        assertThat(contribution.reason()).isEqualTo("selected_instance_has_evidence_for_application_triage");
    }

    @Test
    void applicationTriageContributionMarksKnownRuleInsufficientWhenSelectedEvidenceIsMissing() {
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationInstanceIdAtOrBefore(
                INSTANCE_ID,
                EVALUATION_AT))
                .thenReturn(Optional.of(offset("2026-05-26T06:10:00Z")));
        when(metricBucketRepository.findWindowAggregateByApplicationInstanceId(
                INSTANCE_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(new WindowBucketAggregate(90L, 0L));
        when(triageSummaryService.summarize(any(TriageSummaryService.TriageSummaryInput.class)))
                .thenReturn(new TriageSummaryService.TriageSummary(
                        List.of(triageCard("global_latency_spike")),
                        DegradedHysteresisInput.noConcern()));

        InstanceEvidenceReadModel.ApplicationTriageContribution contribution = service.getEvidence(
                        PROJECT_ID,
                        APPLICATION_ID,
                        INSTANCE_ID)
                .orElseThrow()
                .applicationTriageContribution();

        assertThat(contribution.status()).isEqualTo("insufficient");
        assertThat(contribution.contributed()).isFalse();
        assertThat(contribution.relatedRuleIds()).isEmpty();
        assertThat(contribution.reason()).isEqualTo("selected_instance_evidence_insufficient");
    }

    @Test
    void applicationTriageContributionKeepsAvailableWhenEvidenceIsSufficientButNotContributing() {
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationInstanceIdAtOrBefore(
                INSTANCE_ID,
                EVALUATION_AT))
                .thenReturn(Optional.of(offset("2026-05-26T06:10:00Z")));
        when(metricBucketRepository.findWindowAggregateByApplicationInstanceId(
                INSTANCE_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(new WindowBucketAggregate(90L, 0L));
        when(triageSummaryService.summarize(any(TriageSummaryService.TriageSummaryInput.class)))
                .thenReturn(new TriageSummaryService.TriageSummary(
                        List.of(triageCard("global_error_spike")),
                        DegradedHysteresisInput.noConcern()));

        InstanceEvidenceReadModel.ApplicationTriageContribution contribution = service.getEvidence(
                        PROJECT_ID,
                        APPLICATION_ID,
                        INSTANCE_ID)
                .orElseThrow()
                .applicationTriageContribution();

        assertThat(contribution.status()).isEqualTo("available");
        assertThat(contribution.contributed()).isFalse();
        assertThat(contribution.reason()).isEqualTo("selected_instance_not_linked_to_application_triage");
    }

    @Test
    void endpointEvidenceSuppressesWhenApplicationFreshnessIsNotCurrent() {
        when(metricBucketRepository.findEndpointEvidenceRowsByApplicationInstanceId(
                INSTANCE_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(List.of(endpointRow("2026-05-26T06:09:30Z",
                        "[" + endpoint("GET", "/orders", 10L, 1L, 8L, 10L) + "]")));

        InstanceEvidenceReadModel.EndpointEvidence endpointEvidence = service.getEvidence(
                        PROJECT_ID,
                        APPLICATION_ID,
                        INSTANCE_ID)
                .orElseThrow()
                .endpointEvidence();

        assertThat(endpointEvidence.status()).isEqualTo("suppressed");
        assertThat(endpointEvidence.reason()).isEqualTo("application_freshness_not_current");
        assertThat(endpointEvidence.items()).isEmpty();
    }

    @Test
    void endpointEvidenceSelectsPriorityPresenceAndSelectedInstanceSignalsThenAppliesDisplayOrder() {
        stubCurrentApplicationMetricData();
        when(metricBucketRepository.findEndpointEvidenceRowsByApplicationId(
                APPLICATION_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(List.of(endpointRow("2026-05-26T06:09:30Z", """
                        [
                          %s,
                          %s,
                          %s
                        ]
                        """.formatted(
                        endpoint("POST", "/orders", 100L, 20L, 80L, 100L),
                        endpoint("GET", "/payments", 100L, 12L, 90L, 100L),
                        endpoint("GET", "/cart", 80L, 8L, 60L, 80L)))));
        when(metricBucketRepository.findEndpointEvidenceRowsByApplicationId(
                APPLICATION_ID,
                BASELINE_START,
                CURRENT_START))
                .thenReturn(List.of(endpointRow("2026-05-26T05:54:30Z", """
                        [
                          %s,
                          %s
                        ]
                        """.formatted(
                        endpoint("POST", "/orders", 100L, 1L, 95L, 100L),
                        endpoint("GET", "/payments", 100L, 1L, 95L, 100L)))));
        when(metricBucketRepository.findEndpointEvidenceRowsByApplicationInstanceId(
                INSTANCE_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(List.of(endpointRow("2026-05-26T06:09:30Z", """
                        [
                          %s,
                          %s
                        ]
                        """.formatted(
                        endpoint("POST", "/orders", 50L, 10L, 40L, 50L),
                        endpoint("GET", "/cart", 80L, 8L, 60L, 80L)))));

        List<InstanceEvidenceReadModel.EndpointEvidenceItem> items = service.getEvidence(
                        PROJECT_ID,
                        APPLICATION_ID,
                        INSTANCE_ID)
                .orElseThrow()
                .endpointEvidence()
                .items();

        assertThat(items)
                .extracting(
                        InstanceEvidenceReadModel.EndpointEvidenceItem::localDisplayOrder,
                        InstanceEvidenceReadModel.EndpointEvidenceItem::endpointKey,
                        InstanceEvidenceReadModel.EndpointEvidenceItem::presenceOnSelectedInstance)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, "GET /cart", "observed"),
                        org.assertj.core.groups.Tuple.tuple(2, "POST /orders", "observed"),
                        org.assertj.core.groups.Tuple.tuple(3, "GET /payments", "not_observed"));
        assertThat(items.get(0)).satisfies(item -> {
            assertThat(item.relatedApplicationPriorityRank()).isNull();
            assertThat(item.relatedRuleIds()).isEmpty();
            assertThat(item.instanceRequestShare()).isEqualByComparingTo("1");
            assertThat(item.instanceErrorShare()).isEqualByComparingTo("1");
            assertThat(item.reason()).isEqualTo("selected_instance_endpoint_observed");
        });
        assertThat(items.get(1)).satisfies(item -> {
            assertThat(item.relatedApplicationPriorityRank()).isEqualTo(1);
            assertThat(item.relatedRuleIds()).containsExactly("endpoint_error_spike", "endpoint_latency_spike");
            assertThat(item.instanceRequestCount()).isEqualTo(50L);
            assertThat(item.instanceErrorCount()).isEqualTo(10L);
            assertThat(item.instanceErrorRate()).isEqualByComparingTo("0.2");
            assertThat(item.applicationEndpointRequestCount()).isEqualTo(100L);
            assertThat(item.applicationEndpointErrorCount()).isEqualTo(20L);
            assertThat(item.applicationEndpointErrorRate()).isEqualByComparingTo("0.2");
            assertThat(item.instanceRequestShare()).isEqualByComparingTo("0.5");
            assertThat(item.instanceErrorShare()).isEqualByComparingTo("0.5");
            assertThat(item.durationBuckets())
                    .extracting(
                            InstanceEvidenceReadModel.HistogramBucket::leMs,
                            InstanceEvidenceReadModel.HistogramBucket::count)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(500L, 40L),
                            org.assertj.core.groups.Tuple.tuple(1000L, 50L));
            assertThat(item.reason()).isEqualTo("application_priority_endpoint_observed_on_selected_instance");
        });
        assertThat(items.get(2)).satisfies(item -> {
            assertThat(item.relatedApplicationPriorityRank()).isEqualTo(2);
            assertThat(item.relatedRuleIds()).containsExactly("endpoint_error_spike");
            assertThat(item.instanceRequestCount()).isZero();
            assertThat(item.instanceErrorCount()).isZero();
            assertThat(item.instanceErrorRate()).isNull();
            assertThat(item.instanceRequestShare()).isEqualByComparingTo("0");
            assertThat(item.instanceErrorShare()).isEqualByComparingTo("0");
            assertThat(item.reason()).isEqualTo("application_priority_endpoint_not_seen_on_selected_instance");
        });
    }

    @Test
    void endpointEvidenceUsesTriageAffectedEndpointBeforeSelectedRequestCountWhenPriorityIsEmpty() {
        stubCurrentApplicationMetricData();
        when(triageSummaryService.summarize(any(TriageSummaryService.TriageSummaryInput.class)))
                .thenReturn(new TriageSummaryService.TriageSummary(
                        List.of(triageCard("global_error_spike", "PATCH /triage")),
                        DegradedHysteresisInput.noConcern()));
        when(metricBucketRepository.findEndpointEvidenceRowsByApplicationId(
                APPLICATION_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(List.of(endpointRow("2026-05-26T06:09:30Z",
                        "[" + endpoint("PATCH", "/triage", 40L, 2L, 38L, 40L) + "]")));
        when(metricBucketRepository.findEndpointEvidenceRowsByApplicationInstanceId(
                INSTANCE_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(List.of(endpointRow("2026-05-26T06:09:30Z",
                        "[" + endpoint("GET", "/selected", 120L, 12L, 100L, 120L) + "]")));

        List<InstanceEvidenceReadModel.EndpointEvidenceItem> items = service.getEvidence(
                        PROJECT_ID,
                        APPLICATION_ID,
                        INSTANCE_ID)
                .orElseThrow()
                .endpointEvidence()
                .items();

        assertThat(items)
                .extracting(InstanceEvidenceReadModel.EndpointEvidenceItem::endpointKey)
                .containsExactly("GET /selected", "PATCH /triage");
        assertThat(items.get(1).presenceOnSelectedInstance()).isEqualTo("not_observed");
        assertThat(items.get(1).relatedApplicationPriorityRank()).isNull();
    }

    @Test
    void endpointEvidenceMarksSelectedHistogramBoundaryMismatchAsUnavailableItem() {
        stubCurrentApplicationMetricData();
        when(metricBucketRepository.findEndpointEvidenceRowsByApplicationId(
                APPLICATION_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(List.of(endpointRow("2026-05-26T06:09:30Z",
                        "[" + endpoint("GET", "/orders", 60L, 6L, 50L, 60L) + "]")));
        when(metricBucketRepository.findEndpointEvidenceRowsByApplicationInstanceId(
                INSTANCE_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(List.of(
                        endpointRow("2026-05-26T06:09:00Z",
                                "[" + endpoint("GET", "/orders", 30L, 3L, 20L, 30L) + "]"),
                        endpointRow("2026-05-26T06:09:30Z",
                                "[" + endpointWithBuckets("GET", "/orders", 30L, 3L, 250L, 20L, 1000L, 30L)
                                        + "]")));

        InstanceEvidenceReadModel.EndpointEvidenceItem item = service.getEvidence(
                        PROJECT_ID,
                        APPLICATION_ID,
                        INSTANCE_ID)
                .orElseThrow()
                .endpointEvidence()
                .items()
                .get(0);

        assertThat(item.endpointKey()).isEqualTo("GET /orders");
        assertThat(item.presenceOnSelectedInstance()).isEqualTo("insufficient");
        assertThat(item.status()).isEqualTo("unavailable");
        assertThat(item.reason()).isEqualTo("histogram_boundary_mismatch");
        assertThat(item.durationBuckets()).isEmpty();
    }

    @Test
    void endpointEvidenceMarksSelectedParsingOrRouteFailuresAsInsufficientWithoutRawLeakage() {
        stubCurrentApplicationMetricData();
        when(metricBucketRepository.findEndpointEvidenceRowsByApplicationId(
                APPLICATION_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(List.of(endpointRow("2026-05-26T06:09:30Z",
                        "[" + endpoint("GET", "/orders", 60L, 6L, 50L, 60L) + "]")));
        when(metricBucketRepository.findEndpointEvidenceRowsByApplicationInstanceId(
                INSTANCE_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(List.of(endpointRow("2026-05-26T06:09:30Z", """
                        [
                          %s,
                          {"method": "GET", "route": "/orders/12345?token=secret", "requestCount": 1, "errorCount": 0}
                        ]
                        """.formatted(endpoint("GET", "/orders", 20L, 2L, 18L, 20L)))));

        InstanceEvidenceReadModel.EndpointEvidence endpointEvidence = service.getEvidence(
                        PROJECT_ID,
                        APPLICATION_ID,
                        INSTANCE_ID)
                .orElseThrow()
                .endpointEvidence();

        assertThat(endpointEvidence.status()).isEqualTo("insufficient");
        assertThat(endpointEvidence.reason()).isEqualTo("endpoint_evidence_insufficient");
        assertThat(endpointEvidence.items()).isEmpty();
        assertThat(endpointEvidence.toString()).doesNotContain("token", "12345");
    }

    @Test
    void endpointEvidenceTreatsAnyMalformedSelectedRowAsWholeSubsetInsufficient() {
        stubCurrentApplicationMetricData();
        when(metricBucketRepository.findEndpointEvidenceRowsByApplicationInstanceId(
                INSTANCE_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(List.of(
                        endpointRow("2026-05-26T06:09:00Z",
                                "[" + endpoint("GET", "/orders", 20L, 2L, 18L, 20L) + "]"),
                        endpointRow("2026-05-26T06:09:30Z", "not-json")));

        InstanceEvidenceReadModel.EndpointEvidence endpointEvidence = service.getEvidence(
                        PROJECT_ID,
                        APPLICATION_ID,
                        INSTANCE_ID)
                .orElseThrow()
                .endpointEvidence();

        assertThat(endpointEvidence.status()).isEqualTo("insufficient");
        assertThat(endpointEvidence.reason()).isEqualTo("endpoint_evidence_insufficient");
        assertThat(endpointEvidence.items()).isEmpty();
    }

    @Test
    void endpointEvidenceExcludesUnknownRoutesFromSelectedFallbackAndTriageHints() {
        stubCurrentApplicationMetricData();
        when(triageSummaryService.summarize(any(TriageSummaryService.TriageSummaryInput.class)))
                .thenReturn(new TriageSummaryService.TriageSummary(
                        List.of(triageCard("global_error_spike", "GET UNKNOWN")),
                        DegradedHysteresisInput.noConcern()));
        when(metricBucketRepository.findEndpointEvidenceRowsByApplicationInstanceId(
                INSTANCE_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(List.of(endpointRow("2026-05-26T06:09:30Z", """
                        [
                          %s,
                          %s
                        ]
                        """.formatted(
                        endpoint("GET", "UNKNOWN", 200L, 20L, 180L, 200L),
                        endpoint("GET", "/selected", 120L, 12L, 100L, 120L)))));

        List<InstanceEvidenceReadModel.EndpointEvidenceItem> items = service.getEvidence(
                        PROJECT_ID,
                        APPLICATION_ID,
                        INSTANCE_ID)
                .orElseThrow()
                .endpointEvidence()
                .items();

        assertThat(items)
                .extracting(InstanceEvidenceReadModel.EndpointEvidenceItem::endpointKey)
                .containsExactly("GET /selected");
        assertThat(items.get(0).reason()).isEqualTo("selected_instance_endpoint_observed");
    }

    private void stubCatalogPathConsistency() {
        when(applicationRepository.findByIdAndProjectId(APPLICATION_ID, PROJECT_ID))
                .thenReturn(Optional.of(application()));
        when(applicationInstanceRepository.findByIdAndApplicationId(INSTANCE_ID, APPLICATION_ID))
                .thenReturn(Optional.of(instance()));
    }

    private void stubEmptyEvidence() {
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationInstanceIdAtOrBefore(
                INSTANCE_ID,
                EVALUATION_AT))
                .thenReturn(Optional.empty());
        when(metricBucketRepository.findWindowAggregateByApplicationInstanceId(
                INSTANCE_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(WindowBucketAggregate.zero());
        when(metricBucketRepository.findLocalPercentileEvidenceRowsByApplicationInstanceId(
                INSTANCE_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(List.of());
        when(metricBucketRepository.findSummaryDurationBucketEvidenceRowsByApplicationInstanceId(
                INSTANCE_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(List.of());
        when(metricBucketRepository.findLatestRuntimeRatioEvidenceRowByApplicationInstanceId(
                INSTANCE_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(Optional.empty());
        when(heartbeatRepository.findByIdentity(PROJECT_ID, "orders-api", "prod", "pod-a"))
                .thenReturn(Optional.empty());
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationIdAtOrBefore(
                APPLICATION_ID,
                EVALUATION_AT))
                .thenReturn(Optional.empty());
        when(metricBucketRepository.findWindowAggregateByApplicationId(
                APPLICATION_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(WindowBucketAggregate.zero());
        when(metricBucketRepository.findWindowAggregateByApplicationId(
                APPLICATION_ID,
                BASELINE_START,
                CURRENT_START))
                .thenReturn(WindowBucketAggregate.zero());
        when(metricBucketRepository.findSummaryDurationBucketEvidenceRowsByApplicationId(
                APPLICATION_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(List.of());
        when(metricBucketRepository.findSummaryDurationBucketEvidenceRowsByApplicationId(
                APPLICATION_ID,
                BASELINE_START,
                CURRENT_START))
                .thenReturn(List.of());
        when(metricBucketRepository.findLatestRuntimeRatioEvidenceRowByApplicationId(
                APPLICATION_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(Optional.empty());
        when(metricBucketRepository.findRecentFiveBucketEvidenceRowsByApplicationIdAtOrBefore(
                APPLICATION_ID,
                EVALUATION_AT))
                .thenReturn(List.of());
        when(metricBucketRepository.findEndpointEvidenceRowsByApplicationId(
                APPLICATION_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(List.of());
        when(metricBucketRepository.findEndpointEvidenceRowsByApplicationId(
                APPLICATION_ID,
                BASELINE_START,
                CURRENT_START))
                .thenReturn(List.of());
        when(metricBucketRepository.findEndpointEvidenceRowsByApplicationInstanceId(
                INSTANCE_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(List.of());
        when(triageSummaryService.summarize(any(TriageSummaryService.TriageSummaryInput.class)))
                .thenReturn(TriageSummaryService.TriageSummary.empty());
    }

    private void stubCurrentApplicationMetricData() {
        when(metricBucketRepository.findLatestBucketEndUtcByApplicationIdAtOrBefore(
                APPLICATION_ID,
                EVALUATION_AT))
                .thenReturn(Optional.of(offset("2026-05-26T06:10:00Z")));
        when(metricBucketRepository.findWindowAggregateByApplicationId(
                APPLICATION_ID,
                CURRENT_START,
                EVALUATION_AT))
                .thenReturn(new WindowBucketAggregate(180L, 20L));
        when(metricBucketRepository.findWindowAggregateByApplicationId(
                APPLICATION_ID,
                BASELINE_START,
                CURRENT_START))
                .thenReturn(new WindowBucketAggregate(180L, 2L));
    }

    private static ApplicationEntity application() {
        return new ApplicationEntity(
                APPLICATION_ID,
                PROJECT_ID,
                "orders-api",
                "prod",
                "active",
                offset("2026-05-26T05:00:00Z"),
                offset("2026-05-26T06:10:00Z"),
                offset("2026-05-26T05:00:00Z"),
                offset("2026-05-26T06:10:00Z"));
    }

    private static ApplicationInstanceEntity instance() {
        return new ApplicationInstanceEntity(
                INSTANCE_ID,
                APPLICATION_ID,
                "pod-a",
                offset("2026-05-26T05:00:05Z"),
                offset("2026-05-26T06:10:05Z"),
                offset("2026-05-26T05:00:05Z"),
                offset("2026-05-26T06:10:05Z"));
    }

    private static StarterHeartbeatTelemetryRecord heartbeat(String lastReceivedAtUtc, String status) {
        OffsetDateTime receivedAt = offset(lastReceivedAtUtc);
        return new StarterHeartbeatTelemetryRecord(
                UUID.fromString("00000000-0000-0000-0000-000000005291"),
                PROJECT_ID,
                "orders-api",
                "prod",
                "pod-a",
                "0.1.0",
                receivedAt.minusSeconds(1),
                receivedAt,
                10L,
                30,
                "valid",
                status,
                receivedAt,
                receivedAt);
    }

    private static LocalPercentileEvidenceRow localPercentileRow(
            String bucketStartUtc,
            long requestCount,
            long p95Ms,
            long p99Ms) {
        return localPercentileRow(
                bucketStartUtc,
                requestCount,
                p95Ms,
                p99Ms,
                "instance_bucket",
                "starter_local",
                false);
    }

    private static LocalPercentileEvidenceRow localPercentileRow(
            String bucketStartUtc,
            long requestCount,
            long p95Ms,
            long p99Ms,
            String scope,
            String source,
            boolean mergeable) {
        OffsetDateTime start = OffsetDateTime.parse(bucketStartUtc);
        OffsetDateTime end = start.plusSeconds(30);
        String json = """
                {
                  "scope": "%s",
                  "source": "%s",
                  "bucketStartUtc": "%s",
                  "bucketEndUtc": "%s",
                  "requestCount": %d,
                  "p95Ms": %d,
                  "p99Ms": %d,
                  "mergeable": %s
                }
                """.formatted(scope, source, start, end, requestCount, p95Ms, p99Ms, mergeable);
        return new LocalPercentileEvidenceRow(
                APPLICATION_ID,
                INSTANCE_ID,
                "pod-a",
                start,
                end,
                json);
    }

    private static LocalPercentileEvidenceRow rawLocalPercentileRow(
            String bucketStartUtc,
            String json) {
        OffsetDateTime start = OffsetDateTime.parse(bucketStartUtc);
        return new LocalPercentileEvidenceRow(
                APPLICATION_ID,
                INSTANCE_ID,
                "pod-a",
                start,
                start.plusSeconds(30),
                json);
    }

    private static HistogramBucketEvidenceRow histogramRow(String bucketStartUtc, String json) {
        OffsetDateTime start = offset(bucketStartUtc);
        return new HistogramBucketEvidenceRow(
                APPLICATION_ID,
                start,
                start.plusSeconds(30),
                json);
    }

    private static ApplicationDashboardReadModel.TriageCard triageCard(String ruleId) {
        return triageCard(ruleId, null);
    }

    private static ApplicationDashboardReadModel.TriageCard triageCard(String ruleId, String affectedEndpoint) {
        return new ApplicationDashboardReadModel.TriageCard(
                ruleId,
                ApplicationDashboardReadModel.TriageSeverity.WARNING,
                "title",
                "summary",
                "recommendation",
                0.8d,
                80,
                affectedEndpoint,
                new ApplicationDashboardReadModel.TriageEvidence(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "current",
                        null));
    }

    private static EndpointEvidenceRow endpointRow(String bucketStartUtc, String json) {
        OffsetDateTime start = offset(bucketStartUtc);
        return new EndpointEvidenceRow(
                APPLICATION_ID,
                start,
                start.plusSeconds(30),
                json);
    }

    private static String endpoint(
            String method,
            String route,
            long requestCount,
            long errorCount,
            long countAt500,
            long totalCount) {
        return endpointWithBuckets(method, route, requestCount, errorCount, 500L, countAt500, 1000L, totalCount);
    }

    private static String endpointWithBuckets(
            String method,
            String route,
            long requestCount,
            long errorCount,
            long firstLeMs,
            long firstCount,
            long secondLeMs,
            long secondCount) {
        return """
                {
                  "method": "%s",
                  "route": "%s",
                  "requestCount": %d,
                  "errorCount": %d,
                  "durationBuckets": [
                    {"leMs": %d, "count": %d},
                    {"leMs": %d, "count": %d}
                  ]
                }
                """.formatted(method, route, requestCount, errorCount, firstLeMs, firstCount, secondLeMs, secondCount);
    }

    private static OffsetDateTime offset(String instant) {
        return offset(Instant.parse(instant));
    }

    private static OffsetDateTime offset(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
