import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import {
  CONTRACT_APPLICATION_ID,
  CONTRACT_INSTANCE_ID,
  CONTRACT_PROJECT_ID,
  CONTRACT_SNAPSHOT_ID,
  dashboardContractFixture,
  instanceDashboardLiveContractFixture,
  instanceDashboardRetentionGapFixture,
  instanceDashboardSummaryCapOutsideFixture,
  instanceDashboardSnapshotContractFixture,
  instanceEvidenceContractFixture,
  instanceTrendContractFixture,
  snapshotDetailContractFixture,
  snapshotEventsContractFixture,
  snapshotMarkersContractFixture,
} from "../src/app/lib/read-model-contract-fixtures.js";
import {
  guardApplicationDashboardReadModel,
  guardInstanceDashboardReadModel,
  guardInstanceEvidenceReadModel,
  guardInstanceSnapshotTrendReadModel,
  guardSnapshotDetailReadModel,
  guardSnapshotHistoryReadModels,
  guardSnapshotMarkerReadModel,
} from "../src/app/lib/read-model-contract-guard.js";
import {
  buildLiveInstanceDashboardPath,
  buildSnapshotInstanceDashboardPath,
  HISTORY_PRESET_QUERY,
  snapshotRetentionDayKeys,
  snapshotSlotDayKey,
  snapshotSlotIndexFromWindowEndUtc,
  snapshotSlotTimeLabel,
  toDashboardPresentation,
  toDisplayLatencyBuckets,
  validateLiveInstanceDashboardPath,
  validateSnapshotInstanceDashboardPath,
} from "../src/app/lib/read-model-adapters.js";

const applicationContext = {
  projectId: CONTRACT_PROJECT_ID,
  applicationId: CONTRACT_APPLICATION_ID,
};

const instanceContext = {
  ...applicationContext,
  instanceId: CONTRACT_INSTANCE_ID,
};

const liveInstanceDashboardPath = buildLiveInstanceDashboardPath(
  CONTRACT_PROJECT_ID,
  CONTRACT_APPLICATION_ID,
  CONTRACT_INSTANCE_ID,
);
assert.equal(
  validateLiveInstanceDashboardPath(
    liveInstanceDashboardPath,
    CONTRACT_PROJECT_ID,
    CONTRACT_APPLICATION_ID,
    CONTRACT_INSTANCE_ID,
  ),
  liveInstanceDashboardPath,
);
assert.throws(
  () =>
    validateLiveInstanceDashboardPath(
      `/api/projects/${CONTRACT_PROJECT_ID}/applications/${CONTRACT_APPLICATION_ID}/instances/other/dashboard`,
      CONTRACT_PROJECT_ID,
      CONTRACT_APPLICATION_ID,
      CONTRACT_INSTANCE_ID,
    ),
  /live_instance_dashboard_link_invalid/,
);
const snapshotInstanceDashboardPath = buildSnapshotInstanceDashboardPath(
  CONTRACT_PROJECT_ID,
  CONTRACT_APPLICATION_ID,
  CONTRACT_SNAPSHOT_ID,
  CONTRACT_INSTANCE_ID,
);
assert.equal(
  validateSnapshotInstanceDashboardPath(
    snapshotInstanceDashboardPath,
    CONTRACT_PROJECT_ID,
    CONTRACT_APPLICATION_ID,
    CONTRACT_SNAPSHOT_ID,
    CONTRACT_INSTANCE_ID,
  ),
  snapshotInstanceDashboardPath,
);
assert.throws(
  () =>
    validateSnapshotInstanceDashboardPath(
      liveInstanceDashboardPath,
      CONTRACT_PROJECT_ID,
      CONTRACT_APPLICATION_ID,
      CONTRACT_SNAPSHOT_ID,
      CONTRACT_INSTANCE_ID,
    ),
  /snapshot_instance_dashboard_link_invalid/,
);

