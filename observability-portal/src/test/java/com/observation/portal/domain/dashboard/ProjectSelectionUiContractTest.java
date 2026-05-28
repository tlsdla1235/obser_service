package com.observation.portal.domain.dashboard;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 6.2 static Project selection UI의 계약 경계를 고정한다.
 * 이 테스트는 Project 선택 화면이 API read model을 표시만 하고 후속 Application List/UI 인증 경계를 넘지 않는지 검증한다.
 */
class ProjectSelectionUiContractTest {

    private static final Path STATIC_DASHBOARD = Path.of("src/main/resources/static/dashboard");
    private static final Pattern APPLICATIONS_LINK_PATTERN =
            Pattern.compile("^/api/projects/([^/?#]+)/applications$");

    @Test
    void projectSelectionConsumesOnlyCurrentProjectNavigationFields() throws IOException {
        String appJs = Files.readString(STATIC_DASHBOARD.resolve("app.js"));

        assertThat(appJs).contains(
                "data.generatedAt",
                "project.projectId",
                "project.name",
                "project.applicationCount",
                "project.setupConnectionIssueCount",
                "project.recentConcern",
                "project.links.applications");
        assertThat(appJs).doesNotContain(
                "setupIssueCount",
                "recentConcernCount",
                "projectHealth",
                "project.status",
                "project.priority",
                "project.severity");
    }

    @Test
    void projectSelectionOffersReloadAndLocalNameFilterWithoutBackendQueryExpansion() throws IOException {
        String indexHtml = Files.readString(STATIC_DASHBOARD.resolve("index.html"));
        String appJs = Files.readString(STATIC_DASHBOARD.resolve("app.js"));

        assertThat(indexHtml).contains("id=\"reload-projects\"", "id=\"project-filter\"");
        assertThat(appJs).contains("reloadButton.addEventListener('click', loadProjects)");
        assertThat(appJs).contains("filterInput.addEventListener('input', handleFilterInput)");
        assertThat(appJs).contains("filterProjects");
        assertThat(appJs).contains("표시할 Project가 없습니다.");
        assertThat(appJs).containsOnlyOnce("fetch('/api/projects'");
        assertThat(appJs).doesNotContain(
                "sort(",
                "rank",
                "risk",
                "recentConcernSummary",
                "URLSearchParams");
    }

    @Test
    void projectSelectionUsesApplicationsLinkOnlyForPrimaryNavigation() throws IOException {
        String appJs = Files.readString(STATIC_DASHBOARD.resolve("app.js"));
        String page = Files.readString(STATIC_DASHBOARD.resolve("index.html")) + appJs;

        assertThat(appJs).contains("safeApplicationsLink(project)");
        assertThat(appJs).contains("isProjectApplicationsLink");
        assertThat(appJs).contains("api\\/projects");
        assertThat(appJs).contains("\\/applications");
        assertThat(appJs).contains("links.applications");
        assertThat(appJs).contains("data-applications-link=\"${escapeAttribute(applicationsLink ?? '')}\"");
        assertThat(appJs).contains("disabled aria-disabled=\"true\"");
        assertThat(appJs).contains("pending-application-list");
        assertThat(appJs).contains("return isProjectApplicationsLink(applicationsLink, project.projectId) ? applicationsLink : null;");
        assertThat(appJs).contains("decodeURIComponent(match[1]) === normalizedProjectId");
        assertThat(appJs).doesNotContain(
                "<a class=\"link-button\"",
                "href=\"${escapeAttribute(applicationsLink)}\"",
                "location.href = applicationsLink",
                "encodeURIComponent(normalizedProjectId)");
        assertThat(page).doesNotContain(
                "links.dashboard",
                "/dashboard",
                "dashboard shortcut",
                "auto-select",
                "first application",
                "applicationId");
    }

