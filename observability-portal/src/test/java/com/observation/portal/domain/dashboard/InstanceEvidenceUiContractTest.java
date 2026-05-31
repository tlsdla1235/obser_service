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
 * Story 6.5 Instance Evidence static UI가 dashboard handoff link만으로 인증 fetch와 safe state를 처리하는지 검증한다.
 * 테스트는 실제 dashboard script를 Node VM에서 실행해 instance detail mode, back action, stale guard를 고정한다.
 */
class InstanceEvidenceUiContractTest {

    private static final Path STATIC_DASHBOARD = Path.of("src/main/resources/static/dashboard");

    @Test
    void instanceEvidenceShellUsesDashboardDetailModeAndReadModelFields() throws IOException {
        String indexHtml = Files.readString(STATIC_DASHBOARD.resolve("index.html"));
        String appJs = Files.readString(STATIC_DASHBOARD.resolve("app.js"));
        String styles = Files.readString(STATIC_DASHBOARD.resolve("styles.css"));

        assertThat(indexHtml).contains("id=\"dashboard-detail\"");
        assertThat(appJs).contains(
                "INSTANCE_EVIDENCE_VIEW_STATE",
                "instanceEvidenceRequestSequence",
                "selectedInstanceEvidenceContext",
                "fetch(selectedInstanceEvidenceContext.evidenceLink",
                "isInstanceEvidenceLink",
                "renderInstanceEvidenceReady",
                "evidence.generatedAt",
                "evidence.application",
                "evidence.instance",
                "evidence.metricData",
                "evidence.starterConnection",
                "evidence.starterPercentiles",
                "evidence.histogramDistribution",
                "evidence.resourceHints",
                "evidence.applicationTriageContribution",
                "evidence.endpointEvidence",
                "evidence.links");
        assertThat(sliceFunction(appJs, "instanceEntryMarkup")).contains(
                "data-instance-id",
                "data-instance-name",
                "data-evidence-link",
                "Evidence");
        assertThat(sliceFunction(appJs, "selectInstanceEvidence")).contains(
                "evidenceLink",
                "instanceId",
                "isInstanceEvidenceLink");
        assertThat(sliceFunction(appJs, "selectInstanceEvidence")).doesNotContain(
                "encodeURIComponent",
                "`/api/projects/",
                "window.location",
                "href=");
        assertThat(styles).contains(
                ".instance-detail-header",
                ".instance-evidence-axis",
                ".snapshot-trend-handoff");
    }

