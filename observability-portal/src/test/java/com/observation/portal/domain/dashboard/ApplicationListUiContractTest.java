package com.observation.portal.domain.dashboard;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 6.3 Application List static UI가 Project handoff와 current API shape만 소비하는지 검증한다.
 * 테스트는 Node VM으로 실제 dashboard script를 실행해 인증 fetch, stale guard, safe state를 함께 고정한다.
 */
class ApplicationListUiContractTest {

    private static final Path STATIC_DASHBOARD = Path.of("src/main/resources/static/dashboard");

    @Test
    void applicationListShellUsesCurrentApplicationNavigationShape() throws IOException {
        String indexHtml = Files.readString(STATIC_DASHBOARD.resolve("index.html"));
        String appJs = Files.readString(STATIC_DASHBOARD.resolve("app.js"));
        String styles = Files.readString(STATIC_DASHBOARD.resolve("styles.css"));

        assertThat(indexHtml).contains(
                "id=\"application-list\"",
                "id=\"application-filter\"",
                "id=\"reload-applications\"",
                "id=\"applications-generated-at\"",
                "id=\"selected-project-label\"");
        assertThat(appJs).contains(
                "fetch(selectedProjectContext.applicationsLink",
                "projectRequestHeaders()",
                "data.project.projectId",
                "data.project.name",
                "application.applicationId",
                "application.name",
                "application.environment",
                "application.metricData",
                "metricData.statusSource",
                "metricData.lastAcceptedBucketAt",
                "metricData.freshnessLabel",
                "application.starterConnection",
                "starterConnection.statusSource",
                "starterConnection.lastHeartbeatAt",
                "starterConnection.heartbeatStatus",
                "starterConnection.freshnessLabel",
                "starterConnection.connectionMeaning",
                "starterConnection.stateImpact",
                "application.lifecycleBadge",
                "lifecycleBadge.source",
                "lifecycleBadge.code",
                "lifecycleBadge.label",
                "application.topConcern",
                "application.links.dashboard");
        assertThat(appJs).contains("Accepted bucket", "Starter connection", "server-computed light badge");
        assertThat(styles).contains(
                ".application-list",
                ".application-item",
                ".application-axis",
                "overflow-wrap: anywhere;");
    }

