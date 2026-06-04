package com.observation.portal.domain.dashboard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.portal.common.time.AcceptedBucketFreshnessStatus;
import com.observation.portal.domain.bucket.model.RecentBucketEvidenceRow;
import com.observation.portal.domain.bucket.model.RuntimeRatioEvidenceRow;
import com.observation.portal.domain.bucket.model.WindowBucketAggregate;
import com.observation.portal.domain.dashboard.model.ApplicationDashboardReadModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TriageSummaryServiceTest {

    private static final UUID APPLICATION_ID = UUID.fromString("00000000-0000-0000-0000-000000005411");
    private final TriageSummaryService service = new TriageSummaryService(new ObjectMapper());

    @Test
    void exposesGlobalErrorSpikeOnlyWhenAllSeedGuardsPass() {
        TriageSummaryService.TriageSummary summary = service.summarize(input(
                new WindowBucketAggregate(100L, 8L),
                new WindowBucketAggregate(100L, 2L),
                histogramMissing(),
                List.of()));

        assertThat(summary.triageCards())
                .extracting(ApplicationDashboardReadModel.TriageCard::ruleId)
                .containsExactly("global_error_spike");
        ApplicationDashboardReadModel.TriageCard card = summary.triageCards().get(0);
        assertThat(card.confidence()).isGreaterThanOrEqualTo(0.65d);
        assertThat(card.evidence().currentErrorRate()).isEqualByComparingTo("0.08");
        assertThat(card.evidence().baselineErrorRate()).isEqualByComparingTo("0.02");
        assertThat(card.evidence().errorRateDelta()).isEqualByComparingTo("0.06");
    }

    @Test
    void suppressesGlobalErrorSpikeWhenMinimumOrAbsoluteGuardFails() {
        assertThat(service.summarize(input(
                new WindowBucketAggregate(29L, 4L),
                new WindowBucketAggregate(100L, 1L),
                histogramMissing(),
                List.of())).triageCards()).isEmpty();
        assertThat(service.summarize(input(
                new WindowBucketAggregate(100L, 4L),
                new WindowBucketAggregate(100L, 1L),
                histogramMissing(),
                List.of())).triageCards()).isEmpty();
    }

    @Test
    void exposesSustainedHighErrorRateWithoutBaselineSpike() {
        TriageSummaryService.TriageSummary summary = service.summarize(input(
                new WindowBucketAggregate(44L, 4L),
                new WindowBucketAggregate(66L, 6L),
                histogramMissing(),
                List.of()));

        assertThat(summary.triageCards())
                .extracting(ApplicationDashboardReadModel.TriageCard::ruleId)
                .containsExactly("sustained_error_rate_high");
        ApplicationDashboardReadModel.TriageCard card = summary.triageCards().get(0);
        assertThat(card.title()).contains("오류율 높음");
        assertThat(card.summary()).doesNotContain("증가", "spike");
        assertThat(card.evidence().currentErrorRate()).isEqualByComparingTo("0.090909");
        assertThat(card.evidence().baselineErrorRate()).isEqualByComparingTo("0.090909");
        assertThat(card.evidence().errorRateDelta()).isEqualByComparingTo("0");
    }

    @Test
    void latencySpikeUsesHistogramSlowShareWithoutPercentileScalar() {
        TriageSummaryService.TriageSummary summary = service.summarize(input(
                new WindowBucketAggregate(100L, 0L),
                new WindowBucketAggregate(100L, 0L),
                histogramAvailable(60L, 100L, 90L, 100L),
                List.of()));

        assertThat(summary.triageCards())
                .extracting(ApplicationDashboardReadModel.TriageCard::ruleId)
                .containsExactly("global_latency_spike");
        ApplicationDashboardReadModel.TriageEvidence evidence = summary.triageCards().get(0).evidence();
        assertThat(evidence.currentSlowShare()).isEqualByComparingTo("0.4");
        assertThat(evidence.baselineSlowShare()).isEqualByComparingTo("0.1");
        assertThat(ApplicationDashboardReadModel.TriageEvidence.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("p95Ms", "p99Ms", "endpointP95Ms", "histogramPercentile");
    }

    @Test
    void suppressesLatencySpikeWhenBaselineHistogramMissingOrBoundaryDiffers() {
        assertThat(service.summarize(input(
                new WindowBucketAggregate(100L, 0L),
                new WindowBucketAggregate(100L, 0L),
                new ApplicationDashboardReadModel.HistogramDistribution(
                        "histogram_bucket_distribution",
                        "application",
                        "bucket_distribution_evidence",
                        "sum_cumulative_counts_only_when_boundary_set_matches",
                        histogramWindow(60L, 100L),
                        ApplicationDashboardReadModel.HistogramWindow.missing(
                                "no_histogram_buckets_in_baseline_window")),
                List.of())).triageCards()).isEmpty();

        assertThat(service.summarize(input(
                new WindowBucketAggregate(100L, 0L),
                new WindowBucketAggregate(100L, 0L),
                new ApplicationDashboardReadModel.HistogramDistribution(
                        "histogram_bucket_distribution",
                        "application",
                        "bucket_distribution_evidence",
                        "sum_cumulative_counts_only_when_boundary_set_matches",
                        histogramWindow(60L, 100L),
                        new ApplicationDashboardReadModel.HistogramWindow(
                                "available",
                                null,
                                100L,
                                List.of(
                                        new ApplicationDashboardReadModel.HistogramBucket(250L, 20L),
                                        new ApplicationDashboardReadModel.HistogramBucket(1000L, 100L)))),
                List.of())).triageCards()).isEmpty();
    }

    @Test
    void saturationHintRequiresLatencyOrErrorCompanionSignal() {
        RuntimeRatioEvidenceRow runtime = runtime("2026-05-25T10:32:00Z", 0.20d, 0.20d, 0.86d);

        assertThat(service.summarize(new TriageSummaryService.TriageSummaryInput(
                new WindowBucketAggregate(100L, 0L),
                new WindowBucketAggregate(100L, 0L),
                histogramAvailable(95L, 100L, 95L, 100L),
                ApplicationDashboardReadModel.SourceScopedPercentiles.empty(),
                List.of(),
                Optional.of(runtime),
                AcceptedBucketFreshnessStatus.CURRENT)).triageCards()).isEmpty();

        TriageSummaryService.TriageSummary withLatency = service.summarize(new TriageSummaryService.TriageSummaryInput(
                new WindowBucketAggregate(100L, 0L),
                new WindowBucketAggregate(100L, 0L),
                histogramAvailable(60L, 100L, 90L, 100L),
                ApplicationDashboardReadModel.SourceScopedPercentiles.empty(),
                List.of(),
                Optional.of(runtime),
                AcceptedBucketFreshnessStatus.CURRENT));

        assertThat(withLatency.triageCards())
                .extracting(ApplicationDashboardReadModel.TriageCard::ruleId)
                .contains("db_pool_high_with_latency");
        assertThat(withLatency.triageCards())
                .filteredOn(card -> card.ruleId().equals("db_pool_high_with_latency"))
                .singleElement()
                .satisfies(card -> assertThat(card.recommendation()).contains("가능성을 먼저 확인"));

        TriageSummaryService.TriageSummary withCpuLatency = service.summarize(new TriageSummaryService.TriageSummaryInput(
                new WindowBucketAggregate(100L, 0L),
                new WindowBucketAggregate(100L, 0L),
                histogramAvailable(60L, 100L, 90L, 100L),
                ApplicationDashboardReadModel.SourceScopedPercentiles.empty(),
                List.of(),
                Optional.of(runtime("2026-05-25T10:32:00Z", 0.86d, 0.20d, 0.20d)),
                AcceptedBucketFreshnessStatus.CURRENT));
        assertThat(withCpuLatency.triageCards())
                .extracting(ApplicationDashboardReadModel.TriageCard::ruleId)
                .contains("cpu_high_with_latency");

        TriageSummaryService.TriageSummary withHeapError = service.summarize(new TriageSummaryService.TriageSummaryInput(
                new WindowBucketAggregate(100L, 8L),
                new WindowBucketAggregate(100L, 2L),
                histogramMissing(),
                ApplicationDashboardReadModel.SourceScopedPercentiles.empty(),
                List.of(),
                Optional.of(runtime("2026-05-25T10:32:00Z", 0.20d, 0.91d, 0.20d)),
                AcceptedBucketFreshnessStatus.CURRENT));
        assertThat(withHeapError.triageCards())
                .extracting(ApplicationDashboardReadModel.TriageCard::ruleId)
                .contains("heap_high_hint");
    }

    @Test
    void confidenceAndRecentBadBucketCountAreSeparatedFromCardExposure() {
        TriageSummaryService.TriageSummary cardButNotDegraded = service.summarize(input(
                new WindowBucketAggregate(100L, 8L),
                new WindowBucketAggregate(100L, 2L),
                histogramMissing(),
                List.of(
                        recentBucket("2026-05-25T10:31:30Z", 10L, 1L),
                        recentBucket("2026-05-25T10:31:00Z", 10L, 1L),
                        recentBucket("2026-05-25T10:30:30Z", 10L, 1L))));

        assertThat(cardButNotDegraded.triageCards()).isNotEmpty();
        assertThat(cardButNotDegraded.triageCards().get(0).confidence()).isLessThan(0.75d);
        assertThat(cardButNotDegraded.degradedInput().canEnterDegraded()).isFalse();

        TriageSummaryService.TriageSummary highConfidenceOnlyTwoBad = service.summarize(input(
                new WindowBucketAggregate(100L, 9L),
                new WindowBucketAggregate(100L, 2L),
                histogramMissing(),
                List.of(
                        recentBucket("2026-05-25T10:31:30Z", 10L, 1L),
                        recentBucket("2026-05-25T10:31:00Z", 10L, 1L))));

        assertThat(highConfidenceOnlyTwoBad.triageCards().get(0).confidence()).isGreaterThanOrEqualTo(0.75d);
        assertThat(highConfidenceOnlyTwoBad.degradedInput().canEnterDegraded()).isFalse();

        TriageSummaryService.TriageSummary degraded = service.summarize(input(
                new WindowBucketAggregate(100L, 9L),
                new WindowBucketAggregate(100L, 2L),
                histogramMissing(),
                List.of(
                        recentBucket("2026-05-25T10:31:30Z", 10L, 1L),
                        recentBucket("2026-05-25T10:31:00Z", 10L, 1L),
                        recentBucket("2026-05-25T10:30:30Z", 10L, 1L))));

        assertThat(degraded.degradedInput().canEnterDegraded()).isTrue();
    }

    @Test
    void sameApplicationBucketBoundaryRowsCountAsOneRecentErrorBucket() {
        TriageSummaryService.TriageSummary summary = service.summarize(input(
                new WindowBucketAggregate(100L, 9L),
                new WindowBucketAggregate(100L, 2L),
                histogramMissing(),
                List.of(
                        recentBucket("2026-05-25T10:31:30Z", 10L, 1L),
                        recentBucket("2026-05-25T10:31:30Z", 10L, 1L),
                        recentBucket("2026-05-25T10:31:30Z", 10L, 1L))));

        assertThat(summary.triageCards())
                .extracting(ApplicationDashboardReadModel.TriageCard::ruleId)
                .contains("global_error_spike");
        assertThat(summary.triageCards().get(0).confidence()).isGreaterThanOrEqualTo(0.75d);
        assertThat(summary.degradedInput().badBucketsInRecentFive()).isEqualTo(1);
        assertThat(summary.degradedInput().canEnterDegraded()).isFalse();
    }

    @Test
    void sameApplicationBucketBoundaryRowsCountAsOneRecentLatencyBucket() {
        TriageSummaryService.TriageSummary summary = service.summarize(input(
                new WindowBucketAggregate(100L, 0L),
                new WindowBucketAggregate(100L, 0L),
                histogramAvailable(60L, 100L, 90L, 100L),
                List.of(
                        recentBucket("2026-05-25T10:31:30Z", 10L, 0L),
                        recentBucket("2026-05-25T10:31:30Z", 10L, 0L),
                        recentBucket("2026-05-25T10:31:30Z", 10L, 0L))));

        assertThat(summary.triageCards())
                .extracting(ApplicationDashboardReadModel.TriageCard::ruleId)
                .contains("global_latency_spike");
        assertThat(summary.degradedInput().badBucketsInRecentFive()).isEqualTo(1);
        assertThat(summary.degradedInput().canEnterDegraded()).isFalse();
    }

    @Test
    void mismatchedBoundarySetWithinSameRecentLatencyBucketIsNotBadEvidence() {
        TriageSummaryService.TriageSummary summary = service.summarize(input(
                new WindowBucketAggregate(100L, 0L),
                new WindowBucketAggregate(100L, 0L),
                histogramAvailable(60L, 100L, 90L, 100L),
                List.of(
                        recentBucketWithDurationBuckets(
                                "2026-05-25T10:31:30Z",
                                10L,
                                """
                                [
                                  {"leMs": 500, "count": 7},
                                  {"leMs": 1000, "count": 10}
                                ]
                                """),
                        recentBucketWithDurationBuckets(
                                "2026-05-25T10:31:30Z",
                                10L,
                                """
                                [
                                  {"leMs": 250, "count": 7},
                                  {"leMs": 1000, "count": 10}
                                ]
                                """))));

        assertThat(summary.triageCards())
                .extracting(ApplicationDashboardReadModel.TriageCard::ruleId)
                .contains("global_latency_spike");
        assertThat(summary.degradedInput().badBucketsInRecentFive()).isZero();
        assertThat(summary.degradedInput().canEnterDegraded()).isFalse();
    }

    private static TriageSummaryService.TriageSummaryInput input(
            WindowBucketAggregate current,
            WindowBucketAggregate baseline,
            ApplicationDashboardReadModel.HistogramDistribution histogramDistribution,
            List<RecentBucketEvidenceRow> recentBuckets) {
        return new TriageSummaryService.TriageSummaryInput(
                current,
                baseline,
                histogramDistribution,
                ApplicationDashboardReadModel.SourceScopedPercentiles.empty(),
                recentBuckets,
                Optional.empty(),
                AcceptedBucketFreshnessStatus.CURRENT);
    }

    private static ApplicationDashboardReadModel.HistogramDistribution histogramMissing() {
        return new ApplicationDashboardReadModel.HistogramDistribution(
                "histogram_bucket_distribution",
                "application",
                "bucket_distribution_evidence",
                "sum_cumulative_counts_only_when_boundary_set_matches",
                ApplicationDashboardReadModel.HistogramWindow.missing("no_histogram_buckets_in_current_window"),
                ApplicationDashboardReadModel.HistogramWindow.missing("no_histogram_buckets_in_baseline_window"));
    }

    private static ApplicationDashboardReadModel.HistogramDistribution histogramAvailable(
            long currentAtLe500,
            long currentTotal,
            long baselineAtLe500,
            long baselineTotal) {
        return new ApplicationDashboardReadModel.HistogramDistribution(
                "histogram_bucket_distribution",
                "application",
                "bucket_distribution_evidence",
                "sum_cumulative_counts_only_when_boundary_set_matches",
                histogramWindow(currentAtLe500, currentTotal),
                histogramWindow(baselineAtLe500, baselineTotal));
    }

    private static ApplicationDashboardReadModel.HistogramWindow histogramWindow(long countAtLe500, long total) {
        return new ApplicationDashboardReadModel.HistogramWindow(
                "available",
                null,
                total,
                List.of(
                        new ApplicationDashboardReadModel.HistogramBucket(500L, countAtLe500),
                        new ApplicationDashboardReadModel.HistogramBucket(1000L, total)));
    }

    private static RecentBucketEvidenceRow recentBucket(String bucketStartUtc, long requestCount, long errorCount) {
        OffsetDateTime start = OffsetDateTime.parse(bucketStartUtc);
        return new RecentBucketEvidenceRow(
                APPLICATION_ID,
                start,
                start.plusSeconds(30),
                requestCount,
                errorCount,
                """
                [
                  {"leMs": 500, "count": %d},
                  {"leMs": 1000, "count": %d}
                ]
                """.formatted(Math.max(0L, requestCount - 3L), requestCount));
    }

    private static RecentBucketEvidenceRow recentBucketWithDurationBuckets(
            String bucketStartUtc,
            long requestCount,
            String durationBucketsJson) {
        OffsetDateTime start = OffsetDateTime.parse(bucketStartUtc);
        return new RecentBucketEvidenceRow(
                APPLICATION_ID,
                start,
                start.plusSeconds(30),
                requestCount,
                0L,
                durationBucketsJson);
    }

    private static RuntimeRatioEvidenceRow runtime(
            String bucketStartUtc,
            double cpu,
            double heap,
            double datasource) {
        OffsetDateTime start = OffsetDateTime.parse(bucketStartUtc);
        return new RuntimeRatioEvidenceRow(
                APPLICATION_ID,
                start,
                start.plusSeconds(30),
                BigDecimal.valueOf(cpu),
                BigDecimal.valueOf(heap),
                BigDecimal.valueOf(datasource));
    }
}
