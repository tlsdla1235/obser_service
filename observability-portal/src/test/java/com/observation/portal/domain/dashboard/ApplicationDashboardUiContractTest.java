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
 * Story 6.4 Application Dashboard static UI가 서버 read model을 재계산 없이 표시하는지 검증한다.
 * 테스트는 실제 dashboard script를 Node VM에서 실행해 authenticated fetch, safe state, stale guard를 고정한다.
 */
class ApplicationDashboardUiContractTest {

    private static final Path STATIC_DASHBOARD = Path.of("src/main/resources/static/dashboard");

    @Test
    void dashboardShellUsesStaticRuntimeAndCurrentReadModelFields() throws IOException {
        String indexHtml = Files.readString(STATIC_DASHBOARD.resolve("index.html"));
        String appJs = Files.readString(STATIC_DASHBOARD.resolve("app.js"));
        String styles = Files.readString(STATIC_DASHBOARD.resolve("styles.css"));

        assertThat(indexHtml).contains(
                "id=\"dashboard-detail\"",
                "id=\"selected-application-label\"",
                "id=\"dashboard-generated-at\"");
        assertThat(appJs).contains(
                "DASHBOARD_VIEW_STATE",
                "dashboardRequestSequence",
                "fetch(selectedDashboardContext.dashboardLink",
                "projectRequestHeaders()",
                "renderDashboardReady",
                "dashboard.generatedAt",
                "dashboard.application",
                "dashboard.state",
                "dashboard.starterConnection",
                "dashboard.zeroInsight",
                "dashboard.recovery",
                "dashboard.metrics",
                "dashboard.sourceScopedPercentiles",
                "dashboard.histogramDistribution",
                "dashboard.triageCards",
                "dashboard.endpointPriority",
                "dashboard.instances",
                "dashboard.snapshot");
        assertThat(sliceFunction(appJs, "selectApplicationDashboard")).contains(
                "dashboardLink",
                "applicationId",
                "isApplicationDashboardLink");
        assertThat(sliceFunction(appJs, "selectApplicationDashboard")).doesNotContain(
                "encodeURIComponent",
                "`/api/projects/",
                "window.location",
                "href=");
        assertThat(styles).contains(
                ".dashboard-detail",
                ".dashboard-strip",
                ".starter-connection-strip",
                "overflow-wrap: anywhere;");
    }