    @Test
    void instanceEvidenceRuntimeFetchesAuthenticatedLinkAndRendersSafeStates() throws Exception {
        runNodeDashboardContract("""
                const fs = require('fs');
                const vm = require('vm');
                const assert = require('assert');

                const source = fs.readFileSync('src/main/resources/static/dashboard/app.js', 'utf8');
                const harness = createHarness(source);
                const { auth, requests, elements, response, settle, project, application, dashboard, evidence,
                  clickApplications, clickDashboard, clickEvidence, clickBack } = harness;
                const dashboardDetail = elements.get('#dashboard-detail');

                async function loadDashboard() {
                  auth.setAccessToken('service-token');
                  requests.shift().resolve(response(200, {
                    generatedAt: '2026-05-29T01:00:00Z',
                    projects: [project('project-1', 'Project One', '/api/projects/project-1/applications')]
                  }));
                  await settle();
                  clickApplications('project-1', 'Project One', '/api/projects/project-1/applications');
                  requests.shift().resolve(response(200, {
                    generatedAt: '2026-05-29T01:02:00Z',
                    project: { projectId: 'project-1', name: 'Project One' },
                    applications: [application('app-1')]
                  }));
                  await settle();
                  clickDashboard('app-1', 'Orders API', 'prod', '/api/projects/project-1/applications/app-1/dashboard');
                  requests.shift().resolve(response(200, dashboard('project-1', 'app-1', { applicationName: 'Orders API' })));
                  await settle();
                }

                (async () => {
                  await loadDashboard();

                  clickEvidence('instance-1', 'pod-a', '/api/projects/project-1/applications/app-1/instances/instance-1/evidence');
                  assert.strictEqual(requests.length, 1);
                  assert.strictEqual(requests[0].url, '/api/projects/project-1/applications/app-1/instances/instance-1/evidence');
                  assert.strictEqual(requests[0].init.headers.Authorization, 'Bearer service-token');
                  assert.match(dashboardDetail.innerHTML, /Instance Evidence를 불러오는 중/);
                  requests.shift().resolve(response(200, evidence('project-1', 'app-1', 'instance-1')));
                  await settle();

                  assert.match(dashboardDetail.innerHTML, /Instance Evidence/);
                  assert.match(dashboardDetail.innerHTML, /pod-a/);
                  assert.match(dashboardDetail.innerHTML, /generatedAt/);
                  assert.match(dashboardDetail.innerHTML, /Application Dashboard/);
                  assert.match(dashboardDetail.innerHTML, /Metric data axis/);
                  assert.match(dashboardDetail.innerHTML, /accepted_bucket/);
                  assert.match(dashboardDetail.innerHTML, /Starter connection axis/);
                  assert.match(dashboardDetail.innerHTML, /starter_heartbeat/);
                  assert.match(dashboardDetail.innerHTML, /state impact/);
                  assert.match(dashboardDetail.innerHTML, /none/);
                  assert.match(dashboardDetail.innerHTML, /Application triage contribution/);
                  assert.match(dashboardDetail.innerHTML, /기여 evidence 없음/);
                  assert.match(dashboardDetail.innerHTML, /starter_canonical_percentile/);
                  assert.match(dashboardDetail.innerHTML, /current_15m/);
                  assert.match(dashboardDetail.innerHTML, /bucketDurationSeconds/);
                  assert.match(dashboardDetail.innerHTML, /maxPointCount/);
                  assert.match(dashboardDetail.innerHTML, /source_scoped_series/);
                  assert.match(dashboardDetail.innerHTML, /no_average_no_max_no_merge_no_histogram_recalculation/);
                  assert.match(dashboardDetail.innerHTML, /bucketStartUtc/);
                  assert.match(dashboardDetail.innerHTML, /bucketEndUtc/);
                  assert.match(dashboardDetail.innerHTML, /requestCount/);
                  assert.match(dashboardDetail.innerHTML, /p95Ms/);
                  assert.match(dashboardDetail.innerHTML, /p99Ms/);
                  assert.match(dashboardDetail.innerHTML, /histogram_bucket_distribution/);
                  assert.match(dashboardDetail.innerHTML, /accepted_bucket_latest_sample/);
                  assert.match(dashboardDetail.innerHTML, /accepted_metric_buckets.endpoints_json/);
                  assert.match(dashboardDetail.innerHTML, /instance_current_15m/);
                  assert.match(dashboardDetail.innerHTML, /application_priority_presence_then_triage_then_instance_request_count/);
                  assert.match(dashboardDetail.innerHTML, /selected_instance_signal_then_application_priority_reference/);
                  assert.match(dashboardDetail.innerHTML, /selected instance current window에서 관찰됨/);
                  assert.match(dashboardDetail.innerHTML, /selected instance current window에 해당 endpoint evidence가 없음/);
                  assert.match(dashboardDetail.innerHTML, /endpoint evidence 신뢰 부족/);
                  assert.match(dashboardDetail.innerHTML, /relatedApplicationPriorityRank/);
                  assert.match(dashboardDetail.innerHTML, /localDisplayOrder/);
                  assert.match(dashboardDetail.innerHTML, /data-snapshot-trend-link="\\/api\\/projects\\/project-1\\/applications\\/app-1\\/instances\\/instance-1\\/snapshot-trend"/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /service-token|provider payload|rawPath|queryString|traceId|root cause|recommendedAction|instanceHealth|hostStatus|connectedAndHealthy/);
                  assert.strictEqual(requests.length, 0);

                  clickBack();
                  assert.match(dashboardDetail.innerHTML, /Orders API/);
                  assert.match(dashboardDetail.innerHTML, /Metric data active/);
                  assert.strictEqual(requests.length, 0);

                  clickEvidence('instance-1', 'pod-a', '/api/projects/project-1/applications/other-app/instances/instance-1/evidence');
                  assert.strictEqual(requests.length, 0);
                  assert.match(dashboardDetail.innerHTML, /Evidence link를 확인할 수 없습니다/);

                  auth.clearAccessToken();
                  clickEvidence('instance-1', 'pod-a', '/api/projects/project-1/applications/app-1/instances/instance-1/evidence');
                  assert.strictEqual(requests.length, 0);
                  assert.match(dashboardDetail.innerHTML, /GitHub 로그인 후 Instance Evidence를 볼 수 있습니다/);

                  await loadDashboard();
                  clickEvidence('instance-1', 'pod-a', '/api/projects/project-1/applications/app-1/instances/instance-1/evidence');
                  requests.shift().resolve(response(404, { detail: 'missing scope but not down' }));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /Project\\/Application\\/Instance scope를 찾을 수 없습니다/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /down|deleted|장애|정상/);

                  clickEvidence('instance-1', 'pod-a', '/api/projects/project-1/applications/app-1/instances/instance-1/evidence');
                  requests.shift().resolve(response(500, { detail: 'internal stack service-token provider payload' }));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /Instance Evidence를 불러오지 못했습니다/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /internal stack|service-token|provider payload/);

                  clickEvidence('instance-1', 'pod-a', '/api/projects/project-1/applications/app-1/instances/instance-1/evidence');
                  const malformed = evidence('project-1', 'app-1', 'instance-1', { instanceName: 'Malformed pod' });
                  delete malformed.starterConnection;
                  requests.shift().resolve(response(200, malformed));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /Instance Evidence를 불러오지 못했습니다/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /Malformed pod/);

                  const malformedCases = [{
                    name: 'Malformed resource pod',
                    mutate(payload) {
                      payload.resourceHints = {};
                    }
                  }, {
                    name: 'Too many percentile pod',
                    mutate(payload) {
                      payload.starterPercentiles.points = Array.from({ length: 31 }, (_, index) => ({
                        bucketStartUtc: '2026-05-29T01:19:30Z',
                        bucketEndUtc: '2026-05-29T01:20:00Z',
                        requestCount: index + 1,
                        p95Ms: 120,
                        p99Ms: 240
                      }));
                    }
                  }, {
                    name: 'Reversed percentile pod',
                    mutate(payload) {
                      payload.starterPercentiles.points = [{
                        bucketStartUtc: '2026-05-29T01:20:00Z',
                        bucketEndUtc: '2026-05-29T01:20:30Z',
                        requestCount: 10,
                        p95Ms: 120,
                        p99Ms: 240
                      }, {
                        bucketStartUtc: '2026-05-29T01:19:30Z',
                        bucketEndUtc: '2026-05-29T01:20:00Z',
                        requestCount: 10,
                        p95Ms: 120,
                        p99Ms: 240
                      }];
                    }
                  }, {
                    name: 'Sixty second percentile pod',
                    mutate(payload) {
                      payload.starterPercentiles.points = [{
                        bucketStartUtc: '2026-05-29T01:19:00Z',
                        bucketEndUtc: '2026-05-29T01:20:00Z',
                        requestCount: 10,
                        p95Ms: 120,
                        p99Ms: 240
                      }];
                    }
                  }, {
                    name: 'Out of window percentile pod',
                    mutate(payload) {
                      payload.starterPercentiles.points = [{
                        bucketStartUtc: '2026-05-29T01:05:00Z',
                        bucketEndUtc: '2026-05-29T01:05:30Z',
                        requestCount: 10,
                        p95Ms: 120,
                        p99Ms: 240
                      }];
                    }
                  }, {
                    name: 'Invalid histogram scope pod',
                    mutate(payload) {
                      payload.histogramDistribution.scope = 'application_7d';
                    }
                  }, {
                    name: 'Raw endpoint pod',
                    mutate(payload) {
                      payload.endpointEvidence.items[0].route = '/orders?token=secret';
                      payload.endpointEvidence.items[0].endpointKey = 'POST /orders?token=secret';
                    }
                  }, {
                    name: 'Suppressed endpoint pod',
                    mutate(payload) {
                      payload.endpointEvidence.status = 'suppressed';
                      payload.endpointEvidence.reason = 'application_freshness_not_current';
                    }
                  }, {
                    name: 'Suppressed wrong reason pod',
                    mutate(payload) {
                      payload.endpointEvidence.status = 'suppressed';
                      payload.endpointEvidence.reason = 'endpoint_evidence_insufficient';
                    }
                  }, {
                    name: 'Mismatched trend pod',
                    mutate(payload) {
                      payload.links.snapshotTrend = '/api/projects/project-1/applications/app-1/instances/other-instance/snapshot-trend';
                    }
                  }];

                  for (const malformedCase of malformedCases) {
                    clickEvidence('instance-1', 'pod-a', '/api/projects/project-1/applications/app-1/instances/instance-1/evidence');
                    const payload = evidence('project-1', 'app-1', 'instance-1', { instanceName: malformedCase.name });
                    malformedCase.mutate(payload);
                    requests.shift().resolve(response(200, payload));
                    await settle();
                    assert.match(dashboardDetail.innerHTML, /Instance Evidence를 불러오지 못했습니다/);
                    assert.doesNotMatch(dashboardDetail.innerHTML, new RegExp(malformedCase.name));
                    assert.doesNotMatch(dashboardDetail.innerHTML, /token=secret|application_priority_endpoint_observed_on_selected_instance|other-instance/);
                  }

                  clickEvidence('instance-1', 'pod-a', '/api/projects/project-1/applications/app-1/instances/instance-1/evidence');
                  requests.shift().resolve(response(200, evidence('project-1', 'other-app', 'instance-1', { instanceName: 'Mismatched pod' })));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /Instance Evidence를 불러오지 못했습니다/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /Mismatched pod|other-app/);

                  clickEvidence('instance-1', 'pod-a', '/api/projects/project-1/applications/app-1/instances/instance-1/evidence');
                  requests.shift().resolve(response(401, {}));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /GitHub 로그인 후 Instance Evidence를 볼 수 있습니다/);
                  assert.match(dashboardDetail.innerHTML, /Application Dashboard/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /GitHub 로그인 후 Dashboard를 볼 수 있습니다/);
                  clickBack();
                  assert.match(dashboardDetail.innerHTML, /GitHub 로그인 후 Dashboard를 볼 수 있습니다/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /Orders API|Metric data active/);
                })().catch(error => {
                  console.error(error && error.stack ? error.stack : error);
                  process.exit(1);
                });
                """);
    }

