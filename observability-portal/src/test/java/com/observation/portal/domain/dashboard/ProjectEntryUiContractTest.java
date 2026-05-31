package com.observation.portal.domain.dashboard;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectEntryUiContractTest {

    private static final Path STATIC_DASHBOARD = Path.of("src/main/resources/static/dashboard");

    @Test
    void staticDashboardProjectEntryConsumesNavigationReadModelWithoutFrontendBuildStack() throws IOException {
        Path index = STATIC_DASHBOARD.resolve("index.html");
        Path script = STATIC_DASHBOARD.resolve("app.js");
        Path styles = STATIC_DASHBOARD.resolve("styles.css");

        assertThat(index).exists();
        assertThat(script).exists();
        assertThat(styles).exists();
        assertThat(Path.of("src/main/frontend")).doesNotExist();
        assertThat(Path.of("package.json")).doesNotExist();

        String indexHtml = Files.readString(index);
        String appJs = Files.readString(script);

        assertThat(indexHtml).contains("/api/auth/github/authorize");
        assertThat(appJs).contains("fetch('/api/projects'");
        assertThat(appJs).contains("Authorization", "Bearer", "observationPortalAuth");
        assertThat(appJs).contains("links.applications");
        assertThat(indexHtml + appJs).doesNotContain("React", "Vite", "TypeScript");
        assertThat(indexHtml + appJs).doesNotContain(
                "localStorage",
                "sessionStorage",
                "document.cookie",
                "window.location.hash",
                "window.location.search",
                "URLSearchParams",
                "#access_token",
                "#refresh_token",
                "access_token=",
                "refresh_token=");
        assertThat(indexHtml + appJs).doesNotContain("Create Project", "POST /api/projects");
    }

    @Test
    void githubAuthorizeFailureShowsGeneralizedUnavailableCopy() throws IOException {
        String page = Files.readString(STATIC_DASHBOARD.resolve("index.html"))
                + Files.readString(STATIC_DASHBOARD.resolve("app.js"));

        assertThat(page).contains("auth-status");
        assertThat(page).contains("GitHub 로그인을 시작할 수 없습니다. 잠시 후 다시 시도해 주세요.");
        assertThat(page).doesNotContain(
                "client_secret",
                "client-secret",
                "portal.auth.github.client-secret",
                "providerAccessToken",
                "gho_",
                "credential",
                "설정되지");
    }

    @Test
    void emptyProjectListShowsSafeStateWithoutCreateProjectFlow() throws IOException {
        String appJs = Files.readString(STATIC_DASHBOARD.resolve("app.js"));

        assertThat(appJs).contains("loadedProjects.length === 0");
        assertThat(appJs).contains("local/internal seed 또는 admin bootstrap decision이 필요합니다.");
        assertThat(appJs).doesNotContain(
                "Create Project",
                "POST /api/projects",
                "fetch('/api/projects', { method: 'POST'",
                "fetch(\"/api/projects\", { method: \"POST\"");
    }

    @Test
    void projectEntryUsesOnlyServerProjectResponseForVisibility() throws IOException {
        String appJs = Files.readString(STATIC_DASHBOARD.resolve("app.js"));

        assertThat(appJs).contains("loadedProjects = Array.isArray(data.projects) ? data.projects : []");
        assertThat(appJs).doesNotContain(
                "hardCodedProject",
                "previousProjectResponse",
                "window.location.hash",
                "window.location.search",
                "URLSearchParams",
                "document.body.dataset.project",
                "probeHiddenProject");
    }

    @Test
    void setupGuideCopyStaysOnStarterDependencyAndThreeExistingPropertiesOnly() throws IOException {
        String page = Files.readString(STATIC_DASHBOARD.resolve("index.html"));

        assertThat(page).contains("com.sst:observability-spring-boot-starter:0.1.0-SNAPSHOT");
        assertThat(page).contains("observation.heartbeat.portal-base-url");
        assertThat(page).contains("observation.heartbeat.project-key");
        assertThat(page).contains("observation.metric-flush.environment");
        assertThat(page).doesNotContain(
                "observation.metric-flush.project-id",
                "observation.metric-flush.application-name",
                "observation.metric-flush.instance",
                "queue",
                "drop policy",
                "heartbeat interval",
                "route allowlist",
                "dashboard tuning",
                "alert delivery",
                "raw query",
                "explorer",
                "p95",
                "p99",
                "endpoint priority");
    }

    @Test
    void staticUiDoesNotRecalculateEpicFiveReadModelJudgement() throws IOException {
        String appJs = Files.readString(STATIC_DASHBOARD.resolve("app.js"));
        List<String> forbiddenHelpers = List.of(
                "calculateState",
                "computeP95",
                "computeP99",
                "averageP95",
                "averageP99",
                "maxP95",
                "maxP99",
                "mergeP95",
                "mergeP99",
                "percentileFromHistogram",
                "histogramToPercentile",
                "rankEndpoint",
                "diagnoseConnection",
                "buildHistoryEvent",
                "createSnapshotEvent");

        assertThat(appJs).doesNotContain(forbiddenHelpers.toArray(String[]::new));
        assertThat(appJs).doesNotContain(
                "transitionTable",
                "diagnosis",
                "recompute");
        assertThat(appJs).contains("lifecycleBadge.source", "lifecycleBadge.code", "lifecycleBadge.label");
    }
}
