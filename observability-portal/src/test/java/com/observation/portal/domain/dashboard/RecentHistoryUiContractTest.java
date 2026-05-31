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
 * Story 6.7 Snapshot/History static UI가 stored snapshot/history read model만 안전하게 소비하는지 검증한다.
 * 테스트는 실제 dashboard script를 Node VM에서 실행해 history/detail fetch, safe state, stale guard, cached back action을 고정한다.
 */
class RecentHistoryUiContractTest {

    private static final Path STATIC_DASHBOARD = Path.of("src/main/resources/static/dashboard");

    @Test
    void recentHistoryShellUsesFixedHistoryAndSnapshotDetailBoundaries() throws IOException {
        String appJs = Files.readString(STATIC_DASHBOARD.resolve("app.js"));
        String styles = Files.readString(STATIC_DASHBOARD.resolve("styles.css"));

        assertThat(appJs).contains(
                "SNAPSHOT_HISTORY_VIEW_STATE",
                "SNAPSHOT_DETAIL_VIEW_STATE",
                "selectedSnapshotHistoryContext",
                "loadedOperationalEvents",
                "loadedSnapshotMarkers",
                "selectedSnapshotDetailContext",
                "snapshotHistoryOperationalEventsRequestLink",
                "snapshotHistoryMarkersRequestLink",
                "fetch(eventsLink",
                "fetch(markersLink",
                "fetch(selectedSnapshotDetailContext.detailLink",
                "Authorization",
                "Bearer");
        assertThat(sliceFunction(appJs, "snapshotHistoryOperationalEventsRequestLink")).contains(
                "operational-events",
                "limit=${query.eventLimit}");
        assertThat(sliceFunction(appJs, "snapshotHistoryMarkersRequestLink")).contains(
                "snapshot-markers",
                "limit=${query.markerLimit}");
        assertThat(appJs).contains(
                "eventLimit: 50, markerLimit: 50",
                "eventLimit: 100, markerLimit: 168",
                "eventLimit: 100, markerLimit: 336");
        assertThat(sliceFunction(appJs, "isSnapshotDetailLink")).contains(
                "snapshots",
                "selected");
        assertThat(sliceFunction(appJs, "operationalEventItemMarkup")).contains(
                "event.title",
                "event.summary",
                "event.type",
                "event.severity");
        assertThat(sliceFunction(appJs, "snapshotMarkerItemMarkup")).contains(
                "marker.title",
                "marker.summary",
                "marker.type",
                "marker.severity",
                "marker.readMeaning");
        assertThat(sliceFunction(appJs, "snapshotMarkerItemMarkup")).doesNotContain(
                "captureReason ===",
                "switch (marker.captureReason",
                "recovery complete",
                "복구 완료");
        assertThat(styles).contains(
                ".snapshot-history-detail",
                ".snapshot-detail-read-model",
                ".snapshot-history-presets",
                "[data-active-anchor=\"true\"]");
    }