    @Test
    void greenPathRendersProjectApplicationDashboardAndEvidenceWithSourceAwareGuidance() throws Exception {
        runNodeDashboardContract("""
                const fs = require('fs');
                const vm = require('vm');
                const assert = require('assert');

                const source = fs.readFileSync('src/main/resources/static/dashboard/app.js', 'utf8');
                const harness = createHarness(source);
                const { auth, requests, elements, response, settle, project, application, dashboard, evidence,
                  clickApplications, clickDashboard, clickEvidence } = harness;
                const applicationList = elements.get('#application-list');
                const dashboardDetail = elements.get('#dashboard-detail');
                const forbiddenCopy = /앱 정상 확정|정상 확정|문제 없음|host down|복구 완료|health score|root cause|endpoint health score|connectedAndHealthy|hostStatus|applicationHealth|hostHealth/;

                function safeGreenPathDashboard(reasonCode) {
                  const payload = dashboard('project-1', 'app-1', { applicationName: 'Orders API' });
                  payload.endpointPriority = [];
                  payload.triageCards = [];
                  payload.recovery = { isRecovering: false, lastHealthyAt: null, retryAfterSeconds: null, recommendedAction: null };
                  payload.starterConnection = {
                    statusSource: 'starter_heartbeat',
                    lastHeartbeatAt: '2026-05-29T01:20:45Z',
                    lastHeartbeatStatus: 'received',
                    connectionMeaning: 'starter_connected',
                    stateImpact: 'none'
                  };
                  payload.sourceScopedPercentiles = {
                    source: 'starter_canonical_percentile',
                    scope: 'instance_bucket',
                    displayPolicy: 'source_scoped_points',
                    aggregatePolicy: 'no_average_no_max_no_merge_no_histogram_recalculation',
                    status: 'missing',
                    reason: 'waiting_for_source',
                    items: []
                  };
                  payload.histogramDistribution.current = {
                    status: 'missing',
                    reason: 'waiting_for_accepted_bucket',
                    totalCount: 0,
                    buckets: []
                  };

                  if (reasonCode === 'waiting_first_data') {
                    payload.application.lastAcceptedBucketAt = null;
                    payload.application.freshness.lastObservedAt = null;
                    payload.state = {
                      code: 'unknown',
                      label: 'Metric data waiting',
                      rationale: 'accepted bucket source absence',
                      recommendedAction: 'starter heartbeat와 accepted bucket source를 분리해서 기다립니다.',
                      scope: 'application'
                    };
                    payload.metrics = { requestCount: 0, errorCount: 0, errorRate: 0 };
                    payload.zeroInsight = {
                      reasonCode: 'waiting_first_data',
                      message: 'starter heartbeat는 수신됐지만 metric 판단 source인 accepted bucket은 아직 없습니다.',
                      recommendedAction: '첫 accepted bucket이 수용될 때까지 기다립니다.'
                    };
                    return payload;
                  }

                  if (reasonCode === 'insufficient_sample') {
                    payload.state = {
                      code: 'unknown',
                      label: 'Metric data insufficient sample',
                      rationale: 'accepted bucket은 들어왔지만 sample guard가 부족합니다.',
                      recommendedAction: 'minimum sample guard를 통과할 때까지 다음 bucket을 기다립니다.',
                      scope: 'application'
                    };
                    payload.metrics = { requestCount: 1, errorCount: 0, errorRate: 0 };
                    payload.sourceScopedPercentiles.status = 'insufficient';
                    payload.sourceScopedPercentiles.reason = 'insufficient_sample';
                    payload.histogramDistribution.current.status = 'insufficient';
                    payload.histogramDistribution.current.reason = 'insufficient_sample';
                    payload.zeroInsight = {
                      reasonCode: 'insufficient_sample',
                      message: 'accepted bucket은 들어왔지만 minimum sample guard를 통과할 표본이 아직 부족합니다.',
                      recommendedAction: 'host 상태를 단정하지 말고 accepted bucket 표본이 쌓이는지 확인합니다.'
                    };
                    return payload;
                  }

                  payload.state = {
                    code: 'active',
                    label: 'Metric data active',
                    rationale: 'freshness와 sample guard는 충분하지만 우선 triage 후보가 없습니다.',
                    recommendedAction: '현재 우선 노출할 triage가 없으므로 source 축을 유지해 관찰합니다.',
                    scope: 'application'
                  };
                  payload.metrics = { requestCount: 180, errorCount: 0, errorRate: 0 };
                  payload.sourceScopedPercentiles.status = 'available';
                  payload.sourceScopedPercentiles.reason = null;
                  payload.sourceScopedPercentiles.items = [{
                    source: 'starter_canonical_percentile',
                    application: 'orders-api',
                    environment: 'prod',
                    instance: 'pod-a',
                    bucketStartUtc: '2026-05-29T01:19:30Z',
                    bucketEndUtc: '2026-05-29T01:20:00Z',
                    requestCount: 60,
                    p95Ms: 120,
                    p99Ms: 240
                  }];
                  payload.histogramDistribution.current.status = 'available';
                  payload.histogramDistribution.current.reason = null;
                  payload.histogramDistribution.current.totalCount = 180;
                  payload.histogramDistribution.current.buckets = [{ leMs: 100, count: 130 }, { leMs: 500, count: 180 }];
                  payload.zeroInsight = {
                    reasonCode: 'no_action_needed',
                    message: '현재 우선 노출할 triage는 없습니다.',
                    recommendedAction: 'accepted bucket freshness와 starter heartbeat를 별도 source로 계속 봅니다.'
                  };
                  return payload;
                }

                async function renderDashboardState(reasonCode) {
                  clickDashboard('app-1', 'Orders API', 'prod', '/api/projects/project-1/applications/app-1/dashboard');
                  assert.strictEqual(requests.length, 1);
                  assert.strictEqual(requests[0].url, '/api/projects/project-1/applications/app-1/dashboard');
                  assert.strictEqual(requests[0].init.headers.Authorization, 'Bearer service-token');
                  requests.shift().resolve(response(200, safeGreenPathDashboard(reasonCode)));
                  await settle();
                  assert.doesNotMatch(dashboardDetail.innerHTML, forbiddenCopy);
                }

                (async () => {
                  auth.setAccessToken('service-token');
                  assert.strictEqual(requests[0].url, '/api/projects');
                  assert.strictEqual(requests[0].init.headers.Authorization, 'Bearer service-token');
                  requests.shift().resolve(response(200, {
                    generatedAt: '2026-05-29T01:00:00Z',
                    projects: [project('project-1', 'Project One', '/api/projects/project-1/applications')]
                  }));
                  await settle();

                  clickApplications('project-1', 'Project One', '/api/projects/project-1/applications');
                  assert.strictEqual(requests[0].url, '/api/projects/project-1/applications');
                  assert.strictEqual(requests[0].init.headers.Authorization, 'Bearer service-token');
                  const waitingApplication = application('app-1');
                  waitingApplication.metricData.lastAcceptedBucketAt = null;
                  waitingApplication.metricData.freshnessLabel = 'waiting_first_data';
                  waitingApplication.starterConnection.lastHeartbeatAt = '2026-05-29T01:00:30Z';
                  waitingApplication.starterConnection.freshnessLabel = 'recent';
                  requests.shift().resolve(response(200, {
                    generatedAt: '2026-05-29T01:02:00Z',
                    project: { projectId: 'project-1', name: 'Project One' },
                    applications: [waitingApplication]
                  }));
                  await settle();

                  assert.match(applicationList.innerHTML, /Accepted bucket/);
                  assert.match(applicationList.innerHTML, /source<\\/dt>\\s*<dd>accepted_bucket/);
                  assert.match(applicationList.innerHTML, /last accepted bucket absence/);
                  assert.match(applicationList.innerHTML, /Starter connection/);
                  assert.match(applicationList.innerHTML, /source<\\/dt>\\s*<dd>starter_heartbeat/);
                  assert.match(applicationList.innerHTML, /state impact<\\/dt>\\s*<dd>none/);
                  assert.doesNotMatch(applicationList.innerHTML, forbiddenCopy);

                  await renderDashboardState('waiting_first_data');
                  assert.match(dashboardDetail.innerHTML, /waiting_first_data/);
                  assert.match(dashboardDetail.innerHTML, /starter heartbeat는 수신됐지만 metric 판단 source인 accepted bucket은 아직 없습니다/);
                  assert.match(dashboardDetail.innerHTML, /accepted bucket source absence/);
                  assert.match(dashboardDetail.innerHTML, /starter_heartbeat/);
                  assert.match(dashboardDetail.innerHTML, /state impact<\\/dt>\\s*<dd>none/);

                  await renderDashboardState('insufficient_sample');
                  assert.match(dashboardDetail.innerHTML, /insufficient_sample/);
                  assert.match(dashboardDetail.innerHTML, /accepted bucket은 들어왔지만 minimum sample guard를 통과할 표본이 아직 부족합니다/);
                  assert.match(dashboardDetail.innerHTML, /host 상태를 단정하지 말고 accepted bucket 표본이 쌓이는지 확인합니다/);
                  assert.match(dashboardDetail.innerHTML, /no_average_no_max_no_merge_no_histogram_recalculation/);

                  await renderDashboardState('no_action_needed');
                  assert.match(dashboardDetail.innerHTML, /no_action_needed/);
                  assert.match(dashboardDetail.innerHTML, /현재 우선 노출할 triage는 없습니다/);
                  assert.match(dashboardDetail.innerHTML, /server-computed triage card source absence · zeroInsight 표시 중/);
                  assert.match(dashboardDetail.innerHTML, /starter_canonical_percentile/);
                  assert.match(dashboardDetail.innerHTML, /p95Ms/);
                  assert.match(dashboardDetail.innerHTML, /p99Ms/);
                  assert.match(dashboardDetail.innerHTML, /histogram_bucket_distribution/);
                  assert.match(dashboardDetail.innerHTML, /data-evidence-link="\\/api\\/projects\\/project-1\\/applications\\/app-1\\/instances\\/instance-1\\/evidence"/);

                  clickEvidence('instance-1', 'pod-a', '/api/projects/project-1/applications/app-1/instances/instance-1/evidence');
                  assert.strictEqual(requests[0].url, '/api/projects/project-1/applications/app-1/instances/instance-1/evidence');
                  assert.strictEqual(requests[0].init.headers.Authorization, 'Bearer service-token');
                  requests.shift().resolve(response(200, evidence('project-1', 'app-1', 'instance-1')));
                  await settle();

                  assert.match(dashboardDetail.innerHTML, /Instance Evidence/);
                  assert.match(dashboardDetail.innerHTML, /Metric data axis/);
                  assert.match(dashboardDetail.innerHTML, /source<\\/dt>\\s*<dd>accepted_bucket/);
                  assert.match(dashboardDetail.innerHTML, /Starter connection axis/);
                  assert.match(dashboardDetail.innerHTML, /source<\\/dt>\\s*<dd>starter_heartbeat/);
                  assert.match(dashboardDetail.innerHTML, /state impact<\\/dt>\\s*<dd>none/);
                  assert.match(dashboardDetail.innerHTML, /Application triage contribution/);
                  assert.match(dashboardDetail.innerHTML, /기여 evidence 없음/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, forbiddenCopy);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /service-token|rawPath|queryString|traceId/);
                  assert.strictEqual(requests.length, 0);
                })().catch(error => {
                  console.error(error && error.stack ? error.stack : error);
                  process.exit(1);
                });
                """);
    }

