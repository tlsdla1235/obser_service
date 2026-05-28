package com.observation.portal.domain.dashboard;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;

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
        String projectMarkup = sliceFunction(appJs, "projectMarkup");

        assertThat(projectMarkup).contains("safeApplicationsLink(project)");
        assertThat(appJs).contains("isProjectApplicationsLink");
        assertThat(appJs).contains("api\\/projects");
        assertThat(appJs).contains("\\/applications");
        assertThat(appJs).contains("links.applications");
        assertThat(projectMarkup).contains("data-applications-link=\"${escapeAttribute(exposedApplicationsLink)}\"");
        assertThat(projectMarkup).contains("ready-application-list");
        assertThat(appJs).contains("return isProjectApplicationsLink(applicationsLink, project.projectId) ? applicationsLink : null;");
        assertThat(appJs).contains("decodeURIComponent(match[1]) === normalizedProjectId");
        assertThat(projectMarkup).doesNotContain(
                "<a class=\"link-button\"",
                "href=\"${escapeAttribute(applicationsLink)}\"",
                "location.href = applicationsLink",
                "encodeURIComponent(normalizedProjectId)",
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
    void projectSelectionRuntimeExecutesDomStateAndRequestRaceContracts() throws Exception {
        runNodeDashboardContract("""
                const fs = require('fs');
                const vm = require('vm');
                const assert = require('assert');

                const source = fs.readFileSync('src/main/resources/static/dashboard/app.js', 'utf8');
                const elements = new Map();
                const requests = [];

                function element(selector) {
                  if (!elements.has(selector)) {
                    elements.set(selector, {
                      selector,
                      innerHTML: '',
                      textContent: '',
                      disabled: false,
                      value: '',
                      dataset: { authUrl: '/api/auth/github/authorize' },
                      listeners: {},
                      addEventListener(type, listener) {
                        this.listeners[type] = listener;
                      }
                    });
                  }
                  return elements.get(selector);
                }

                const context = {
                  document: { querySelector: element },
                  window: {
                    location: {
                      assigned: null,
                      assign(url) {
                        this.assigned = url;
                      }
                    }
                  },
                  fetch(url, init = {}) {
                    return new Promise((resolve, reject) => requests.push({ url, init, resolve, reject }));
                  },
                  Intl,
                  Date,
                  Number,
                  String,
                  Array,
                  Object,
                  Error,
                  decodeURIComponent,
                  console
                };
                context.window.window = context.window;
                vm.runInNewContext(source, context);

                const auth = context.window.observationPortalAuth;
                const projectList = element('#project-list');
                const filterInput = element('#project-filter');
                const reloadButton = element('#reload-projects');

                function response(status, body) {
                  return {
                    status,
                    ok: status >= 200 && status < 300,
                    json: async () => body
                  };
                }

                async function settle() {
                  await new Promise(resolve => setImmediate(resolve));
                  await new Promise(resolve => setImmediate(resolve));
                }

                function project(id, name, applicationsLink, overrides = {}) {
                  return {
                    projectId: id,
                    name,
                    applicationCount: overrides.applicationCount ?? 1,
                    setupConnectionIssueCount: overrides.setupConnectionIssueCount ?? 0,
                    recentConcern: overrides.recentConcern ?? null,
                    links: { applications: applicationsLink }
                  };
                }

                (async () => {
                  assert.match(projectList.innerHTML, /GitHub 로그인 후 Project 목록을 볼 수 있습니다/);
                  assert.strictEqual(filterInput.disabled, true);
                  assert.strictEqual(requests.length, 0);

                  auth.setAccessToken('  token-a  ');
                  assert.strictEqual(requests.length, 1);
                  assert.strictEqual(requests[0].url, '/api/projects');
                  assert.strictEqual(requests[0].init.headers.Authorization, 'Bearer token-a');
                  assert.match(projectList.innerHTML, /Project 목록 로딩 중/);
                  assert.strictEqual(filterInput.disabled, true);
                  filterInput.value = 'anything';
                  filterInput.listeners.input();
                  assert.match(projectList.innerHTML, /Project 목록 로딩 중/);
                  filterInput.value = '';

                  requests.shift().resolve(response(200, {
                    generatedAt: '2026-05-28T01:00:00Z',
                    projects: [
                      project(
                        'project <1>',
                        '<img src=x onerror=alert(1)>',
                        '/api/projects/project%20%3C1%3E/applications',
                        {
                          applicationCount: '<b>2</b>',
                          setupConnectionIssueCount: 3,
                          recentConcern: { label: '<script>alert(1)</script>' }
                        }
                      )
                    ]
                  }));
                  await settle();
                  assert.strictEqual(filterInput.disabled, false);
                  assert.match(projectList.innerHTML, /&lt;img src=x onerror=alert\\(1\\)&gt;/);
                  assert.doesNotMatch(projectList.innerHTML, /<img src=x/);
                  assert.match(projectList.innerHTML, /&lt;script&gt;alert\\(1\\)&lt;\\/script&gt;/);
                  assert.match(projectList.innerHTML, /data-action-state="ready-application-list"/);
                  assert.match(projectList.innerHTML, /data-applications-link="\\/api\\/projects\\/project%20%3C1%3E\\/applications"/);
                  assert.doesNotMatch(projectList.innerHTML, /href=/);

                  filterInput.value = 'no-match';
                  filterInput.listeners.input();
                  assert.match(projectList.innerHTML, /표시할 Project가 없습니다/);
                  assert.strictEqual(filterInput.disabled, false);

                  auth.setAccessToken('token-b');
                  const invalidLinkRequest = requests.shift();
                  invalidLinkRequest.resolve(response(200, {
                    generatedAt: '2026-05-28T01:01:00Z',
                    projects: [project('project-1', 'Project One', '/api/projects/project-2/applications')]
                  }));
                  await settle();
                  assert.match(projectList.innerHTML, /data-action-state="missing-applications-link"/);
                  assert.match(projectList.innerHTML, /data-applications-link=""/);
                  assert.doesNotMatch(projectList.innerHTML, /href=/);

                  auth.setAccessToken('race-old');
                  const oldRequest = requests.shift();
                  auth.setAccessToken('race-new');
                  const latestRequest = requests.shift();
                  latestRequest.resolve(response(200, {
                    generatedAt: '2026-05-28T01:02:00Z',
                    projects: [project('new', 'New Project', '/api/projects/new/applications')]
                  }));
                  await settle();
                  assert.match(projectList.innerHTML, /New Project/);
                  oldRequest.resolve(response(200, {
                    generatedAt: '2026-05-28T01:03:00Z',
                    projects: [project('old', 'Old Project', '/api/projects/old/applications')]
                  }));
                  await settle();
                  assert.match(projectList.innerHTML, /New Project/);
                  assert.doesNotMatch(projectList.innerHTML, /Old Project/);

                  auth.setAccessToken('clear-me');
                  const clearRequest = requests.shift();
                  auth.clearAccessToken();
                  assert.match(projectList.innerHTML, /GitHub 로그인 후 Project 목록을 볼 수 있습니다/);
                  assert.strictEqual(filterInput.disabled, true);
                  clearRequest.resolve(response(200, {
                    generatedAt: '2026-05-28T01:04:00Z',
                    projects: [project('stale', 'Stale Project', '/api/projects/stale/applications')]
                  }));
                  await settle();
                  assert.match(projectList.innerHTML, /GitHub 로그인 후 Project 목록을 볼 수 있습니다/);
                  assert.doesNotMatch(projectList.innerHTML, /Stale Project/);

                  auth.setAccessToken('will-401');
                  const unauthorizedRequest = requests.shift();
                  unauthorizedRequest.resolve(response(401, {}));
                  await settle();
                  assert.match(projectList.innerHTML, /GitHub 로그인 후 Project 목록을 볼 수 있습니다/);
                  assert.strictEqual(filterInput.disabled, true);
                  const requestCountBeforeReload = requests.length;
                  reloadButton.listeners.click();
                  await settle();
                  assert.strictEqual(requests.length, requestCountBeforeReload);
                })().catch(error => {
                  console.error(error && error.stack ? error.stack : error);
                  process.exit(1);
                });
                """);
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

    private static String sliceFunction(String source, String functionName) {
        int start = source.indexOf("function " + functionName);
        assertThat(start).as("function %s should exist", functionName).isGreaterThanOrEqualTo(0);
        int nextFunction = source.indexOf("\nfunction ", start + 1);
        return nextFunction < 0 ? source.substring(start) : source.substring(start, nextFunction);
    }

    /**
     * static dashboard script를 실제 Node VM에서 실행해 문자열 탐색으로는 놓칠 수 있는 DOM 상태 전이를 검증한다.
     * Gradle 테스트 작업의 현재 디렉터리는 portal module root이므로 runtime asset을 그대로 읽어 실행한다.
     */
    private static void runNodeDashboardContract(String script) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("node", "-")
                .redirectErrorStream(true)
                .start();
        try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
            writer.write(script);
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        assertThat(finished)
                .as("Node contract script timed out. Output:%n%s", output)
                .isTrue();
        assertThat(process.exitValue())
                .as("Node contract script failed. Output:%n%s", output)
                .isZero();
    }
}
