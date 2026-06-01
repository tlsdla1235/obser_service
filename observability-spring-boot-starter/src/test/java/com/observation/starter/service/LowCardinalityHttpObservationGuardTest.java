package com.observation.starter.service;

import com.observation.starter.model.metric.HttpServerObservationInput;
import com.observation.starter.model.metric.LowCardinalityHttpServerObservation;
import com.observation.starter.model.metric.LowCardinalityTagKey;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LowCardinalityHttpObservationGuardTest {

    @Test
    void convertsHttpObservationInputToNormalizedRouteAndEndpointKey() {
        LowCardinalityHttpObservationGuard guard = new LowCardinalityHttpObservationGuard(
                new RouteNormalizationService());
        HttpServerObservationInput input = httpInput(" post ",
                Optional.of("/orders/{orderId}?debug=true"),
                Optional.of("/orders/123?debug=true"));

        LowCardinalityHttpServerObservation guarded = guard.guard(input);

        assertEquals("POST", guarded.method());
        assertEquals("/orders/{orderId}", guarded.normalizedRoute().value());
        assertEquals("POST /orders/{orderId}", guarded.endpointKey().value());
    }

    @Test
    void frameworkRouteTemplateHasPriorityOverRawPathCandidate() {
        LowCardinalityHttpObservationGuard guard = new LowCardinalityHttpObservationGuard(
                new RouteNormalizationService(List.of("/orders/{orderId}")));
        HttpServerObservationInput input = httpInput("GET",
                Optional.of("/api/orders/{orderId}"),
                Optional.of("/orders/123?debug=true"));

        LowCardinalityHttpServerObservation guarded = guard.guard(input);

        assertEquals("/api/orders/{orderId}", guarded.normalizedRoute().value());
        assertEquals("GET /api/orders/{orderId}", guarded.endpointKey().value());
    }

    @Test
    void preservesBoundedOmissionMarkerAcrossInputAndGuardBoundary() {
        LowCardinalityHttpObservationGuard guard = new LowCardinalityHttpObservationGuard(
                new RouteNormalizationService(List.of("/users/{userId}/posts")));
        HttpServerObservationInput input = httpInput("GET",
                Optional.of("/{userId}?.../posts"),
                Optional.of("/users/123/posts?debug=true"));

        LowCardinalityHttpServerObservation guarded = guard.guard(input);

        assertEquals(Optional.of("/{userId}?.../posts"), input.routePattern());
        assertEquals("/{userId}?.../posts", guarded.normalizedRoute().value());
        assertEquals("GET /{userId}?.../posts", guarded.endpointKey().value());
    }

    @Test
    void rawFallbackIsAvailableWhenRoutePatternNormalizesToUnknown() {
        LowCardinalityHttpObservationGuard guard = new LowCardinalityHttpObservationGuard(
                new RouteNormalizationService(List.of("/orders/{orderId}")));
        HttpServerObservationInput input = httpInput("GET",
                Optional.of("/orders/{orderId"),
                Optional.of("/orders/123?debug=true"));

        LowCardinalityHttpServerObservation guarded = guard.guard(input);

        assertEquals("GET /orders/{orderId}", guarded.endpointKey().value());
    }

    @Test
    void rawPathCandidateCanMatchConfiguredAllowlistWhenFrameworkRouteIsAbsent() {
        LowCardinalityHttpObservationGuard guard = new LowCardinalityHttpObservationGuard(
                new RouteNormalizationService(List.of("/search", "/orders/{orderId}")));

        LowCardinalityHttpServerObservation search = guard.guard(httpInput(
                "GET",
                Optional.empty(),
                Optional.of("/search?q=abc")));
        LowCardinalityHttpServerObservation order = guard.guard(httpInput(
                "GET",
                Optional.empty(),
                Optional.of("/orders/123?debug=true")));

        assertEquals("GET /search", search.endpointKey().value());
        assertEquals("GET /orders/{orderId}", order.endpointKey().value());
    }

    @Test
    void unsafeRawPathCandidatesConvergeToUnknown() {
        LowCardinalityHttpObservationGuard guard = new LowCardinalityHttpObservationGuard(
                new RouteNormalizationService(List.of("/orders/{orderId}")));

        List<Optional<String>> candidates = List.of(
                Optional.of("/users/alice?token=secret"),
                Optional.of("https://example.test/orders/123?token=secret"),
                Optional.of("orders/123"),
                Optional.of("/orders/%ZZ"));

        for (Optional<String> candidate : candidates) {
            HttpServerObservationInput input = httpInput("GET", Optional.empty(), candidate);

            LowCardinalityHttpServerObservation guarded = guard.guard(input);

            assertEquals("UNKNOWN", guarded.normalizedRoute().value());
            assertEquals("GET UNKNOWN", guarded.endpointKey().value());
        }
    }

    @Test
    void ambiguousAllowlistMatchConvergesToUnknown() {
        LowCardinalityHttpObservationGuard guard = new LowCardinalityHttpObservationGuard(
                new RouteNormalizationService(List.of("/orders/{orderId}", "/orders/{id}")));
        HttpServerObservationInput input = httpInput("GET", Optional.empty(), Optional.of("/orders/123"));

        LowCardinalityHttpServerObservation guarded = guard.guard(input);

        assertEquals("UNKNOWN", guarded.normalizedRoute().value());
        assertEquals("GET UNKNOWN", guarded.endpointKey().value());
    }

    @Test
    void highCardinalityTagKeysAreRejectedByPolicy() {
        Set<String> forbiddenKeys = Set.of("userId", "tenantId", "sessionId", "traceId", "customerLabel");

        for (String forbiddenKey : forbiddenKeys) {
            assertFalse(LowCardinalityTagKey.isAllowedExternalKey(forbiddenKey));
        }
        assertTrue(LowCardinalityTagKey.isAllowedExternalKey("application"));
        assertTrue(LowCardinalityTagKey.isAllowedExternalKey("environment"));
        assertTrue(LowCardinalityTagKey.isAllowedExternalKey("instance"));
        assertTrue(LowCardinalityTagKey.isAllowedExternalKey("method"));
        assertTrue(LowCardinalityTagKey.isAllowedExternalKey("normalizedRoute"));
    }

    @Test
    void guardedObservationDoesNotExposeArbitraryTagMapOrRawPathField() {
        RecordComponent[] components = LowCardinalityHttpServerObservation.class.getRecordComponents();

        assertTrue(Arrays.stream(components).noneMatch(component -> Map.class.isAssignableFrom(component.getType())));
        assertTrue(Arrays.stream(components).noneMatch(component -> component.getName().toLowerCase().contains("path")));
        assertTrue(Arrays.stream(components).noneMatch(component -> component.getName().toLowerCase().contains("tag")));
    }

    @Test
    void guardDoesNotPropagateUntrustedRouteCandidateFailureToHostRequestPath() {
        LowCardinalityHttpObservationGuard guard = new LowCardinalityHttpObservationGuard(
                new RouteNormalizationService());
        HttpServerObservationInput input = httpInput("GET",
                Optional.of("https://example.test/sessions/{sessionId}"),
                Optional.empty());

        LowCardinalityHttpServerObservation guarded = assertDoesNotThrow(() -> guard.guard(input));

        assertEquals("UNKNOWN", guarded.normalizedRoute().value());
        assertEquals("GET UNKNOWN", guarded.endpointKey().value());
    }

    private static HttpServerObservationInput httpInput(
            String method,
            Optional<String> routePattern,
            Optional<String> rawPathCandidate) {
        return new HttpServerObservationInput(
                Instant.parse("2026-05-13T12:00:00Z"),
                method,
                200,
                false,
                null,
                Duration.ofMillis(42),
                routePattern,
                rawPathCandidate);
    }
}
