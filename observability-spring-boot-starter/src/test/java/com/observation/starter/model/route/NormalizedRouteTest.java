package com.observation.starter.model.route;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NormalizedRouteTest {

    @Test
    void preservesBoundedOmissionMarkerButStripsActualQuerySuffix() {
        assertEquals("/{userId}?.../posts", NormalizedRoute.of("/{userId}?.../posts").value());
        assertEquals("/orders/{orderId}", NormalizedRoute.of("/orders/{orderId}?token=abc").value());
    }

    @Test
    void acceptsSafePrefixCollapseMarkerAsNormalizedRoute() {
        assertEquals("/orders/...", NormalizedRoute.of("/orders/...").value());
        assertEquals("/api/v1/orders/...", NormalizedRoute.of("/api/v1/orders/...").value());
    }
}
