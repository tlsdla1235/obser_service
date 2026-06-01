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
    void productIntroExplainsStarterFirstOnboardingFlowWithoutMarketingOverreach() throws IOException {
        String indexHtml = Files.readString(STATIC_DASHBOARD.resolve("index.html"));

        assertThat(indexHtml).contains(
                "Spring Boot 앱에 starter를 붙이면 project, application, instance 단위로 수집 연결과 운영 상태를 확인합니다.",
                "starter-first observability dashboard",
                "Starter 연결",
                "Project 등록",
                "Dashboard 확인",
                "Application Dashboard가 primary first-screen",
                "Project Entry는 운영 판단 화면이 아니라 scope 선택 화면");
        assertThat(indexHtml).doesNotContain(
                "APM",
                "alerting",
                "trace/log",
                "full monitoring",
                "host application 정상",
                "앱 정상 확정",
                "복구 완료");
    }

    @Test
    void onboardingEntryKeepsGithubOAuthAndBearerResourceBoundaryVisible() throws IOException {
        String indexHtml = Files.readString(STATIC_DASHBOARD.resolve("index.html"));

        assertThat(indexHtml).contains(
                "GitHub OAuth only",
                "service access token",
                "JSON 응답",
                "Authorization: Bearer",
                "브라우저 저장소나 URL token 전달을 요구하지 않습니다");
        assertThat(indexHtml).doesNotContain(
                "email/password",
                "magic link",
                "Google",
                "anonymous",
                "cookie server session");
    }

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
        assertThat(indexHtml + appJs).doesNotContain("Create Project");
    }

    @Test
    void projectEntrySupportsRegistrationOneTimeDisplayAndCredentialLifecycleWithoutSeedShortcut() throws IOException {
        String page = Files.readString(STATIC_DASHBOARD.resolve("index.html"))
                + Files.readString(STATIC_DASHBOARD.resolve("app.js"));

        assertThat(page).contains(
                "id=\"project-registration-form\"",
                "fetch('/api/projects'",
                "method: 'POST'",
                "renderOneTimeCredentialSuccess",
                "copyStarterCredential",
                "복사했음",
                "credential은 생성/회전 성공 직후에만 표시됩니다",
                "starterCredentialActionPath",
                "starter-credential",
                "/rotations",
                "/revocations",
                "loadProjects();");
        assertThat(page).doesNotContain(
                "Create Project",
                "fetch(\"/api/projects\", { method: \"POST\"",
                "seed",
                "starter key 발급",
                "service token 예시");
    }

    @Test
    void oneTimeCredentialUiAvoidsBrowserPersistenceAndLongLivedRawAttributes() throws IOException {
        String page = Files.readString(STATIC_DASHBOARD.resolve("index.html"))
                + Files.readString(STATIC_DASHBOARD.resolve("app.js"));

        assertThat(page).contains(
                "navigator.clipboard.writeText(displayValue)",
                "clearOneTimeCredentialDisplay",
                "starterCredential.displayValue",
                "credential-display");
        assertThat(page).doesNotContain(
                "localStorage",
                "sessionStorage",
                "document.cookie",
                "window.location.hash",
                "window.location.search",
                "URLSearchParams",
                "type=\"hidden\"",
                "data-starter-credential",
                "data-credential-value",
                "aria-label=\"${escapeAttribute(displayValue)}\"",
                "title=\"${escapeAttribute(displayValue)}\"",
                "console.log(displayValue)");
    }

    @Test
    void githubAuthorizeFailureShowsGeneralizedUnavailableCopy() throws IOException {
        String page = Files.readString(STATIC_DASHBOARD.resolve("index.html"))
                + Files.readString(STATIC_DASHBOARD.resolve("app.js"));

        assertThat(page).contains("auth-status");
        assertThat(page).contains("GitHub 로그인을 시작할 수 없습니다. 잠시 후 다시 시도해 주세요.");
        String githubEntrySource = sliceFunction(page, "startGithubEntry");
        assertThat(githubEntrySource).doesNotContain(
                "client_secret",
                "client-secret",
                "portal.auth.github.client-secret",
                "providerAccessToken",
                "gho_",
                "설정되지");
    }

    @Test
    void emptyProjectListShowsSafeStateWithoutCreateProjectFlow() throws IOException {
        String appJs = Files.readString(STATIC_DASHBOARD.resolve("app.js"));

        assertThat(appJs).contains("loadedProjects.length === 0");
        assertThat(appJs).contains("아래 등록 폼으로 새 project를 만든 뒤 서버가 준 membership project 목록을 다시 불러옵니다.");
        assertThat(appJs).doesNotContain(
                "Create Project",
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
        assertThat(page).contains("X-OBS-Project-Key-placeholder");
        assertThat(page).contains("Authorization: Bearer token과 분리된 starter ingest credential");
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

    private static String sliceFunction(String source, String functionName) {
        int start = source.indexOf("function " + functionName + "(");
        assertThat(start).as("function start: %s", functionName).isGreaterThanOrEqualTo(0);
        int brace = source.indexOf('{', start);
        int depth = 0;
        for (int index = brace; index < source.length(); index += 1) {
            char character = source.charAt(index);
            if (character == '{') {
                depth += 1;
            } else if (character == '}') {
                depth -= 1;
                if (depth == 0) {
                    return source.substring(start, index + 1);
                }
            }
        }
        throw new AssertionError("function not closed: " + functionName);
    }
}