    @Test
    void applicationListRuntimeUsesProjectApplicationsLinkForAuthenticatedFetchAndSafeStates() throws Exception {
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
                const applicationList = element('#application-list');
                const applicationFilter = element('#application-filter');
                const reloadProjects = element('#reload-projects');
                const reloadApplications = element('#reload-applications');
                const selectedProjectLabel = element('#selected-project-label');

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

                function project(id, name, applicationsLink) {
                  return {
                    projectId: id,
                    name,
                    applicationCount: 1,
                    setupConnectionIssueCount: 0,
                    recentConcern: null,
                    links: { applications: applicationsLink }
                  };
                }

                function application(id, overrides = {}) {
                  const applicationId = overrides.applicationId ?? id;
                  return {
                    applicationId,
                    name: overrides.name ?? 'Orders API',
                    environment: overrides.environment ?? 'prod',
                    metricData: overrides.metricData ?? {
                      statusSource: 'accepted_bucket',
                      lastAcceptedBucketAt: '2026-05-28T01:10:00Z',
                      freshnessLabel: 'recent'
                    },
                    starterConnection: overrides.starterConnection ?? {
                      statusSource: 'starter_heartbeat',
                      lastHeartbeatAt: '2026-05-28T01:11:00Z',
                      heartbeatStatus: 'received',
                      freshnessLabel: 'recent',
                      connectionMeaning: 'starter_connected',
                      stateImpact: 'none'
                    },
                    lifecycleBadge: overrides.lifecycleBadge ?? {
                      source: 'server_light_navigation_read_model',
                      code: 'unknown',
                      label: 'Metric data unknown'
                    },
                    topConcern: overrides.topConcern ?? null,
                    links: {
                      dashboard: overrides.dashboardLink ?? `/api/projects/${overrides.projectId ?? 'project-1'}/applications/${applicationId}/dashboard`
                    }
                  };
                }

                function clickApplications(projectId, name, link) {
                  projectList.listeners.click({
                    target: {
                      closest(selector) {
                        if (selector === '[data-applications-link]') {
                          return { dataset: { projectId, projectName: name, applicationsLink: link } };
                        }
                        return null;
                      }
                    }
                  });
                }

                function decodeAttribute(value) {
                  return String(value ?? '')
                    .replace(/&quot;/g, '"')
                    .replace(/&#39;/g, "'")
                    .replace(/&lt;/g, '<')
                    .replace(/&gt;/g, '>')
                    .replace(/&amp;/g, '&');
                }

                function projectAttribute(name) {
                  const match = projectList.innerHTML.match(new RegExp(`${name}="([^"]*)"`));
                  return match ? decodeAttribute(match[1]) : '';
                }

                function clickRenderedApplications() {
                  clickApplications(
                    projectAttribute('data-project-id'),
                    projectAttribute('data-project-name'),
                    projectAttribute('data-applications-link')
                  );
                }

                (async () => {
                  assert.match(applicationList.innerHTML, /GitHub 로그인 후 Application 목록을 볼 수 있습니다/);
                  assert.strictEqual(applicationFilter.disabled, true);

                  auth.setAccessToken('service-token');
                  requests.shift().resolve(response(200, {
                    generatedAt: '2026-05-28T01:00:00Z',
                    projects: [project('project-1', 'Project One', '/api/projects/project-1/applications')]
                  }));
                  await settle();
                  assert.match(projectList.innerHTML, /data-applications-link="\\/api\\/projects\\/project-1\\/applications"/);
                  assert.doesNotMatch(projectList.innerHTML, /href=/);

                  clickRenderedApplications();
                  assert.strictEqual(requests.length, 1);
                  assert.strictEqual(requests[0].url, '/api/projects/project-1/applications');
                  assert.strictEqual(requests[0].init.headers.Authorization, 'Bearer service-token');
                  assert.match(applicationList.innerHTML, /Application 목록 로딩 중/);
                  requests.shift().resolve(response(200, {
                    generatedAt: '2026-05-28T01:12:00Z',
                    project: { projectId: 'project-1', name: 'Project One' },
                    applications: [application('app-1')]
                  }));
                  await settle();
                  assert.strictEqual(applicationFilter.disabled, false);
                  assert.match(selectedProjectLabel.textContent, /Project One/);
                  assert.match(applicationList.innerHTML, /Orders API/);
                  assert.match(applicationList.innerHTML, /app-1/);
                  assert.match(applicationList.innerHTML, /Accepted bucket/);
                  assert.match(applicationList.innerHTML, /accepted_bucket/);
                  assert.match(applicationList.innerHTML, /Starter connection/);
                  assert.match(applicationList.innerHTML, /starter_heartbeat/);
                  assert.match(applicationList.innerHTML, /Metric data unknown/);
                  assert.match(applicationList.innerHTML, /Concern source absence/);
                  assert.match(applicationList.innerHTML, /data-dashboard-link="\\/api\\/projects\\/project-1\\/applications\\/app-1\\/dashboard"/);
                  assert.doesNotMatch(applicationList.innerHTML, /정상|문제 없음|복구 완료/);

                  applicationFilter.value = 'no match';
                  applicationFilter.listeners.input();
                  assert.match(applicationList.innerHTML, /표시할 Application이 없습니다/);

                  reloadProjects.listeners.click();
                  requests.shift().resolve(response(200, {
                    generatedAt: '2026-05-28T01:12:03Z',
                    projects: []
                  }));
                  await settle();
                  assert.match(applicationList.innerHTML, /Project를 먼저 선택해 주세요/);
                  assert.doesNotMatch(applicationList.innerHTML, /Orders API|app-1/);
                  assert.strictEqual(reloadApplications.disabled, true);

                  clickApplications('project-1', 'Project One', 'https://example.invalid/api/projects/project-1/applications');
                  assert.strictEqual(requests.length, 0);
                  assert.match(applicationList.innerHTML, /Application List link를 확인할 수 없습니다/);

                  clickApplications('project-1', 'Project One', '/api/projects/project-1/applications');
                  requests.shift().resolve(response(200, {
                    generatedAt: '2026-05-28T01:12:05Z',
                    project: { projectId: 'project-1', name: 'Project One' },
                    applications: [application('app"quote', {
                      name: '<img src=x onerror=alert(1)>',
                      environment: 'prod"><script>alert(2)</script>',
                      metricData: {
                        statusSource: '<accepted_bucket>',
                        lastAcceptedBucketAt: '2026-05-28T01:10:00Z',
                        freshnessLabel: '<fresh>'
                      },
                      starterConnection: {
                        statusSource: '<starter_heartbeat>',
                        lastHeartbeatAt: '2026-05-28T01:11:00Z',
                        heartbeatStatus: '<received>',
                        freshnessLabel: '<recent>',
                        connectionMeaning: '<connected>',
                        stateImpact: '<none>'
                      },
                      lifecycleBadge: {
                        source: '<server_light_navigation_read_model>',
                        code: 'unknown"quote',
                        label: '<Metric data unknown>'
                      },
                      topConcern: {
                        source: '<source>',
                        code: 'concern"quote',
                        label: '<script>alert(3)</script>'
                      },
                      dashboardLink: '/api/projects/project-1/applications/app%22quote/dashboard'
                    })]
                  }));
                  await settle();
                  assert.match(applicationList.innerHTML, /&lt;img src=x onerror=alert\\(1\\)&gt;/);
                  assert.match(applicationList.innerHTML, /prod&quot;&gt;&lt;script&gt;alert\\(2\\)&lt;\\/script&gt;/);
                  assert.match(applicationList.innerHTML, /&lt;script&gt;alert\\(3\\)&lt;\\/script&gt;/);
                  assert.match(applicationList.innerHTML, /data-application-id="app&quot;quote"/);
                  assert.match(applicationList.innerHTML, /data-dashboard-link="\\/api\\/projects\\/project-1\\/applications\\/app%22quote\\/dashboard"/);
                  assert.doesNotMatch(applicationList.innerHTML, /<img src=x|<script>/);

                  clickApplications('project-1', 'Project One', '/api/projects/project-1/applications');
                  requests.shift().resolve(response(200, {
                    generatedAt: '2026-05-28T01:12:07Z',
                    project: { projectId: 'project-1', name: 'Project One' },
                    applications: null
                  }));
                  await settle();
                  assert.match(applicationList.innerHTML, /Application 목록을 불러오지 못했습니다/);
                  assert.doesNotMatch(applicationList.innerHTML, /catalog 또는 accepted bucket source가 아직 비어 있습니다/);

                  clickApplications('project-1', 'Project One', '/api/projects/project-1/applications');
                  requests.shift().resolve(response(200, {
                    generatedAt: '2026-05-28T01:12:07Z',
                    project: { projectId: 'project-1', name: 'Project One' },
                    applications: [application('valid-app', {
                      name: 'Invalid Dashboard Link API',
                      dashboardLink: '/api/projects/project-1/applications/other-app/dashboard'
                    })]
                  }));
                  await settle();
                  assert.match(applicationList.innerHTML, /Application 목록을 불러오지 못했습니다/);
                  assert.doesNotMatch(applicationList.innerHTML, /Invalid Dashboard Link API|other-app\\/dashboard/);

                  clickApplications('project-1', 'Project One', '/api/projects/project-1/applications');
                  requests.shift().resolve(response(200, {
                    generatedAt: '2026-05-28T01:12:08Z',
                    project: { projectId: 'project-1', name: 'Project One' },
                    applications: [application('', {
                      name: 'Malformed API',
                      dashboardLink: '/api/projects/project-2/applications/malformed/dashboard'
                    })]
                  }));
                  await settle();
                  assert.match(applicationList.innerHTML, /Application 목록을 불러오지 못했습니다/);
                  assert.doesNotMatch(applicationList.innerHTML, /Malformed API|project-2\\/applications\\/malformed\\/dashboard/);

                  clickApplications('project-1', 'Project One', '/api/projects/project-1/applications');
                  requests.shift().resolve(response(200, {
                    generatedAt: '2026-05-28T01:12:09Z',
                    project: { projectId: 'project-1', name: 'Project One' },
                    applications: [application('bad-badge', {
                      name: 'Malformed Badge API',
                      lifecycleBadge: { source: 'server_light_navigation_read_model', code: 'unknown' }
                    })]
                  }));
                  await settle();
                  assert.match(applicationList.innerHTML, /Application 목록을 불러오지 못했습니다/);
                  assert.doesNotMatch(applicationList.innerHTML, /Malformed Badge API|bad-badge/);

                  clickApplications('project-1', 'Project One', '/api/projects/project-1/applications');
                  requests.shift().resolve(response(200, {
                    generatedAt: '2026-05-28T01:12:09Z',
                    project: { projectId: 'project-1', name: 'Project One' },
                    applications: [application('bad-concern', {
                      name: 'Malformed Concern API',
                      topConcern: { code: 'missing-source', label: 'Missing source' }
                    })]
                  }));
                  await settle();
                  assert.match(applicationList.innerHTML, /Application 목록을 불러오지 못했습니다/);
                  assert.doesNotMatch(applicationList.innerHTML, /Malformed Concern API|bad-concern|Missing source/);

                  clickApplications('project-1', 'Project One', '/api/projects/project-1/applications');
                  requests.shift().resolve(response(200, {
                    generatedAt: '2026-05-28T01:12:10Z',
                    project: { projectId: 'project-2', name: 'Wrong Project' },
                    applications: [application('wrong-app', { name: 'Wrong API', projectId: 'project-2' })]
                  }));
                  await settle();
                  assert.match(applicationList.innerHTML, /Application 목록을 불러오지 못했습니다/);
                  assert.doesNotMatch(applicationList.innerHTML, /Wrong API|Wrong Project|wrong-app|project-2\\/applications\\/wrong-app\\/dashboard/);

                  clickApplications('error-project', 'Error Project', '/api/projects/error-project/applications');
                  requests.shift().resolve(response(500, { detail: 'internal stack provider token service-token' }));
                  await settle();
                  assert.match(applicationList.innerHTML, /Application 목록을 불러오지 못했습니다/);
                  assert.doesNotMatch(applicationList.innerHTML, /internal stack|provider token|service-token/);

                  clickApplications('auth-project', 'Auth Project', '/api/projects/auth-project/applications');
                  requests.shift().resolve(response(401, {}));
                  await settle();
                  assert.match(applicationList.innerHTML, /GitHub 로그인 후 Application 목록을 볼 수 있습니다/);
                  assert.match(element('#project-list').innerHTML, /GitHub 로그인 후 Project 목록을 볼 수 있습니다/);
                  auth.setAccessToken('service-token-2');
                  requests.shift().resolve(response(200, {
                    generatedAt: '2026-05-28T01:12:30Z',
                    projects: [project('missing-project', 'Missing Project', '/api/projects/missing-project/applications')]
                  }));
                  await settle();

                  clickApplications('missing-project', 'Missing Project', '/api/projects/missing-project/applications');
                  requests.shift().resolve(response(404, {}));
                  await settle();
                  assert.match(applicationList.innerHTML, /Project를 찾을 수 없습니다/);
                  assert.doesNotMatch(applicationList.innerHTML, /정상|장애|healthy|critical/);

                  clickApplications('old-project', 'Old Project', '/api/projects/old-project/applications');
                  const oldApplicationRequest = requests.shift();
                  clickApplications('new-project', 'New Project', '/api/projects/new-project/applications');
                  const newApplicationRequest = requests.shift();
                  newApplicationRequest.resolve(response(200, {
                    generatedAt: '2026-05-28T01:13:00Z',
                    project: { projectId: 'new-project', name: 'New Project' },
                    applications: [application('new-app', { name: 'New API', projectId: 'new-project' })]
                  }));
                  await settle();
                  assert.match(applicationList.innerHTML, /New API/);
                  oldApplicationRequest.resolve(response(200, {
                    generatedAt: '2026-05-28T01:14:00Z',
                    project: { projectId: 'old-project', name: 'Old Project' },
                    applications: [application('old-app', { name: 'Old API', projectId: 'old-project' })]
                  }));
                  await settle();
                  assert.match(applicationList.innerHTML, /New API/);
                  assert.doesNotMatch(applicationList.innerHTML, /Old API/);

                  clickApplications('clear-project', 'Clear Project', '/api/projects/clear-project/applications');
                  const clearRequest = requests.shift();
                  auth.clearAccessToken();
                  assert.match(applicationList.innerHTML, /GitHub 로그인 후 Application 목록을 볼 수 있습니다/);
                  assert.strictEqual(applicationFilter.disabled, true);
                  clearRequest.resolve(response(200, {
                    generatedAt: '2026-05-28T01:15:00Z',
                    project: { projectId: 'clear-project', name: 'Clear Project' },
                    applications: [application('stale-app', { name: 'Stale API', projectId: 'clear-project' })]
                  }));
                  await settle();
                  assert.doesNotMatch(applicationList.innerHTML, /Stale API/);

                  auth.setAccessToken('reload-token');
                  requests.shift().resolve(response(200, {
                    generatedAt: '2026-05-28T01:16:00Z',
                    projects: [project('reload-project', 'Reload Project', '/api/projects/reload-project/applications')]
                  }));
                  await settle();
                  clickApplications('reload-project', 'Reload Project', '/api/projects/reload-project/applications');
                  requests.shift().resolve(response(200, {
                    generatedAt: '2026-05-28T01:17:00Z',
                    project: { projectId: 'reload-project', name: 'Reload Project' },
                    applications: []
                  }));
                  await settle();
                  assert.match(applicationList.innerHTML, /catalog 또는 accepted bucket source가 아직 비어 있습니다/);
                  reloadApplications.listeners.click();
                  assert.strictEqual(requests[0].url, '/api/projects/reload-project/applications');
                })().catch(error => {
                  console.error(error && error.stack ? error.stack : error);
                  process.exit(1);
                });
                """);
    }

    @Test
    void dashboardHandoffStaysOnApplicationItemsOnly() throws IOException {
        String appJs = Files.readString(STATIC_DASHBOARD.resolve("app.js"));
        String projectMarkup = sliceFunction(appJs, "projectMarkup");
        String applicationMarkup = sliceFunction(appJs, "applicationMarkup");

        assertThat(projectMarkup).contains("data-applications-link");
        assertThat(projectMarkup).doesNotContain("links.dashboard", "data-dashboard-link", "/dashboard");
        assertThat(applicationMarkup).contains(
                "application.links.dashboard",
                "data-dashboard-link",
                "safeDashboardLink(application",
                "application.applicationId");
        assertThat(applicationMarkup).doesNotContain("fetch(dashboard", "location.href", "<a ");
    }

    @Test
    void applicationListDoesNotIntroduceTokenPersistenceFrontendStackOrUiSideReadModelComputation() throws IOException {
        String page = Files.readString(STATIC_DASHBOARD.resolve("index.html"))
                + Files.readString(STATIC_DASHBOARD.resolve("app.js"))
                + Files.readString(STATIC_DASHBOARD.resolve("styles.css"));
        List<String> forbiddenHelpers = List.of(
                "calculateLifecycle",
                "computeLifecycle",
                "calculateState",
                "diagnoseConnection",
                "computeP95",
                "computeP99",
                "rankEndpoint",
                "rankApplication",
                "zeroInsight",
                "recoveryRule",
                "buildHistoryEvent",
                "createSnapshotEvent");

        assertThat(Path.of("src/main/frontend")).doesNotExist();
        assertThat(Path.of("package.json")).doesNotExist();
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
                "#refresh_token",
                "access_token=",
                "refresh_token=");
        assertThat(page).doesNotContain(forbiddenHelpers.toArray(String[]::new));
        assertThat(page).doesNotContain(
                "applicationHealth",
                "hostHealth",
                "endpoint priority",
                "snapshot event",
                "p95",
                "p99");
    }

    private static String sliceFunction(String source, String functionName) {
        int start = source.indexOf("function " + functionName);
        assertThat(start).as("function %s should exist", functionName).isGreaterThanOrEqualTo(0);
        int nextFunction = source.indexOf("\nfunction ", start + 1);
        return nextFunction < 0 ? source.substring(start) : source.substring(start, nextFunction);
    }

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
