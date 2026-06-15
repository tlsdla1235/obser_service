package com.observation.portal.domain.dashboard.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EndpointPriorityReadModelShapeTest {

    @Test
    void endpointPriorityFieldIsTypedListOnDashboardReadModel() {
        RecordComponent endpointPriority = Arrays.stream(ApplicationDashboardReadModel.class.getRecordComponents())
                .filter(component -> component.getName().equals("endpointPriority"))
                .findFirst()
                .orElseThrow();

        assertThat(endpointPriority.getGenericType().getTypeName())
                .isEqualTo("java.util.List<com.observation.portal.domain.dashboard.model."
                        + "ApplicationDashboardReadModel$EndpointPriorityItem>");
    }

    @Test
    void validatesEndpointPriorityItemShapeAndKeepsBoundedEvidence() {
        ApplicationDashboardReadModel.EndpointPriorityItem item =
                new ApplicationDashboardReadModel.EndpointPriorityItem(
                        1,
                        "POST",
                        "/orders",
                        "POST /orders",
                        ApplicationDashboardReadModel.EndpointPriorityReason.ERROR_AND_LATENCY,
                        List.of("endpoint_error_rate_high", "endpoint_slow_share_high"),
                        0.84d,
                        84,
                        new ApplicationDashboardReadModel.EndpointPriorityFreshness(
                                "current",
                                OffsetDateTime.parse("2026-05-26T01:10:30Z"),
                                "recent_30_minutes",
                                null),
                        new ApplicationDashboardReadModel.EndpointPriorityEvidence(
                                120L,
                                12L,
                                BigDecimal.valueOf(0.10d),
                                null,
                                null,
                                null,
                                null,
                                List.of(
                                        new ApplicationDashboardReadModel.HistogramBucket(500L, 70L),
                                        new ApplicationDashboardReadModel.HistogramBucket(1000L, 120L)),
                                null,
                                BigDecimal.valueOf(0.416667d),
                                null,
                                null,
                                "accepted_bucket",
                                ApplicationDashboardReadModel.EndpointEvidenceStatus.AVAILABLE,
                                ApplicationDashboardReadModel.EndpointEvidenceStatus.AVAILABLE),
                        "최근 30분 동안 이 endpoint의 오류와 느린 응답 근거를 함께 확인하세요.");

        assertThat(item.rank()).isEqualTo(1);
        assertThat(item.endpointKey()).isEqualTo("POST /orders");
        assertThat(item.reason()).isEqualTo(ApplicationDashboardReadModel.EndpointPriorityReason.ERROR_AND_LATENCY);
        assertThat(item.freshness().status()).isEqualTo("current");
        assertThat(item.evidence().bucketDistributionSource()).isEqualTo("accepted_bucket");
        assertThat(item.evidence().durationBuckets()).hasSize(2);
        assertThat(item.evidence().baselineDurationBuckets()).isNull();
    }

    @Test
    void readSemanticsKeepsAcceptedBucketAsCanonicalDistributionSourceAndDisallowsHistogramPercentiles() {
        ApplicationDashboardReadModel.ReadSemantics live = ApplicationDashboardReadModel.ReadSemantics.live();
        ApplicationDashboardReadModel.ReadSemantics snapshot = ApplicationDashboardReadModel.ReadSemantics.snapshot();

        assertThat(live.source()).isEqualTo("accepted_metric_buckets");
        assertThat(snapshot.source()).isEqualTo("dashboard_snapshots.read_model_json");
        assertThat(live.bucketDistributionSource()).isEqualTo("accepted_bucket");
        assertThat(snapshot.bucketDistributionSource()).isEqualTo("accepted_bucket");
        assertThat(live.histogramBucketsUsedForPercentiles()).isFalse();
        assertThat(snapshot.histogramBucketsUsedForPercentiles()).isFalse();

        assertThatThrownBy(() -> new ApplicationDashboardReadModel.EndpointPriorityEvidence(
                10L,
                1L,
                BigDecimal.valueOf(0.1d),
                null,
                null,
                null,
                null,
                List.of(new ApplicationDashboardReadModel.HistogramBucket(500L, 10L)),
                null,
                null,
                null,
                null,
                "histogram_bucket_distribution",
                ApplicationDashboardReadModel.EndpointEvidenceStatus.AVAILABLE,
                ApplicationDashboardReadModel.EndpointEvidenceStatus.AVAILABLE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void degradedDashboardKeepsResourceTriageAsAttentionEvidence() {
        ApplicationDashboardReadModel readModel = readModelWithTriageCards(List.of(
                triageCard("application_error_rate_high", ApplicationDashboardReadModel.TriageSeverity.WARNING),
                triageCard("db_pool_high_with_latency", ApplicationDashboardReadModel.TriageSeverity.INFO)));

        assertThat(readModel.stateReasons())
                .extracting(ApplicationDashboardReadModel.StateReason::reasonCode)
                .containsExactly("application_error_rate_high");
        assertThat(readModel.attentionEvidence())
                .extracting(ApplicationDashboardReadModel.AttentionEvidence::reasonCode)
                .contains("db_pool_high_with_latency");
        assertThat(readModel.attentionEvidence())
                .allMatch(evidence -> !evidence.affectsLifecycleState());
    }

    @Test
    void rejectsInvalidEndpointPriorityValues() {
        ApplicationDashboardReadModel.EndpointPriorityFreshness freshness =
                new ApplicationDashboardReadModel.EndpointPriorityFreshness(
                        "current",
                        OffsetDateTime.parse("2026-05-26T01:10:30Z"),
                        "recent_30_minutes",
                        null);
        ApplicationDashboardReadModel.EndpointPriorityEvidence evidence =
                new ApplicationDashboardReadModel.EndpointPriorityEvidence(
                        30L,
                        2L,
                        BigDecimal.valueOf(0.066667d),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "accepted_bucket",
                        ApplicationDashboardReadModel.EndpointEvidenceStatus.AVAILABLE,
                        ApplicationDashboardReadModel.EndpointEvidenceStatus.UNAVAILABLE);

        assertThatThrownBy(() -> new ApplicationDashboardReadModel.EndpointPriorityItem(
                0,
                "GET",
                "/orders",
                "GET /orders",
                ApplicationDashboardReadModel.EndpointPriorityReason.ERROR_SPIKE,
                List.of("endpoint_error_spike"),
                0.7d,
                70,
                freshness,
                evidence,
                "오류 로그를 먼저 확인해보세요."))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApplicationDashboardReadModel.EndpointPriorityItem(
                1,
                "GET",
                "/orders",
                "POST /orders",
                ApplicationDashboardReadModel.EndpointPriorityReason.ERROR_SPIKE,
                List.of("endpoint_error_spike"),
                0.7d,
                70,
                freshness,
                evidence,
                "오류 로그를 먼저 확인해보세요."))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApplicationDashboardReadModel.EndpointPriorityItem(
                1,
                "GET",
                "/orders",
                "GET /orders",
                ApplicationDashboardReadModel.EndpointPriorityReason.ERROR_SPIKE,
                List.of("endpoint_error_spike"),
                Double.NaN,
                70,
                freshness,
                evidence,
                "오류 로그를 먼저 확인해보세요."))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApplicationDashboardReadModel.EndpointPriorityItem(
                1,
                "GET",
                "/orders",
                "GET /orders",
                ApplicationDashboardReadModel.EndpointPriorityReason.ERROR_SPIKE,
                List.of("endpoint_error_spike"),
                Double.POSITIVE_INFINITY,
                70,
                freshness,
                evidence,
                "오류 로그를 먼저 확인해보세요."))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApplicationDashboardReadModel.EndpointPriorityItem(
                1,
                "GET",
                "/orders",
                "GET /orders",
                ApplicationDashboardReadModel.EndpointPriorityReason.ERROR_SPIKE,
                List.of("endpoint_error_spike"),
                Double.NEGATIVE_INFINITY,
                70,
                freshness,
                evidence,
                "오류 로그를 먼저 확인해보세요."))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApplicationDashboardReadModel.EndpointPriorityEvidence(
                10L,
                11L,
                BigDecimal.ONE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "accepted_bucket",
                ApplicationDashboardReadModel.EndpointEvidenceStatus.AVAILABLE,
                ApplicationDashboardReadModel.EndpointEvidenceStatus.UNAVAILABLE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ApplicationDashboardReadModel readModelWithTriageCards(
            List<ApplicationDashboardReadModel.TriageCard> triageCards) {
        OffsetDateTime end = OffsetDateTime.parse("2026-05-26T01:10:30Z");
        return new ApplicationDashboardReadModel(
                end,
                new ApplicationDashboardReadModel.Application(
                        java.util.UUID.fromString("00000000-0000-0000-0000-000000005201"),
                        java.util.UUID.fromString("00000000-0000-0000-0000-000000005211"),
                        "orders-api",
                        "prod",
                        end,
                        null,
                        new ApplicationDashboardReadModel.SourceWindow(
                                new ApplicationDashboardReadModel.Window(end.minusMinutes(30), end),
                                null),
                        new ApplicationDashboardReadModel.Freshness(end, end.plusSeconds(90), end.plusSeconds(180))),
                new ApplicationDashboardReadModel.State(
                        "degraded",
                        "서비스 성능 저하",
                        "최근 30분 기준 degraded입니다.",
                        "state reason을 확인하세요.",
                        "application"),
                new ApplicationDashboardReadModel.StarterConnection(
                        "starter_heartbeat",
                        end.minusSeconds(15),
                        "received",
                        "starter_connected",
                        "none"),
                null,
                new ApplicationDashboardReadModel.Recovery(false, null, null, null),
                new ApplicationDashboardReadModel.Metrics(100L, 8L, BigDecimal.valueOf(0.08d)),
                ApplicationDashboardReadModel.SourceScopedPercentiles.empty(),
                ApplicationDashboardReadModel.HistogramDistribution.empty(),
                triageCards,
                List.of(),
                List.of(),
                null);
    }

    private static ApplicationDashboardReadModel.TriageCard triageCard(
            String ruleId,
            ApplicationDashboardReadModel.TriageSeverity severity) {
        return new ApplicationDashboardReadModel.TriageCard(
                ruleId,
                severity,
                "확인 필요",
                ruleId + " summary",
                ruleId + " recommendation",
                severity == ApplicationDashboardReadModel.TriageSeverity.INFO ? 0.7d : 0.9d,
                severity == ApplicationDashboardReadModel.TriageSeverity.INFO ? 65 : 90,
                null,
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
}
