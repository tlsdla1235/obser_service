package com.observation.portal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PortalModuleSmokeTest {

    @Test
    void exposesPortalBasePackage() {
        assertEquals("com.observation.portal", PortalApplication.class.getPackageName());
    }
}

