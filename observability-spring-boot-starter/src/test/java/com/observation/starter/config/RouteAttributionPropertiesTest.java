package com.observation.starter.config;

import com.observation.starter.service.LowCardinalityHttpObservationGuard;
import com.observation.starter.service.RouteNormalizationService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteAttributionPropertiesTest {

    @Test
    void declaresRouteAttributionAllowlistNamespaceAndAcceptsRouteTemplates() {
        RouteAttributionProperties properties = new RouteAttributionProperties();

        properties.setAllowlist(List.of(" /orders/{orderId} ", "/search", "/api/v1/orders/{orderId}"));

        assertEquals("observation.route-attribution", RouteAttributionProperties.PREFIX);
        assertEquals("observation.route-attribution.allowlist", RouteAttributionProperties.ALLOWLIST_PROPERTY);
        assertEquals(List.of("/orders/{orderId}", "/search", "/api/v1/orders/{orderId}"),
                properties.getAllowlist());
        assertTrue(RouteAttributionProperties.isValidAllowlistTemplate("/orders/{orderId}"));
    }

    @Test
    void rejectsQueryStringAbsoluteUrlAndConcreteIdValuesInAllowlist() {
        RouteAttributionProperties properties = new RouteAttributionProperties();

        assertThrows(IllegalArgumentException.class,
                () -> properties.setAllowlist(List.of("/orders/{orderId}?debug=true")));
        assertThrows(IllegalArgumentException.class,
                () -> properties.setAllowlist(List.of("https://example.test/orders/{orderId}")));
        assertThrows(IllegalArgumentException.class,
                () -> properties.setAllowlist(List.of("/orders/")));
        assertThrows(IllegalArgumentException.class,
                () -> properties.setAllowlist(List.of("/orders//{orderId}")));
        assertThrows(IllegalArgumentException.class,
                () -> properties.setAllowlist(List.of("/orders/12345")));
        assertThrows(IllegalArgumentException.class,
                () -> properties.setAllowlist(List.of("/assets/deadbeef")));
        assertThrows(IllegalArgumentException.class,
                () -> properties.setAllowlist(List.of("/orders/550e8400-e29b-41d4-a716-446655440000")));
        assertThrows(IllegalArgumentException.class,
                () -> properties.setAllowlist(List.of("/orders/{order-id}")));
    }

    @Test
    void autoConfigurationWiresAllowlistBackedRouteNormalizationService() {
        RouteAttributionProperties properties = new RouteAttributionProperties();
        properties.setAllowlist(List.of("/orders/{orderId}"));
        RouteAttributionAutoConfiguration autoConfiguration = new RouteAttributionAutoConfiguration();

        RouteNormalizationService service = autoConfiguration.routeNormalizationService(properties);
        LowCardinalityHttpObservationGuard guard = autoConfiguration.lowCardinalityHttpObservationGuard(service);

        assertEquals("/orders/{orderId}",
                service.normalize(Optional.empty(), Optional.of("/orders/123?debug=true")).value());
        assertEquals(LowCardinalityHttpObservationGuard.class, guard.getClass());
    }
}