    @Test
    void failureRecoveryEvidenceKeepsAcceptedBucketAndStarterHeartbeatAxesSeparate() throws Exception {
        runNodeDashboardContract("""
                const fs = require('fs');
                const vm = require('vm');
                const assert = require('assert');

                const source = fs.readFileSync('src/main/resources/static/dashboard/app.js', 'utf8');
                const harness = createHarness(source);
                const { auth, requests, elements, response, settle, project, application, dashboard, evidence,
                  clickApplications, clickDashboard, clickEvidence } = harness;
                const dashboardDetail = elements.get('#dashboard-detail');
                const forbiddenCopy = /host application down|host process down|앱 정상 확정|정상 확정|문제 없음|복구 완료|장애 해결 완료|root cause|instanceHealth|hostStatus|connectedAndHealthy|applicationHealth|hostHealth/;

                async function loadFailureDashboard() {
                  auth.setAccessToken('service-token');
                  requests.shift().resolve(response(200, {
                    generatedAt: '2026-05-29T01:00:00Z',
                    projects: [project('project-1', 'Project One', '/api/projects/project-1/applications')]
                  }));
                  await settle();
                  clickApplications('project-1', 'Project One', '/api/projects/project-1/applications');
                  requests.shift().resolve(response(200, {
                    generatedAt: '2026-05-29T01:02:00Z',
                    project: { projectId: 'project-1', name: 'Project One' },
                    applications: [application('app-1')]
                  }));
                  await settle();
                  const failureDashboard = dashboard('project-1', 'app-1', { applicationName: 'Orders API' });
                  failureDashboard.state = {
                    code: 'stale',
                    label: 'Metric data stale',
                    rationale: 'server read model에서 accepted bucket freshness가 stale로 관찰됐습니다.',
                    recommendedAction: 'starter heartbeat와 metric freshness를 분리해 확인합니다.',
                    scope: 'application'
                  };
                  failureDashboard.zeroInsight = {
                    reasonCode: 'metric_data_idle',
                    message: 'metric data가 최근 기준을 벗어나 관찰 부족 상태입니다.',
                    recommendedAction: 'host 상태를 단정하지 말고 다음 accepted bucket을 확인합니다.'
                  };
                  failureDashboard.endpointPriority = [];
                  clickDashboard('app-1', 'Orders API', 'prod', '/api/projects/project-1/applications/app-1/dashboard');
                  requests.shift().resolve(response(200, failureDashboard));
                  await settle();
                }

                function suppressedEvidence(freshnessLabel, heartbeatStatus, starterMeaning) {
                  const payload = evidence('project-1', 'app-1', 'instance-1', { instanceName: `${freshnessLabel} pod` });
                  payload.metricData.lastAcceptedBucketAt = '2026-05-29T00:55:30Z';
                  payload.metricData.freshnessLabel = freshnessLabel;
                  payload.metricData.sampleReadiness = 'not_current';
                  payload.metricData.requestCount = 0;
                  payload.metricData.errorCount = 0;
                  payload.metricData.errorRate = 0;
                  payload.metricData.reason = 'application_freshness_not_current';
                  payload.starterConnection.lastHeartbeatAt = heartbeatStatus === 'missing' ? null : '2026-05-29T01:20:45Z';
                  payload.starterConnection.lastHeartbeatStatus = heartbeatStatus;
                  payload.starterConnection.freshnessLabel = heartbeatStatus === 'missing' ? 'missing' : 'recent';
                  payload.starterConnection.connectionMeaning = starterMeaning;
                  payload.starterConnection.stateImpact = 'none';
                  payload.endpointEvidence.status = 'suppressed';
                  payload.endpointEvidence.reason = 'application_freshness_not_current';
                  payload.endpointEvidence.items = [];
                  payload.applicationTriageContribution = {
                    status: 'missing',
                    contributed: false,
                    relatedRuleIds: [],
                    reason: 'application_freshness_not_current'
                  };
                  payload.starterPercentiles.status = 'missing';
                  payload.starterPercentiles.reason = 'application_freshness_not_current';
                  payload.starterPercentiles.points = [];
                  payload.histogramDistribution.status = 'missing';
                  payload.histogramDistribution.reason = 'application_freshness_not_current';
                  payload.histogramDistribution.totalCount = 0;
                  payload.histogramDistribution.buckets = [];
                  return payload;
                }

                async function renderSuppressedEvidence(freshnessLabel, heartbeatStatus, starterMeaning) {
                  clickEvidence('instance-1', 'pod-a', '/api/projects/project-1/applications/app-1/instances/instance-1/evidence');
                  assert.strictEqual(requests.length, 1);
                  assert.strictEqual(requests[0].init.headers.Authorization, 'Bearer service-token');
                  requests.shift().resolve(response(200, suppressedEvidence(freshnessLabel, heartbeatStatus, starterMeaning)));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /Instance Evidence/);
                  assert.match(dashboardDetail.innerHTML, /Metric data axis/);
                  assert.match(dashboardDetail.innerHTML, /Metric data axis[\\s\\S]*source<\\/dt>\\s*<dd>accepted_bucket/);
                  assert.match(dashboardDetail.innerHTML, /Starter connection axis/);
                  assert.match(dashboardDetail.innerHTML, /Starter connection axis[\\s\\S]*source<\\/dt>\\s*<dd>starter_heartbeat/);
                  assert.match(dashboardDetail.innerHTML, /state impact<\\/dt>\\s*<dd>none/);
                  assert.match(dashboardDetail.innerHTML, /application_freshness_not_current/);
                  assert.match(dashboardDetail.innerHTML, /stale\\/down 직전 endpoint evidence를 current concern처럼 표시하지 않습니다/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /POST \\/orders|GET \\/inventory|relatedApplicationPriorityRank/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, forbiddenCopy);
                }

                (async () => {
                  await loadFailureDashboard();
                  await renderSuppressedEvidence('stale', 'received', 'starter_connected');
                  await renderSuppressedEvidence('down', 'missing', 'telemetry_unreachable');
                })().catch(error => {
                  console.error(error && error.stack ? error.stack : error);
                  process.exit(1);
                });
                """);
    }