const guardedDashboard = guardApplicationDashboardReadModel(dashboardContractFixture, applicationContext);
assert.equal(guardedDashboard.schemaVersion, "dashboard_read_model.v1");
assert.equal(guardedDashboard.mode, "live");
assert.equal(guardedDashboard.window.type, "recent_30_minutes");
assert.equal(guardedDashboard.application.sourceWindow.baseline, null);
assert.equal(guardedDashboard.readSemantics.source, "accepted_metric_buckets");
assert.equal(guardedDashboard.readSemantics.bucketDistributionSource, "accepted_bucket");
assert.equal(guardedDashboard.readSemantics.histogramBucketsUsedForPercentiles, false);
assert.equal(guardedDashboard.endpointPriority, dashboardContractFixture.endpointPriority);
const dashboardPresentation = toDashboardPresentation(guardedDashboard);
assert.equal(dashboardPresentation.endpointPriority, dashboardContractFixture.endpointPriority);
assert.deepEqual(
  guardedDashboard.firstLookCandidates.map((item) => `${item.rank}:${item.target}`),
  ["2:GET /z-contract", "1:heap"],
);
assert.deepEqual(
  guardedDashboard.endpointPriority.map((item) => `${item.rank}:${item.endpointKey}`),
  ["20:GET /z-contract", "10:POST /a-contract"],
);
assert.equal(guardedDashboard.histogramDistribution.current.buckets[1].count, 70);
assert.deepEqual(
  toDisplayLatencyBuckets(guardedDashboard.histogramDistribution.current.buckets).map((bucket) => bucket.count),
  [7, 63, 21],
);

assert.throws(
  () =>
    guardApplicationDashboardReadModel(
      {
        ...dashboardContractFixture,
        sourceScopedPercentiles: {
          ...dashboardContractFixture.sourceScopedPercentiles,
          aggregatePolicy: "average_percentiles",
        },
      },
      applicationContext,
    ),
  /dashboard_contract_mismatch/,
);

assert.throws(
  () =>
    guardApplicationDashboardReadModel(
      {
        ...dashboardContractFixture,
        signals: {
          ...dashboardContractFixture.signals,
          use: {
            ...dashboardContractFixture.signals.use,
            datasourcePoolUsage: undefined,
          },
        },
      } as unknown as typeof dashboardContractFixture,
      applicationContext,
    ),
  /dashboard_contract_mismatch/,
);

assert.throws(
  () =>
    guardApplicationDashboardReadModel(
      {
        ...dashboardContractFixture,
        stateReasons: [
          {
            ...dashboardContractFixture.stateReasons[0],
            operatorText: "",
          },
        ],
      },
      applicationContext,
    ),
  /dashboard_contract_mismatch/,
);

assert.throws(
  () =>
    guardApplicationDashboardReadModel(
      {
        ...dashboardContractFixture,
        histogramDistribution: {
          ...dashboardContractFixture.histogramDistribution,
          aggregatePolicy: "calculate_p95_from_histogram",
        },
      },
      applicationContext,
    ),
  /dashboard_contract_mismatch/,
);

assert.throws(
  () =>
    guardApplicationDashboardReadModel(
      {
        ...dashboardContractFixture,
        application: {
          ...dashboardContractFixture.application,
          sourceWindow: {
            ...dashboardContractFixture.application.sourceWindow,
            baseline: {
              startUtc: "2026-06-08T23:05:00Z",
              endUtc: "2026-06-08T23:35:00Z",
            },
          },
        },
      },
      applicationContext,
    ),
  /dashboard_contract_mismatch/,
);

assert.throws(
  () =>
    guardApplicationDashboardReadModel(
      {
        ...dashboardContractFixture,
        readSemantics: {
          ...dashboardContractFixture.readSemantics,
          source: "accepted_bucket",
        },
      },
      applicationContext,
    ),
  /dashboard_contract_mismatch/,
);

assert.throws(
  () =>
    guardApplicationDashboardReadModel(
      {
        ...dashboardContractFixture,
        endpointPriority: [
          {
            ...dashboardContractFixture.endpointPriority[0],
            evidence: {
              ...dashboardContractFixture.endpointPriority[0].evidence,
              bucketDistributionSource: "histogram_bucket_distribution",
            },
          },
        ],
      },
      applicationContext,
    ),
  /dashboard_contract_mismatch/,
);