    @Test
    void dashboardRuntimeUsesDataDashboardLinkForAuthenticatedFetchAndSafeStates() throws Exception {
        runNodeDashboardContract("""
                const fs = require('fs');
                const vm = require('vm');
                const assert = require('assert');

                const source = fs.readFileSync('src/main/resources/static/dashboard/app.js', 'utf8');
                const harness = createHarness(source);
                const { auth, requests, elements, response, settle, project, application, dashboard,
                  clickApplications, clickDashboard } = harness;
                const projectList = elements.get('#project-list');
                const applicationList = elements.get('#application-list');
                const dashboardDetail = elements.get('#dashboard-detail');

                (async () => {
                  assert.match(dashboardDetail.innerHTML, /GitHub 로그인 후 Dashboard를 볼 수 있습니다/);

                  auth.setAccessToken('service-token');
                  requests.shift().resolve(response(200, {
                    generatedAt: '2026-05-28T01:00:00Z',
                    projects: [project('project-1', 'Project One', '/api/projects/project-1/applications')]
                  }));
                  await settle();
                  clickApplications('project-1', 'Project One', '/api/projects/project-1/applications');
                  requests.shift().resolve(response(200, {
                    generatedAt: '2026-05-28T01:02:00Z',
                    project: { projectId: 'project-1', name: 'Project One' },
                    applications: [application('app-1')]
                  }));
                  await settle();

                  assert.match(applicationList.innerHTML, /data-dashboard-link="\\/api\\/projects\\/project-1\\/applications\\/app-1\\/dashboard"/);
                  assert.doesNotMatch(applicationList.innerHTML, /href=/);
                  clickDashboard('app-1', 'Orders API', 'prod', '/api/projects/project-1/applications/app-1/dashboard');
                  assert.strictEqual(requests.length, 1);
                  assert.strictEqual(requests[0].url, '/api/projects/project-1/applications/app-1/dashboard');
                  assert.strictEqual(requests[0].init.headers.Authorization, 'Bearer service-token');
                  assert.match(dashboardDetail.innerHTML, /Dashboard를 불러오는 중/);
                  requests.shift().resolve(response(200, dashboard('project-1', 'app-1', { applicationName: 'Orders API' })));
                  await settle();

                  assert.match(dashboardDetail.innerHTML, /Orders API/);
                  assert.match(dashboardDetail.innerHTML, /Metric data active/);
                  assert.match(dashboardDetail.innerHTML, /starter_heartbeat/);
                  assert.match(dashboardDetail.innerHTML, /state impact/);
                  assert.match(dashboardDetail.innerHTML, /none/);
                  assert.match(dashboardDetail.innerHTML, /waiting_first_data/);
                  assert.match(dashboardDetail.innerHTML, /회복 관찰 중/);
                  assert.match(dashboardDetail.innerHTML, /자동 예약이 아니라 다음 판단 대기/);
                  assert.match(dashboardDetail.innerHTML, /이전 정상 시점 없음/);
                  assert.match(dashboardDetail.innerHTML, /request count/);
                  assert.match(dashboardDetail.innerHTML, /error rate/);
                  assert.match(dashboardDetail.innerHTML, /starter_local/);
                  assert.match(dashboardDetail.innerHTML, /no_average_no_max_no_merge_no_histogram_recalculation/);
                  assert.match(dashboardDetail.innerHTML, /p95Ms/);
                  assert.match(dashboardDetail.innerHTML, /p99Ms/);
                  assert.match(dashboardDetail.innerHTML, /histogram_bucket_distribution/);
                  assert.match(dashboardDetail.innerHTML, /먼저 확인할 endpoint/);
                  assert.match(dashboardDetail.innerHTML, /data-evidence-link="\\/api\\/projects\\/project-1\\/applications\\/app-1\\/instances\\/instance-1\\/evidence"/);
                  assert.match(dashboardDetail.innerHTML, /data-snapshot-handoff="available"/);
                  assert.match(dashboardDetail.innerHTML, /data-snapshot-id="snapshot-1"/);
                  assert.match(dashboardDetail.innerHTML, /data-snapshot-link="\\/api\\/projects\\/project-1\\/applications\\/app-1\\/dashboard\\/snapshots\\/snapshot-1"/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /복구 완료|문제 없음|host down|root cause|장애 순위|endpoint health score|rawPath|queryString|traceId|secret|service-token/);
                  assert.strictEqual(requests.length, 0);

                  clickDashboard('app-1', 'Orders API', 'prod', '/api/projects/project-1/applications/app-1/dashboard');
                  requests.shift().resolve(response(200, dashboard('project-1', 'other-app', { applicationName: 'Mismatched API' })));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /Dashboard를 불러오지 못했습니다/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /Mismatched API|other-app/);

                  clickDashboard('app-1', 'Orders API', 'prod', '/api/projects/project-1/applications/app-1/dashboard');
                  const missingZeroInsightDashboard = dashboard('project-1', 'app-1', { applicationName: 'Missing ZeroInsight API' });
                  delete missingZeroInsightDashboard.zeroInsight;
                  requests.shift().resolve(response(200, missingZeroInsightDashboard));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /Dashboard를 불러오지 못했습니다/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /Missing ZeroInsight API|reasonCode/);

                  clickDashboard('app-1', 'Orders API', 'prod', '/api/projects/project-1/applications/app-1/dashboard');
                  const malformedNestedDashboard = dashboard('project-1', 'app-1', { applicationName: 'Malformed Nested API' });
                  malformedNestedDashboard.instances = [null];
                  requests.shift().resolve(response(200, malformedNestedDashboard));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /Dashboard를 불러오지 못했습니다/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /Malformed Nested API/);

                  clickDashboard('app-1', 'Orders API', 'prod', '/api/projects/project-1/applications/app-1/dashboard');
                  const objectLinkSnapshotDashboard = dashboard('project-1', 'app-1', { applicationName: 'Object Link Snapshot API' });
                  objectLinkSnapshotDashboard.snapshot = { snapshotId: 'snapshot-2', links: { self: { href: '/api/projects/project-1/applications/app-1/dashboard/snapshots/snapshot-2' } } };
                  requests.shift().resolve(response(200, objectLinkSnapshotDashboard));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /Object Link Snapshot API/);
                  assert.match(dashboardDetail.innerHTML, /data-snapshot-id="snapshot-2"/);
                  assert.match(dashboardDetail.innerHTML, /data-snapshot-link="\\/api\\/projects\\/project-1\\/applications\\/app-1\\/dashboard\\/snapshots\\/snapshot-2"/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /\\[object Object\\]/);

                  clickDashboard('app-1', 'Orders API', 'prod', '/api/projects/project-1/applications/app-1/dashboard');
                  const malformedSnapshotDashboard = dashboard('project-1', 'app-1', { applicationName: 'Malformed Snapshot API' });
                  malformedSnapshotDashboard.snapshot = 'bad-snapshot';
                  requests.shift().resolve(response(200, malformedSnapshotDashboard));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /Dashboard를 불러오지 못했습니다/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /Malformed Snapshot API|bad-snapshot/);

                  clickDashboard('app-1', 'Orders API', 'prod', '/api/projects/project-1/applications/app-1/dashboard');
                  const malformedBucketDashboard = dashboard('project-1', 'app-1', { applicationName: 'Malformed Bucket API' });
                  malformedBucketDashboard.endpointPriority[0].evidence.durationBuckets = [{}];
                  requests.shift().resolve(response(200, malformedBucketDashboard));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /Malformed Bucket API/);
                  assert.match(dashboardDetail.innerHTML, /bucket evidence absence/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /undefined/);

                  clickDashboard('app-1', 'Orders API', 'prod', '/api/projects/project-1/applications/other-app/dashboard');
                  assert.strictEqual(requests.length, 0);
                  assert.match(dashboardDetail.innerHTML, /Dashboard link를 확인할 수 없습니다/);

                  clickDashboard('app-1', 'Orders API', 'prod', '/api/projects/project-1/applications/app-1/dashboard');
                  requests.shift().resolve(response(404, { detail: 'missing scope but not health' }));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /Project\\/Application scope를 찾을 수 없습니다/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /정상|장애|host down|healthy|unhealthy/);

                  clickDashboard('app-1', 'Orders API', 'prod', '/api/projects/project-1/applications/app-1/dashboard');
                  requests.shift().resolve(response(500, { detail: 'internal stack service-token provider payload' }));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /Dashboard를 불러오지 못했습니다/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /internal stack|service-token|provider payload/);

                  clickDashboard('app-1', 'Orders API', 'prod', '/api/projects/project-1/applications/app-1/dashboard');
                  requests.shift().resolve(response(401, {}));
                  await settle();
                  assert.match(projectList.innerHTML, /GitHub 로그인 후 Project 목록을 볼 수 있습니다/);
                  assert.match(dashboardDetail.innerHTML, /GitHub 로그인 후 Dashboard를 볼 수 있습니다/);

                  auth.setAccessToken('');
                  assert.match(dashboardDetail.innerHTML, /GitHub 로그인 후 Dashboard를 볼 수 있습니다/);
                  assert.strictEqual(requests.length, 0);
                })().catch(error => {
                  console.error(error && error.stack ? error.stack : error);
                  process.exit(1);
                });
                """);
    }

