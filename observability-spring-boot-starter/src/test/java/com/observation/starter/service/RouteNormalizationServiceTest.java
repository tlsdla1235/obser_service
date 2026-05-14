package com.observation.starter.service;

import com.observation.starter.model.route.NormalizedRoute;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RouteNormalizationServiceTest {

    @Test
    void removesRawPathParametersAndUsesUnknownFallbackWhenNoTemplateOrAllowlistMatches() {
        RouteNormalizationService service = new RouteNormalizationService();

        NormalizedRoute route = service.normalize(Optional.empty(), Optional.of("/orders/12345"));

        assertEquals(NormalizedRoute.unknown(), route);
        assertEquals("UNKNOWN", route.value());
    }

    @Test
    void removesQueryStringBeforeAllowlistMatchAndDoesNotReturnQuery() {
        RouteNormalizationService service = new RouteNormalizationService(List.of("/orders/{orderId}"));

        NormalizedRoute frameworkRoute = service.normalize(
                Optional.of("/orders/{orderId}?debug=true"),
                Optional.of("/orders/12345?debug=true"));
        NormalizedRoute allowlistRoute = service.normalize(
                Optional.empty(),
                Optional.of("/orders/12345?debug=true"));

        assertEquals("/orders/{orderId}", frameworkRoute.value());
        assertEquals("/orders/{orderId}", allowlistRoute.value());
    }

    @Test
    void frameworkRouteTemplateHasPriorityOverAllowlistMatch() {
        RouteNormalizationService service = new RouteNormalizationService(List.of("/orders/{orderId}"));

        NormalizedRoute route = service.normalize(
                Optional.of("/api/orders/{orderId}"),
                Optional.of("/orders/12345"));

        assertEquals("/api/orders/{orderId}", route.value());
    }

    @Test
    void frameworkRouteFromHttpRouteIsTrustedEvenWhenItHasNoTemplateMarker() {
        RouteNormalizationService service = new RouteNormalizationService(List.of("/orders/{orderId}"));

        assertEquals(NormalizedRoute.unknown(),
                service.normalize(Optional.empty(), Optional.of("/api/orders")));
        assertEquals("/api/orders",
                service.normalize(Optional.of("/api/orders"), Optional.of("/orders/12345")).value());
    }

    @Test
    void allowlistMatchSucceedsOnlyWhenExactlyOneTemplateMatches() {
        RouteNormalizationService exactOneService = new RouteNormalizationService(
                List.of("/orders/{orderId}", "/search"));
        RouteNormalizationService ambiguousService = new RouteNormalizationService(
                List.of("/orders/{orderId}", "/orders/{id}"));

        assertEquals("/orders/{orderId}",
                exactOneService.normalize(Optional.empty(), Optional.of("/orders/12345")).value());
        assertEquals(NormalizedRoute.unknown(),
                ambiguousService.normalize(Optional.empty(), Optional.of("/orders/12345")));
    }

    @Test
    void untrustedRawPathCandidatesConvergeToUnknownInsteadOfReturningRawPath() {
        RouteNormalizationService service = new RouteNormalizationService(List.of("/orders/{orderId}"));

        assertEquals(NormalizedRoute.unknown(),
                service.normalize(Optional.empty(), Optional.of("https://example.test/orders/123?token=secret")));
        assertEquals(NormalizedRoute.unknown(),
                service.normalize(Optional.empty(), Optional.of("/sessions/abc")));
        assertEquals(NormalizedRoute.unknown(),
                service.normalize(Optional.empty(), Optional.of("orders/123")));
        assertEquals(NormalizedRoute.unknown(),
                service.normalize(Optional.empty(), Optional.of("/orders/%ZZ")));
    }

    @Test
    void doesNotApplySingleSegmentHeuristicFallbackWithoutAllowlist() {
        RouteNormalizationService service = new RouteNormalizationService();

        assertEquals(NormalizedRoute.unknown(),
                service.normalize(Optional.empty(), Optional.of("/search")));
    }

    @Test
    void absoluteFrameworkRouteConvergesToUnknown() {
        RouteNormalizationService service = new RouteNormalizationService(List.of("/orders/{orderId}"));

        assertEquals(NormalizedRoute.unknown(),
                service.normalize(Optional.of("https://example.test/orders/{orderId}"), Optional.of("/orders/123")));
    }
}