const guardedHistory = guardSnapshotHistoryReadModels(snapshotEventsContractFixture, snapshotMarkersContractFixture, {
  applicationId: CONTRACT_APPLICATION_ID,
  eventLimit: 50,
  markerLimit: 48,
  preset: "24h",
});
assert.equal(guardedHistory.events.events, snapshotEventsContractFixture.events);
assert.equal(guardedHistory.markers.markers, snapshotMarkersContractFixture.markers);
assert.deepEqual(
  guardedHistory.events.events.map((event) => event.eventId),
  ["event-server-first", "event-server-second"],
);
assert.deepEqual(
  guardedHistory.markers.markers.map((marker) => `${marker.markerId}:${marker.storedApplicationStateCode}`),
  ["marker-server-second:down", "marker-server-first:active"],
);
assert.equal(guardedHistory.markers.horizon.limit, 48);
assert.equal(guardedHistory.markers.horizon.maxLimit, 672);
assert.equal(guardedHistory.markers.horizon.order, "currentWindowEndUtc_asc");
assert.equal(guardedHistory.markers.markers[1].captureReason, "hourly_scheduled");
assert.equal(guardedHistory.markers.markers[1].type, "normal");
assert.notEqual(
  guardedHistory.markers.markers[1].type,
  guardedHistory.markers.markers[1].storedApplicationStateCode,
  "markerBucket은 stored state source가 아니어야 한다",
);
const guardedMarkerOnly = guardSnapshotMarkerReadModel(snapshotMarkersContractFixture, {
  applicationId: CONTRACT_APPLICATION_ID,
  markerLimit: HISTORY_PRESET_QUERY["24h"].markerLimit,
  preset: "24h",
});
assert.equal(guardedMarkerOnly.markers, snapshotMarkersContractFixture.markers);
assert.deepEqual(snapshotRetentionDayKeys("2026-06-09T00:00:00Z", 3), ["2026-06-08", "2026-06-07", "2026-06-06"]);
assert.deepEqual(snapshotRetentionDayKeys("2026-06-09T00:30:00Z", 3), ["2026-06-09", "2026-06-08", "2026-06-07"]);
assert.equal(snapshotSlotDayKey("2026-06-09T00:00:00Z"), "2026-06-08");
assert.equal(snapshotSlotDayKey("2026-06-09T00:30:00Z"), "2026-06-09");
assert.equal(snapshotSlotIndexFromWindowEndUtc("2026-06-09T00:00:00Z"), 47);
assert.equal(snapshotSlotIndexFromWindowEndUtc("2026-06-09T00:30:00Z"), 0);
assert.equal(snapshotSlotIndexFromWindowEndUtc("2026-06-09T23:30:00Z"), 46);
assert.equal(snapshotSlotTimeLabel(0), "00:30Z");
assert.equal(snapshotSlotTimeLabel(47), "24:00Z");
assert.throws(
  () =>
    guardSnapshotHistoryReadModels(
      snapshotEventsContractFixture,
      {
        ...snapshotMarkersContractFixture,
        markers: [...snapshotMarkersContractFixture.markers].reverse(),
      },
      {
        applicationId: CONTRACT_APPLICATION_ID,
        eventLimit: 50,
        markerLimit: 48,
        preset: "24h",
      },
    ),
  /snapshot_history_context_mismatch/,
);
assert.throws(
  () =>
    guardSnapshotHistoryReadModels(
      snapshotEventsContractFixture,
      {
        ...snapshotMarkersContractFixture,
        markers: [
          {
            ...snapshotMarkersContractFixture.markers[0],
            currentWindowEndUtc: "2026-06-08T00:45:00Z",
          },
        ],
      },
      {
        applicationId: CONTRACT_APPLICATION_ID,
        eventLimit: 50,
        markerLimit: 48,
        preset: "24h",
      },
    ),
  /snapshot_history_context_mismatch/,
);
assert.throws(
  () =>
    guardSnapshotHistoryReadModels(
      snapshotEventsContractFixture,
      {
        ...snapshotMarkersContractFixture,
        markers: [
          {
            ...snapshotMarkersContractFixture.markers[0],
            currentWindowEndUtc: "2026-06-07T23:30:00Z",
          },
        ],
      },
      {
        applicationId: CONTRACT_APPLICATION_ID,
        eventLimit: 50,
        markerLimit: 48,
        preset: "24h",
      },
    ),
  /snapshot_history_context_mismatch/,
);