    @Test
    void instanceEvidenceRuntimePreventsStaleResponsesAcrossEvidenceTokenDashboardAndApplicationChanges() throws Exception {
        runNodeDashboardContract("""
                const fs = require('fs');
                const vm = require('vm');
                const assert = require('assert');

                const source = fs.readFileSync('src/main/resources/static/dashboard/app.js', 'utf8');
                const harness = createHarness(source);
                const { auth, requests, elements, response, settle, project, application, dashboard, evidence,
                  clickApplications, clickDashboard, clickEvidence } = harness;
                const dashboardDetail = elements.get('#dashboard-detail');
                const reloadProjects = elements.get('#reload-projects');
                const reloadApplications = elements.get('#reload-applications');

                async function loadDashboard(projectId = 'project-1', appId = 'app-1', appName = 'Orders API') {
                  auth.setAccessToken('token-' + projectId);
                  requests.shift().resolve(response(200, {
                    generatedAt: '2026-05-29T01:00:00Z',
                    projects: [project(projectId, 'Project ' + projectId, `/api/projects/${projectId}/applications`)]
                  }));
                  await settle();
                  clickApplications(projectId, 'Project ' + projectId, `/api/projects/${projectId}/applications`);
                  requests.shift().resolve(response(200, {
                    generatedAt: '2026-05-29T01:02:00Z',
                    project: { projectId, name: 'Project ' + projectId },
                    applications: [application(appId, { name: appName, projectId })]
                  }));
                  await settle();
                  clickDashboard(appId, appName, 'prod', `/api/projects/${projectId}/applications/${appId}/dashboard`);
                  requests.shift().resolve(response(200, dashboard(projectId, appId, { applicationName: appName })));
                  await settle();
                }

                (async () => {
                  await loadDashboard();
                  clickEvidence('instance-old', 'pod-old', '/api/projects/project-1/applications/app-1/instances/instance-old/evidence');
                  const oldRequest = requests.shift();
                  clickEvidence('instance-new', 'pod-new', '/api/projects/project-1/applications/app-1/instances/instance-new/evidence');
                  const newRequest = requests.shift();
                  newRequest.resolve(response(200, evidence('project-1', 'app-1', 'instance-new', { instanceName: 'pod-new' })));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /pod-new/);
                  oldRequest.resolve(response(200, evidence('project-1', 'app-1', 'instance-old', { instanceName: 'pod-old' })));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /pod-new/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /pod-old/);

                  clickEvidence('instance-stale-auth', 'pod-stale-auth', '/api/projects/project-1/applications/app-1/instances/instance-stale-auth/evidence');
                  const staleAuthRequest = requests.shift();
                  clickEvidence('instance-active', 'pod-active', '/api/projects/project-1/applications/app-1/instances/instance-active/evidence');
                  const activeRequest = requests.shift();
                  activeRequest.resolve(response(200, evidence('project-1', 'app-1', 'instance-active', { instanceName: 'pod-active' })));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /pod-active/);
                  staleAuthRequest.resolve(response(401, {}));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /pod-active/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /pod-stale-auth|GitHub 로그인 후 Instance Evidence를 볼 수 있습니다|GitHub 로그인 후 Dashboard를 볼 수 있습니다/);

                  clickEvidence('instance-new', 'pod-new', '/api/projects/project-1/applications/app-1/instances/instance-new/evidence');
                  const clearRequest = requests.shift();
                  auth.clearAccessToken();
                  clearRequest.resolve(response(200, evidence('project-1', 'app-1', 'instance-new', { instanceName: 'cleared-pod' })));
                  await settle();
                  assert.doesNotMatch(dashboardDetail.innerHTML, /cleared-pod/);

                  await loadDashboard();
                  clickEvidence('instance-1', 'pod-a', '/api/projects/project-1/applications/app-1/instances/instance-1/evidence');
                  const evidenceRequest = requests.shift();
                  clickDashboard('app-1', 'Orders API', 'prod', '/api/projects/project-1/applications/app-1/dashboard');
                  assert.match(dashboardDetail.innerHTML, /Dashboard를 불러오는 중/);
                  evidenceRequest.resolve(response(200, evidence('project-1', 'app-1', 'instance-1', { instanceName: 'stale-dashboard-pod' })));
                  await settle();
                  assert.doesNotMatch(dashboardDetail.innerHTML, /stale-dashboard-pod/);

                  requests.shift().resolve(response(200, dashboard('project-1', 'app-1', { applicationName: 'Orders API' })));
                  await settle();
                  clickEvidence('instance-1', 'pod-a', '/api/projects/project-1/applications/app-1/instances/instance-1/evidence');
                  const reloadRequest = requests.shift();
                  reloadApplications.listeners.click();
                  assert.match(dashboardDetail.innerHTML, /Project를 선택하면 Application Dashboard를 볼 수 있습니다/);
                  const applicationReloadRequest = requests.shift();
                  reloadRequest.resolve(response(200, evidence('project-1', 'app-1', 'instance-1', { instanceName: 'stale-application-pod' })));
                  await settle();
                  assert.doesNotMatch(dashboardDetail.innerHTML, /stale-application-pod/);
                  applicationReloadRequest.resolve(response(200, {
                    generatedAt: '2026-05-29T01:26:00Z',
                    project: { projectId: 'project-1', name: 'Project project-1' },
                    applications: [application('app-1', { name: 'Orders API', projectId: 'project-1' })]
                  }));
                  await settle();

                  await loadDashboard();
                  clickEvidence('instance-1', 'pod-a', '/api/projects/project-1/applications/app-1/instances/instance-1/evidence');
                  const projectReloadEvidenceRequest = requests.shift();
                  reloadProjects.listeners.click();
                  assert.match(dashboardDetail.innerHTML, /Project를 선택하면 Application Dashboard를 볼 수 있습니다/);
                  const projectReloadRequest = requests.shift();
                  projectReloadEvidenceRequest.resolve(response(200, evidence('project-1', 'app-1', 'instance-1', { instanceName: 'stale-project-reload-pod' })));
                  await settle();
                  assert.doesNotMatch(dashboardDetail.innerHTML, /stale-project-reload-pod/);
                  projectReloadRequest.resolve(response(200, {
                    generatedAt: '2026-05-29T01:30:00Z',
                    projects: [project('project-1', 'Project project-1', '/api/projects/project-1/applications')]
                  }));
                  await settle();
                  assert.doesNotMatch(dashboardDetail.innerHTML, /stale-project-reload-pod/);
                })().catch(error => {
                  console.error(error && error.stack ? error.stack : error);
                  process.exit(1);
                });
                """);
    }

