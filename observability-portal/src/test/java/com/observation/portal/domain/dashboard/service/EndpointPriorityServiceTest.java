package com.observation.portal.domain.dashboard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.observation.portal.common.time.AcceptedBucketFreshnessStatus;
import com.observation.portal.domain.bucket.model.EndpointEvidenceRow;
import com.observation.portal.domain.dashboard.model.ApplicationDashboardReadModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class EndpointPriorityServiceTest {

    private static final UUID APPLICATION_ID = UUID.fromString("00000000-0000-0000-0000-000000005511");

    private final EndpointEvidenceAggregationService endpointEvidenceAggregationService =
            new EndpointEvidenceAggregationService(new ObjectMapper());
    private final EndpointPriorityService service = new EndpointPriorityService(endpointEvidenceAggregationService);

    @Test
    void ranksByReasonPriorityTieBreakersAndCapsAtFiveItems() {
        List<ApplicationDashboardReadModel.EndpointPriorityItem> items = service.endpointPriority(
                new EndpointPriorityService.EndpointPriorityInput(
                        AcceptedBucketFreshnessStatus.CURRENT,
                        List.of(row("2026-05-26T01:10:00Z", """
                                [
                                  %s,
                                  %s,
                                  %s,
                                  %s,
                                  %s,
                                  %s,
                                  %s
                                ]
                                """.formatted(
                                endpoint("POST", "/both", 100, 10, 70, 100),
                                endpoint("GET", "/error-b", 100, 8, 95, 100),
                                endpoint("GET", "/error-a", 100, 8, 95, 100),
                                endpoint("GET", "/latency", 100, 0, 60, 100),
                                endpoint("GET", "/compare-b", 100, 3, 90, 100),
                                endpoint("GET", "/compare-a", 100, 3, 90, 100),
                                endpoint("GET", "/compare-c", 100, 3, 90, 100)))),
                        List.of(row("2026-05-26T00:55:00Z", """
                                [
                                  %s,
                                  %s,
                                  %s,
                                  %s,
                                  %s,
                                  %s,
                                  %s
                                ]
                                """.formatted(
                                endpoint("POST", "/both", 100, 1, 95, 100),
                                endpoint("GET", "/error-b", 100, 1, 95, 100),
                                endpoint("GET", "/error-a", 100, 1, 95, 100),
                                endpoint("GET", "/latency", 100, 0, 90, 100),
                                endpoint("GET", "/compare-b", 100, 0, 95, 100),
                                endpoint("GET", "/compare-a", 100, 0, 95, 100),
                                endpoint("GET", "/compare-c", 100, 0, 95, 100)))),
                        offset("2026-05-26T01:10:30Z")));

        assertThat(items).hasSize(5);
        assertThat(items)
                .extracting(
                        ApplicationDashboardReadModel.EndpointPriorityItem::rank,
                        ApplicationDashboardReadModel.EndpointPriorityItem::endpointKey,
                        ApplicationDashboardReadModel.EndpointPriorityItem::reason)
                .containsExactly(
                        tuple(1, "POST /both", ApplicationDashboardReadModel.EndpointPriorityReason.ERROR_AND_LATENCY),
                        tuple(2, "GET /error-a", ApplicationDashboardReadModel.EndpointPriorityReason.ERROR_SPIKE),
                        tuple(3, "GET /error-b", ApplicationDashboardReadModel.EndpointPriorityReason.ERROR_SPIKE),
                        tuple(4, "GET /latency", ApplicationDashboardReadModel.EndpointPriorityReason.LATENCY_SPIKE),
                        tuple(5, "GET /compare-a", ApplicationDashboardReadModel.EndpointPriorityReason.RECENT_ERROR));
        assertThat(items).allSatisfy(item -> {
            assertThat(item.method()).isNotBlank();
            assertThat(item.route()).isNotBlank();
            assertThat(item.endpointKey()).isEqualTo(item.method() + " " + item.route());
            assertThat(item.freshness().status()).isEqualTo("current");
            assertThat(item.freshness().sourceWindow()).isEqualTo("recent_30_minutes");
            assertThat(item.freshness().lastObservedAt()).isEqualTo(offset("2026-05-26T01:10:30Z"));
            assertThat(item.evidence().baselineRequestCount()).isNull();
            assertThat(item.evidence().errorRateDelta()).isNull();
        });
    }

    @Test
    void baselineOnlyRegressionStillOnlyExposesRecentServerErrorAttention() {
        List<ApplicationDashboardReadModel.EndpointPriorityItem> items = service.endpointPriority(input(
                List.of(endpoint("GET", "/compare", 100, 3, 90, 100)),
                List.of(endpoint("GET", "/compare", 100, 0, 95, 100))));

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.reason()).isEqualTo(ApplicationDashboardReadModel.EndpointPriorityReason.RECENT_ERROR);
            assertThat(item.ruleIds()).containsExactly("endpoint_recent_server_error");
            assertThat(item.evidence().baselineRequestCount()).isNull();
            assertThat(item.evidence().errorRateDelta()).isNull();
        });
    }

    @Test
    void suppressesEndpointPriorityWhenApplicationFreshnessIsNotCurrent() {
        List<ApplicationDashboardReadModel.EndpointPriorityItem> items = service.endpointPriority(
                new EndpointPriorityService.EndpointPriorityInput(
                        AcceptedBucketFreshnessStatus.DOWN_CANDIDATE,
                        List.of(row("2026-05-26T01:10:00Z",
                                "[" + endpoint("GET", "/orders", 100, 10, 70, 100) + "]")),
                        List.of(row("2026-05-26T00:55:00Z",
                                "[" + endpoint("GET", "/orders", 100, 1, 95, 100) + "]")),
                        offset("2026-05-26T01:10:30Z")));

        assertThat(items).isEmpty();
    }

    @Test
    void excludesUnknownRoutesButKeepsLowSampleRecentErrorRows() {
        List<ApplicationDashboardReadModel.EndpointPriorityItem> items = service.endpointPriority(input(
                List.of(
                        endpoint("GET", "UNKNOWN", 200, 30, 70, 200),
                        endpoint("POST", "/too-small", 29, 10, 20, 29)),
                List.of(
                        endpoint("GET", "UNKNOWN", 200, 1, 190, 200),
                        endpoint("POST", "/too-small", 100, 1, 95, 100))));

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.endpointKey()).isEqualTo("POST /too-small");
            assertThat(item.reason()).isEqualTo(ApplicationDashboardReadModel.EndpointPriorityReason.RECENT_ERROR);
            assertThat(item.ruleIds()).containsExactly("endpoint_recent_server_error");
            assertThat(item.evidence().errorCount()).isEqualTo(10L);
        });
    }

    @Test
    void exposesRecentErrorEndpointWithoutBaselineOrMinimumSample() {
        List<ApplicationDashboardReadModel.EndpointPriorityItem> items = service.endpointPriority(
                new EndpointPriorityService.EndpointPriorityInput(
                        AcceptedBucketFreshnessStatus.CURRENT,
                        List.of(row("2026-05-26T01:10:00Z",
                                "[" + endpoint("GET", "/health", 1, 1, 1, 1) + "]")),
                        List.of(),
                        offset("2026-05-26T01:10:30Z")));

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.endpointKey()).isEqualTo("GET /health");
            assertThat(item.reason()).isEqualTo(ApplicationDashboardReadModel.EndpointPriorityReason.RECENT_ERROR);
            assertThat(item.ruleIds()).containsExactly("endpoint_recent_server_error");
            assertThat(item.confidence()).isLessThanOrEqualTo(0.64d);
            assertThat(item.evidence().requestCount()).isEqualTo(1L);
            assertThat(item.evidence().errorCount()).isEqualTo(1L);
            assertThat(item.evidence().errorRate()).isEqualByComparingTo(BigDecimal.ONE);
            assertThat(item.evidence().baselineRequestCount()).isNull();
            assertThat(item.evidence().baselineErrorRate()).isNull();
            assertThat(item.evidence().errorRateDelta()).isNull();
        });
    }

    @Test
    void sortsFallbackRecentErrorEvidenceAfterPrimaryConcernsByErrorSlowAndRequestCount() {
        List<ApplicationDashboardReadModel.EndpointPriorityItem> items = service.endpointPriority(input(
                List.of(
                        endpoint("GET", "/fallback-high-error", 10, 3, 10, 10),
                        endpoint("GET", "/fallback-slow-tie", 20, 2, 5, 20),
                        endpoint("GET", "/fallback-request-tie", 80, 2, 70, 80),
                        endpoint("GET", "/fallback-low", 100, 1, 100, 100),
                        endpoint("GET", "/primary-error", 100, 5, 100, 100)),
                List.of()));

        assertThat(items)
                .extracting(ApplicationDashboardReadModel.EndpointPriorityItem::endpointKey)
                .containsExactly(
                        "GET /primary-error",
                        "GET /fallback-high-error",
                        "GET /fallback-slow-tie",
                        "GET /fallback-request-tie",
                        "GET /fallback-low");
        assertThat(items.get(0).reason()).isEqualTo(ApplicationDashboardReadModel.EndpointPriorityReason.ERROR_SPIKE);
        assertThat(items.subList(1, items.size()))
                .allSatisfy(item -> assertThat(item.reason())
                        .isEqualTo(ApplicationDashboardReadModel.EndpointPriorityReason.RECENT_ERROR));
    }

    @Test
    void keepsNormalizedDiagnosticRoutesWhileStillSuppressingUnsafeIdentifiers() {
        List<ApplicationDashboardReadModel.EndpointPriorityItem> items = service.endpointPriority(input(
                List.of(
                        endpoint("GET", "/api/ecc-smoke/error-500", 40, 4, 40, 40),
                        endpoint("GET", "/api/ecc-smoke/slow-p99", 40, 0, 20, 40),
                        endpoint("GET", "/orders/order-123", 120, 12, 70, 120),
                        endpoint("GET", "/orders/12345?token=secret", 120, 12, 70, 120),
                        endpoint("GET", "https://example.test/orders/{orderId}", 120, 12, 70, 120),
                        endpoint("GET", "/orders/550e8400-e29b-41d4-a716-446655440000", 120, 12, 70, 120)),
                List.of()));

        assertThat(items)
                .extracting(ApplicationDashboardReadModel.EndpointPriorityItem::endpointKey)
                .containsExactly("GET /api/ecc-smoke/error-500", "GET /api/ecc-smoke/slow-p99");
        assertThat(items.toString())
                .doesNotContain("order-123")
                .doesNotContain("token")
                .doesNotContain("https://example.test")
                .doesNotContain("550e8400");
    }

    @Test
    void skipsPrivateRoutesWithoutLeakingRawRouteValues() throws Exception {
        List<ApplicationDashboardReadModel.EndpointPriorityItem> items = service.endpointPriority(input(
                List.of(
                        endpoint("POST", "/orders", 120, 12, 70, 120),
                        endpoint("GET", "/orders/01ARZ3NDEKTSV4RRFFQ69G5FAV", 120, 12, 70, 120),
                        endpoint("GET", "/orders/aB3dE5fG7hJ9kL2m", 120, 12, 70, 120),
                        endpoint("GET", "/orders/order-123", 120, 12, 70, 120),
                        endpoint("GET", "/users/jane.doe", 120, 12, 70, 120),
                        endpoint("GET", "/orders/12345?token=secret", 120, 12, 70, 120),
                        endpoint("GET", "https://example.test/orders/{orderId}", 120, 12, 70, 120),
                        endpoint("GET", "/assets/deadbeef", 120, 12, 70, 120),
                        """
                        {
                          "method": "GET",
                          "route": "/debug/\\u0001",
                          "requestCount": 120,
                          "errorCount": 12,
                          "durationBuckets": [
                            {"leMs": 500, "count": 70},
                            {"leMs": 1000, "count": 120}
                          ]
                        }
                        """),
                List.of(
                        endpoint("POST", "/orders", 120, 1, 114, 120),
                        endpoint("GET", "/orders/01ARZ3NDEKTSV4RRFFQ69G5FAV", 120, 1, 114, 120),
                        endpoint("GET", "/orders/aB3dE5fG7hJ9kL2m", 120, 1, 114, 120),
                        endpoint("GET", "/orders/order-123", 120, 1, 114, 120),
                        endpoint("GET", "/users/jane.doe", 120, 1, 114, 120),
                        endpoint("GET", "/orders/12345?token=secret", 120, 1, 114, 120),
                        endpoint("GET", "https://example.test/orders/{orderId}", 120, 1, 114, 120),
                        endpoint("GET", "/assets/deadbeef", 120, 1, 114, 120))));

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.endpointKey()).isEqualTo("POST /orders");
            assertThat(item.route()).isEqualTo("/orders");
        });
        assertThat(items.toString())
                .doesNotContain("token")
                .doesNotContain("12345")
                .doesNotContain("01ARZ3NDEKTSV4RRFFQ69G5FAV")
                .doesNotContain("aB3dE5fG7hJ9kL2m")
                .doesNotContain("order-123")
                .doesNotContain("jane.doe")
                .doesNotContain("https://example.test")
                .doesNotContain("deadbeef")
                .doesNotContain("/debug");
    }

    @Test
    void keepsValidCandidateWhenDecimalNumericEndpointItemsAreMalformed() {
        List<ApplicationDashboardReadModel.EndpointPriorityItem> items = service.endpointPriority(input(
                List.of(
                        endpoint("GET", "/orders", 100, 10, 95, 100),
                        """
                        {
                          "method": "GET",
                          "route": "/decimal-request",
                          "requestCount": 100.5,
                          "errorCount": 10,
                          "durationBuckets": [
                            {"leMs": 500, "count": 95},
                            {"leMs": 1000, "count": 100}
                          ]
                        }
                        """,
                        """
                        {
                          "method": "GET",
                          "route": "/decimal-error",
                          "requestCount": 100,
                          "errorCount": 10.5,
                          "durationBuckets": [
                            {"leMs": 500, "count": 95},
                            {"leMs": 1000, "count": 100}
                          ]
                        }
                        """,
                        """
                        {
                          "method": "GET",
                          "route": "/decimal-bucket",
                          "requestCount": 100,
                          "errorCount": 10,
                          "durationBuckets": [
                            {"leMs": 500.5, "count": 95},
                            {"leMs": 1000, "count": 100}
                          ]
                        }
                        """),
                List.of(
                        endpoint("GET", "/orders", 100, 1, 95, 100),
                        endpoint("GET", "/decimal-request", 100, 1, 95, 100),
                        endpoint("GET", "/decimal-error", 100, 1, 95, 100),
                        endpoint("GET", "/decimal-bucket", 100, 1, 95, 100))));

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.endpointKey()).isEqualTo("GET /orders");
            assertThat(item.reason()).isEqualTo(ApplicationDashboardReadModel.EndpointPriorityReason.ERROR_SPIKE);
            assertThat(item.evidence().requestCount()).isEqualTo(100L);
        });
        assertThat(items.toString())
                .doesNotContain("/decimal-request")
                .doesNotContain("/decimal-error")
                .doesNotContain("/decimal-bucket");
    }

    @Test
    void mergesCountsAndKeepsErrorEvidenceWhenEndpointLatencyBoundaryMismatches() {
        List<ApplicationDashboardReadModel.EndpointPriorityItem> items = service.endpointPriority(
                new EndpointPriorityService.EndpointPriorityInput(
                        AcceptedBucketFreshnessStatus.CURRENT,
                        List.of(
                                row("2026-05-26T01:09:30Z",
                                        "[" + endpoint("GET", "/orders", 30, 3, 20, 30) + "]"),
                                row("2026-05-26T01:10:00Z",
                                        "[" + endpointWithBuckets("GET", "/orders", 30, 3, 250, 18, 1000, 30)
                                                + "]")),
                        List.of(row("2026-05-26T00:55:00Z",
                                "[" + endpoint("GET", "/orders", 60, 1, 57, 60) + "]")),
                        offset("2026-05-26T01:10:30Z")));

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.reason()).isEqualTo(ApplicationDashboardReadModel.EndpointPriorityReason.ERROR_SPIKE);
            assertThat(item.evidence().requestCount()).isEqualTo(60L);
            assertThat(item.evidence().errorCount()).isEqualTo(6L);
            assertThat(item.evidence().errorRate()).isEqualByComparingTo(BigDecimal.valueOf(0.1d));
            assertThat(item.evidence().durationBuckets()).isNull();
            assertThat(item.evidence().errorEvidenceStatus())
                    .isEqualTo(ApplicationDashboardReadModel.EndpointEvidenceStatus.AVAILABLE);
            assertThat(item.evidence().latencyEvidenceStatus())
                    .isEqualTo(ApplicationDashboardReadModel.EndpointEvidenceStatus.UNAVAILABLE);
        });
    }

    @Test
    void keepsErrorEvidenceWhenEndpointHistogramCountsAreInvalid() {
        List<ApplicationDashboardReadModel.EndpointPriorityItem> items = service.endpointPriority(input(
                List.of(
                        endpointWithBuckets("GET", "/non-cumulative", 120, 12, 500, 90, 1000, 80),
                        endpointWithBuckets("GET", "/over-count", 120, 12, 500, 100, 1000, 121)),
                List.of(
                        endpoint("GET", "/non-cumulative", 120, 1, 114, 120),
                        endpoint("GET", "/over-count", 120, 1, 114, 120))));

        assertThat(items)
                .extracting(ApplicationDashboardReadModel.EndpointPriorityItem::endpointKey)
                .containsExactly("GET /non-cumulative", "GET /over-count");
        assertThat(items).allSatisfy(item -> {
            assertThat(item.reason()).isEqualTo(ApplicationDashboardReadModel.EndpointPriorityReason.ERROR_SPIKE);
            assertThat(item.evidence().errorEvidenceStatus())
                    .isEqualTo(ApplicationDashboardReadModel.EndpointEvidenceStatus.AVAILABLE);
            assertThat(item.evidence().latencyEvidenceStatus())
                    .isEqualTo(ApplicationDashboardReadModel.EndpointEvidenceStatus.UNAVAILABLE);
            assertThat(item.evidence().durationBuckets()).isNull();
            assertThat(item.evidence().baselineDurationBuckets()).isNull();
        });
    }

    @Test
    void keepsValidCandidatesWhenUnrelatedEndpointRowsOrItemsAreMalformed() {
        List<ApplicationDashboardReadModel.EndpointPriorityItem> items = service.endpointPriority(
                new EndpointPriorityService.EndpointPriorityInput(
                        AcceptedBucketFreshnessStatus.CURRENT,
                        List.of(
                                row("2026-05-26T01:09:30Z", "{ not-json"),
                                row("2026-05-26T01:10:00Z", """
                                        [
                                          %s,
                                          {"method": "GET", "route": "/missing-counts"},
                                          %s
                                        ]
                                        """.formatted(
                                        endpoint("GET", "/orders", 100, 10, 95, 100),
                                        endpoint("GET", "/users/12345", 100, 10, 70, 100)))),
                        List.of(row("2026-05-26T00:55:00Z", """
                                [
                                  %s,
                                  {"route": "/missing-method", "requestCount": 100, "errorCount": 1}
                                ]
                                """.formatted(endpoint("GET", "/orders", 100, 1, 95, 100)))),
                        offset("2026-05-26T01:10:30Z")));

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.endpointKey()).isEqualTo("GET /orders");
            assertThat(item.reason()).isEqualTo(ApplicationDashboardReadModel.EndpointPriorityReason.ERROR_SPIKE);
            assertThat(item.evidence().requestCount()).isEqualTo(100L);
        });
    }

    @Test
    void returnsEmptyForInvalidEndpointJsonWithoutLeakingRawJson() {
        List<ApplicationDashboardReadModel.EndpointPriorityItem> items = service.endpointPriority(
                new EndpointPriorityService.EndpointPriorityInput(
                        AcceptedBucketFreshnessStatus.CURRENT,
                        List.of(row("2026-05-26T01:10:00Z", "{ not-json")),
                        List.of(row("2026-05-26T00:55:00Z",
                                "[" + endpoint("GET", "/orders", 100, 1, 95, 100) + "]")),
                        offset("2026-05-26T01:10:30Z")));

        assertThat(items).isEmpty();
    }

    private static EndpointPriorityService.EndpointPriorityInput input(
            List<String> currentEndpoints,
            List<String> baselineEndpoints) {
        return new EndpointPriorityService.EndpointPriorityInput(
                AcceptedBucketFreshnessStatus.CURRENT,
                List.of(row("2026-05-26T01:10:00Z", "[" + String.join(",", currentEndpoints) + "]")),
                List.of(row("2026-05-26T00:55:00Z", "[" + String.join(",", baselineEndpoints) + "]")),
                offset("2026-05-26T01:10:30Z"));
    }

    private static EndpointEvidenceRow row(String bucketStartUtc, String endpointsJson) {
        OffsetDateTime start = offset(bucketStartUtc);
        return new EndpointEvidenceRow(APPLICATION_ID, start, start.plusSeconds(30), endpointsJson);
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
        return OffsetDateTime.parse(instant);
    }
}