assert.throws(
  () =>
    guardSnapshotHistoryReadModels(
      snapshotEventsContractFixture,
      {
        ...snapshotMarkersContractFixture,
        markers: [
          {
            ...snapshotMarkersContractFixture.markers[0],
            readMeaning: "lifecycle_state_source",
          },
        ],
      },
      {
        applicationId: CONTRACT_APPLICATION_ID,
        eventLimit: 50,
        markerLimit: 48,
        preset: "24h",
      },
    ),
  /snapshot_history_context_mismatch/,
);

const guardedDetail = guardSnapshotDetailReadModel(snapshotDetailContractFixture, {
  snapshotId: CONTRACT_SNAPSHOT_ID,
});
assert.equal(guardedDetail.readSemantics.source, "dashboard_snapshots.read_model_json");
assert.equal(guardedDetail.readSemantics.snapshotDetailRecalculates, false);
assert.equal(guardedDetail.readSemantics.markerIsStateSource, false);
assert.equal(guardedDetail.marker.storedApplicationStateCode, "active");
assert.equal(guardedDetail.marker.type, "normal");
assert.ok(Array.isArray(guardedDetail.readModel.attentionEvidence));
assert.equal(guardedDetail.readModel.attentionEvidence.length, 1);

assert.throws(
  () =>
    guardSnapshotDetailReadModel(
      {
        ...snapshotDetailContractFixture,
        readSemantics: {
          ...snapshotDetailContractFixture.readSemantics,
          markerIsStateSource: true,
        },
      },
      { snapshotId: CONTRACT_SNAPSHOT_ID },
    ),
  /snapshot_detail_contract_mismatch/,
);
assert.throws(
  () =>
    guardSnapshotDetailReadModel(
      {
        ...snapshotDetailContractFixture,
        readModel: {
          ...snapshotDetailContractFixture.readModel,
          state: null,
        },
      },
      { snapshotId: CONTRACT_SNAPSHOT_ID },
    ),
  /snapshot_detail_contract_mismatch/,
);

assert.throws(
  () =>
    guardSnapshotDetailReadModel(
      {
        ...snapshotDetailContractFixture,
        readSemantics: {
          ...snapshotDetailContractFixture.readSemantics,
          source: "dashboard_snapshots",
        },
      },
      { snapshotId: CONTRACT_SNAPSHOT_ID },
    ),
  /snapshot_detail_contract_mismatch/,
);

const guardedLiveInstanceDashboard = guardInstanceDashboardReadModel(instanceDashboardLiveContractFixture, {
  ...instanceContext,
  mode: "live",
});
assert.equal(guardedLiveInstanceDashboard.schemaVersion, "instance_dashboard_read_model.v1");
assert.equal(guardedLiveInstanceDashboard.mode, "live");
assert.equal(guardedLiveInstanceDashboard.window.name, "recent_30_minutes");
assert.equal(guardedLiveInstanceDashboard.window.windowSource, "live_recent_30_minutes");
assert.equal(guardedLiveInstanceDashboard.snapshot, null);
assert.equal(guardedLiveInstanceDashboard.readSemantics.source, "accepted_metric_buckets");
assert.equal(guardedLiveInstanceDashboard.readSemantics.snapshotRowSource, null);
assert.equal(guardedLiveInstanceDashboard.readSemantics.instanceEvidenceReconstructedFromMetrics, false);
assert.equal(guardedLiveInstanceDashboard.applicationStateRef.lifecycleOwner, "application");
assert.deepEqual(
  guardedLiveInstanceDashboard.endpointEvidence.items.map((item) => `${item.localDisplayOrder}:${item.endpointKey}`),
  ["2:GET /z-contract", "1:POST /a-contract"],
);