    @Test
    void instanceEvidenceStaticGuardsForbidTokenPersistenceFollowupFetchAndUiRecomputation() throws IOException {
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
                "averageP95",
                "averageP99",
                "maxP95",
                "maxP99",
                "mergePercentile",
                "mergeP95",
                "mergeP99",
                "interpolatePercentile",
                "percentileFromHistogram",
                "histogramToPercentile",
                "rankEndpoint",
                "sortEndpointPriority",
                "deriveInstanceHealth",
                "deriveHostStatus",
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
        assertThat(appJs).contains(
                "fetch(selectedInstanceEvidenceContext.evidenceLink",
                "snapshotHistoryOperationalEventsRequestLink",
                "isSnapshotDetailLink");
        assertThat(appJs).doesNotContain(forbiddenHelpers.toArray(String[]::new));
        assertThat(appJs).doesNotContain(
                "fetch(snapshotDetail",
                "fetch(snapshotHistory",
                "fetch(history",
                "fetch(operational",
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
     * Instance Evidence UI test들이 공유하는 DOM/fetch fixture다. 실제 app.js 상태 머신만 실행하도록 최소 DOM을 제공한다.
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
                        lastAcceptedBucketAt: '2026-05-29T01:10:00Z',
                        freshnessLabel: 'recent'
                      },
                      starterConnection: {
                        statusSource: 'starter_heartbeat',
                        lastHeartbeatAt: '2026-05-29T01:11:00Z',
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
                      generatedAt: '2026-05-29T01:20:00Z',
                      application: {
                        projectId,
                        applicationId,
                        name: overrides.applicationName ?? 'Orders API',
                        environment: overrides.environment ?? 'prod',
                        lastAcceptedBucketAt: '2026-05-29T01:19:30Z',
                        lastHealthyAt: null,
                        sourceWindow: {
                          current: { startUtc: '2026-05-29T01:05:00Z', endUtc: '2026-05-29T01:20:00Z' },
                          baseline: { startUtc: '2026-05-29T00:50:00Z', endUtc: '2026-05-29T01:05:00Z' }
                        },
                        freshness: {
                          lastObservedAt: '2026-05-29T01:19:30Z',
                          staleAt: '2026-05-29T01:21:00Z',
                          downAt: '2026-05-29T01:22:30Z'
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
                        lastHeartbeatAt: '2026-05-29T01:19:45Z',
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
                        instanceId: 'instance-1',
                        instanceName: 'pod-a',
                        lastSeenAt: '2026-05-29T01:19:30Z',
                        links: { evidence: `/api/projects/${projectId}/applications/${applicationId}/instances/instance-1/evidence` }
                      }],
                      snapshot: null
                    };
                  }