    @Test
    void dashboardRuntimePreventsStaleResponsesAcrossRequestTokenAndSelectionChanges() throws Exception {
        runNodeDashboardContract("""
                const fs = require('fs');
                const vm = require('vm');
                const assert = require('assert');

                const source = fs.readFileSync('src/main/resources/static/dashboard/app.js', 'utf8');
                const harness = createHarness(source);
                const { auth, requests, elements, response, settle, project, application, dashboard,
                  clickApplications, clickDashboard } = harness;
                const dashboardDetail = elements.get('#dashboard-detail');

                async function loadApplicationList(projectId, projectName, appId, appName) {
                  auth.setAccessToken('token-' + projectId);
                  requests.shift().resolve(response(200, {
                    generatedAt: '2026-05-28T01:00:00Z',
                    projects: [project(projectId, projectName, `/api/projects/${projectId}/applications`)]
                  }));
                  await settle();
                  clickApplications(projectId, projectName, `/api/projects/${projectId}/applications`);
                  requests.shift().resolve(response(200, {
                    generatedAt: '2026-05-28T01:02:00Z',
                    project: { projectId, name: projectName },
                    applications: [application(appId, { name: appName, projectId })]
                  }));
                  await settle();
                }

                (async () => {
                  await loadApplicationList('project-1', 'Project One', 'app-old', 'Old API');
                  clickDashboard('app-old', 'Old API', 'prod', '/api/projects/project-1/applications/app-old/dashboard');
                  const oldRequest = requests.shift();
                  clickDashboard('app-new', 'New API', 'prod', '/api/projects/project-1/applications/app-new/dashboard');
                  const newRequest = requests.shift();
                  newRequest.resolve(response(200, dashboard('project-1', 'app-new', { applicationName: 'New API' })));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /New API/);
                  oldRequest.resolve(response(200, dashboard('project-1', 'app-old', { applicationName: 'Old API' })));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /New API/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /Old API/);

                  clickDashboard('app-new', 'New API', 'prod', '/api/projects/project-1/applications/app-new/dashboard');
                  const clearRequest = requests.shift();
                  auth.clearAccessToken();
                  assert.match(dashboardDetail.innerHTML, /GitHub 로그인 후 Dashboard를 볼 수 있습니다/);
                  clearRequest.resolve(response(200, dashboard('project-1', 'app-new', { applicationName: 'Cleared API' })));
                  await settle();
                  assert.doesNotMatch(dashboardDetail.innerHTML, /Cleared API/);

                  await loadApplicationList('project-2', 'Project Two', 'app-two', 'Two API');
                  clickDashboard('app-two', 'Two API', 'prod', '/api/projects/project-2/applications/app-two/dashboard');
                  const projectTwoRequest = requests.shift();
                  clickApplications('project-3', 'Project Three', '/api/projects/project-3/applications');
                  projectTwoRequest.resolve(response(200, dashboard('project-2', 'app-two', { applicationName: 'Stale Project API' })));
                  await settle();
                  assert.doesNotMatch(dashboardDetail.innerHTML, /Stale Project API/);
                  assert.match(dashboardDetail.innerHTML, /Project를 선택하면 Application Dashboard를 볼 수 있습니다/);
                })().catch(error => {
                  console.error(error && error.stack ? error.stack : error);
                  process.exit(1);
                });
                """);
    }