const guardedSnapshotInstanceDashboard = guardInstanceDashboardReadModel(instanceDashboardSnapshotContractFixture, {
  ...instanceContext,
  mode: "snapshot",
  snapshotId: CONTRACT_SNAPSHOT_ID,
});
assert.equal(guardedSnapshotInstanceDashboard.mode, "snapshot");
assert.equal(guardedSnapshotInstanceDashboard.window.windowSource, "selected_application_snapshot");
assert.equal(guardedSnapshotInstanceDashboard.snapshot?.snapshotRowSource, "dashboard_snapshots");
assert.equal(guardedSnapshotInstanceDashboard.readSemantics.snapshotRowSource, "dashboard_snapshots");
assert.equal(guardedSnapshotInstanceDashboard.readSemantics.acceptedAtCutoffApplied, false);
assert.equal(guardedSnapshotInstanceDashboard.readSemantics.includesLateAcceptedMetrics, true);
assert.equal(guardedSnapshotInstanceDashboard.readSemantics.mayDifferFromStoredApplicationSnapshot, true);
assert.equal(guardedSnapshotInstanceDashboard.readSemantics.applicationSnapshotRecalculated, false);
assert.equal(guardedSnapshotInstanceDashboard.readSemantics.instanceEvidenceReconstructedFromMetrics, true);
assert.equal(guardedSnapshotInstanceDashboard.readSemantics.markerIsStateSource, false);
assert.equal(
  guardedSnapshotInstanceDashboard.endpointEvidence.items[0].presenceOnSelectedInstance,
  "not_observed",
);
assert.equal(
  guardInstanceDashboardReadModel(instanceDashboardRetentionGapFixture, {
    ...instanceContext,
    mode: "snapshot",
    snapshotId: CONTRACT_SNAPSHOT_ID,
  }).observationStatus.code,
  "metric_missing",
);
assert.equal(
  guardInstanceDashboardReadModel(instanceDashboardSummaryCapOutsideFixture, {
    ...instanceContext,
    mode: "snapshot",
    snapshotId: CONTRACT_SNAPSHOT_ID,
  }).readSemantics.source,
  "accepted_metric_buckets",
);
assert.equal(
  guardInstanceDashboardReadModel(instanceDashboardSummaryCapOutsideFixture, {
    ...instanceContext,
    mode: "snapshot",
    snapshotId: CONTRACT_SNAPSHOT_ID,
  }).applicationContribution.reason,
  "selected_instance_outside_snapshot_summary_cap_reconstructed_from_metrics",
);
assert.equal(
  guardInstanceDashboardReadModel(instanceDashboardSummaryCapOutsideFixture, {
    ...instanceContext,
    mode: "snapshot",
    snapshotId: CONTRACT_SNAPSHOT_ID,
  }).dataQuality.limitations.some((item) => item.includes("stored instanceSummary.items[]")),
  true,
);

assert.throws(
  () =>
    guardInstanceDashboardReadModel(
      {
        ...instanceDashboardLiveContractFixture,
        readSemantics: {
          ...instanceDashboardLiveContractFixture.readSemantics,
          includesLateAcceptedMetrics: true,
        },
      },
      { ...instanceContext, mode: "live" },
    ),
  /instance_dashboard_contract_mismatch/,
);

assert.throws(
  () =>
    guardInstanceDashboardReadModel(
      {
        ...instanceDashboardSnapshotContractFixture,
        readSemantics: {
          ...instanceDashboardSnapshotContractFixture.readSemantics,
          acceptedAtCutoffApplied: true,
        },
      },
      { ...instanceContext, mode: "snapshot", snapshotId: CONTRACT_SNAPSHOT_ID },
    ),
  /instance_dashboard_contract_mismatch/,
);

assert.throws(
  () =>
    guardInstanceDashboardReadModel(
      {
        ...instanceDashboardSnapshotContractFixture,
        stateCode: "down",
      } as unknown as typeof instanceDashboardSnapshotContractFixture,
      { ...instanceContext, mode: "snapshot", snapshotId: CONTRACT_SNAPSHOT_ID },
    ),
  /instance_dashboard_contract_mismatch/,
);

assert.throws(
  () =>
    guardInstanceDashboardReadModel(
      {
        ...instanceDashboardSnapshotContractFixture,
        applicationStateRef: {
          ...instanceDashboardSnapshotContractFixture.applicationStateRef,
          lifecycleOwner: "instance",
        },
      },
      { ...instanceContext, mode: "snapshot", snapshotId: CONTRACT_SNAPSHOT_ID },
    ),
  /instance_dashboard_contract_mismatch/,
);