                  function evidence(projectId, applicationId, instanceId, overrides = {}) {
                    return {
                      generatedAt: '2026-05-29T01:21:00Z',
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
                        firstSeenAt: '2026-05-29T00:00:00Z',
                        lastSeenAt: '2026-05-29T01:20:30Z'
                      },
                      metricData: {
                        statusSource: 'accepted_bucket',
                        window: {
                          name: 'current_15m',
                          startUtc: '2026-05-29T01:06:00Z',
                          endUtc: '2026-05-29T01:21:00Z',
                          bucketDurationSeconds: 30
                        },
                        lastAcceptedBucketAt: '2026-05-29T01:20:30Z',
                        freshnessLabel: 'current',
                        sampleReadiness: 'sufficient',
                        requestCount: 42,
                        errorCount: 2,
                        errorRate: 0.047619,
                        reason: null
                      },
                      starterConnection: {
                        statusSource: 'starter_heartbeat',
                        lastHeartbeatAt: '2026-05-29T01:20:45Z',
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
                          bucketStartUtc: '2026-05-29T01:19:30Z',
                          bucketEndUtc: '2026-05-29T01:20:00Z',
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
                        bucketEndUtc: '2026-05-29T01:20:30Z',
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
                        items: [{
                          method: 'POST',
                          route: '/orders',
                          endpointKey: 'POST /orders',
                          presenceOnSelectedInstance: 'observed',
                          instanceRequestCount: 20,
                          instanceErrorCount: 2,
                          instanceErrorRate: 0.1,
                          applicationEndpointRequestCount: 100,
                          applicationEndpointErrorCount: 10,
                          applicationEndpointErrorRate: 0.1,
                          instanceRequestShare: 0.2,
                          instanceErrorShare: 0.2,
                          durationBuckets: [{ leMs: 500, count: 20 }],
                          bucketDistributionSource: 'histogram_bucket_distribution',
                          relatedApplicationPriorityRank: 1,
                          localDisplayOrder: 1,
                          relatedRuleIds: ['endpoint_error_spike'],
                          status: 'available',
                          reason: 'application_priority_endpoint_observed_on_selected_instance'
                        }, {
                          method: 'GET',
                          route: '/inventory',
                          endpointKey: 'GET /inventory',
                          presenceOnSelectedInstance: 'not_observed',
                          instanceRequestCount: 0,
                          instanceErrorCount: 0,
                          instanceErrorRate: null,
                          applicationEndpointRequestCount: 80,
                          applicationEndpointErrorCount: 4,
                          applicationEndpointErrorRate: 0.05,
                          instanceRequestShare: null,
                          instanceErrorShare: null,
                          durationBuckets: [],
                          bucketDistributionSource: 'histogram_bucket_distribution',
                          relatedApplicationPriorityRank: 2,
                          localDisplayOrder: 2,
                          relatedRuleIds: [],
                          status: 'missing',
                          reason: 'application_priority_endpoint_not_seen_on_selected_instance'
                        }, {
                          method: 'PATCH',
                          route: '/orders',
                          endpointKey: 'PATCH /orders',
                          presenceOnSelectedInstance: 'insufficient',
                          instanceRequestCount: 0,
                          instanceErrorCount: 0,
                          instanceErrorRate: null,
                          applicationEndpointRequestCount: null,
                          applicationEndpointErrorCount: null,
                          applicationEndpointErrorRate: null,
                          instanceRequestShare: null,
                          instanceErrorShare: null,
                          durationBuckets: [],
                          bucketDistributionSource: 'histogram_bucket_distribution',
                          relatedApplicationPriorityRank: null,
                          localDisplayOrder: 3,
                          relatedRuleIds: [],
                          status: 'insufficient',
                          reason: 'endpoint_evidence_insufficient'
                        }]
                      },
                      links: {
                        self: `/api/projects/${projectId}/applications/${applicationId}/instances/${instanceId}/evidence`,
                        dashboard: `/api/projects/${projectId}/applications/${applicationId}/dashboard`,
                        snapshotTrend: `/api/projects/${projectId}/applications/${applicationId}/instances/${instanceId}/snapshot-trend`
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
                          if (selector === '[data-dashboard-back]') {
                            return null;
                          }
                          return null;
                        }
                      }
                    });
                  }

                  function clickBack() {
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
                    clickApplications,
                    clickDashboard,
                    clickEvidence,
                    clickBack
                  };
                }
                """;
    }
}
