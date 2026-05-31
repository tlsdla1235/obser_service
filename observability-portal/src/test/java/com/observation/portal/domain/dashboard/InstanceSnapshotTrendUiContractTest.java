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
 * Story 6.6 Instance Snapshot Trend static UI가 Evidence links.snapshotTrend만으로 stored trend를 표시하는지 검증한다.
 * 테스트는 실제 dashboard script를 Node VM에서 실행해 authenticated fetch, horizon preset, safe state, stale guard를 고정한다.
 */
class InstanceSnapshotTrendUiContractTest {

    private static final Path STATIC_DASHBOARD = Path.of("src/main/resources/static/dashboard");

    @Test
    void snapshotTrendShellUsesEvidenceHandoffAndStoredReadModelFields() throws IOException {
        String indexHtml = Files.readString(STATIC_DASHBOARD.resolve("index.html"));
        String appJs = Files.readString(STATIC_DASHBOARD.resolve("app.js"));
        String styles = Files.readString(STATIC_DASHBOARD.resolve("styles.css"));

        assertThat(indexHtml).contains("id=\"dashboard-detail\"");
        assertThat(appJs).contains(
                "INSTANCE_SNAPSHOT_TREND_VIEW_STATE",
                "instanceSnapshotTrendRequestSequence",
                "selectedInstanceSnapshotTrendContext",
                "fetch(snapshotTrendRequestLink(",
                "isInstanceSnapshotTrendLink",
                "renderInstanceSnapshotTrendReady",
                "trend.generatedAt",
                "trend.application",
                "trend.instance",
                "trend.source",
                "trend.horizon",
                "trend.points");
        assertThat(sliceFunction(appJs, "snapshotTrendPendingHandoffMarkup")).contains(
                "linkBlock.snapshotTrend",
                "data-snapshot-trend-link",
                "Snapshot trend");
        assertThat(sliceFunction(appJs, "selectInstanceSnapshotTrend")).contains(
                "snapshotTrendLink",
                "isInstanceSnapshotTrendLink");
        assertThat(sliceFunction(appJs, "selectInstanceSnapshotTrend")).doesNotContain(
                "encodeURIComponent",
                "`/api/projects/",
                "window.location",
                "href=");
        assertThat(sliceFunction(appJs, "snapshotTrendRequestLink")).contains(
                "since=7d&limit=168",
                "since=14d&limit=336");
        assertThat(sliceFunction(appJs, "snapshotTrendRequestLink")).doesNotContain("since=24h");
        assertThat(styles).contains(
                ".instance-snapshot-trend-detail",
                ".trend-horizon-controls",
                ".snapshot-trend-lanes");
    }

