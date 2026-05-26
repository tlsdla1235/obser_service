---
artifactType: contract-decisions
projectName: Spring Boot 운영 첫 화면 포털
storyId: "5.7"
storyKey: "5-7-instance-snapshot-trend-projection"
status: closed
date: 2026-05-26
scope: Story 5.7 pre-story contract closure
---

# Story 5.7 Instance Snapshot Trend Projection Contract Decisions

## Purpose

Story 5.7은 특정 application instance의 7일/14일 관찰 흐름을 `dashboard_snapshots.read_model_json`에 저장된 bounded instance summary에서 projection하기 전에, source substrate, JSON path, 최소 shape, query clamp, 재판정 금지선을 닫는다.

이 결정은 Story 5.8의 snapshot persistence, marker, detail, recovery, history handoff 전체를 앞당기는 것이 아니다. Instance snapshot trend projection이 저장된 snapshot summary 없이는 구현될 수 없으므로, Story 5.7 구현에 필요한 최소 snapshot source 계약만 Story 5.7로 이관한다.

## 1. Story 5.7 Snapshot Source Substrate Contract

Story 5.7은 instance snapshot trend projection에 필요한 최소 read-side snapshot substrate를 소유한다.

Story 5.7 may introduce:

- `dashboard_snapshots` physical table if it does not exist yet
- read-side JPA entity/repository mapping needed to read snapshot rows by `projectId + applicationId + generatedAt`
- test fixtures or repository integration tests that seed `read_model_json.instanceSummary`
- `InstanceSnapshotTrendService` projection from stored snapshot rows

If Story 5.7 creates `dashboard_snapshots`, the table includes nullable `capture_reason` as read metadata because Story 5.7 trend points expose it opaquely. Story 5.7 does not add a non-null constraint, enum check, marker mapping, capture index requirement, or writer behavior for `capture_reason`; those semantics remain Story 5.8/5.9 responsibilities.

Story 5.7 must not introduce:

- scheduled snapshot writer
- dashboard query fallback snapshot capture
- final snapshot capture policy implementation
- snapshot marker API
- snapshot detail API
- recovery marker source priority
- operational event promotion/dedup/suppression
- snapshot/history UI or deep link UX

The `dashboard_snapshots` table in Story 5.7 is a read-side substrate for stored projection tests, not the full Story 5.8 snapshot persistence feature.

## 2. Stored Instance Summary Path Contract

Story 5.7 fixes the bounded instance trend source path as:

```text
dashboard_snapshots.read_model_json.instanceSummary.items[]
```

`read_model_json.instances[]` remains the live dashboard navigation list and must not be expanded into the stored trend source. `read_model_json.snapshot` remains available for snapshot marker/link semantics and is not the owner of the instance trend source.

Example top-level relationship:

```json
{
  "instances": [],
  "instanceSummary": {
    "schemaVersion": "1.0",
    "source": "bounded_instance_summary",
    "maxItems": 50,
    "selectionPolicy": "triage_contributors_then_freshness_attention_then_high_request_count",
    "items": []
  }
}
```

`instanceSummary.schemaVersion` is required and MVP Story 5.7 supports only `"1.0"`. Unknown extra fields in `instanceSummary` or its items are ignored so Story 5.8 can add backward-compatible optional fields without breaking the 5.7 parser.

`instanceSummary.maxItems` is a contract cap of `50` items per snapshot. Story 5.8 writer must not store more than 50 items. Story 5.7 parser must not fail if older or malformed fixtures exceed the cap; it may ignore items after the first 50 to keep projection bounded.

## 3. Bounded Instance Summary Minimum Shape Contract

Story 5.7 fixes the minimum bounded trend shape for `instanceSummary.items[]`.

Required or allowed blocks:

- `instanceId`
- `instanceName`
- `observationStatus`
- `metricData`
- `starterConnection`
- nullable `starterPercentilePoint`
- nullable/empty `resourceHints`
- `applicationTriageContribution`
- `endpointEvidenceRefs`

Minimum shape:

```json
{
  "instanceId": "application_instances.id UUID",
  "instanceName": "orders-api-7f9c9c8c9d-x2p4k",
  "observationStatus": "observed",
  "metricData": {
    "statusSource": "accepted_bucket",
    "lastAcceptedBucketAt": "2026-05-25T11:59:30Z",
    "freshnessLabel": "current"
  },
  "starterConnection": {
    "statusSource": "starter_heartbeat",
    "lastHeartbeatAt": "2026-05-25T11:59:45Z",
    "lastHeartbeatStatus": "received",
    "connectionMeaning": "starter_connected",
    "stateImpact": "none"
  },
  "starterPercentilePoint": {
    "source": "starter_canonical_percentile",
    "scope": "instance_bucket",
    "bucketStartUtc": "2026-05-25T11:59:00Z",
    "bucketEndUtc": "2026-05-25T11:59:30Z",
    "requestCount": 820,
    "p95Ms": 210,
    "p99Ms": 360
  },
  "resourceHints": {
    "source": "accepted_bucket_latest_sample",
    "status": "available",
    "bucketEndUtc": "2026-05-25T11:59:30Z",
    "cpuUsageRatio": 0.41,
    "heapUsedRatio": 0.62,
    "datasourcePoolUsageRatio": 0.37
  },
  "applicationTriageContribution": {
    "status": "available",
    "contributed": false,
    "relatedRuleIds": [],
    "reason": "no_action_needed"
  },
  "endpointEvidenceRefs": []
}
```