    @Test
    void filterInputCannotOverwriteLoadingAuthOrErrorStates() throws IOException {
        String indexHtml = Files.readString(STATIC_DASHBOARD.resolve("index.html"));
        String appJs = Files.readString(STATIC_DASHBOARD.resolve("app.js"));

        assertThat(indexHtml).contains("id=\"project-filter\"", "disabled");
        assertThat(appJs).contains(
                "const VIEW_STATE = Object.freeze",
                "LOADING: 'loading'",
                "AUTH_REQUIRED: 'auth-required'",
                "ERROR: 'error'",
                "filterInput.disabled = !canRenderFilteredProjects();",
                "function handleFilterInput()",
                "if (!canRenderFilteredProjects())",
                "filterInput.addEventListener('input', handleFilterInput)");
        assertThat(appJs).doesNotContain("filterInput.addEventListener('input', renderProjects)");
    }

    @Test
    void tokenClearAndOverlappingFetchesCannotRestoreStaleProjectState() throws IOException {
        String appJs = Files.readString(STATIC_DASHBOARD.resolve("app.js"));

        assertThat(appJs).contains(
                "let projectRequestSequence = 0;",
                "const requestId = ++projectRequestSequence;",
                "if (!serviceAccessToken)",
                "if (!isLatestProjectRequest(requestId))",
                "function isLatestProjectRequest(requestId)",
                "return requestId === projectRequestSequence;",
                "function clearProjectSnapshot({ resetFilter = false } = {})",
                "loadedProjects = [];",
                "loadedGeneratedAt = null;",
                "filterInput.value = '';",
                "clearAccessToken()",
                "serviceAccessToken = null;",
                "clearProjectSnapshot({ resetFilter: true });",
                "projectRequestSequence += 1;",
                "renderAuthorizationRequired();");
    }

    @Test
    void projectRequestsRequireInMemoryAccessTokenBeforeFetch() throws IOException {
        String appJs = Files.readString(STATIC_DASHBOARD.resolve("app.js"));

        assertThat(appJs).contains(
                "if (!serviceAccessToken) {\n"
                        + "    clearProjectSnapshot({ resetFilter: true });\n"
                        + "    renderAuthorizationRequired();\n"
                        + "    return;\n"
                        + "  }\n"
                        + "  renderLoadingState();",
                "githubButton.addEventListener('click', startGithubEntry);\n"
                        + "renderAuthorizationRequired();");
        assertThat(appJs).doesNotContain("githubButton.addEventListener('click', startGithubEntry);\nloadProjects();");
    }

    @Test
    void applicationsLinkGuardRejectsExternalMismatchedAndMalformedLinks() throws IOException {
        String appJs = Files.readString(STATIC_DASHBOARD.resolve("app.js"));

        assertThat(appJs).contains(
                "match(/^\\/api\\/projects\\/([^/?#]+)\\/applications$/)",
                "decodeURIComponent(match[1]) === normalizedProjectId");
        assertThat(acceptsApplicationsLinkLikeStaticUi("/api/projects/project-1/applications", "project-1"))
                .isTrue();
        assertThat(acceptsApplicationsLinkLikeStaticUi("/api/projects/project%201/applications", "project 1"))
                .isTrue();
        assertThat(acceptsApplicationsLinkLikeStaticUi(
                "https://example.invalid/api/projects/project-1/applications",
                "project-1"))
                .isFalse();
        assertThat(acceptsApplicationsLinkLikeStaticUi("/api/projects/project-1/applications?next=/dashboard", "project-1"))
                .isFalse();
        assertThat(acceptsApplicationsLinkLikeStaticUi("/api/projects/project-1/applications#token", "project-1"))
                .isFalse();
        assertThat(acceptsApplicationsLinkLikeStaticUi("/api/projects/project-2/applications", "project-1"))
                .isFalse();
        assertThat(acceptsApplicationsLinkLikeStaticUi("/api/projects/%E0%A4%A/applications", "project-1"))
                .isFalse();
        assertThat(acceptsApplicationsLinkLikeStaticUi("/api/projects/project-1/applications/app-a", "project-1"))
                .isFalse();
        assertThat(acceptsApplicationsLinkLikeStaticUi("/api/projects/project-1/applications", " "))
                .isFalse();
    }