assert.throws(
  () =>
    guardInstanceDashboardReadModel(
      {
        ...instanceDashboardSnapshotContractFixture,
        endpointEvidence: {
          ...instanceDashboardSnapshotContractFixture.endpointEvidence,
          items: [
            {
              ...instanceDashboardSnapshotContractFixture.endpointEvidence.items[0],
              healthScore: 100,
            },
          ],
        },
      } as unknown as typeof instanceDashboardSnapshotContractFixture,
      { ...instanceContext, mode: "snapshot", snapshotId: CONTRACT_SNAPSHOT_ID },
    ),
  /instance_dashboard_contract_mismatch/,
);

assert.throws(
  () =>
    guardInstanceDashboardReadModel(
      {
        ...instanceDashboardSnapshotContractFixture,
        instance: {
          ...instanceDashboardSnapshotContractFixture.instance,
          stateCode: "active",
        },
      } as unknown as typeof instanceDashboardSnapshotContractFixture,
      { ...instanceContext, mode: "snapshot", snapshotId: CONTRACT_SNAPSHOT_ID },
    ),
  /instance_dashboard_contract_mismatch/,
);

assert.throws(
  () =>
    guardInstanceDashboardReadModel(
      {
        ...instanceDashboardSnapshotContractFixture,
        instanceSummary: {
          items: [
            {
              instanceId: CONTRACT_INSTANCE_ID,
            },
          ],
        },
      } as unknown as typeof instanceDashboardSnapshotContractFixture,
      { ...instanceContext, mode: "snapshot", snapshotId: CONTRACT_SNAPSHOT_ID },
    ),
  /instance_dashboard_contract_mismatch/,
);

const guardedEvidence = guardInstanceEvidenceReadModel(instanceEvidenceContractFixture, instanceContext);
assert.equal(guardedEvidence.endpointEvidence.items, instanceEvidenceContractFixture.endpointEvidence.items);
assert.deepEqual(
  guardedEvidence.endpointEvidence.items.map((item) => `${item.localDisplayOrder}:${item.endpointKey}`),
  ["2:GET /z-contract", "1:POST /a-contract"],
);

assert.throws(
  () =>
    guardInstanceEvidenceReadModel(
      {
        ...instanceEvidenceContractFixture,
        state: { code: "down" },
      } as typeof instanceEvidenceContractFixture,
      instanceContext,
    ),
  /instance_evidence_contract_mismatch/,
);

const guardedTrend = guardInstanceSnapshotTrendReadModel(instanceTrendContractFixture, {
  ...instanceContext,
  limit: 336,
  preset: "7d",
});
assert.equal(guardedTrend.points, instanceTrendContractFixture.points);
assert.deepEqual(
  guardedTrend.points.map((point) => `${point.snapshotId}:${point.storedApplicationStateCode}`),
  [`${CONTRACT_SNAPSHOT_ID}:active`, "44444444-4444-4444-4444-444444444444:down"],
);

assert.throws(
  () =>
    guardInstanceSnapshotTrendReadModel(
      {
        ...instanceTrendContractFixture,
        points: [
          {
            ...instanceTrendContractFixture.points[0],
            healthScore: 100,
          },
        ],
      } as unknown as typeof instanceTrendContractFixture,
      { ...instanceContext, limit: 336, preset: "7d" },
    ),
  /instance_snapshot_trend_contract_mismatch/,
);

assert.throws(
  () =>
    guardInstanceEvidenceReadModel(
      {
        ...instanceEvidenceContractFixture,
        endpointEvidence: {
          ...instanceEvidenceContractFixture.endpointEvidence,
          items: [
            {
              ...instanceEvidenceContractFixture.endpointEvidence.items[0],
              rootCause: "client-side-root-cause",
            },
          ],
        },
      } as unknown as typeof instanceEvidenceContractFixture,
      instanceContext,
    ),
  /instance_evidence_contract_mismatch/,
);

assert.throws(
  () =>
    guardInstanceSnapshotTrendReadModel(
      {
        ...instanceTrendContractFixture,
        points: [
          {
            ...instanceTrendContractFixture.points[0],
            metricData: {
              ...instanceTrendContractFixture.points[0].metricData,
              currentState: "healthy",
            },
          },
        ],
      } as unknown as typeof instanceTrendContractFixture,
      { ...instanceContext, limit: 336, preset: "7d" },
    ),
  /instance_snapshot_trend_contract_mismatch/,
);