Story 5.8 owns the writer/capture responsibility for filling this block when snapshots are saved. Story 5.8 must not rename, remove, or reinterpret the minimum fields fixed by Story 5.7. Optional backward-compatible fields may be added later.

## 4. Instance Identity Matching Contract

Story 5.7 projects trend points only when the target API path `{instanceId}` exactly matches `instanceSummary.items[].instanceId`.

Rules:

- `instanceId` is the canonical matching key.
- `instanceName` is display metadata only.
- `instanceName` must not be used as a fallback matching key.
- Missing, blank, or invalid `instanceId` values in stored JSON cause that item to be skipped.
- A skipped malformed item does not fail the whole request.

This preserves Story 5.6's UUID path identity contract and prevents name reuse, name change, or URL escaping concerns from contaminating historical trend projection.

## 5. Snapshot Row Metadata Projection Contract

Story 5.7 trend points copy only the row metadata needed to interpret stored points.

Projected row metadata:

- `snapshotId = dashboard_snapshots.id`
- `capturedAt = dashboard_snapshots.generated_at`
- `currentWindowEndUtc = dashboard_snapshots.current_window_end_utc`
- `storedApplicationStateCode = dashboard_snapshots.state_code`
- `captureReason = dashboard_snapshots.capture_reason`, nullable

`captureReason` is nullable opaque metadata in Story 5.7. The service copies the stored value but does not use it for ordering, filtering, marker type, severity, event meaning, or recovery semantics.

The final capture reason enum and marker mapping remain Story 5.8/5.9 responsibilities.

## 6. Horizon, Limit, Ordering, and Retention Clamp Contract

Story 5.7 fixes bounded query defaults and clamps so the endpoint cannot become an arbitrary time-series query.

Query rules:

- omitted `since` defaults to `7d`
- MVP supported `since` tokens are `7d` and `14d`
- maximum `since` is `14d`, additionally clamped by configured `dashboard_snapshots` retention
- invalid duration format maps to `400`
- omitted `limit` defaults to `168`
- maximum `limit` is `336`
- `limit > 336` is clamped to `336`
- response order is `capturedAt ASC`
- response tie-breaker is `snapshotId ASC`
- query implementation may read newest snapshots within the effective horizon first, apply limit, then return the selected points in ascending order

Retention gaps and missing hourly snapshots are not interpolated. Additional event snapshots can reduce how far back a limited response reaches; this is acceptable because the limit is the response cap.

## 7. Missing Snapshot and Missing Instance Handling Contract

Story 5.7 maps only project/application/instance membership failure to `404`.

Snapshot source absence is a normal empty trend:

- no snapshot rows inside retention/effective horizon returns `200` with `points=[]`
- snapshot row without `instanceSummary` is skipped
- snapshot row with missing or unsupported `instanceSummary.schemaVersion` is skipped
- snapshot row without the target `instanceId` item is skipped
- malformed instance summary item is skipped
- final result with zero projected points still returns `200` with `points=[]`
- missing hourly snapshots are not interpolated

This keeps retention, coarse snapshot cadence, and snapshot item cap from being treated as API errors.

## 8. Current Recalculation Prohibition Contract

Story 5.7 `InstanceSnapshotTrendService` performs stored snapshot projection only.

Allowed dependencies:

- project/application/instance membership lookup repositories
- `DashboardSnapshotRepository` read query
- `read_model_json.instanceSummary` parser/projection helper

Forbidden dependencies:

- `MetricBucketRepository`
- `StarterHeartbeatTelemetryRepository`
- `LifecycleStateService`
- `TriageSummaryService`
- `EndpointPriorityService`
- `DashboardReadModelService`
- `InstanceEvidenceReadModelService`

Snapshot absence must not trigger live accepted bucket lookup, heartbeat lookup, dashboard current read model generation, instance evidence generation, synthetic current point creation, or current lifecycle/rule/endpoint recalculation.

## 9. Story 5.6 Field Meaning Reuse Contract

Story 5.7 reuses the meaning of Story 5.6 current instance evidence fields, but reduces depth for stored trend projection.

Meaning reuse:

- `metricData` is an accepted bucket metric data axis summary.
- `starterConnection` is a starter heartbeat control-plane axis summary and keeps `stateImpact=none`.
- `starterPercentilePoint` is a latest single starter canonical percentile point and is not a series.
- `resourceHints` are latest accepted bucket sample hints and are not state, score, or root-cause inputs.
- `applicationTriageContribution` is a bounded bridge indicating whether the selected instance contributed to stored application triage evidence.
- `endpointEvidenceRefs` are bounded references only.

Excluded from the Story 5.7 minimum summary shape:

- full `histogramDistribution`
- full endpoint evidence body
- percentile series
- endpoint priority item body
- raw accepted bucket JSON
- raw endpoint JSON

This keeps Story 5.7 aligned with Story 5.6 semantics without turning snapshot trend points into snapshot detail payloads.

## 10. Endpoint Evidence Reference Contract

Story 5.7 does not include endpoint evidence bodies in trend points.

`instanceSummary.items[].endpointEvidenceRefs` may be an empty array or a bounded reference list.

Allowed fields:

- `endpointKey`
- optional `method`
- optional `route`
- optional `relatedApplicationPriorityRank`
- optional `relatedRuleIds`
- optional `snapshotDetailAnchor`

Forbidden fields:

- request/error count body
- error rate body
- duration buckets
- baseline buckets
- confidence or score recalculation values
- recommended action body
- endpoint p95/p99
- raw `endpoints_json`
- raw path, query string, query key/value, trace id, per-request sample

Story 5.8 may connect these refs to snapshot detail anchors, but Story 5.7 treats them only as stored references.

## 11. Capture Reason and Marker Separation Contract

Story 5.7 exposes `captureReason` only as nullable opaque metadata.

Rules:

- copy the stored row value as-is
- return `null` when absent
- allow unknown values
- do not use `captureReason` for ordering or filtering
- do not interpret `captureReason` as marker type
- do not derive marker severity from `captureReason`
- do not derive operational event semantics from `captureReason`

Final capture reason enum, marker mapping, and operational event promotion semantics remain Story 5.8/5.9 responsibilities.

## 12. Recovery, Previous State, and Last Healthy Boundary Contract

Story 5.7 does not define or project recovery semantics.

Trend points must not include:

- `previousState`
- `lastHealthyAt`
- `recoveryMarker`
- `recoveredAt`
- `lastRecoveredAt`
- recovery source priority
- gap fallback vs snapshot source priority

Story 5.7 exposes stored application state only as `storedApplicationStateCode`. Recovery marker, previous state, `lastHealthyAt` source priority, and fallback ordering remain Story 5.8 responsibilities. Operational event promotion remains Story 5.9 responsibility.

This preserves Story 5.4's rule that `lastHealthyAt` is not inferred from the current accepted bucket.

## 13. Story 5.8 Handoff Contract

Story 5.8 treats the `read_model_json.instanceSummary.items[]` path and minimum field meanings fixed by Story 5.7 as a stable contract.

Story 5.8 writer must fill this block when storing snapshots. It must not rename, remove, or reinterpret the minimum fields that the Story 5.7 parser depends on.

Story 5.8 may add backward-compatible optional fields, but marker/detail/history/recovery semantics must extend without breaking the `instanceSummary` minimum shape. Valid extension patterns include:

- separate block
- optional refs
- separate read model
- optional fields that older 5.7 parsers can ignore

Story 5.8 owns:

- snapshot writer
- scheduler
- capture policy
- marker/detail contract
- recovery marker
- previous read model source priority
- `lastHealthyAt` snapshot source priority

Story 5.9 owns:

- operational event history promotion
- deduplication
- suppression
- event API

## Story 5.7 Seed Summary

Story 5.7 should create an instance snapshot trend projection from stored snapshots:

- endpoint candidate: `GET /api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/snapshot-trend?since=7d&limit=168`
- response source: `dashboard_snapshots.read_model_json.instanceSummary.items[]`
- default horizon/limit: `7d` / `168`
- max horizon/limit: `14d` / `336`
- ordering: `capturedAt ASC`
- missing data: `200 + points=[]`
- membership failure: `404`
- no current recalculation
- no raw bucket explorer
- no endpoint timeseries
- no marker/detail/history/recovery implementation

## Non-Conflict Notes

- Story 5.4 recovery source remains unchanged: `lastHealthyAt` is not inferred from the current accepted bucket.
- Story 5.5 endpoint priority remains current-only; stale/down previous endpoint evidence is not copied into current priority.
- Story 5.6 current instance evidence does not depend on `dashboard_snapshots`; Story 5.7 is a separate stored trend projection.
- Story 5.8 extends snapshot writer/marker/detail/recovery over the stable Story 5.7 source contract.
- Story 5.9 derives operational event history without introducing an MVP `operational_events` table.
