package com.observation.starter;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StarterModuleSmokeTest {

    @Test
    void exposesStarterSkeletonPackageMarkers() {
        List.of(
                "com.observation.starter",
                "com.observation.starter.model",
                "com.observation.starter.service",
                "com.observation.starter.spring",
                "com.observation.starter.client",
                "com.observation.starter.client.http",
                "com.observation.starter.queue",
                "com.observation.starter.config"
        ).forEach(packageName -> assertTrue(
                Files.isRegularFile(packageInfoPath(packageName)),
                () -> "Missing package marker for " + packageName));
    }

    private static Path packageInfoPath(String packageName) {
        return Path.of("src/main/java", packageName.replace('.', '/'), "package-info.java");
    }
}