for (const path of [
  "src/app/components/dashboard.tsx",
  "src/app/components/instance-dashboard-surface.tsx",
  "src/app/components/instance-panels.tsx",
  "src/app/components/snapshot-detail-surface.tsx",
  "src/app/components/snapshot-history-panel.tsx",
]) {
  const source = readFileSync(path, "utf8");
  assert.equal(/\.sort\(|\.toSorted\(|\.reduce\(/.test(source), false, `${path} must preserve server order`);
}

// Story 14.2: Live dashboard와 Snapshot/History는 tab으로 분리하지 않고 같은 main flow anchor를 유지한다.
const dashboardSource = readFileSync("src/app/components/dashboard.tsx", "utf8");
assert.equal(/from "\.\/ui\/tabs"/.test(dashboardSource), false, "DashboardMain must not reintroduce tab-only dashboard flow");
assert.equal(/<Tabs|TabsList|TabsTrigger|TabsContent/.test(dashboardSource), false, "DashboardMain must keep Snapshot/History in the same flow");
const dashboardFlowAnchors = [
  "<DashboardContext",
  "<DataQualityFreshnessStrip",
  "<LifecycleStateHero",
  "<DirectStateReasonsPanel",
  "<AttentionAndFirstLookPanel",
  "<EndpointResourceEvidencePanel",
  "<MetricDetailSection",
  "<StarterConnectionStrip",
  "<InstancesPanel",
  "<SnapshotHistoryPanel",
];
let previousDashboardAnchor = -1;
for (const anchor of dashboardFlowAnchors) {
  const currentDashboardAnchor = dashboardSource.indexOf(anchor);
  assert.notEqual(currentDashboardAnchor, -1, `DashboardMain missing ${anchor}`);
  assert.ok(currentDashboardAnchor > previousDashboardAnchor, `DashboardMain order regression at ${anchor}`);
  previousDashboardAnchor = currentDashboardAnchor;
}

const instanceDashboardSurfaceSource = readFileSync("src/app/components/instance-dashboard-surface.tsx", "utf8");
assert.match(instanceDashboardSurfaceSource, /buildLiveInstanceDashboardPath/);
assert.match(instanceDashboardSurfaceSource, /buildSnapshotInstanceDashboardPath/);
assert.match(instanceDashboardSurfaceSource, /selected instance에서 관찰되지 않음/);
assert.match(instanceDashboardSurfaceSource, /Application Snapshot 자체는 dashboard_snapshots\.read_model_json/);
assert.match(instanceDashboardSurfaceSource, /stored Application Snapshot state\/evidence를 override, 검증, 대체하지 않습니다/);
const instanceDashboardModalAnchors = [
  "<InstanceContextNote",
  "<ApplicationStateReferencePanel",
  "<ReadSemanticsPanel",
  "<MetricGrid",
  "<EndpointEvidencePanel",
  "<ResourceEvidencePanel",
  "<StarterConnectionPanel",
  "<NormalizedEndpointEvidenceTable",
];
let previousInstanceDashboardAnchor = -1;
for (const anchor of instanceDashboardModalAnchors) {
  const currentInstanceDashboardAnchor = instanceDashboardSurfaceSource.indexOf(anchor);
  assert.notEqual(currentInstanceDashboardAnchor, -1, `InstanceDashboardSurface missing ${anchor}`);
  assert.ok(currentInstanceDashboardAnchor > previousInstanceDashboardAnchor, `InstanceDashboardSurface modal order regression at ${anchor}`);
  previousInstanceDashboardAnchor = currentInstanceDashboardAnchor;
}
assert.equal(/<ContextHeader/.test(instanceDashboardSurfaceSource), false, "Instance modal body must not insert an extra header panel before Application state reference");
assert.match(instanceDashboardSurfaceSource, /InfoCell label="mode"/);
assert.match(instanceDashboardSurfaceSource, /InfoCell label="source"/);
assert.match(instanceDashboardSurfaceSource, /InfoCell label="instance top-level state" value="없음"/);
assert.equal(
  /not_observed.*(정상|문제 없음|복구 완료)|(정상|문제 없음|복구 완료).*not_observed/.test(instanceDashboardSurfaceSource),
  false,
);
assert.equal(/healthScore|rootCause|recoveryProof/.test(instanceDashboardSurfaceSource), false);

const instancePanelsSource = readFileSync("src/app/components/instance-panels.tsx", "utf8");
assert.match(instancePanelsSource, /DialogContent/);
assert.match(instancePanelsSource, /w-\[min\(1120px,calc\(100vw-2rem\)\)\]/);
assert.match(instancePanelsSource, /DialogHeader className="[^"]*sticky[^"]*top-0/);
assert.match(instancePanelsSource, /snapshot-dashboard/);
assert.match(instancePanelsSource, /dashboard_snapshots\.read_model_json\.instanceSummary\.items\[\] stored projection/);

const snapshotDetailSurfaceSource = readFileSync("src/app/components/snapshot-detail-surface.tsx", "utf8");
const snapshotHistoryPanelSource = readFileSync("src/app/components/snapshot-history-panel.tsx", "utf8");
assert.match(snapshotHistoryPanelSource, /scheduled points/);
assert.match(snapshotHistoryPanelSource, /48\/day/);
assert.match(snapshotHistoryPanelSource, /default view/);
assert.match(snapshotHistoryPanelSource, /cleanup/);
assert.match(snapshotHistoryPanelSource, /Selected snapshot summary/);
assert.match(snapshotHistoryPanelSource, /currentWindowEndUtc/);
assert.match(snapshotHistoryPanelSource, /30분 정기 저장/);
assert.match(snapshotHistoryPanelSource, /Secondary server marker order/);
assert.match(snapshotHistoryPanelSource, /buildSnapshotHistoryPaths\(selectedProject\.projectId, selectedApplication\.applicationId, "14d"\)/);
assert.match(snapshotHistoryPanelSource, /guardSnapshotMarkerReadModel/);
assert.match(snapshotHistoryPanelSource, /retentionMarkers/);
assert.match(snapshotHistoryPanelSource, /snapshotRetentionDayKeys\(markers\.horizon\.until, SNAPSHOT_RETENTION_DAYS\)/);
assert.match(snapshotHistoryPanelSource, /snapshotSlotDayKey\(marker\.currentWindowEndUtc\)/);
assert.match(snapshotHistoryPanelSource, /snapshotSlotIndexFromWindowEndUtc\(marker\.currentWindowEndUtc\)/);
assert.match(snapshotHistoryPanelSource, /snapshotSlotTimeLabel\(slotIndex\)/);
assert.equal(/raw snapshot|endpoint timeseries|arbitrary query|retention fallback|current fallback/.test(snapshotHistoryPanelSource), false);
assert.equal(/현재 dashboard 보기|현재 상태로 대체|current dashboard fallback|live\/current fallback/.test(snapshotHistoryPanelSource), false);
assert.match(snapshotDetailSurfaceSource, /Application Dashboard \/ Snapshot/);
assert.match(snapshotDetailSurfaceSource, /dashboard_snapshots\.read_model_json에 저장된 과거 dashboard surface/);
assert.match(snapshotDetailSurfaceSource, /currentWindowStartUtc/);
assert.match(snapshotDetailSurfaceSource, /보관 기간이 지났거나 저장된 snapshot을 찾을 수 없습니다/);
assert.match(snapshotDetailSurfaceSource, /live dashboard\/current accepted bucket으로 복원하지 않습니다/);
assert.match(snapshotDetailSurfaceSource, /Instance Dashboard snapshot detail의 필수 source로 사용하지 않습니다/);
assert.match(snapshotDetailSurfaceSource, /Instance snapshot dashboard/);
assert.equal(/raw snapshot|endpoint timeseries|arbitrary query|retention fallback|current fallback/.test(snapshotDetailSurfaceSource), false);
assert.equal(/현재 dashboard 보기|현재 상태로 대체|current dashboard fallback|live\/current fallback/.test(snapshotDetailSurfaceSource), false);

console.log("read-model contract guard fixtures passed");
