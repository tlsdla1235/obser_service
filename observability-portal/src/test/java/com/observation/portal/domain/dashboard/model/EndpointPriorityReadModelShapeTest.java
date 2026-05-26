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
                        List.of("endpoint_error_spike", "endpoint_latency_spike"),
                        0.84d,
                        84,
                        new ApplicationDashboardReadModel.EndpointPriorityFreshness(
                                "current",
                                OffsetDateTime.parse("2026-05-26T01:10:30Z"),
                                "current",
                                null),
                        new ApplicationDashboardReadModel.EndpointPriorityEvidence(
                                120L,
                                12L,
                                BigDecimal.valueOf(0.10d),
                                100L,
                                1L,
                                BigDecimal.valueOf(0.01d),
                                BigDecimal.valueOf(0.09d),
                                List.of(
                                        new ApplicationDashboardReadModel.HistogramBucket(500L, 70L),
                                        new ApplicationDashboardReadModel.HistogramBucket(1000L, 120L)),
                                List.of(
                                        new ApplicationDashboardReadModel.HistogramBucket(500L, 95L),
                                        new ApplicationDashboardReadModel.HistogramBucket(1000L, 100L)),
                                BigDecimal.valueOf(0.416667d),
                                BigDecimal.valueOf(0.05d),
                                BigDecimal.valueOf(0.366667d),
                                "histogram_bucket_distribution",
                                ApplicationDashboardReadModel.EndpointEvidenceStatus.AVAILABLE,
                                ApplicationDashboardReadModel.EndpointEvidenceStatus.AVAILABLE),
                        "이 endpoint의 오류 로그와 외부 의존성 지연 가능성을 먼저 확인해보세요.");

        assertThat(item.rank()).isEqualTo(1);
        assertThat(item.endpointKey()).isEqualTo("POST /orders");
        assertThat(item.reason()).isEqualTo(ApplicationDashboardReadModel.EndpointPriorityReason.ERROR_AND_LATENCY);
        assertThat(item.freshness().status()).isEqualTo("current");
        assertThat(item.evidence().bucketDistributionSource()).isEqualTo("histogram_bucket_distribution");
        assertThat(item.evidence().durationBuckets()).hasSize(2);
        assertThat(item.evidence().baselineDurationBuckets()).hasSize(2);
    }

    @Test
    void rejectsInvalidEndpointPriorityValues() {
        ApplicationDashboardReadModel.EndpointPriorityFreshness freshness =
                new ApplicationDashboardReadModel.EndpointPriorityFreshness(
                        "current",
                        OffsetDateTime.parse("2026-05-26T01:10:30Z"),
                        "current",
                        null);
        ApplicationDashboardReadModel.EndpointPriorityEvidence evidence =
                new ApplicationDashboardReadModel.EndpointPriorityEvidence(
                        30L,
                        2L,
                        BigDecimal.valueOf(0.066667d),
                        30L,
                        0L,
                        BigDecimal.ZERO,
                        BigDecimal.valueOf(0.066667d),
                        null,
                        null,
                        null,
                        null,
                        null,
                        "histogram_bucket_distribution",
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
                "histogram_bucket_distribution",
                ApplicationDashboardReadModel.EndpointEvidenceStatus.AVAILABLE,
                ApplicationDashboardReadModel.EndpointEvidenceStatus.UNAVAILABLE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
