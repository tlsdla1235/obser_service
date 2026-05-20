package com.observation.starter.model.metric;

import com.observation.starter.model.route.NormalizedRoute;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EndpointKeyTest {

    @Test
    void normalizesOnlyPortalAcceptedMethods() {
        List<String> acceptedMethods = List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE", "UNKNOWN");

        for (String method : acceptedMethods) {
            assertEquals(method, EndpointKey.normalizeMethod(method.toLowerCase()));
        }
    }

    @Test
    void unsupportedAlphabeticMethodsConvergeToUnknown() {
        assertEquals("UNKNOWN", EndpointKey.normalizeMethod("CONNECT"));
        assertEquals("UNKNOWN", EndpointKey.normalizeMethod("BREW"));
        assertEquals("UNKNOWN", EndpointKey.normalizeMethod("M-SEARCH"));

        EndpointKey endpointKey = new EndpointKey("connect", NormalizedRoute.of("/orders"));

        assertEquals("UNKNOWN", endpointKey.method());
        assertEquals("UNKNOWN /orders", endpointKey.value());
    }
}
