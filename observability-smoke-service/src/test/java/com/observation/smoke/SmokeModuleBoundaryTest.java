package com.observation.smoke;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SmokeModuleBoundaryTest {

    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void smokeModuleDependsOnStarterButPortalAndStarterDoNotDependOnSmokeModule() throws IOException {
        String smokeBuild = Files.readString(Path.of("build.gradle"));
        String portalBuild = Files.readString(REPO_ROOT.resolve("observability-portal/build.gradle"));
        String starterBuild = Files.readString(REPO_ROOT.resolve("observability-spring-boot-starter/build.gradle"));

        assertThat(smokeBuild).contains("project(':observability-spring-boot-starter')");
        assertThat(portalBuild).doesNotContain("observability-smoke-service");
        assertThat(starterBuild).doesNotContain("observability-smoke-service");
    }

    @Test
    void smokeSourceDoesNotOverrideStarterBucketClientOrEnvelopeBoundary() throws IOException {
        List<Path> smokeSources;
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            smokeSources = paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }

        for (Path source : smokeSources) {
            String content = Files.readString(source);
            assertThat(content)
                    .as("smoke source must not bypass starter defaults: %s", source)
                    .doesNotContain(
                            "PortalMetricBucketClient",
                            "JdkPortalMetricBucketClient",
                            "IngestEnvelopeBuilderService",
                            "IngestEnvelopeCandidate",
                            "Idempotency");
        }
    }

    @Test
    void smokeScriptsAndRunbookAvoidSecretLeakAndUnsafeShellPatterns() throws IOException {
        List<Path> artifacts = List.of(
                REPO_ROOT.resolve("implementation-artifacts/real-github-oauth-smoke-runbook.md"),
                REPO_ROOT.resolve("scripts/smoke/run-smoke-traffic.sh"),
                REPO_ROOT.resolve("scripts/smoke/verify-smoke-portal-flow.sh"),
                Path.of("src/main/resources/application-local-smoke.properties"));

        for (Path artifact : artifacts) {
            assertThat(Files.exists(artifact)).as("smoke artifact must exist: %s", artifact).isTrue();
            String content = Files.readString(artifact);
            assertThat(content)
                    .as("smoke artifact must not expose secret values or unsafe debug output: %s", artifact)
                    .doesNotContain(
                            "curl -v",
                            "set -x",
                            "source .private/smoke-auth.env",
                            "client_secret=",
                            "raw-client-secret",
                            "Authorization: Bearer ${OBSERVATION_SMOKE_PROJECT_KEY}");
            if (artifact.toString().endsWith(".sh")) {
                assertThat(content).contains("jq");
                assertThat(content).contains("command -v");
            }
        }
    }
}