    @Test
    void recentHistoryRuntimeFetchesAuthenticatedHistoryRendersDetailAndUsesCachedBackActions() throws Exception {
        runNodeDashboardContract("""
                const fs = require('fs');
                const vm = require('vm');
                const assert = require('assert');

                const source = fs.readFileSync('src/main/resources/static/dashboard/app.js', 'utf8');
                const harness = createHarness(source);
                const { auth, requests, elements, response, settle, project, application, dashboard,
                  history, markers, detail, clickApplications, clickDashboard, clickSnapshotHistory,
                  clickHistoryPreset, clickSnapshotDetail, clickSnapshotHistoryBack, clickDashboardBack } = harness;
                const dashboardDetail = elements.get('#dashboard-detail');

                async function loadDashboard() {
                  auth.setAccessToken('service-token');
                  requests.shift().resolve(response(200, {
                    generatedAt: '2026-05-31T03:00:00Z',
                    projects: [project('project-1', 'Project One', '/api/projects/project-1/applications')]
                  }));
                  await settle();
                  clickApplications('project-1', 'Project One', '/api/projects/project-1/applications');
                  requests.shift().resolve(response(200, {
                    generatedAt: '2026-05-31T03:02:00Z',
                    project: { projectId: 'project-1', name: 'Project One' },
                    applications: [application('app-1')]
                  }));
                  await settle();
                  clickDashboard('app-1', 'Orders API', 'prod', '/api/projects/project-1/applications/app-1/dashboard');
                  requests.shift().resolve(response(200, dashboard('project-1', 'app-1')));
                  await settle();
                }

                async function loadHistory() {
                  clickSnapshotHistory();
                  assert.strictEqual(requests.length, 2);
                  assert.strictEqual(requests[0].url, '/api/projects/project-1/applications/app-1/operational-events?since=24h&limit=50');
                  assert.strictEqual(requests[1].url, '/api/projects/project-1/applications/app-1/dashboard/snapshot-markers?since=24h&limit=50');
                  assert.strictEqual(requests[0].init.headers.Authorization, 'Bearer service-token');
                  assert.strictEqual(requests[1].init.headers.Authorization, 'Bearer service-token');
                  requests.shift().resolve(response(200, history('project-1', 'app-1')));
                  requests.shift().resolve(response(200, markers('app-1')));
                  await settle();
                }

                (async () => {
                  await loadDashboard();
                  assert.match(dashboardDetail.innerHTML, /Snapshot\\/History/);
                  assert.match(dashboardDetail.innerHTML, /data-snapshot-history-action="true"/);

                  await loadHistory();
                  assert.match(dashboardDetail.innerHTML, /Snapshot\\/History/);
                  assert.match(dashboardDetail.innerHTML, /Project One/);
                  assert.match(dashboardDetail.innerHTML, /Orders API/);
                  assert.match(dashboardDetail.innerHTML, /dashboard_snapshots/);
                  assert.match(dashboardDetail.innerHTML, /occurredAt_desc/);
                  assert.match(dashboardDetail.innerHTML, /capturedAt_asc/);
                  assert.match(dashboardDetail.innerHTML, /degraded_entered/);
                  assert.match(dashboardDetail.innerHTML, /warning/);
                  assert.match(dashboardDetail.innerHTML, /server event title/);
                  assert.match(dashboardDetail.innerHTML, /snapshotDetailAnchor/);
                  assert.match(dashboardDetail.innerHTML, /high_confidence_concern/);
                  assert.match(dashboardDetail.innerHTML, /stored_read_model_point/);
                  assert.match(dashboardDetail.innerHTML, /captureReason/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /rawSnapshotJson|raw bucket|endpoint-timeseries|trace-secret|queryString|복구 완료|장애 해결 완료|정상입니다/);

                  clickHistoryPreset('7d');
                  assert.strictEqual(requests.length, 2);
                  assert.strictEqual(requests[0].url, '/api/projects/project-1/applications/app-1/operational-events?since=7d&limit=100');
                  assert.strictEqual(requests[1].url, '/api/projects/project-1/applications/app-1/dashboard/snapshot-markers?since=7d&limit=168');
                  requests.shift().resolve(response(200, history('project-1', 'app-1', { requestedSince: '7d', limit: 100 })));
                  requests.shift().resolve(response(200, markers('app-1', { requestedSince: '7d', limit: 168 })));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /7d/);

                  clickHistoryPreset('14d');
                  assert.strictEqual(requests.length, 2);
                  assert.strictEqual(requests[0].url, '/api/projects/project-1/applications/app-1/operational-events?since=14d&limit=100');
                  assert.strictEqual(requests[1].url, '/api/projects/project-1/applications/app-1/dashboard/snapshot-markers?since=14d&limit=336');
                  requests.shift().resolve(response(200, history('project-1', 'app-1', { requestedSince: '14d', limit: 100 })));
                  requests.shift().resolve(response(200, markers('app-1', { requestedSince: '14d', limit: 336 })));
                  await settle();

                  clickSnapshotDetail('/api/projects/project-1/applications/app-1/dashboard/snapshots/018f6b9a-2e1a-7d2b-9b2f-4db69d92c241', 'endpoint-evidence-1');
                  assert.strictEqual(requests.length, 1);
                  assert.strictEqual(requests[0].url, '/api/projects/project-1/applications/app-1/dashboard/snapshots/018f6b9a-2e1a-7d2b-9b2f-4db69d92c241');
                  assert.strictEqual(requests[0].init.headers.Authorization, 'Bearer service-token');
                  requests.shift().resolve(response(200, detail('project-1', 'app-1', '018f6b9a-2e1a-7d2b-9b2f-4db69d92c241')));
                  await settle();

                  assert.match(dashboardDetail.innerHTML, /Snapshot Detail/);
                  assert.match(dashboardDetail.innerHTML, /stored_snapshot_detail/);
                  assert.match(dashboardDetail.innerHTML, /currentStateRecalculated/);
                  assert.match(dashboardDetail.innerHTML, /rawReadModelJsonExposed/);
                  assert.match(dashboardDetail.innerHTML, /snapshot metadata/);
                  assert.match(dashboardDetail.innerHTML, /previousState/);
                  assert.match(dashboardDetail.innerHTML, /lastHealthyAt/);
                  assert.match(dashboardDetail.innerHTML, /회복 관찰 중/);
                  assert.match(dashboardDetail.innerHTML, /readModel summary/);
                  assert.match(dashboardDetail.innerHTML, /snapshotEndpointEvidence/);
                  assert.match(dashboardDetail.innerHTML, /data-active-anchor="true"/);
                  assert.match(dashboardDetail.innerHTML, /instanceSummary/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /read_model_json|rawSnapshotJson|trace-secret|service-token|endpoint p95|endpoint p99|복구 완료/);

                  clickSnapshotHistoryBack();
                  assert.match(dashboardDetail.innerHTML, /Snapshot\\/History/);
                  assert.match(dashboardDetail.innerHTML, /server event title/);
                  assert.strictEqual(requests.length, 0);

                  clickSnapshotDetail('/api/projects/project-1/applications/app-1/dashboard/snapshots/018f6b9a-2e1a-7d2b-9b2f-4db69d92c241', 'missing-anchor');
                  requests.shift().resolve(response(200, detail('project-1', 'app-1', '018f6b9a-2e1a-7d2b-9b2f-4db69d92c241')));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /anchorStatus missing/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /현재 문제 없음|정상입니다|복구 완료|장애 해결 완료/);

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
    void recentHistoryRuntimeRendersFailureRecoveryEventsAndMarkersAsServerProvidedFields() throws Exception {
        runNodeDashboardContract("""
                const fs = require('fs');
                const vm = require('vm');
                const assert = require('assert');

                const source = fs.readFileSync('src/main/resources/static/dashboard/app.js', 'utf8');
                const harness = createHarness(source);
                const { auth, requests, elements, response, settle, project, application, dashboard,
                  history, markers, clickApplications, clickDashboard, clickSnapshotHistory } = harness;
                const dashboardDetail = elements.get('#dashboard-detail');

                async function loadDashboard() {
                  auth.setAccessToken('service-token');
                  requests.shift().resolve(response(200, {
                    generatedAt: '2026-05-31T03:00:00Z',
                    projects: [project('project-1', 'Project One', '/api/projects/project-1/applications')]
                  }));
                  await settle();
                  clickApplications('project-1', 'Project One', '/api/projects/project-1/applications');
                  requests.shift().resolve(response(200, {
                    generatedAt: '2026-05-31T03:02:00Z',
                    project: { projectId: 'project-1', name: 'Project One' },
                    applications: [application('app-1')]
                  }));
                  await settle();
                  clickDashboard('app-1', 'Orders API', 'prod', '/api/projects/project-1/applications/app-1/dashboard');
                  requests.shift().resolve(response(200, dashboard('project-1', 'app-1')));
                  await settle();
                }

                function serverEvent(snapshotId, type, severity, title, summary, occurredAt, resolvedAt, stateCode) {
                  return {
                    eventId: 'snapshot:' + snapshotId + ':' + type,
                    type,
                    severity,
                    title,
                    summary,
                    occurredAt,
                    resolvedAt,
                    stateCode,
                    confidence: type === 'degraded_entered' ? 0.84 : null,
                    snapshotId,
                    evidence: {
                      ruleId: type.startsWith('degraded') ? 'endpoint_latency_spike' : null,
                      endpointKey: type.startsWith('degraded') ? 'POST /orders' : null,
                      method: type.startsWith('degraded') ? 'POST' : null,
                      route: type.startsWith('degraded') ? '/orders' : null,
                      snapshotDetailAnchor: type.startsWith('degraded') ? 'endpoint-evidence-1' : null,
                      anchorStatus: type.startsWith('degraded') ? 'resolved' : 'missing',
                      traceId: 'trace-secret'
                    },
                    links: {
                      snapshot: '/api/projects/project-1/applications/app-1/dashboard/snapshots/' + snapshotId
                    }
                  };
                }

                function serverMarker(snapshotId, type, severity, title, summary, capturedAt, captureReason, stateCode) {
                  return {
                    markerId: 'snapshot:' + snapshotId + ':' + type,
                    snapshotId,
                    capturedAt,
                    currentWindowEndUtc: capturedAt,
                    type,
                    severity,
                    readMeaning: 'stored_read_model_point',
                    captureReason,
                    storedApplicationStateCode: stateCode,
                    previousState: {
                      stateCode: 'stale',
                      source: 'previous_dashboard_snapshot',
                      snapshotId: '018f6b9a-2e1a-7d2b-9b2f-4db69d92c111',
                      capturedAt: '2026-05-31T02:00:00Z'
                    },
                    title,
                    summary,
                    recommendedAction: 'server marker next action',
                    confidence: severity === 'warning' ? 0.84 : null,
                    primaryRuleId: type === 'high_confidence_concern' ? 'endpoint_latency_spike' : null,
                    primaryEndpointKey: type === 'high_confidence_concern' ? 'POST /orders' : null,
                    rawSnapshotJson: { hidden: true },
                    links: {
                      snapshot: '/api/projects/project-1/applications/app-1/dashboard/snapshots/' + snapshotId
                    }
                  };
                }

                (async () => {
                  await loadDashboard();
                  clickSnapshotHistory();
                  const failureRecoveryHistory = history('project-1', 'app-1', { empty: true });
                  failureRecoveryHistory.events = [
                    serverEvent('018f6b9a-2e1a-7d2b-9b2f-4db69d92c205', 'recovery_observed', 'info', 'server recovery observed title', 'server recovery observed summary', '2026-05-31T03:20:00Z', null, 'unknown'),
                    serverEvent('018f6b9a-2e1a-7d2b-9b2f-4db69d92c204', 'degraded_resolved', 'info', 'server degraded resolved title', 'server degraded resolved summary', '2026-05-31T03:15:00Z', null, 'active'),
                    serverEvent('018f6b9a-2e1a-7d2b-9b2f-4db69d92c203', 'degraded_entered', 'warning', 'server degraded entered title', 'server degraded entered summary', '2026-05-31T03:10:00Z', '2026-05-31T03:15:00Z', 'degraded'),
                    serverEvent('018f6b9a-2e1a-7d2b-9b2f-4db69d92c202', 'down_entered', 'critical', 'server down entered title', 'server down entered summary', '2026-05-31T03:05:00Z', '2026-05-31T03:20:00Z', 'down'),
                    serverEvent('018f6b9a-2e1a-7d2b-9b2f-4db69d92c201', 'stale_entered', 'warning', 'server stale entered title', 'server stale entered summary', '2026-05-31T03:00:00Z', '2026-05-31T03:20:00Z', 'stale')
                  ];
                  const failureRecoveryMarkers = markers('app-1', { empty: true });
                  failureRecoveryMarkers.emptyState = null;
                  failureRecoveryMarkers.markers = [
                    serverMarker('018f6b9a-2e1a-7d2b-9b2f-4db69d92c301', 'state_change', 'warning', 'server marker stale transition title', 'server marker stale transition summary', '2026-05-31T03:00:00Z', 'state_change', 'stale'),
                    serverMarker('018f6b9a-2e1a-7d2b-9b2f-4db69d92c302', 'state_observation', 'critical', 'server marker down observation title', 'server marker down observation summary', '2026-05-31T03:05:00Z', 'hourly_scheduled', 'down'),
                    serverMarker('018f6b9a-2e1a-7d2b-9b2f-4db69d92c303', 'recovery_observed', 'warning', 'server marker recovery observation title', 'server marker recovery observation summary', '2026-05-31T03:20:00Z', 'state_change', 'unknown')
                  ];
                  requests.shift().resolve(response(200, failureRecoveryHistory));
                  requests.shift().resolve(response(200, failureRecoveryMarkers));
                  await settle();

                  assert.match(dashboardDetail.innerHTML, /stale_entered/);
                  assert.match(dashboardDetail.innerHTML, /down_entered/);
                  assert.match(dashboardDetail.innerHTML, /degraded_entered/);
                  assert.match(dashboardDetail.innerHTML, /degraded_resolved/);
                  assert.match(dashboardDetail.innerHTML, /recovery_observed/);
                  assert.match(dashboardDetail.innerHTML, /server stale entered title/);
                  assert.match(dashboardDetail.innerHTML, /server down entered summary/);
                  assert.match(dashboardDetail.innerHTML, /server degraded entered title/);
                  assert.match(dashboardDetail.innerHTML, /server degraded resolved summary/);
                  assert.match(dashboardDetail.innerHTML, /server recovery observed title/);
                  assert.match(dashboardDetail.innerHTML, /server marker stale transition title/);
                  assert.match(dashboardDetail.innerHTML, /server marker down observation summary/);
                  assert.match(dashboardDetail.innerHTML, /server marker recovery observation title/);
                  assert.match(dashboardDetail.innerHTML, /stored_read_model_point/);
                  assert.match(dashboardDetail.innerHTML, /captureReason/);
                  assert.match(dashboardDetail.innerHTML, /opaque metadata state_change/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /trace-secret|rawSnapshotJson|raw bucket|endpoint-timeseries|event meaning|marker meaning|복구 완료|장애 해결 완료|앱 정상 확정|현재 문제 없음|정상입니다/);
                  assert.strictEqual(requests.length, 0);
                })().catch(error => {
                  console.error(error && error.stack ? error.stack : error);
                  process.exit(1);
                });
                """);
    }

    @Test
    void recentHistoryRuntimeRendersSafeStatesAndRejectsMalformedStaleOrMismatchedResponses() throws Exception {
        runNodeDashboardContract("""
                const fs = require('fs');
                const vm = require('vm');
                const assert = require('assert');

                const source = fs.readFileSync('src/main/resources/static/dashboard/app.js', 'utf8');
                const harness = createHarness(source);
                const { auth, requests, elements, response, settle, project, application, dashboard,
                  history, markers, detail, clickApplications, clickDashboard, clickSnapshotHistory,
                  clickSnapshotDetail, clickHistoryPreset } = harness;
                const dashboardDetail = elements.get('#dashboard-detail');
                const reloadProjects = elements.get('#reload-projects');

                async function loadDashboard() {
                  auth.setAccessToken('token-project-1');
                  requests.shift().resolve(response(200, {
                    generatedAt: '2026-05-31T04:00:00Z',
                    projects: [project('project-1', 'Project One', '/api/projects/project-1/applications')]
                  }));
                  await settle();
                  clickApplications('project-1', 'Project One', '/api/projects/project-1/applications');
                  requests.shift().resolve(response(200, {
                    generatedAt: '2026-05-31T04:02:00Z',
                    project: { projectId: 'project-1', name: 'Project One' },
                    applications: [application('app-1')]
                  }));
                  await settle();
                  clickDashboard('app-1', 'Orders API', 'prod', '/api/projects/project-1/applications/app-1/dashboard');
                  requests.shift().resolve(response(200, dashboard('project-1', 'app-1')));
                  await settle();
                }

                async function resolveHistory(eventBody, markerBody) {
                  requests.shift().resolve(eventBody);
                  requests.shift().resolve(markerBody);
                  await settle();
                }

                (async () => {
                  auth.clearAccessToken();
                  clickSnapshotHistory();
                  assert.strictEqual(requests.length, 0);
                  assert.match(dashboardDetail.innerHTML, /GitHub 로그인 후 Snapshot\\/History를 볼 수 있습니다|GitHub 로그인 후 Dashboard를 볼 수 있습니다/);

                  await loadDashboard();
                  clickSnapshotHistory();
                  await resolveHistory(response(401, { detail: 'service-token provider payload' }), response(401, {}));
                  assert.match(dashboardDetail.innerHTML, /GitHub 로그인 후 Snapshot\\/History를 볼 수 있습니다/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /service-token|provider payload/);

                  await loadDashboard();
                  clickSnapshotHistory();
                  await resolveHistory(response(400, { detail: 'bad since=30d token' }), response(200, markers('app-1')));
                  assert.match(dashboardDetail.innerHTML, /History query contract를 확인할 수 없습니다/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /since=30d|token/);

                  clickSnapshotHistory();
                  await resolveHistory(response(404, { detail: 'missing scope but not down' }), response(404, {}));
                  assert.match(dashboardDetail.innerHTML, /Project\\/Application scope를 찾을 수 없습니다/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /deleted|정상|복구 완료|down/);

                  clickSnapshotHistory();
                  await resolveHistory(response(500, { detail: 'internal stack token' }), response(200, markers('app-1')));
                  assert.match(dashboardDetail.innerHTML, /Snapshot\\/History를 불러오지 못했습니다/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /internal stack|token/);

                  clickSnapshotHistory();
                  await resolveHistory(response(200, history('project-1', 'other-app')), response(200, markers('app-1')));
                  assert.match(dashboardDetail.innerHTML, /Snapshot\\/History를 불러오지 못했습니다/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /other-app/);

                  clickSnapshotHistory();
                  await resolveHistory(response(200, history('project-1', 'app-1', { linkSnapshotId: '018f6b9a-2e1a-7d2b-9b2f-4db69d92c242' })), response(200, markers('app-1')));
                  assert.match(dashboardDetail.innerHTML, /Snapshot\\/History를 불러오지 못했습니다/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /018f6b9a-2e1a-7d2b-9b2f-4db69d92c242/);

                  clickSnapshotHistory();
                  const malformedHistory = history('project-1', 'app-1');
                  malformedHistory.source = 'operational_events';
                  await resolveHistory(response(200, malformedHistory), response(200, markers('app-1')));
                  assert.match(dashboardDetail.innerHTML, /Snapshot\\/History를 불러오지 못했습니다/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /operational_events/);

                  clickSnapshotHistory();
                  await resolveHistory(response(200, history('project-1', 'app-1')), response(200, markers('app-1', { linkSnapshotId: '018f6b9a-2e1a-7d2b-9b2f-4db69d92c242' })));
                  assert.match(dashboardDetail.innerHTML, /Snapshot\\/History를 불러오지 못했습니다/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /018f6b9a-2e1a-7d2b-9b2f-4db69d92c242/);

                  clickSnapshotHistory();
                  await resolveHistory(response(200, history('project-1', 'app-1', { empty: true })), response(200, markers('app-1', { empty: true })));
                  assert.match(dashboardDetail.innerHTML, /operational event source absence 또는 event 후보 없음/);
                  assert.match(dashboardDetail.innerHTML, /marker source absence 또는 retention horizon 안에 표시할 marker 없음/);
                  assert.match(dashboardDetail.innerHTML, /no_snapshots_in_retention/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /현재 문제 없음|정상입니다|복구 완료|장애 해결 완료/);

                  clickSnapshotHistory();
                  const malformedMarkerHistory = markers('app-1');
                  malformedMarkerHistory.markers[0].type = 'custom_marker_type';
                  await resolveHistory(response(200, history('project-1', 'app-1')), response(200, malformedMarkerHistory));
                  assert.match(dashboardDetail.innerHTML, /Snapshot\\/History를 불러오지 못했습니다/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /custom_marker_type/);

                  clickSnapshotHistory();
                  const oldEventRequest = requests.shift();
                  const oldMarkerRequest = requests.shift();
                  clickSnapshotHistory();
                  const newEventRequest = requests.shift();
                  const newMarkerRequest = requests.shift();
                  newEventRequest.resolve(response(200, history('project-1', 'app-1', { title: 'fresh event title' })));
                  newMarkerRequest.resolve(response(200, markers('app-1')));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /fresh event title/);
                  oldEventRequest.resolve(response(200, history('project-1', 'app-1', { title: 'stale event title' })));
                  oldMarkerRequest.resolve(response(200, markers('app-1')));
                  await settle();
                  assert.doesNotMatch(dashboardDetail.innerHTML, /stale event title/);

                  clickSnapshotDetail('/api/projects/project-1/applications/other-app/dashboard/snapshots/018f6b9a-2e1a-7d2b-9b2f-4db69d92c241', '');
                  assert.strictEqual(requests.length, 0);
                  assert.match(dashboardDetail.innerHTML, /Snapshot detail link를 확인할 수 없습니다/);

                  clickSnapshotDetail('/api/projects/project-1/applications/app-1/dashboard/snapshots/not-a-uuid', '');
                  assert.strictEqual(requests.length, 0);
                  assert.match(dashboardDetail.innerHTML, /Snapshot detail link를 확인할 수 없습니다/);

                  clickSnapshotDetail('/api/projects/project-1/applications/app-1/dashboard/snapshots/018f6b9a-2e1a-7d2b-9b2f-4db69d92c241', '');
                  requests.shift().resolve(response(400, { detail: 'invalid_snapshot_id token' }));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /Snapshot detail link를 확인할 수 없습니다/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /invalid_snapshot_id|token/);

                  clickSnapshotDetail('/api/projects/project-1/applications/app-1/dashboard/snapshots/018f6b9a-2e1a-7d2b-9b2f-4db69d92c241', '');
                  requests.shift().resolve(response(404, { detail: 'snapshot_not_found_or_expired token' }));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /저장된 snapshot detail이 없거나 보관 기간이 지나/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /snapshot_not_found_or_expired|current dashboard|token|현재 문제 없음|정상입니다|복구 완료|장애 해결 완료/);

                  clickSnapshotDetail('/api/projects/project-1/applications/app-1/dashboard/snapshots/018f6b9a-2e1a-7d2b-9b2f-4db69d92c241', '');
                  requests.shift().resolve(response(500, { detail: 'internal stack provider payload' }));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /Snapshot detail을 불러오지 못했습니다/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /internal stack|provider payload/);