    @Test
    void snapshotTrendRuntimeFetchesAuthenticatedHandoffAndRendersStoredLanes() throws Exception {
        runNodeDashboardContract("""
                const fs = require('fs');
                const vm = require('vm');
                const assert = require('assert');

                const source = fs.readFileSync('src/main/resources/static/dashboard/app.js', 'utf8');
                const harness = createHarness(source);
                        const { auth, requests, elements, response, settle, project, application, dashboard, evidence, trend, detail,
                          clickApplications, clickDashboard, clickEvidence, clickSnapshotTrend, clickTrendHorizon,
                          clickTrendDetail, clickDashboardBack, clickEvidenceBack } = harness;
                const dashboardDetail = elements.get('#dashboard-detail');

                async function loadEvidence() {
                  auth.setAccessToken('service-token');
                  requests.shift().resolve(response(200, {
                    generatedAt: '2026-05-31T01:00:00Z',
                    projects: [project('project-1', 'Project One', '/api/projects/project-1/applications')]
                  }));
                  await settle();
                  clickApplications('project-1', 'Project One', '/api/projects/project-1/applications');
                  requests.shift().resolve(response(200, {
                    generatedAt: '2026-05-31T01:02:00Z',
                    project: { projectId: 'project-1', name: 'Project One' },
                    applications: [application('app-1')]
                  }));
                  await settle();
                  clickDashboard('app-1', 'Orders API', 'prod', '/api/projects/project-1/applications/app-1/dashboard');
                  requests.shift().resolve(response(200, dashboard('project-1', 'app-1', { applicationName: 'Orders API' })));
                  await settle();
                  clickEvidence('instance-1', 'pod-a', '/api/projects/project-1/applications/app-1/instances/instance-1/evidence');
                  requests.shift().resolve(response(200, evidence('project-1', 'app-1', 'instance-1')));
                  await settle();
                }

                (async () => {
                  await loadEvidence();
                  assert.match(dashboardDetail.innerHTML, /Snapshot trend/);
                  assert.match(dashboardDetail.innerHTML, /data-snapshot-trend-link="\\/api\\/projects\\/project-1\\/applications\\/app-1\\/instances\\/instance-1\\/snapshot-trend"/);

                  clickSnapshotTrend('/api/projects/project-1/applications/app-1/instances/instance-1/snapshot-trend');
                  assert.strictEqual(requests.length, 1);
                  assert.strictEqual(requests[0].url, '/api/projects/project-1/applications/app-1/instances/instance-1/snapshot-trend?since=7d&limit=168');
                  assert.strictEqual(requests[0].init.headers.Authorization, 'Bearer service-token');
                  assert.match(dashboardDetail.innerHTML, /Instance Snapshot Trend를 불러오는 중/);
                  requests.shift().resolve(response(200, trend('project-1', 'app-1', 'instance-1')));
                  await settle();

                  assert.match(dashboardDetail.innerHTML, /Instance Snapshot Trend/);
                  assert.match(dashboardDetail.innerHTML, /Application Dashboard/);
                  assert.match(dashboardDetail.innerHTML, /Instance Evidence/);
                  assert.match(dashboardDetail.innerHTML, /generatedAt/);
                  assert.match(dashboardDetail.innerHTML, /dashboard_snapshots\\.read_model_json\\.instanceSummary\\.items/);
                  assert.match(dashboardDetail.innerHTML, /requestedSince/);
                  assert.match(dashboardDetail.innerHTML, /7d/);
                  assert.match(dashboardDetail.innerHTML, /defaultSince/);
                  assert.match(dashboardDetail.innerHTML, /maxSince/);
                  assert.match(dashboardDetail.innerHTML, /maxLimit/);
                  assert.match(dashboardDetail.innerHTML, /capturedAt_asc/);
                  assert.match(dashboardDetail.innerHTML, /24h preset pending/);
                  assert.match(dashboardDetail.innerHTML, /Stored application state copy lane/);
                  assert.match(dashboardDetail.innerHTML, /storedApplicationStateCode/);
                  assert.match(dashboardDetail.innerHTML, /Metric data axis lane/);
                  assert.match(dashboardDetail.innerHTML, /accepted_bucket/);
                  assert.match(dashboardDetail.innerHTML, /Starter connection axis lane/);
                  assert.match(dashboardDetail.innerHTML, /starter_heartbeat/);
                  assert.match(dashboardDetail.innerHTML, /stateImpact/);
                  assert.match(dashboardDetail.innerHTML, /none/);
                  assert.match(dashboardDetail.innerHTML, /Capture reason\\/source marker lane/);
                  assert.match(dashboardDetail.innerHTML, /opaque metadata/);
                  assert.match(dashboardDetail.innerHTML, /starter_canonical_percentile/);
                  assert.match(dashboardDetail.innerHTML, /accepted_bucket_latest_sample/);
                  assert.match(dashboardDetail.innerHTML, /applicationTriageContribution/);
                  assert.match(dashboardDetail.innerHTML, /저장된 application triage contribution 없음/);
                  assert.match(dashboardDetail.innerHTML, /endpointEvidenceRefs/);
                          assert.match(dashboardDetail.innerHTML, /endpoint-evidence-1/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /service-token|provider payload|rawSnapshotJson|raw bucket|instanceHealth|hostStatus|connectedAndHealthy|root cause|recovery marker|endpoint p95|endpoint p99|복구 완료/);
                  assert.strictEqual(requests.length, 0);

                  clickTrendHorizon('24h');
                  assert.strictEqual(requests.length, 0);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /since=24h/);

                  clickTrendHorizon('14d');
                  assert.strictEqual(requests.length, 1);
                  assert.strictEqual(requests[0].url, '/api/projects/project-1/applications/app-1/instances/instance-1/snapshot-trend?since=14d&limit=336');
                  requests.shift().resolve(response(200, trend('project-1', 'app-1', 'instance-1', { requestedSince: '14d', limit: 336 })));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /14d/);

                  clickTrendHorizon('7d');
                  requests.shift().resolve(response(200, trend('project-1', 'app-1', 'instance-1', { empty: true })));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /points source absence/);
                  assert.match(dashboardDetail.innerHTML, /retention gap/);
                  assert.match(dashboardDetail.innerHTML, /missing hourly snapshot은 보간하지 않습니다/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /복구 완료|정상입니다/);

                  clickEvidenceBack();
                  assert.match(dashboardDetail.innerHTML, /Instance Evidence/);
                  assert.match(dashboardDetail.innerHTML, /Metric data axis/);
                  assert.strictEqual(requests.length, 0);

                  clickSnapshotTrend('/api/projects/project-1/applications/app-1/instances/instance-1/snapshot-trend');
                  requests.shift().resolve(response(200, trend('project-1', 'app-1', 'instance-1')));
                  await settle();
                  clickTrendDetail('018f6b9a-2e1a-7d2b-9b2f-4db69d92c241', 'endpoint-evidence-1');
                  assert.strictEqual(requests.length, 1);
                  assert.strictEqual(requests[0].url, '/api/projects/project-1/applications/app-1/dashboard/snapshots/018f6b9a-2e1a-7d2b-9b2f-4db69d92c241');
                  assert.strictEqual(requests[0].init.headers.Authorization, 'Bearer service-token');
                  requests.shift().resolve(response(200, detail('project-1', 'app-1', '018f6b9a-2e1a-7d2b-9b2f-4db69d92c241')));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /Snapshot Detail/);
                  assert.match(dashboardDetail.innerHTML, /data-active-anchor="true"/);
                  clickDashboardBack();
                  assert.match(dashboardDetail.innerHTML, /Orders API/);
                  assert.match(dashboardDetail.innerHTML, /Metric data active/);
                  assert.strictEqual(requests.length, 0);
                })().catch(error => {
                  console.error(error && error.stack ? error.stack : error);
                  process.exit(1);
                });
                """);
    }