    @Test
    void dashboardStaticGuardsForbidPersistenceRoutingFollowupFetchAndUiRecalculation() throws IOException {
        String page = Files.readString(STATIC_DASHBOARD.resolve("index.html"))
                + Files.readString(STATIC_DASHBOARD.resolve("app.js"))
                + Files.readString(STATIC_DASHBOARD.resolve("styles.css"));
        String appJs = Files.readString(STATIC_DASHBOARD.resolve("app.js"));
        List<String> forbiddenHelpers = List.of(
                "calculateLifecycle",
                "computeLifecycle",
                "calculateState",
                "diagnoseConnection",
                "computeP95",
                "computeP99",
                "calculateP95",
                "calculateP99",
                "rankEndpoint",
                "sortEndpointPriority",
                "buildHistoryEvent",
                "createSnapshotEvent",
                "deriveInstanceHealth");

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
        assertThat(appJs).doesNotContain(forbiddenHelpers.toArray(String[]::new));
        assertThat(appJs).doesNotContain(
                "fetch(instance",
                "fetch(snapshot",
                "fetch(history",
                "snapshot-markers",
                "dashboard/snapshots",
                "operational-events",
                "window.location.href",
                "location.href");
    }

    private static String sliceFunction(String source, String functionName) {
        int start = source.indexOf("function " + functionName);
        assertThat(start).as("function %s should exist", functionName).isGreaterThanOrEqualTo(0);
        int nextFunction = source.indexOf("\nfunction ", start + 1);
        return nextFunction < 0 ? source.substring(start) : source.substring(start, nextFunction);
    }