                  clickSnapshotDetail('/api/projects/project-1/applications/app-1/dashboard/snapshots/018f6b9a-2e1a-7d2b-9b2f-4db69d92c241', '');
                  const mismatchedDetail = detail('project-1', 'app-1', '018f6b9a-2e1a-7d2b-9b2f-4db69d92c242');
                  requests.shift().resolve(response(200, mismatchedDetail));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /Snapshot detail을 불러오지 못했습니다/);

                  clickSnapshotDetail('/api/projects/project-1/applications/app-1/dashboard/snapshots/018f6b9a-2e1a-7d2b-9b2f-4db69d92c241', '');
                  const malformedDetail = detail('project-1', 'app-1', '018f6b9a-2e1a-7d2b-9b2f-4db69d92c241');
                  malformedDetail.marker.type = 'custom_marker_type';
                  requests.shift().resolve(response(200, malformedDetail));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /Snapshot detail을 불러오지 못했습니다/);
                  assert.doesNotMatch(dashboardDetail.innerHTML, /custom_marker_type/);

                  clickSnapshotDetail('/api/projects/project-1/applications/app-1/dashboard/snapshots/018f6b9a-2e1a-7d2b-9b2f-4db69d92c241', '');
                  const oldDetailRequest = requests.shift();
                  clickSnapshotDetail('/api/projects/project-1/applications/app-1/dashboard/snapshots/018f6b9a-2e1a-7d2b-9b2f-4db69d92c242', '');
                  const freshDetailRequest = requests.shift();
                  freshDetailRequest.resolve(response(200, detail('project-1', 'app-1', '018f6b9a-2e1a-7d2b-9b2f-4db69d92c242')));
                  await settle();
                  assert.match(dashboardDetail.innerHTML, /018f6b9a-2e1a-7d2b-9b2f-4db69d92c242/);
                  assert.match(dashboardDetail.innerHTML, /Snapshot Detail/);
                  assert.match(dashboardDetail.innerHTML, /stored_snapshot_detail/);
                  oldDetailRequest.resolve(response(200, detail('project-1', 'app-1', '018f6b9a-2e1a-7d2b-9b2f-4db69d92c241')));
                  await settle();
                  assert.doesNotMatch(dashboardDetail.innerHTML, /018f6b9a-2e1a-7d2b-9b2f-4db69d92c241/);

                          clickHistoryPreset('24h');
                  const projectReloadEventRequest = requests.shift();
                  const projectReloadMarkerRequest = requests.shift();
                  reloadProjects.listeners.click();
                  const projectReloadRequest = requests.shift();
                  projectReloadEventRequest.resolve(response(200, history('project-1', 'app-1', { title: 'reload stale event' })));
                  projectReloadMarkerRequest.resolve(response(200, markers('app-1')));
                  await settle();
                  assert.doesNotMatch(dashboardDetail.innerHTML, /reload stale event/);
                  projectReloadRequest.resolve(response(200, {
                    generatedAt: '2026-05-31T04:30:00Z',
                    projects: [project('project-1', 'Project One', '/api/projects/project-1/applications')]
                  }));
                  await settle();
                })().catch(error => {
                  console.error(error && error.stack ? error.stack : error);
                  process.exit(1);
                });
                """);
    }

    @Test
    void recentHistoryStaticGuardsForbidRawExplorersPersistenceRoutingAndUiRecomputation() throws IOException {
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
                "deriveEndpointPriority",
                "promoteOperationalEvent",
                "buildHistoryEvent",
                "createSnapshotEvent",
                "classifyCaptureReason",
                "deriveMarkerSeverity",
                "deriveRecoveryMarker",
                "foldEventPeriod",
                "applySuppression");

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
                "refresh_token=",
                "rawSnapshotJson",
                "raw bucket",
                "endpoint-timeseries",
                "arbitrary query",
                "traceId",
                "queryString");
        assertThat(appJs).contains(
                "snapshotHistoryOperationalEventsRequestLink",
                "snapshotHistoryMarkersRequestLink",
                "isSnapshotDetailLink");
        assertThat(appJs).doesNotContain(forbiddenHelpers.toArray(String[]::new));
        assertThat(appJs).doesNotContain(
                "fetch('/api/projects/",
                "fetch(`/api/projects/",
                "fetch(endpoint",
                "fetch(raw",
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
     * Recent History UI test들이 공유하는 DOM/fetch fixture다. 실제 app.js 상태 머신만 실행하도록 최소 DOM을 제공한다.
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
                        lastAcceptedBucketAt: '2026-05-31T03:10:00Z',
                        freshnessLabel: 'recent'
                      },
                      starterConnection: {
                        statusSource: 'starter_heartbeat',
                        lastHeartbeatAt: '2026-05-31T03:11:00Z',
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

                  function dashboard(projectId, applicationId) {
                    return {
                      generatedAt: '2026-05-31T03:20:00Z',
                      application: {
                        projectId,
                        applicationId,
                        name: 'Orders API',
                        environment: 'prod',
                        lastAcceptedBucketAt: '2026-05-31T03:19:30Z',
                        lastHealthyAt: null,
                        sourceWindow: {
                          current: { startUtc: '2026-05-31T03:05:00Z', endUtc: '2026-05-31T03:20:00Z' },
                          baseline: { startUtc: '2026-05-31T02:50:00Z', endUtc: '2026-05-31T03:05:00Z' }
                        },
                        freshness: {
                          lastObservedAt: '2026-05-31T03:19:30Z',
                          staleAt: '2026-05-31T03:21:00Z',
                          downAt: '2026-05-31T03:22:30Z'
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
                        lastHeartbeatAt: '2026-05-31T03:19:45Z',
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
                      instances: [],
                      snapshot: null
                    };
                  }

                  function history(projectId, applicationId, overrides = {}) {
                    const requestedSince = overrides.requestedSince ?? '24h';
                    const limit = overrides.limit ?? 50;
                    const eventSnapshotId = overrides.snapshotId ?? snapshotId;
                    const linkSnapshotId = overrides.linkSnapshotId ?? eventSnapshotId;
                    return {
                      generatedAt: '2026-05-31T03:30:00Z',
                      applicationId,
                      source: 'dashboard_snapshots',
                      horizon: {
                        since: '2026-05-30T03:30:00Z',
                        until: '2026-05-31T03:30:00Z',
                        requestedSince,
                        defaultSince: '24h',
                        maxSince: '14d',
                        limit,
                        maxLimit: 100,
                        order: 'occurredAt_desc'
                      },
                      events: overrides.empty ? [] : [{
                        eventId: 'snapshot:' + eventSnapshotId + ':degraded',
                        type: 'degraded_entered',
                        severity: 'warning',
                        title: overrides.title ?? 'server event title',
                        summary: 'server event summary',
                        occurredAt: '2026-05-31T03:00:00Z',
                        resolvedAt: null,
                        stateCode: 'degraded',
                        confidence: 0.84,
                        snapshotId: eventSnapshotId,
                        evidence: {
                          ruleId: 'endpoint_error_spike',
                          endpointKey: 'POST /orders',
                          method: 'POST',
                          route: '/orders',
                          snapshotDetailAnchor: 'endpoint-evidence-1',
                          anchorStatus: 'resolved',
                          traceId: 'trace-secret'
                        },
                        links: {
                          snapshot: `/api/projects/${projectId}/applications/${applicationId}/dashboard/snapshots/${linkSnapshotId}`
                        }
                      }]
                    };
                  }

                  function markers(applicationId, overrides = {}) {
                    const requestedSince = overrides.requestedSince ?? '24h';
                    const limit = overrides.limit ?? 50;
                    const markerSnapshotId = overrides.snapshotId ?? snapshotId;
                    const linkSnapshotId = overrides.linkSnapshotId ?? markerSnapshotId;
                    return {
                      generatedAt: '2026-05-31T03:30:10Z',
                      applicationId,
                      source: 'dashboard_snapshots',
                      horizon: {
                        since: '2026-05-30T03:30:10Z',
                        until: '2026-05-31T03:30:10Z',
                        requestedSince,
                        defaultSince: '24h',
                        maxSince: '14d',
                        limit,
                        maxLimit: 336,
                        order: 'capturedAt_asc'
                      },
                      emptyState: overrides.empty ? { reasonCode: 'no_snapshots_in_retention' } : null,
                      markers: overrides.empty ? [] : [{
                        markerId: 'snapshot:' + markerSnapshotId + ':high_confidence_concern',
                        snapshotId: markerSnapshotId,
                        capturedAt: '2026-05-31T03:00:00Z',
                        currentWindowEndUtc: '2026-05-31T03:00:00Z',
                        type: 'high_confidence_concern',
                        severity: 'warning',
                        readMeaning: 'stored_read_model_point',
                        captureReason: 'high_confidence_concern',
                        storedApplicationStateCode: 'degraded',
                        previousState: {
                          stateCode: 'active',
                          source: 'previous_dashboard_snapshot',
                          snapshotId: '018f6b9a-2e1a-7d2b-9b2f-4db69d92c111',
                          capturedAt: '2026-05-31T02:00:00Z'
                        },
                        title: 'server marker title',
                        summary: 'server marker summary',
                        recommendedAction: 'server marker action',
                        confidence: 0.84,
                        primaryRuleId: 'endpoint_error_spike',
                        primaryEndpointKey: 'POST /orders',
                        links: {
                          snapshot: `/api/projects/project-1/applications/${applicationId}/dashboard/snapshots/${linkSnapshotId}`
                        }
                      }]
                    };
                  }

                  function detail(projectId, applicationId, requestedSnapshotId) {
                    return {
                      generatedAt: '2026-05-31T03:31:00Z',
                      source: 'dashboard_snapshots',
                      readSemantics: {
                        mode: 'stored_snapshot_detail',
                        currentStateRecalculated: false,
                        liveSourcesJoined: [],
                        rawReadModelJsonExposed: false
                      },
                      snapshot: {
                        snapshotId: requestedSnapshotId,
                        capturedAt: '2026-05-31T03:00:00Z',
                        generatedAt: '2026-05-31T03:00:00Z',
                        currentWindow: { startUtc: '2026-05-31T02:45:00Z', endUtc: '2026-05-31T03:00:00Z' },
                        baselineWindow: { startUtc: '2026-05-31T02:30:00Z', endUtc: '2026-05-31T02:45:00Z' },
                        captureReason: 'high_confidence_concern',
                        storedApplicationStateCode: 'degraded',
                        primaryRuleId: 'endpoint_error_spike',
                        primaryEndpointKey: 'POST /orders',
                        maxConfidence: 0.84
                      },
                      marker: markers(applicationId, { snapshotId: requestedSnapshotId }).markers[0],
                      previousState: {
                        stateCode: 'active',
                        source: 'previous_dashboard_snapshot',
                        snapshotId: '018f6b9a-2e1a-7d2b-9b2f-4db69d92c111',
                        capturedAt: '2026-05-31T02:00:00Z'
                      },
                      lastHealthyAt: {
                        value: '2026-05-31T02:00:00Z',
                        source: 'previous_active_snapshot',
                        snapshotId: '018f6b9a-2e1a-7d2b-9b2f-4db69d92c111'
                      },
                      recoveryMarker: {
                        markerId: 'snapshot:' + requestedSnapshotId + ':recovery_observed',
                        type: 'recovery_observed',
                        severity: 'warning',
                        title: '회복 관찰 중',
                        summary: '새 bucket이 들어와 회복 관찰 중입니다.',
                        recommendedAction: '다음 저장 시점까지 관찰하세요.',
                        previousState: {
                          stateCode: 'degraded',
                          source: 'previous_dashboard_snapshot',
                          snapshotId: '018f6b9a-2e1a-7d2b-9b2f-4db69d92c111',
                          capturedAt: '2026-05-31T02:00:00Z'
                        },
                        lastHealthyAt: {
                          value: '2026-05-31T02:00:00Z',
                          source: 'previous_active_snapshot',
                          snapshotId: '018f6b9a-2e1a-7d2b-9b2f-4db69d92c111'
                        }
                      },
                      readModel: {
                        application: { name: 'Orders API', environment: 'prod' },
                        state: { code: 'degraded', label: 'Metric data degraded' },
                        starterConnection: { statusSource: 'starter_heartbeat', connectionMeaning: 'starter_connected' },
                        zeroInsight: null,
                        recovery: { isRecovering: true, recommendedAction: 'server recovery action' },
                        metrics: { requestCount: 42, errorCount: 2, errorRate: 0.047619 },
                        sourceScopedPercentiles: { source: 'starter_local', status: 'available' },
                        triageCards: [{ title: 'server triage title', ruleId: 'endpoint_error_spike', severity: 'warning' }],
                        endpointPriority: [{ endpointKey: 'POST /orders', rank: 1, reason: 'error_and_latency' }]
                      },
                      snapshotEndpointEvidence: {
                        source: 'bounded_endpoint_evidence',
                        maxItems: 10,
                        selectionPolicy: 'endpoint_priority_rank_then_high_confidence_concern_then_triage_affected_endpoint',
                        unavailableReason: null,
                        items: [{
                          anchorId: 'endpoint-evidence-1',
                          method: 'POST',
                          route: '/orders',
                          endpointKey: 'POST /orders',
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
                          recommendedAction: 'server endpoint action',
                          queryString: 'secret=true'
                        }]
                      },
                      instanceSummary: {
                        schemaVersion: '1.0',
                        source: 'bounded_instance_summary',
                        maxItems: 50,
                        selectionPolicy: 'stored_instance_summary_order',
                        unavailableReason: null,
                        items: [{
                          instanceId: 'instance-1',
                          instanceName: 'pod-a',
                          observationStatus: 'observed',
                          metricData: { statusSource: 'accepted_bucket', freshnessLabel: 'current' },
                          starterConnection: { statusSource: 'starter_heartbeat', connectionMeaning: 'starter_connected' },
                          starterPercentilePoint: { source: 'starter_canonical_percentile', requestCount: 10 },
                          resourceHints: { source: 'accepted_bucket_latest_sample', status: 'available' },
                          applicationTriageContribution: { status: 'available', contributed: true, relatedRuleIds: ['endpoint_error_spike'] },
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

                  function clickSnapshotHistory() {
                    elements.get('#dashboard-detail').listeners.click({
                      target: {
                        closest(selector) {
                          if (selector === '[data-snapshot-history-action]') {
                            return {
                              disabled: false,
                              getAttribute(name) {
                                return name === 'aria-disabled' ? 'false' : null;
                              },
                              dataset: { snapshotHistoryAction: 'true' }
                            };
                          }
                          return null;
                        }
                      }
                    });
                  }

                  function clickHistoryPreset(preset) {
                    elements.get('#dashboard-detail').listeners.click({
                      target: {
                        closest(selector) {
                          if (selector === '[data-history-preset]') {
                            return {
                              disabled: false,
                              getAttribute(name) {
                                return name === 'aria-disabled' ? 'false' : null;
                              },
                              dataset: { historyPreset: preset }
                            };
                          }
                          return null;
                        }
                      }
                    });
                  }

                  function clickSnapshotDetail(link, anchor) {
                    elements.get('#dashboard-detail').listeners.click({
                      target: {
                        closest(selector) {
                          if (selector === '[data-snapshot-detail-link]') {
                            return {
                              disabled: false,
                              getAttribute(name) {
                                return name === 'aria-disabled' ? 'false' : null;
                              },
                              dataset: { snapshotDetailLink: link, snapshotDetailAnchor: anchor }
                            };
                          }
                          return null;
                        }
                      }
                    });
                  }

                  function clickSnapshotHistoryBack() {
                    elements.get('#dashboard-detail').listeners.click({
                      target: {
                        closest(selector) {
                          if (selector === '[data-snapshot-history-back]') {
                            return { dataset: { snapshotHistoryBack: 'true' } };
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

                  return {
                    auth: context.window.observationPortalAuth,
                    requests,
                    elements,
                    response,
                    settle,
                    project,
                    application,
                    dashboard,
                    history,
                    markers,
                    detail,
                    clickApplications,
                    clickDashboard,
                    clickSnapshotHistory,
                    clickHistoryPreset,
                    clickSnapshotDetail,
                    clickSnapshotHistoryBack,
                    clickDashboardBack
                  };
                }
                """;
    }
}