    @Test
    void snapshotTrendRuntimeRendersSafeStatesAndRejectsMalformedStaleOrMismatchedResponses() throws Exception {
        runNodeDashboardContract("""
                const fs = require('fs');
                const vm = require('vm');
                const assert = require('assert');

                const source = fs.readFileSync('src/main/resources/static/dashboard/app.js', 'utf8');
                const harness = createHarness(source);
                const { auth, requests, elements, response, settle, project, application, dashboard, evidence, trend,
                  clickApplications, clickDashboard, clickEvidence, clickSnapshotTrend } = harness;
                const dashboardDetail = elements.get('#dashboard-detail');
                const reloadProjects = elements.get('#reload-projects');
                const reloadApplications = elements.get('#reload-applications');

                async function loadEvidence(projectId = 'project-1', appId = 'app-1', instanceId = 'instance-1') {
                  auth.setAccessToken('token-' + projectId);
                  requests.shift().resolve(response(200, {
                    generatedAt: '2026-05-31T02:00:00Z',
                    projects: [project(projectId, 'Project ' + projectId, `/api/projects/${projectId}/applications`)]
                  }));
                  await settle();
                  clickApplications(projectId, 'Project ' + projectId, `/api/projects/${projectId}/applications`);
                  requests.shift().resolve(response(200, {
                    generatedAt: '2026-05-31T02:02:00Z',
                    project: { projectId, name: 'Project ' + projectId },
                    applications: [application(appId, { projectId, name: 'Orders API' })]
                  }));
                  await settle();
                  clickDashboard(appId, 'Orders API', 'prod', `/api/projects/${projectId}/applications/${appId}/dashboard`);
                  requests.shift().resolve(response(200, dashboard(projectId, appId, { applicationName: 'Orders API', instanceId })));
                  await settle();
                  clickEvidence(instanceId, 'pod-a', `/api/projects/${projectId}/applications/${appId}/instances/${instanceId}/evidence`);
                  requests.shift().resolve(response(200, evidence(projectId, appId, instanceId)));
                  await settle();
                }

                (async () => {
                  await loadEvidence();

                  clickSnapshotTrend('/api/projects/project-1/applications/other-app/instances/instance-1/snapshot-trend');
                  assert.strictEqual(requests.length, 0);
                  assert.match(dashboardDetail.innerHTML, /Snapshot Trend link를 확인할 수 없습니다/);

                  auth.clearAccessToken();
                  clickSnapshotTrend('/api/projects/project-1/applications/app-1/instances/instance-1/snapshot-trend');
                  assert.strictEqual(requests.length, 0);
                  assert.match(dashboardDetail.innerHTML, /GitHub 로그인 후 Instance Snapshot Trend를 볼 수 있습니다/);

                  await loadEvidence();
                  clickSnapshotTrend('/api/projects/project-1/applications/app-1/instances/instance-1/snapshot-trend');
                  requests.shift().resolve(response(401, { detail: 'service-token provider payload' }));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /GitHub 로그인 후 Instance Snapshot Trend를 볼 수 있습니다/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /service-token|provider payload/);

                  await loadEvidence();
                  clickSnapshotTrend('/api/projects/project-1/applications/app-1/instances/instance-1/snapshot-trend');
                  requests.shift().resolve(response(400, { detail: 'since=24h is invalid service-token' }));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /Snapshot Trend query contract를 확인할 수 없습니다/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /since=24h|service-token/);

                  clickSnapshotTrend('/api/projects/project-1/applications/app-1/instances/instance-1/snapshot-trend');
                  requests.shift().resolve(response(404, { detail: 'missing scope but not down' }));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /Project\\/Application\\/Instance scope를 찾을 수 없습니다/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /down|deleted|정상|복구 완료/);

                  clickSnapshotTrend('/api/projects/project-1/applications/app-1/instances/instance-1/snapshot-trend');
                  requests.shift().resolve(response(500, { detail: 'internal stack provider payload token' }));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /Instance Snapshot Trend를 불러오지 못했습니다/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /internal stack|provider payload|token/);

                  clickSnapshotTrend('/api/projects/project-1/applications/app-1/instances/instance-1/snapshot-trend');
                  const malformed = trend('project-1', 'app-1', 'instance-1', { instanceName: 'Malformed Trend Pod' });
                  delete malformed.points[0].starterConnection;
                  requests.shift().resolve(response(200, malformed));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /Instance Snapshot Trend를 불러오지 못했습니다/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /Malformed Trend Pod/);

                  clickSnapshotTrend('/api/projects/project-1/applications/app-1/instances/instance-1/snapshot-trend');
                  requests.shift().resolve(response(200, trend('project-1', 'other-app', 'instance-1', { instanceName: 'Mismatched Trend Pod' })));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /Instance Snapshot Trend를 불러오지 못했습니다/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /Mismatched Trend Pod|other-app/);

                  clickSnapshotTrend('/api/projects/project-1/applications/app-1/instances/instance-1/snapshot-trend');
                  const oldRequest = requests.shift();
                  clickSnapshotTrend('/api/projects/project-1/applications/app-1/instances/instance-1/snapshot-trend');
                  const newRequest = requests.shift();
                  newRequest.resolve(response(200, trend('project-1', 'app-1', 'instance-1', { instanceName: 'fresh-trend-pod' })));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /fresh-trend-pod/);
                  oldRequest.resolve(response(200, trend('project-1', 'app-1', 'instance-1', { instanceName: 'stale-trend-pod' })));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /fresh-trend-pod/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /stale-trend-pod/);

                  clickSnapshotTrend('/api/projects/project-1/applications/app-1/instances/instance-1/snapshot-trend');
                  const projectReloadTrendRequest = requests.shift();
                  reloadProjects.listeners.click();
                  const projectReloadRequest = requests.shift();
                  projectReloadTrendRequest.resolve(response(200, trend('project-1', 'app-1', 'instance-1', { instanceName: 'project-reload-stale-pod' })));
                  await settle();
                  assert.doesNotMatch(dashboardDetail.innerHTML, /project-reload-stale-pod/);
                  projectReloadRequest.resolve(response(200, {
                    generatedAt: '2026-05-31T02:30:00Z',
                    projects: [project('project-1', 'Project project-1', '/api/projects/project-1/applications')]
                  }));
                  await settle();

                  await loadEvidence();
                  clickSnapshotTrend('/api/projects/project-1/applications/app-1/instances/instance-1/snapshot-trend');
                  const applicationReloadTrendRequest = requests.shift();
                  reloadApplications.listeners.click();
                  const applicationReloadRequest = requests.shift();
                  applicationReloadTrendRequest.resolve(response(200, trend('project-1', 'app-1', 'instance-1', { instanceName: 'application-reload-stale-pod' })));
                  await settle();
                  assert.doesNotMatch(dashboardDetail.innerHTML, /application-reload-stale-pod/);
                  applicationReloadRequest.resolve(response(200, {
                    generatedAt: '2026-05-31T02:35:00Z',
                    project: { projectId: 'project-1', name: 'Project project-1' },
                    applications: [application('app-1', { name: 'Orders API', projectId: 'project-1' })]
                  }));
                  await settle();
                })().catch(error => {
                  console.error(error && error.stack ? error.stack : error);
                  process.exit(1);
                });
                """);
    }