    @Test
    void projectSelectionKeepsSafeStateCopyAndHealthJudgementOutOfUi() throws IOException {
        String page = Files.readString(STATIC_DASHBOARD.resolve("index.html"))
                + Files.readString(STATIC_DASHBOARD.resolve("app.js"))
                + Files.readString(STATIC_DASHBOARD.resolve("styles.css"));

        assertThat(page).contains(
                "GitHub 로그인 후 Project 목록을 볼 수 있습니다.",
                "local/internal seed 또는 admin bootstrap decision이 필요합니다.",
                "Project 목록을 불러오지 못했습니다.",
                "Connection/setup candidates",
                "최근 concern 없음");
        assertThat(page).doesNotContain(
                "정상",
                "문제 없음",
                "앱 다운",
                "장애",
                "복구 완료",
                "healthy",
                "unhealthy",
                "degraded",
                "critical",
                "Project health");
    }

    @Test
    void projectSelectionKeepsStaticDashboardBoundaryAndAuthTokenNonPersistence() throws IOException {
        String page = Files.readString(STATIC_DASHBOARD.resolve("index.html"))
                + Files.readString(STATIC_DASHBOARD.resolve("app.js"));

        assertThat(Path.of("src/main/frontend")).doesNotExist();
        assertThat(Path.of("package.json")).doesNotExist();
        assertThat(page).contains("observationPortalAuth", "Authorization", "Bearer");
        assertThat(page).doesNotContain(
                "React",
                "Vite",
                "TypeScript",
                "localStorage",
                "sessionStorage",
                "document.cookie",
                "window.location.hash",
                "window.location.search",
                "#access_token",
                "access_token=",
                "refresh_token=");
    }

    @Test
    void setupGuideRemainsLimitedToStorySixOneStarterProperties() throws IOException {
        String indexHtml = Files.readString(STATIC_DASHBOARD.resolve("index.html"));

        assertThat(indexHtml).contains(
                "com.sst:observability-spring-boot-starter:0.1.0-SNAPSHOT",
                "observation.heartbeat.portal-base-url",
                "observation.heartbeat.project-key",
                "observation.metric-flush.environment");
        assertThat(indexHtml).doesNotContain(
                "project-id",
                "application-name",
                "instance",
                "heartbeat interval",
                "route allowlist",
                "dashboard tuning",
                "raw explorer",
                "p95",
                "p99",
                "endpoint priority");
    }

    @Test
    void badgeStyleConstrainsLongRecentConcernAndCountText() throws IOException {
        String styles = Files.readString(STATIC_DASHBOARD.resolve("styles.css"));

        assertThat(styles).contains(
                ".badge",
                "max-width: 100%;",
                "min-width: 0;",
                "white-space: normal;",
                "overflow-wrap: anywhere;",
                "word-break: break-word;");
    }

    /**
     * static UI의 `isProjectApplicationsLink` 의도를 Java 쪽에서 같은 입력군으로 검증한다.
     * JS 실행 환경을 테스트에 추가하지 않고도 외부 URL, query/hash, malformed encoding을 계약 사례로 고정한다.
     */
    private static boolean acceptsApplicationsLinkLikeStaticUi(String applicationsLink, String projectId) {
        String normalizedProjectId = String.valueOf(projectId).trim();
        if (normalizedProjectId.isEmpty()) {
            return false;
        }
        Matcher matcher = APPLICATIONS_LINK_PATTERN.matcher(String.valueOf(applicationsLink));
        if (!matcher.matches()) {
            return false;
        }
        try {
            return decodeURIComponentLike(matcher.group(1)).equals(normalizedProjectId);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String decodeURIComponentLike(String value) {
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }
}
