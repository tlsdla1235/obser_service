import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import {
  CONTRACT_APPLICATION_ID,
  CONTRACT_INSTANCE_ID,
  CONTRACT_PROJECT_ID,
  CONTRACT_SNAPSHOT_ID,
  dashboardContractFixture,
  instanceEvidenceContractFixture,
  instanceTrendContractFixture,
  snapshotDetailContractFixture,
  snapshotEventsContractFixture,
  snapshotMarkersContractFixture,
} from "../src/app/lib/read-model-contract-fixtures.js";
import {
  guardApplicationDashboardReadModel,
  guardInstanceEvidenceReadModel,
  guardInstanceSnapshotTrendReadModel,
  guardSnapshotDetailReadModel,
  guardSnapshotHistoryReadModels,
} from "../src/app/lib/read-model-contract-guard.js";
import { toDashboardPresentation, toDisplayLatencyBuckets } from "../src/app/lib/read-model-adapters.js";

const applicationContext = {
  projectId: CONTRACT_PROJECT_ID,
  applicationId: CONTRACT_APPLICATION_ID,
};

const instanceContext = {
  ...applicationContext,
  instanceId: CONTRACT_INSTANCE_ID,
};

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

for (const path of ["src/app/components/dashboard.tsx", "src/app/components/instance-panels.tsx", "src/app/components/snapshot-history-panel.tsx"]) {
  const source = readFileSync(path, "utf8");
  assert.equal(/\.sort\(|\.toSorted\(|\.reduce\(/.test(source), false, `${path} must preserve server order`);
}

const snapshotDetailSurfaceSource = readFileSync("src/app/components/snapshot-detail-surface.tsx", "utf8");
assert.match(snapshotDetailSurfaceSource, /보관 기간이 지났거나 저장된 snapshot을 찾을 수 없습니다/);
assert.match(snapshotDetailSurfaceSource, /live dashboard\/current accepted bucket으로 복원하지 않습니다/);

console.log("read-model contract guard fixtures passed");