    @Test
    void snapshotTrendStaticGuardsForbidTokenPersistenceFollowupFetchAndUiRecomputation() throws IOException {
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
                "averagePercentile",
                "mergePercentile",
                "interpolatePercentile",
                "deriveInstanceHealth",
                "deriveHostStatus",
                "deriveEndpointPriority",
                "buildHistoryEvent",
                "createSnapshotEvent",
                "classifyCaptureReason",
                "deriveMarkerSeverity",
                "deriveRecoveryMarker");

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
        assertThat(appJs).contains(
                "fetch(snapshotTrendRequestLink(",
                "snapshotTrendLink",
                "isSnapshotDetailLink");
        assertThat(appJs).doesNotContain(forbiddenHelpers.toArray(String[]::new));
        assertThat(appJs).doesNotContain(
                "fetch('/api/projects/",
                "fetch(`/api/projects/",
                "fetch(snapshotDetail",
                "fetch(snapshotHistory",
                "fetch(history",
                "fetch(operational",
                "fetch(endpoint",
                "fetch(raw",
                "endpoint-timeseries",
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
     * Instance Snapshot Trend UI test들이 공유하는 DOM/fetch fixture다. 실제 app.js 상태 머신만 실행하도록 최소 DOM을 제공한다.
     */
    private static String commonNodeHarness() {
        return """
                        function createHarness(source) {
                          const elements = new Map();
                          const requests = [];
                          const snapshotId = '018f6b9a-2e1a-7d2b-9b2f-4db69d92c241';

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
                        lastAcceptedBucketAt: '2026-05-31T01:10:00Z',
                        freshnessLabel: 'recent'
                      },
                      starterConnection: {
                        statusSource: 'starter_heartbeat',
                        lastHeartbeatAt: '2026-05-31T01:11:00Z',
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
                    const instanceId = overrides.instanceId ?? 'instance-1';
                    return {
                      generatedAt: '2026-05-31T01:20:00Z',
                      application: {
                        projectId,
                        applicationId,
                        name: overrides.applicationName ?? 'Orders API',
                        environment: overrides.environment ?? 'prod',
                        lastAcceptedBucketAt: '2026-05-31T01:19:30Z',
                        lastHealthyAt: null,
                        sourceWindow: {
                          current: { startUtc: '2026-05-31T01:05:00Z', endUtc: '2026-05-31T01:20:00Z' },
                          baseline: { startUtc: '2026-05-31T00:50:00Z', endUtc: '2026-05-31T01:05:00Z' }
                        },
                        freshness: {
                          lastObservedAt: '2026-05-31T01:19:30Z',
                          staleAt: '2026-05-31T01:21:00Z',
                          downAt: '2026-05-31T01:22:30Z'
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
                        lastHeartbeatAt: '2026-05-31T01:19:45Z',
                        lastHeartbeatStatus: 'received',
                        connectionMeaning: 'starter_connected',
                        stateImpact: 'none'
                      },
                      zeroInsight: {
                        reasonCode: 'waiting_first_data',
                        message: 'Starter connected; waiting for traffic.',
                        recommendedAction: 'Wait for accepted metric bucket.'
                      },
                      recovery: { isRecovering: false, lastHealthyAt: null, retryAfterSeconds: null, recommendedAction: null },
                      metrics: { requestCount: 42, errorCount: 2, errorRate: 0.047619 },
                      sourceScopedPercentiles: {
                        source: 'starter_local',
                        scope: 'instance_bucket',
                        displayPolicy: 'latest_starter_point_per_instance_in_current_window',
                        aggregatePolicy: 'no_average_no_max_no_merge_no_histogram_recalculation',
                        status: 'available',
                        reason: null,
                        items: []
                      },
                      histogramDistribution: {
                        source: 'histogram_bucket_distribution',
                        scope: 'application',
                        displayPolicy: 'bucket_distribution_evidence',
                        aggregatePolicy: 'sum_cumulative_counts_only_when_boundary_set_matches',
                        current: { status: 'available', reason: null, totalCount: 42, buckets: [{ leMs: 500, count: 42 }] },
                        baseline: { status: 'missing', reason: 'no_histogram_buckets_in_baseline_window', totalCount: 0, buckets: [] }
                      },
                      triageCards: [],
                      endpointPriority: [],
                      instances: [{
                        instanceId,
                        instanceName: 'pod-a',
                        lastSeenAt: '2026-05-31T01:19:30Z',
                        links: { evidence: `/api/projects/${projectId}/applications/${applicationId}/instances/${instanceId}/evidence` }
                      }],
                      snapshot: null
                    };
                  }

                  function evidence(projectId, applicationId, instanceId, overrides = {}) {
                    return {
                      generatedAt: '2026-05-31T01:21:00Z',
                      application: {
                        projectId,
                        applicationId,
                        name: overrides.applicationName ?? 'Orders API',
                        environment: overrides.environment ?? 'prod',
                        links: { dashboard: `/api/projects/${projectId}/applications/${applicationId}/dashboard` }
                      },
                      instance: {
                        instanceId,
                        instanceName: overrides.instanceName ?? 'pod-a',
                        firstSeenAt: '2026-05-31T00:00:00Z',
                        lastSeenAt: '2026-05-31T01:20:30Z'
                      },
                      metricData: {
                        statusSource: 'accepted_bucket',
                        window: {
                          name: 'current_15m',
                          startUtc: '2026-05-31T01:06:00Z',
                          endUtc: '2026-05-31T01:21:00Z',
                          bucketDurationSeconds: 30
                        },
                        lastAcceptedBucketAt: '2026-05-31T01:20:30Z',
                        freshnessLabel: 'current',
                        sampleReadiness: 'sufficient',
                        requestCount: 42,
                        errorCount: 2,
                        errorRate: 0.047619,
                        reason: null
                      },
                      starterConnection: {
                        statusSource: 'starter_heartbeat',
                        lastHeartbeatAt: '2026-05-31T01:20:45Z',
                        lastHeartbeatStatus: 'received',
                        freshnessLabel: 'recent',
                        connectionMeaning: 'starter_connected',
                        stateImpact: 'none'
                      },
                      starterPercentiles: {
                        source: 'starter_canonical_percentile',
                        scope: 'instance',
                        window: 'current_15m',
                        bucketDurationSeconds: 30,
                        maxPointCount: 30,
                        displayPolicy: 'source_scoped_series',
                        aggregatePolicy: 'no_average_no_max_no_merge_no_histogram_recalculation',
                        status: 'available',
                        reason: null,
                        points: [{
                          bucketStartUtc: '2026-05-31T01:19:30Z',
                          bucketEndUtc: '2026-05-31T01:20:00Z',
                          requestCount: 10,
                          p95Ms: 120,
                          p99Ms: 240
                        }]
                      },
                      histogramDistribution: {
                        source: 'histogram_bucket_distribution',
                        scope: 'selected_instance_current_15m',
                        status: 'available',
                        reason: null,
                        totalCount: 42,
                        buckets: [{ leMs: 100, count: 20 }, { leMs: 500, count: 42 }]
                      },
                      resourceHints: {
                        source: 'accepted_bucket_latest_sample',
                        status: 'available',
                        reason: null,
                        bucketEndUtc: '2026-05-31T01:20:30Z',
                        cpuUsageRatio: 0.25,
                        heapUsedRatio: 0.40,
                        datasourcePoolUsageRatio: 0.50
                      },
                      applicationTriageContribution: {
                        status: 'missing',
                        contributed: false,
                        relatedRuleIds: [],
                        reason: 'no_application_triage_cards'
                      },
                      endpointEvidence: {
                        source: 'accepted_metric_buckets.endpoints_json',
                        scope: 'instance_current_15m',
                        selectionPolicy: 'application_priority_presence_then_triage_then_instance_request_count',
                        displayOrderingPolicy: 'selected_instance_signal_then_application_priority_reference',
                        status: 'available',
                        reason: null,
                        items: []
                      },
                      links: {
                        self: `/api/projects/${projectId}/applications/${applicationId}/instances/${instanceId}/evidence`,
                        dashboard: `/api/projects/${projectId}/applications/${applicationId}/dashboard`,
                        snapshotTrend: `/api/projects/${projectId}/applications/${applicationId}/instances/${instanceId}/snapshot-trend`
                      }
                    };
                  }

                  function trend(projectId, applicationId, instanceId, overrides = {}) {
                    const requestedSince = overrides.requestedSince ?? '7d';
                    const limit = overrides.limit ?? 168;
                    return {
                      generatedAt: '2026-05-31T01:30:00Z',
                      application: {
                        projectId,
                        applicationId,
                        name: overrides.applicationName ?? 'Orders API',
                        environment: 'prod',
                        links: { dashboard: `/api/projects/${projectId}/applications/${applicationId}/dashboard` }
                      },
                      instance: {
                        instanceId,
                        instanceName: overrides.instanceName ?? 'pod-a',
                        firstSeenAt: '2026-05-31T00:00:00Z',
                        lastSeenAt: '2026-05-31T01:20:30Z',
                        links: { evidence: `/api/projects/${projectId}/applications/${applicationId}/instances/${instanceId}/evidence` }
                      },
                      source: 'dashboard_snapshots.read_model_json.instanceSummary.items',
                      horizon: {
                        since: requestedSince === '14d' ? '2026-05-17T01:30:00Z' : '2026-05-24T01:30:00Z',
                        until: '2026-05-31T01:30:00Z',
                        requestedSince,
                        defaultSince: '7d',
                        maxSince: '14d',
                        limit,
                        maxLimit: 336,
                        order: 'capturedAt_asc'
                      },
                              points: overrides.empty ? [] : [{
                                snapshotId,
                        capturedAt: '2026-05-31T01:00:00Z',
                        currentWindowEndUtc: '2026-05-31T01:00:00Z',
                        storedApplicationStateCode: 'active',
                        captureReason: null,
                        instanceName: overrides.instanceName ?? 'pod-a',
                        observationStatus: 'observed',
                        metricData: {
                          statusSource: 'accepted_bucket',
                          lastAcceptedBucketAt: '2026-05-31T00:59:30Z',
                          freshnessLabel: 'current'
                        },
                        starterConnection: {
                          statusSource: 'starter_heartbeat',
                          lastHeartbeatAt: '2026-05-31T00:59:45Z',
                          lastHeartbeatStatus: 'received',
                          connectionMeaning: 'starter_connected',
                          stateImpact: 'none'
                        },
                        starterPercentilePoint: {
                          source: 'starter_canonical_percentile',
                          scope: 'instance_bucket',
                          bucketStartUtc: '2026-05-31T00:59:00Z',
                          bucketEndUtc: '2026-05-31T00:59:30Z',
                          requestCount: 820,
                          p95Ms: 210,
                          p99Ms: 360
                        },
                        resourceHints: {
                          source: 'accepted_bucket_latest_sample',
                          status: 'available',
                          bucketEndUtc: '2026-05-31T00:59:30Z',
                          cpuUsageRatio: 0.41,
                          heapUsedRatio: 0.62,
                          datasourcePoolUsageRatio: 0.37
                        },
                        applicationTriageContribution: {
                          status: 'available',
                          contributed: false,
                          relatedRuleIds: [],
                          reason: 'no_action_needed'
                        },
                                endpointEvidenceRefs: [{
                                  endpointKey: 'POST /orders',
                                  method: 'POST',
                                  route: '/orders',
                                  relatedApplicationPriorityRank: 1,
                                  relatedRuleIds: ['endpoint_error_spike'],
                                  snapshotDetailAnchor: 'endpoint-evidence-1',
                                  anchorStatus: 'resolved'
                                }]
                              }]
                            };
                          }

                          function detail(projectId, applicationId, requestedSnapshotId) {
                            return {
                              generatedAt: '2026-05-31T01:31:00Z',
                              source: 'dashboard_snapshots',
                              readSemantics: {
                                mode: 'stored_snapshot_detail',
                                currentStateRecalculated: false,
                                liveSourcesJoined: [],
                                rawReadModelJsonExposed: false
                              },
                              snapshot: {
                                snapshotId: requestedSnapshotId,
                                capturedAt: '2026-05-31T01:00:00Z',
                                generatedAt: '2026-05-31T01:00:00Z',
                                currentWindow: { startUtc: '2026-05-31T00:45:00Z', endUtc: '2026-05-31T01:00:00Z' },
                                baselineWindow: { startUtc: '2026-05-31T00:30:00Z', endUtc: '2026-05-31T00:45:00Z' },
                                captureReason: 'high_confidence_concern',
                                storedApplicationStateCode: 'active',
                                primaryRuleId: 'endpoint_error_spike',
                                primaryEndpointKey: 'POST /orders',
                                maxConfidence: 0.84
                              },
                              marker: {
                                markerId: 'snapshot:' + requestedSnapshotId + ':high_confidence_concern',
                                snapshotId: requestedSnapshotId,
                                capturedAt: '2026-05-31T01:00:00Z',
                                currentWindowEndUtc: '2026-05-31T01:00:00Z',
                                type: 'high_confidence_concern',
                                severity: 'warning',
                                readMeaning: 'stored_read_model_point',
                                storedApplicationStateCode: 'active',
                                previousState: {},
                                title: 'server marker title',
                                summary: 'server marker summary',
                                links: {
                                  snapshot: `/api/projects/${projectId}/applications/${applicationId}/dashboard/snapshots/${requestedSnapshotId}`
                                }
                              },
                              previousState: {},
                              lastHealthyAt: {},
                              recoveryMarker: null,
                              readModel: {
                                application: { name: 'Orders API', environment: 'prod' },
                                state: { code: 'active', label: 'Metric data active' },
                                starterConnection: { statusSource: 'starter_heartbeat' },
                                zeroInsight: null,
                                recovery: null,
                                metrics: null,
                                sourceScopedPercentiles: null,
                                triageCards: [],
                                endpointPriority: []
                              },
                              snapshotEndpointEvidence: {
                                source: 'bounded_endpoint_evidence',
                                maxItems: 10,
                                selectionPolicy: 'endpoint_priority_rank_then_high_confidence_concern_then_triage_affected_endpoint',
                                unavailableReason: null,
                                items: [{
                                  anchorId: 'endpoint-evidence-1',
                                  endpointKey: 'POST /orders',
                                  method: 'POST',
                                  route: '/orders',
                                  rank: 1,
                                  reason: 'error_and_latency',
                                  ruleIds: ['endpoint_error_spike'],
                                  confidence: 0.84,
                                  score: 84,
                                  requestCount: 120,
                                  errorRate: 0.1,
                                  durationBuckets: [{ leMs: 500, count: 120 }],
                                  baselineDurationBuckets: [{ leMs: 500, count: 100 }],
                                  bucketDistributionSource: 'histogram_bucket_distribution',
                                  freshness: { status: 'stored' },
                                  recommendedAction: 'server endpoint action'
                                }]
                              },
                              instanceSummary: {
                                schemaVersion: '1.0',
                                source: 'bounded_instance_summary',
                                maxItems: 50,
                                selectionPolicy: 'stored_instance_summary_order',
                                unavailableReason: null,
                                items: []
                              },
                              links: {
                                self: `/api/projects/${projectId}/applications/${applicationId}/dashboard/snapshots/${requestedSnapshotId}`,
                                markers: `/api/projects/${projectId}/applications/${applicationId}/dashboard/snapshot-markers?since=24h`
                              }
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
                            return { dataset: { applicationId, applicationName: name, applicationEnvironment: environment, dashboardLink: link } };
                          }
                          return null;
                        }
                      }
                    });
                  }

                  function clickEvidence(instanceId, instanceName, link) {
                    elements.get('#dashboard-detail').listeners.click({
                      target: {
                        closest(selector) {
                          if (selector === '[data-evidence-link]') {
                            return {
                              disabled: false,
                              getAttribute(name) {
                                return name === 'aria-disabled' ? 'false' : null;
                              },
                              dataset: {
                                instanceId,
                                instanceName,
                                evidenceLink: link
                              }
                            };
                          }
                          return null;
                        }
                      }
                    });
                  }

                  function clickSnapshotTrend(link) {
                    elements.get('#dashboard-detail').listeners.click({
                      target: {
                        closest(selector) {
                          if (selector === 'button[data-snapshot-trend-link]') {
                            return {
                              disabled: false,
                              getAttribute(name) {
                                return name === 'aria-disabled' ? 'false' : null;
                              },
                              dataset: { snapshotTrendLink: link }
                            };
                          }
                          return null;
                        }
                      }
                    });
                  }

                  function clickTrendHorizon(horizon) {
                    elements.get('#dashboard-detail').listeners.click({
                      target: {
                        closest(selector) {
                          if (selector === '[data-trend-horizon]') {
                            return {
                              disabled: horizon === '24h',
                              getAttribute(name) {
                                return name === 'aria-disabled' && horizon === '24h' ? 'true' : 'false';
                              },
                              dataset: { trendHorizon: horizon }
                            };
                          }
                          return null;
                        }
                      }
                    });
                  }

                  function clickTrendDetail(snapshotId, anchor) {
                    elements.get('#dashboard-detail').listeners.click({
                      target: {
                        closest(selector) {
                          if (selector === '[data-trend-snapshot-id]') {
                            return {
                              disabled: false,
                              getAttribute(name) {
                                return name === 'aria-disabled' ? 'false' : null;
                              },
                              dataset: { trendSnapshotId: snapshotId, snapshotDetailAnchor: anchor }
                            };
                          }
                          return null;
                        }
                      }
                    });
                  }

                  function clickDashboardBack() {
                    elements.get('#dashboard-detail').listeners.click({
                      target: {
                        closest(selector) {
                          if (selector === '[data-dashboard-back]') {
                            return { dataset: { dashboardBack: 'true' } };
                          }
                          return null;
                        }
                      }
                    });
                  }

                  function clickEvidenceBack() {
                    elements.get('#dashboard-detail').listeners.click({
                      target: {
                        closest(selector) {
                          if (selector === '[data-instance-evidence-back]') {
                            return { dataset: { instanceEvidenceBack: 'true' } };
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
                            evidence,
                            trend,
                            detail,
                            clickApplications,
                    clickDashboard,
                    clickEvidence,
                            clickSnapshotTrend,
                            clickTrendHorizon,
                            clickTrendDetail,
                            clickDashboardBack,
                    clickEvidenceBack
                  };
                }
                """;
    }
}