    /**
     * static dashboard script를 Node VM에서 실행한다. 실패 시 stdout/stderr를 JUnit assertion 메시지로 전달한다.
     */
    private static void runNodeDashboardContract(String script) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("node", "-")
                .redirectErrorStream(true)
                .start();
        try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
            writer.write(commonNodeHarness() + "\n" + script);
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

    /**
     * dashboard runtime test들이 공유하는 DOM/fetch fixture다. 실제 app.js만 바꿔 끼울 수 있게 문자열로 주입한다.
     */
    private static String commonNodeHarness() {
        return """
                function createHarness(source) {
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
                    Boolean,
                    decodeURIComponent,
                    console
                  };
                  context.window.window = context.window;
                  vm.runInNewContext(source, context);

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
                    const projectId = overrides.projectId ?? 'project-1';
                    return {
                      applicationId,
                      name: overrides.name ?? 'Orders API',
                      environment: overrides.environment ?? 'prod',
                      metricData: {
                        statusSource: 'accepted_bucket',
                        lastAcceptedBucketAt: '2026-05-28T01:10:00Z',
                        freshnessLabel: 'recent'
                      },
                      starterConnection: {
                        statusSource: 'starter_heartbeat',
                        lastHeartbeatAt: '2026-05-28T01:11:00Z',
                        heartbeatStatus: 'received',
                        freshnessLabel: 'recent',
                        connectionMeaning: 'starter_connected',
                        stateImpact: 'none'
                      },
                      lifecycleBadge: {
                        source: 'server_light_navigation_read_model',
                        code: 'unknown',
                        label: 'Metric data unknown'
                      },
                      topConcern: null,
                      links: {
                        dashboard: `/api/projects/${projectId}/applications/${applicationId}/dashboard`
                      }
                    };
                  }

                  function dashboard(projectId, applicationId, overrides = {}) {
                    return {
                      generatedAt: '2026-05-28T01:20:00Z',
                      application: {
                        projectId,
                        applicationId,
                        name: overrides.applicationName ?? 'Orders API',
                        environment: overrides.environment ?? 'prod',
                        lastAcceptedBucketAt: '2026-05-28T01:19:30Z',
                        lastHealthyAt: null,
                        sourceWindow: {
                          current: { startUtc: '2026-05-28T01:05:00Z', endUtc: '2026-05-28T01:20:00Z' },
                          baseline: { startUtc: '2026-05-28T00:50:00Z', endUtc: '2026-05-28T01:05:00Z' }
                        },
                        freshness: {
                          lastObservedAt: '2026-05-28T01:19:30Z',
                          staleAt: '2026-05-28T01:21:00Z',
                          downAt: '2026-05-28T01:22:30Z'
                        }
                      },
                      state: {
                        code: 'active',
                        label: 'Metric data active',
                        rationale: 'server-provided rationale',
                        recommendedAction: 'server-provided state action',
                        scope: 'application'
                      },
                      starterConnection: {
                        statusSource: 'starter_heartbeat',
                        lastHeartbeatAt: '2026-05-28T01:19:45Z',
                        lastHeartbeatStatus: 'received',
                        connectionMeaning: 'starter_connected',
                        stateImpact: 'none'
                      },
                      zeroInsight: {
                        reasonCode: 'waiting_first_data',
                        message: 'Starter connected; waiting for traffic.',
                        recommendedAction: 'Wait for accepted metric bucket.'
                      },
                      recovery: {
                        isRecovering: true,
                        lastHealthyAt: null,
                        retryAfterSeconds: 30,
                        recommendedAction: '다음 판단까지 약 30초 기다린 뒤 accepted bucket 수용과 sample 증가를 확인하세요.'
                      },
                      metrics: { requestCount: 42, errorCount: 2, errorRate: 0.047619 },
                      sourceScopedPercentiles: {
                        source: 'starter_local',
                        scope: 'instance_bucket',
                        displayPolicy: 'latest_starter_point_per_instance_in_current_window',
                        aggregatePolicy: 'no_average_no_max_no_merge_no_histogram_recalculation',
                        status: 'available',
                        reason: null,
                        items: [{
                          source: 'starter_local',
                          application: 'orders-api',
                          environment: 'prod',
                          instance: 'pod-a',
                          bucketStartUtc: '2026-05-28T01:19:00Z',
                          bucketEndUtc: '2026-05-28T01:19:30Z',
                          requestCount: 42,
                          p95Ms: 480,
                          p99Ms: 960
                        }]
                      },
                      histogramDistribution: {
                        source: 'histogram_bucket_distribution',
                        scope: 'application',
                        displayPolicy: 'bucket_distribution_evidence',
                        aggregatePolicy: 'sum_cumulative_counts_only_when_boundary_set_matches',
                        current: {
                          status: 'available',
                          reason: null,
                          totalCount: 42,
                          buckets: [{ leMs: 100, count: 20 }, { leMs: 500, count: 42 }]
                        },
                        baseline: {
                          status: 'missing',
                          reason: 'no_histogram_buckets_in_baseline_window',
                          totalCount: 0,
                          buckets: []
                        }
                      },
                      triageCards: [],
                      endpointPriority: [{
                        rank: 1,
                        method: 'POST',
                        route: '/orders',
                        endpointKey: 'POST /orders',
                        reason: 'error_and_latency',
                        ruleIds: ['endpoint_error_spike', 'endpoint_latency_spike'],
                        confidence: 0.84,
                        score: 84,
                        freshness: {
                          status: 'current',
                          lastObservedAt: '2026-05-28T01:19:30Z',
                          sourceWindow: 'current',
                          reason: null
                        },
                        evidence: {
                          requestCount: 120,
                          errorCount: 12,
                          errorRate: 0.1,
                          baselineRequestCount: 100,
                          baselineErrorCount: 1,
                          baselineErrorRate: 0.01,
                          durationBuckets: [{ leMs: 500, count: 70 }, { leMs: 1000, count: 120 }],
                          bucketDistributionSource: 'histogram_bucket_distribution',
                          errorEvidenceStatus: 'available',
                          latencyEvidenceStatus: 'available',
                          rawPath: '/orders/123',
                          queryString: 'token=secret',
                          traceId: 'trace-secret'
                        },
                        recommendedAction: '이 endpoint의 오류 로그와 외부 의존성 지연 가능성을 먼저 확인해보세요.'
                      }],
                      instances: [{
                        instanceId: 'instance-1',
                        instanceName: 'pod-a',
                        lastSeenAt: '2026-05-28T01:19:30Z',
                        links: { evidence: `/api/projects/${projectId}/applications/${applicationId}/instances/instance-1/evidence` }
                      }],
                      snapshot: { id: 'snapshot-1', link: `/api/projects/${projectId}/applications/${applicationId}/dashboard/snapshots/snapshot-1` }
                    };
                  }

                  function clickApplications(projectId, name, link) {
                    elements.get('#project-list').listeners.click({
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

                  function clickDashboard(applicationId, name, environment, link) {
                    elements.get('#application-list').listeners.click({
                      target: {
                        closest(selector) {
                          if (selector === '[data-dashboard-link]') {
                            return {
                              dataset: {
                                applicationId,
                                applicationName: name,
                                applicationEnvironment: environment,
                                dashboardLink: link
                              }
                            };
                          }
                          return null;
                        }
                      }
                    });
                  }

                  return {
                    auth: context.window.observationPortalAuth,
                    requests,
                    elements,
                    response,
                    settle,
                    project,
                    application,
                    dashboard,
                    clickApplications,
                    clickDashboard
                  };
                }
                """;
    }
}
