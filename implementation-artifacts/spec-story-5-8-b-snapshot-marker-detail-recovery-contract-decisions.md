---
artifactType: contract-decisions
projectName: Spring Boot 운영 첫 화면 포털
storyId: "5.8-b"
storyKey: "5-8-b-snapshot-marker-detail-recovery-source"
status: closed
date: 2026-05-27
scope: Story 5-8-b pre-story contract closure before BMAD story creation
---

# Story 5-8-b Snapshot Marker, Detail, and Recovery Source Contract Decisions

## Purpose

Story 5-8-b는 5-8-a가 저장한 dashboard snapshot을 사용자-facing marker, detail, recovery source로 읽는 계약을 닫는다.

이 문서는 Story 5-8-b story 파일을 만들기 전에 사용할 pre-story contract decision이다. 목표는 저장된 snapshot/read model을 current dashboard 재판정 없이 보여주고, marker와 recovery copy를 안전하게 해석하며, Story 5.9 operational event history가 사용할 최소 handoff만 남기는 것이다.

5-8-b는 snapshot writer, scheduler, capture policy, operational event promotion/dedup/suppression/API를 구현하지 않는다.

## Authority and Non-Reopen Rules

이 문서는 아래 계약을 기준으로 한다.

- `implementation-artifacts/spec-story-5-8-dashboard-snapshot-decomposition-contract-decisions.md`
- `implementation-artifacts/spec-story-5-8-a-dashboard-snapshot-writer-and-capture-policy-contract-decisions.md`
- `implementation-artifacts/spec-story-5-7-instance-snapshot-trend-contract-decisions.md`
- `implementation-artifacts/spec-story-5-4-triage-zero-insight-recovery-contract-decisions.md`
- `implementation-artifacts/spec-story-5-5-endpoint-priority-contract-decisions.md`
- `implementation-artifacts/spec-story-5-6-instance-evidence-contract-decisions.md`
- `planning-artifacts/contracts/read-model-contract.md`
- `planning-artifacts/contracts/operational-event-history.md`
- `planning-artifacts/database-schema.md`
- `planning-artifacts/api-surface.md`
- `planning-artifacts/source-of-truth/current-product-source-of-truth.md`
- `planning-artifacts/sprint-plan.md`

Story 5.7이 고정한 아래 계약은 5-8-b에서 다시 열지 않는다.

- `dashboard_snapshots.read_model_json.instanceSummary.items[]`가 instance snapshot trend의 stable source path다.
- `instanceSummary.schemaVersion = "1.0"`과 `maxItems = 50`은 유지한다.
- `instanceSummary.items[]` minimum shape와 field meaning은 rename, remove, reinterpret하지 않는다.
- 5.7 trend API는 stored snapshot projection이며 current state, rule, p95/p99, endpoint priority, recovery marker를 재계산하지 않는다.
- 5.7 `captureReason`은 nullable opaque metadata이며 marker, severity, recovery, operational event 의미로 해석하지 않는다.

Story 5-8-a가 고정한 아래 계약은 5-8-b에서 다시 열지 않는다.

- 새 writer persisted token은 `hourly_scheduled`, `state_change`, `high_confidence_concern`, `short_strong_spike`, `query_fallback`이다.
- duplicate identity는 `application_id + current_window_end_utc`다.
- capture reason priority와 upsert semantics는 writer 책임이다.
- bounded endpoint evidence는 `read_model_json.snapshotEndpointEvidence.items[]`에 최대 10개 저장된다.
- bounded instance summary는 `read_model_json.instanceSummary.items[]`에 최대 50개 저장된다.
- `instanceSummary.items[].endpointEvidenceRefs[]`는 endpoint evidence body가 아니라 ref-only 목록이다.
- snapshot save failure로 row가 없으면 5-8-b detail/marker source도 없다.

5-8-b는 아래 금지선을 유지한다.

- snapshot writer/scheduler/capture policy를 구현하거나 재정의하지 않는다.
- 5.7 `instanceSummary` path/minimum shape를 변경하지 않는다.
- 5-8-a capture reason 저장 계약을 재정의하지 않는다.
- operational event history event list API를 구현하지 않는다.
- operational event promotion, deduplication, suppression 최종 규칙을 구현하지 않는다.
- 별도 `operational_events` table, event repository, materialized view, Redis/outbox를 도입하지 않는다.
- raw snapshot explorer, raw bucket explorer, endpoint timeseries를 제공하지 않는다.
- endpoint p95/p99, endpoint percentile rollup, endpoint p99 alert 기준을 만들지 않는다.
- UI/controller/repository/DB trigger가 lifecycle state, rule, p95/p99, endpoint priority, marker/event를 계산하지 않는다.
- heartbeat를 accepted bucket freshness, host application health, dashboard snapshot, recovery source, operational event source로 합성하지 않는다.

## Closed Decisions

### 1. Snapshot Detail API Contract

5-8-b는 stored dashboard snapshot detail API를 닫는다.

Endpoint:

```http
GET /api/projects/{projectId}/applications/{applicationId}/dashboard/snapshots/{snapshotId}
Accept: application/json
```

결정 내용:

- API는 `projectId + applicationId + snapshotId` membership을 검증한다.
- response source는 `dashboard_snapshots` row metadata와 stored `read_model_json`이다.
- detail은 저장 당시 dashboard read model과 5-8-a가 저장한 bounded evidence를 보여준다.
- detail은 current accepted bucket, current heartbeat, current dashboard read model을 live join하지 않는다.
- detail은 current state, lifecycle state, rule, p95/p99, endpoint priority, marker severity, operational event를 재계산하지 않는다.
- detail은 raw `read_model_json` dump가 아니라 typed/bounded wrapper로 반환한다.
- 저장된 snapshot row가 없으면 current dashboard를 재생성해 detail을 보완하지 않는다.

Status mapping:

| Status | 조건 | Body/copy 방향 |
|---|---|---|
| `200` | project/application membership이 맞고 snapshot row가 존재함 | stored snapshot detail wrapper 반환 |
| `400` | `{snapshotId}`가 UUID 형식이 아님 | `invalid_snapshot_id` |
| `404` | project/application/snapshot이 없거나 서로 맞지 않음 | `snapshot_not_found_or_expired` 또는 일반 not found |
| `404` | retention cleanup으로 snapshot row가 삭제됨 | "저장된 snapshot detail이 보관 기간이 지나 더 이상 없습니다." |
| `500` | snapshot row 조회 또는 stored JSON projection 실패 | generic server error. current dashboard fallback 금지 |

결정 이유:

- Snapshot detail deep link는 사용자가 marker/history에서 특정 시점을 확인하는 경로다.
- retention으로 사라진 snapshot을 current dashboard로 대체하면 "그때의 read model"과 "지금의 read model"이 섞인다.
- raw JSON dump를 열면 snapshot detail이 raw explorer처럼 확장될 위험이 있다.

금지:

- `404` detail을 current dashboard response로 대신 반환하지 않는다.
- detail 조회 실패를 query fallback capture로 복구하지 않는다.
- detail response에 raw snapshot JSON, raw bucket JSON, raw `endpoints_json`, raw path/query/trace/per-request sample을 노출하지 않는다.

### 2. Snapshot Detail Response Wrapper Contract

5-8-b detail response는 아래 wrapper 의미를 사용한다.

Rough shape:

```json
{
  "generatedAt": "2026-05-27T12:05:35Z",
  "source": "dashboard_snapshots",
  "readSemantics": {
    "mode": "stored_snapshot_detail",
    "currentStateRecalculated": false,
    "liveSourcesJoined": [],
    "rawReadModelJsonExposed": false
  },
  "snapshot": {
    "snapshotId": "018f6b9a-2e1a-7d2b-9b2f-4db69d92c241",
    "capturedAt": "2026-05-25T12:00:00Z",
    "generatedAt": "2026-05-25T12:00:00Z",
    "currentWindow": {
      "startUtc": "2026-05-25T11:45:00Z",
      "endUtc": "2026-05-25T12:00:00Z"
    },
    "baselineWindow": {
      "startUtc": "2026-05-25T11:30:00Z",
      "endUtc": "2026-05-25T11:45:00Z"
    },
    "captureReason": "hourly_scheduled",
    "storedApplicationStateCode": "active",
    "primaryRuleId": null,
    "primaryEndpointKey": null,
    "maxConfidence": null
  },
  "marker": {
    "markerId": "snapshot:018f6b9a-2e1a-7d2b-9b2f-4db69d92c241:scheduled_snapshot",
    "type": "scheduled_snapshot",
    "severity": "info",
    "readMeaning": "stored_read_model_point",
    "title": "정기 snapshot",
    "summary": "저장된 dashboard read model입니다."
  },
  "previousState": {
    "stateCode": null,
    "source": "no_previous_snapshot_in_retention",
    "snapshotId": null,
    "capturedAt": null
  },
  "lastHealthyAt": {
    "value": null,
    "source": "no_previous_active_snapshot_in_retention",
    "snapshotId": null
  },
  "recoveryMarker": null,
  "readModel": {
    "application": {},
    "state": {},
    "starterConnection": {},
    "zeroInsight": null,
    "recovery": {},
    "metrics": {},
    "sourceScopedPercentiles": {},
    "triageCards": [],
    "endpointPriority": []
  },
  "snapshotEndpointEvidence": {
    "source": "bounded_endpoint_evidence",
    "maxItems": 10,
    "selectionPolicy": "endpoint_priority_rank_then_high_confidence_concern_then_triage_affected_endpoint",
    "items": []
  },
  "instanceSummary": {
    "schemaVersion": "1.0",
    "source": "bounded_instance_summary",
    "maxItems": 50,
    "items": []
  },
  "links": {
    "self": "/api/projects/{projectId}/applications/{applicationId}/dashboard/snapshots/{snapshotId}",
    "markers": "/api/projects/{projectId}/applications/{applicationId}/dashboard/snapshot-markers?since=24h"
  }
}
```

결정 내용:

- `snapshot` block은 row metadata와 5-8-a helper column 값을 노출한다.
- `readModel` block은 stored `read_model_json`의 UI-facing top-level block을 typed projection으로 반환한다.
- `snapshotEndpointEvidence` block은 stored `read_model_json.snapshotEndpointEvidence`를 bounded projection으로 반환한다.
- `instanceSummary` block은 stored `read_model_json.instanceSummary`를 그대로 의미 보존해 반환하되, detail anchor 해석에 필요한 read-side enrichment만 optional로 붙일 수 있다.
- `readSemantics.currentStateRecalculated=false`와 `liveSourcesJoined=[]`는 테스트 가능한 계약이다.
- `rawReadModelJsonExposed=false`가 기본이며 raw JSON 전체를 별도 field로 반환하지 않는다.
- unknown extra field는 ignore하거나 typed projection 밖으로 둔다. Extra field를 raw escape hatch로 노출하지 않는다.

결정 이유:

- UI는 stored read model을 표시해야 하지만, raw explorer를 열 필요는 없다.
- detail response에 read semantics를 명시하면 구현과 테스트가 current recalculation 금지를 쉽게 확인할 수 있다.
- row metadata, marker, previous/recovery source, stored read model을 분리하면 5.9 event handoff와 detail copy가 섞이지 않는다.

금지:

- `readModel` projection 중 누락 field를 current service 호출로 채우지 않는다.
- `snapshotEndpointEvidence`가 없다는 이유로 accepted bucket endpoint JSON을 다시 조회하지 않는다.
- `instanceSummary.items[]` minimum shape를 detail 전용 shape로 바꾸지 않는다.

### 3. Snapshot Marker API and Response Shape Contract

5-8-b는 snapshot marker query API를 operational event API와 분리한다.

Endpoint:

```http
GET /api/projects/{projectId}/applications/{applicationId}/dashboard/snapshot-markers?since=24h&limit=50
Accept: application/json
```

Query rules:

- omitted `since` defaults to `24h`.
- MVP supported `since` tokens are `24h`, `7d`, `14d`.
- `since` is clamped by configured `dashboard_snapshots` retention.
- omitted `limit` defaults to `50`.
- maximum `limit` is `336`.
- response order is `capturedAt ASC`, tie-breaker `snapshotId ASC`.
- no snapshot rows in the effective horizon returns `200` with `markers=[]`.

Rough response:

```json
{
  "generatedAt": "2026-05-27T12:05:35Z",
  "applicationId": "5c942671-e251-4f7f-b610-18ae6ca4ef65",
  "source": "dashboard_snapshots",
  "horizon": {
    "since": "2026-05-26T12:05:35Z",
    "until": "2026-05-27T12:05:35Z",
    "defaultSince": "24h",
    "maxSince": "14d",
    "limit": 50,
    "maxLimit": 336,
    "order": "capturedAt_asc"
  },
  "emptyState": null,
  "markers": [
    {
      "markerId": "snapshot:018f6b9a-2e1a-7d2b-9b2f-4db69d92c241:high_confidence_concern",
      "snapshotId": "018f6b9a-2e1a-7d2b-9b2f-4db69d92c241",
      "capturedAt": "2026-05-25T12:00:00Z",
      "currentWindowEndUtc": "2026-05-25T12:00:00Z",
      "type": "high_confidence_concern",
      "severity": "warning",
      "readMeaning": "stored_read_model_point",
      "captureReason": "high_confidence_concern",
      "storedApplicationStateCode": "degraded",
      "previousState": {
        "stateCode": "active",
        "source": "previous_dashboard_snapshot",
        "snapshotId": "018f6a88-2e1a-7d2b-9b2f-4db69d92c111"
      },
      "title": "고신뢰 concern snapshot",
      "summary": "저장 당시 dashboard read model에서 확인 우선 신호가 있었습니다.",
      "confidence": 0.84,
      "primaryRuleId": "endpoint_error_spike",
      "primaryEndpointKey": "POST /orders",
      "links": {
        "snapshot": "/api/projects/{projectId}/applications/{applicationId}/dashboard/snapshots/{snapshotId}"
      }
    }
  ]
}
```

Status mapping:

| Status | 조건 |
|---|---|
| `200` | marker list 반환. snapshot이 없으면 `markers=[]` |
| `400` | unsupported `since`, invalid `limit` |
| `404` | project/application membership 실패 |
| `500` | snapshot 조회/projection 실패 |

결정 이유:

- Marker API는 timeline point annotation을 위한 read model이다.
- Operational event history API는 Story 5.9에서 별도로 promotion/dedup/suppression을 적용한다.
- Marker list가 raw snapshot explorer가 되지 않도록 horizon과 limit을 clamp한다.

금지:

- marker API에서 operational event `eventId`, `resolvedAt`, dedup 결과, suppression 결과를 약속하지 않는다.
- marker API를 arbitrary snapshot query 또는 raw JSON list로 확장하지 않는다.
- UI가 marker type/severity를 계산하게 하지 않는다.

### 4. Marker Type, Severity, and Read Semantics Contract

5-8-b marker는 stored snapshot/read model을 읽는 point annotation이다. Operational event item이 아니다.

Marker type seed set:

| Type | 의미 | 주요 source |
|---|---|---|
| `scheduled_snapshot` | UTC hourly scheduled snapshot | `capture_reason=hourly_scheduled` |
| `query_fallback_snapshot` | query fallback으로 저장된 snapshot | `capture_reason=query_fallback` |
| `state_change` | writer가 state-change reason으로 대표 row를 저장했거나 previous snapshot 대비 state가 달라진 marker | `capture_reason=state_change`, previous stored snapshot |
| `state_observation` | scheduled/fallback/legacy snapshot이지만 stored state가 사용자의 주의를 요구하는 상태 | stored `state_code` |
| `high_confidence_concern` | 고신뢰 concern marker | `capture_reason=high_confidence_concern`, helper/read model confidence |
| `short_strong_spike` | 짧지만 강한 spike capture marker | `capture_reason=short_strong_spike` |
| `recovery_observed` | stale/down 이후 회복 관찰 중 marker | stored read model `recovery`/`zeroInsight`, previous snapshot |
| `stored_snapshot` | null/unknown/legacy capture reason 또는 분류 불가 snapshot | row metadata |

Severity enum:

| Severity | 사용 조건 |
|---|---|
| `critical` | stored `state_code=down` 또는 stored triage severity 중 `critical`이 있음 |
| `warning` | stored `state_code in [degraded, stale, unknown]`, recovery observed, high-confidence concern, short-strong-spike, stored triage severity `warning` |
| `info` | stored `state_code in [active, idle, waiting_first_data]`이고 warning/critical 조건이 없음 |

Severity rules:

- severity는 stored row/read model field만 사용한다.
- `capture_reason`만으로 severity를 확정하지 않는다.
- `query_fallback`과 `hourly_scheduled`는 neutral reason이며 stored state/triage/recovery가 없으면 `info`다.
- unknown stored state는 fallback severity `info`가 아니라 `warning`으로 둔다. 단, parse failure가 아니라 legacy/unknown state일 때만 copy는 "저장된 상태를 완전히 해석하지 못했습니다" 계열로 제한한다.
- severity는 operational event severity와 같은 문자열을 쓸 수 있지만, event promotion 결과는 아니다.

Read semantics:

- marker는 "그 시점에 저장된 dashboard read model이 이렇게 보였다"는 뜻이다.
- marker는 current state confirmation이 아니다.
- marker는 alert delivery log나 incident acknowledgement가 아니다.
- marker는 5.9 event candidate가 될 수 있지만 5-8-b에서 dedup/suppression하지 않는다.
- marker item은 `links.snapshot`으로 stored detail에 연결한다.

결정 이유:

- 사용자에게 timeline annotation은 필요하지만 event로 승격할지 여부는 5.9가 닫아야 한다.
- capture reason은 저장 이유이고, severity는 저장된 read model의 상태/근거에서 읽어야 한다.
- scheduled snapshot도 down/stale 상태를 담을 수 있으므로 reason만으로 "중요하지 않음"으로 처리하면 안 된다.

금지:

- marker type을 5.9 operational event type과 1:1로 고정하지 않는다.
- marker severity를 confidence 숫자 하나 또는 capture reason 하나로만 결정하지 않는다.
- marker API에서 60분 suppression window를 적용하지 않는다.

### 5. Capture Reason to Marker Interpretation Contract

5-8-b는 `capture_reason`을 marker 후보의 seed로만 해석한다.

Canonical interpretation priority:

1. `recovery_observed`: stored `recovery.isRecovering=true` 또는 stored `zeroInsight.reasonCode=observing_recovery`가 있으면 capture reason보다 우선한다.
2. `state_change`: `capture_reason=state_change`이거나 previous stored snapshot의 `state_code`와 current snapshot의 `state_code`가 다르면 사용한다.
3. `high_confidence_concern`: `capture_reason=high_confidence_concern`이거나 stored `max_confidence >= 0.82`/stored triage confidence가 threshold를 만족하면 사용한다.
4. `short_strong_spike`: `capture_reason=short_strong_spike`이면 사용한다.
5. `state_observation`: 위 조건은 아니지만 stored `state_code in [degraded, stale, down, unknown]`이면 사용한다.
6. `query_fallback_snapshot`: `capture_reason=query_fallback`이면 사용한다.
7. `scheduled_snapshot`: `capture_reason=hourly_scheduled`이면 사용한다.
8. `stored_snapshot`: `capture_reason`이 null, blank, unknown, legacy이면 사용한다.

결정 내용:

- `scheduled` legacy token은 새 writer token이 아니지만 read side는 unknown/legacy로 허용한다.
- null/unknown/legacy `capture_reason`은 detail/marker 조회 실패가 아니다.
- canonical token spelling은 5-8-a 계약을 따른다. 5-8-b는 token을 새로 저장하거나 정규화해 업데이트하지 않는다.
- marker `captureReason` field는 persisted value를 그대로 표시할 수 있다.
- marker `type`은 위 priority로 정규화한 read-side enum이다.

결정 이유:

- 5.7은 `captureReason`을 opaque metadata로 노출하므로 read compatibility를 유지해야 한다.
- recovery observed는 capture reason과 별개로 사용자가 timeline에서 놓치면 안 되는 상태다.
- scheduled/fallback snapshot도 저장 당시 state가 나쁘면 주의 marker로 읽혀야 한다.

금지:

- `capture_reason`을 operational event type으로 직접 매핑하지 않는다.
- `capture_reason=query_fallback`을 장애 또는 recovery 의미로 해석하지 않는다.
- unknown capture reason을 500으로 처리하지 않는다.

### 6. Stored Detail Boundary and Current Recalculation Prohibition Contract

5-8-b detail/marker service는 stored snapshot projection만 수행한다.

Allowed dependencies:

- project/application membership lookup repository
- `DashboardSnapshotRepository` read query
- stored `read_model_json` parser/projection helper
- previous snapshot lookup for same application

Forbidden dependencies:

- `MetricBucketRepository`
- `StarterHeartbeatTelemetryRepository`
- `LifecycleStateService`
- `TriageSummaryService`
- `EndpointPriorityService`
- `DashboardReadModelService`
- `InstanceEvidenceReadModelService`
- operational event promotion/dedup/suppression service

결정 내용:

- snapshot absence must not trigger live accepted bucket lookup.
- snapshot absence must not trigger heartbeat lookup.
- snapshot absence must not trigger dashboard current read model generation.
- malformed optional blocks are omitted or marked unavailable in bounded fashion; service does not rehydrate them from raw sources.
- previous snapshot lookup is allowed only against `dashboard_snapshots` for the same application.

결정 이유:

- Detail API는 "그때 저장한 read model"을 보는 API다.
- live source join을 허용하면 retention 이후 detail 의미와 current dashboard 의미가 섞인다.
- repository/DB/view가 state/rule/marker를 계산하면 MVC boundary가 깨진다.

금지:

- detail/marker 구현에 `MetricBucketRepository`를 주입하지 않는다.
- detail/marker 구현에 `DashboardReadModelService`를 재호출하지 않는다.
- detail/marker 구현에서 endpoint priority, p95/p99, lifecycle state를 새로 계산하지 않는다.

### 7. Endpoint Evidence Detail Anchor Contract

5-8-b는 stored `snapshotEndpointEvidence.items[]`와 `instanceSummary.items[].endpointEvidenceRefs[]`를 같은 snapshot 안에서 연결하는 anchor 계약을 닫는다.

Anchor source:

```text
dashboard_snapshots.read_model_json.snapshotEndpointEvidence.items[]
dashboard_snapshots.read_model_json.instanceSummary.items[].endpointEvidenceRefs[]
```

Anchor generation rule:

- detail response의 endpoint evidence item은 `anchorId`를 가진다.
- `anchorId` format은 `endpoint-evidence-{n}`이다.
- `{n}`은 detail response의 `snapshotEndpointEvidence.items[]` 1-based display order다.
- display order는 stored `snapshotEndpointEvidence.items[]` order를 보존한다.
- 같은 snapshot 안에서 `anchorId`는 deterministic하고 stable하다.
- anchor는 cross-snapshot durable id가 아니다.

Endpoint evidence item rough shape:

```json
{
  "anchorId": "endpoint-evidence-1",
  "method": "POST",
  "route": "/orders",
  "endpointKey": "POST /orders",
  "rank": 1,
  "reason": "error_and_latency",
  "ruleIds": ["endpoint_error_spike"],
  "confidence": 0.84,
  "score": 0.91,
  "requestCount": 12000,
  "errorRate": 0.064,
  "durationBuckets": [],
  "baselineDurationBuckets": [],
  "bucketDistributionSource": "histogram_bucket_distribution",
  "freshness": {},
  "recommendedAction": "이 endpoint의 error log와 dependency latency를 먼저 확인하세요."
}
```

Ref resolution rule:

- `endpointEvidenceRefs[]`는 `endpointKey`를 canonical match key로 사용한다.
- `method`와 `route`가 ref와 evidence item 양쪽에 있으면 일치 여부를 검증 보조로 사용한다.
- 같은 `endpointKey`가 여러 evidence item에 있으면 첫 번째 stored order item에 연결한다. 5-8-a selection은 endpointKey dedupe를 보장해야 하므로 이 상황은 legacy/malformed fallback이다.
- match 성공 시 detail response의 instance summary ref projection은 `snapshotDetailAnchor`를 read-side field로 추가할 수 있다.
- match 실패 시 ref는 유지하고 `anchorStatus=missing` 또는 `snapshotDetailAnchor=null`로 표현한다.
- missing anchor는 trend point나 detail response 전체를 invalid로 만들지 않는다.

Resolved ref rough shape:

```json
{
  "endpointKey": "POST /orders",
  "method": "POST",
  "route": "/orders",
  "relatedApplicationPriorityRank": 1,
  "relatedRuleIds": ["endpoint_error_spike"],
  "snapshotDetailAnchor": "endpoint-evidence-1",
  "anchorStatus": "resolved"
}
```

결정 이유:

- Story 5.7 trend point는 endpoint evidence body가 아니라 ref만 가진다.
- Detail anchor가 있으면 instance trend/detail UI가 같은 snapshot detail 안의 bounded endpoint evidence로 이동할 수 있다.
- Anchor를 `endpointKey` 기반으로 해석하되 response order 기반 id를 쓰면 URL fragment와 DOM anchor가 안정적이다.

금지:

- `endpointEvidenceRefs[]`에 endpoint evidence body를 복사하지 않는다.
- ref 해석을 이유로 raw endpoint body, endpoint timeseries, endpoint p95/p99를 trend point에 넣지 않는다.
- UI가 endpoint evidence matching rule을 새로 계산하지 않는다. Server response가 resolved/missing 상태를 제공한다.

### 8. Previous State Source Contract

5-8-b는 snapshot detail/marker에서 `previousState`를 stored snapshot source 우선으로 제공한다.

Lookup rule:

- same `application_id` 안에서만 조회한다.
- current snapshot보다 `current_window_end_utc`가 strictly earlier인 snapshot만 previous 후보가 될 수 있다.
- 후보 ordering은 `current_window_end_utc DESC`, `generated_at DESC`, `id ASC`다.
- 첫 번째 후보의 `state_code`를 `previousState.stateCode`로 사용한다.
- previous snapshot이 없거나 retention으로 삭제됐으면 `previousState.stateCode=null`과 `source=no_previous_snapshot_in_retention`을 반환한다.
- previous snapshot의 state code가 unknown/legacy이면 raw value를 `stateCode`로 보존하고 `source=previous_dashboard_snapshot_unknown_state`를 둘 수 있다.

Rough shape:

```json
{
  "stateCode": "stale",
  "source": "previous_dashboard_snapshot",
  "snapshotId": "018f6a88-2e1a-7d2b-9b2f-4db69d92c111",
  "capturedAt": "2026-05-25T11:00:00Z"
}
```

결정 내용:

- `previousState`는 application state source이며 instance state가 아니다.
- `previousState`는 current accepted bucket gap을 새로 조회해서 만들지 않는다.
- 같은 `current_window_end_utc` 안에서 5-8-a upsert로 덮인 이전 대표 read model은 5-8-b가 복원하지 않는다.
- `capture_reason=state_change`이지만 retention 안에 previous snapshot이 없으면 marker type은 `state_change`일 수 있고, `previousState`는 unavailable로 둔다.

결정 이유:

- 5-8-a duplicate identity가 같은 window를 대표 row 하나로 유지하므로 previous lookup도 window 기준이어야 한다.
- Snapshot source가 생긴 뒤에는 accepted bucket gap보다 previous stored read model이 더 안정적인 이전 상태 source다.
- Retention gap을 보간하면 stored history가 아닌 synthetic history가 된다.

금지:

- `previousState`를 current request의 accepted bucket, heartbeat, resource hint로 추론하지 않는다.
- missing previous snapshot을 raw bucket scan으로 보완하지 않는다.
- previous state가 없다는 이유로 detail/marker 조회를 실패시키지 않는다.

### 9. Last Healthy At Source Priority Contract

5-8-b는 `lastHealthyAt` normalized source priority를 snapshot 기반으로 닫는다.

Healthy state set:

```text
active
```

Priority:

1. 같은 application의 previous stored snapshot 중 `state_code=active`인 가장 최신 snapshot을 사용한다.
2. lookup ordering은 `current_window_end_utc DESC`, `generated_at DESC`, `id ASC`다.
3. timestamp value는 selected healthy snapshot의 `generated_at`을 사용한다.
4. previous active snapshot이 retention 안에 없으면 `lastHealthyAt.value=null`이다.
5. stored read model 안의 `recovery.lastHealthyAt` 또는 `application.lastHealthyAt`은 `readModel` block에 그대로 보일 수 있지만, 5-8-b normalized `lastHealthyAt`을 current accepted bucket으로 새로 만들지는 않는다.

Rough shape:

```json
{
  "value": "2026-05-25T10:00:00Z",
  "source": "previous_active_dashboard_snapshot",
  "snapshotId": "018f6999-2e1a-7d2b-9b2f-4db69d92c999"
}
```

Null shape:

```json
{
  "value": null,
  "source": "no_previous_active_snapshot_in_retention",
  "snapshotId": null
}
```

결정 이유:

- Story 5.4와 read-model contract는 `lastHealthyAt`을 현재 accepted bucket만으로 추론하지 않는다고 고정했다.
- `idle`은 반드시 unhealthy는 아니지만 "healthy/active request-serving 시점"으로 확정하기 어렵기 때문에 MVP healthy set에서 제외한다.
- Snapshot detail에서 normalized source를 분리하면 UI copy가 "이전 정상 시점 없음"을 안전하게 표시할 수 있다.

금지:

- current snapshot의 accepted bucket freshness가 current라는 이유로 `lastHealthyAt`을 current time으로 채우지 않는다.
- accepted bucket gap fallback으로 `lastHealthyAt`을 생성하지 않는다.
- heartbeat recent/missing으로 `lastHealthyAt`을 생성하지 않는다.
- `degraded_resolved` 또는 `lastRecoveredAt` field를 5-8-b에서 새로 만들지 않는다.

### 10. Recovery Marker Contract

5-8-b recovery marker는 "recovery observed" 계열 marker다. 회복 완료 marker가 아니다.

Recovery marker trigger:

- stored `read_model_json.recovery.isRecovering=true`, or
- stored `read_model_json.zeroInsight.reasonCode=observing_recovery`, or
- previous stored snapshot `state_code in [stale, down]`이고 current stored `state_code=unknown`이며 stored recovery block이 recovery 중임을 표현함.

Recovery marker shape:

```json
{
  "markerId": "snapshot:018f6b9a-2e1a-7d2b-9b2f-4db69d92c241:recovery_observed",
  "type": "recovery_observed",
  "severity": "warning",
  "title": "회복 관찰 중",
  "summary": "저장 당시 stale/down 이후 metric data가 다시 들어왔지만 판단 sample이 아직 부족했습니다.",
  "previousState": {
    "stateCode": "down",
    "source": "previous_dashboard_snapshot"
  },
  "lastHealthyAt": {
    "value": "2026-05-25T10:00:00Z",
    "source": "previous_active_dashboard_snapshot"
  },
  "recommendedAction": "다음 bucket에서 accepted bucket 수용과 sample 증가를 확인하세요."
}
```

Copy rules:

- "회복 관찰 중", "sample이 충분해지는지 확인" 계열 표현을 사용한다.
- "복구 완료", "장애 해결", "앱이 다시 정상" 같은 완료 표현을 쓰지 않는다.
- host application down, host process down, 앱 내려감 같은 확정 표현을 쓰지 않는다.
- starter connection copy와 recovery copy를 합쳐 host health를 단정하지 않는다.

결정 이유:

- Story 5.4 recovery는 stale/down 이후 새 bucket이 들어왔지만 sample이 부족한 상태를 설명한다.
- Recovery marker는 사용자가 timeline에서 회복 관찰 구간을 찾게 하지만, resolve event는 아니다.
- 회복 완료 시각은 hysteresis/dedup/suppression이 필요한 event semantics이므로 Story 5.9 이후로 남긴다.

금지:

- `lastRecoveredAt`, `recoveredAt`, `resolvedAt`을 5-8-b recovery marker에 추가하지 않는다.
- recovery marker를 `degraded_resolved` operational event로 승격하지 않는다.
- recovery marker 판단에 heartbeat를 사용하지 않는다.

### 11. Accepted Bucket Gap Fallback vs Snapshot Source Priority Contract

5-8-b는 Story 5.4의 accepted bucket gap fallback을 snapshot source보다 낮은 priority로 둔다.

Priority:

1. previous stored dashboard snapshot/read model source
2. stored read model 안에 이미 포함된 recovery/zeroInsight context
3. no value 또는 unavailable reason

결정 내용:

- 5-8-b는 detail/marker 조회 중 accepted bucket gap을 새로 계산하지 않는다.
- 5-8-b는 stored `read_model_json`에 이미 들어 있는 recovery/zeroInsight 결과를 읽을 수 있다.
- previous snapshot source가 있으면 그 값이 `previousState`와 `lastHealthyAt`의 primary source다.
- previous snapshot source가 없으면 stored recovery/zeroInsight context는 recovery marker copy를 설명하는 fallback context일 수 있다.
- accepted bucket gap fallback은 `lastHealthyAt`을 채울 수 없다.
- accepted bucket gap fallback은 host application down, host process down, 앱 내려감 같은 확정 표현을 만들 수 없다.
- accepted bucket gap fallback은 operational event source가 아니다. Story 5.9가 snapshot/read model candidate를 보고 promotion 여부를 결정한다.

결정 이유:

- 5.4는 snapshot이 없던 시기의 lightweight fallback을 닫았다.
- 5-8-b 시점에는 stored snapshot/read model이 primary history source다.
- detail/marker 조회에서 raw accepted bucket을 다시 훑으면 snapshot detail이 stored read model API가 아니라 historical recalculation API가 된다.

금지:

- 5-8-b service가 `MetricBucketRepository`로 gap을 재계산하지 않는다.
- gap fallback을 `lastHealthyAt` source로 사용하지 않는다.
- heartbeat success/missing을 gap fallback이나 recovery source로 섞지 않는다.

### 12. Retention and Missing Snapshot User Response Contract

Retention 때문에 snapshot이 없을 때의 API behavior를 아래로 고정한다.

Detail direct link:

```json
{
  "error": {
    "code": "snapshot_not_found_or_expired",
    "message": "저장된 snapshot detail이 없거나 보관 기간이 지나 더 이상 없습니다.",
    "recommendedAction": "현재 상태는 application dashboard에서 다시 확인하세요."
  }
}
```

Marker list empty state:

```json
{
  "generatedAt": "2026-05-27T12:05:35Z",
  "applicationId": "5c942671-e251-4f7f-b610-18ae6ca4ef65",
  "source": "dashboard_snapshots",
  "horizon": {
    "since": "2026-05-26T12:05:35Z",
    "until": "2026-05-27T12:05:35Z",
    "limit": 50,
    "order": "capturedAt_asc"
  },
  "emptyState": {
    "reasonCode": "no_snapshots_in_retention",
    "message": "보관 기간 안에 표시할 snapshot marker가 없습니다.",
    "recommendedAction": "현재 상태는 application dashboard에서 확인하세요."
  },
  "markers": []
}
```

결정 내용:

- direct detail link missing은 `404`다.
- marker collection에 snapshot이 없는 것은 정상 빈 결과이므로 `200 + markers=[]`다.
- retention empty copy는 "현재 문제가 없음" 또는 "회복 완료"를 뜻하지 않는다.
- missing snapshot을 current dashboard read model로 대체하지 않는다.

결정 이유:

- Detail link는 특정 stored row를 가리키므로 row가 없으면 not found다.
- Marker list는 bounded collection 조회이므로 retention 안에 row가 없는 상황이 정상일 수 있다.
- Empty state copy가 운영 상태를 단정하면 snapshot absence와 health semantics가 섞인다.

금지:

- retention empty를 `no_action_needed`로 바꾸지 않는다.
- expired snapshot detail에서 current state를 보여주지 않는다.
- snapshot absence를 operational event나 recovery marker로 만들지 않는다.

### 13. Story 5.9 Operational Event Handoff Contract

5-8-b는 Story 5.9가 operational event history를 만들 때 사용할 최소 handoff field를 안정화한다.

Handoff candidate fields:

- `snapshotId`
- `applicationId`
- `capturedAt`
- `currentWindowEndUtc`
- `storedApplicationStateCode`
- `previousState.stateCode`
- `captureReason`
- marker `type`
- marker `severity`
- `primaryRuleId`
- `primaryEndpointKey`
- `maxConfidence`
- recovery marker presence and type
- `snapshotEndpointEvidence.items[].anchorId`
- `links.snapshot`

5.9 owns:

- operational event endpoint `GET /api/projects/{projectId}/applications/{applicationId}/operational-events`
- event type promotion from stored snapshot/read model candidates
- `state_changed`, `degraded_entered`, `degraded_resolved`, `stale_entered`, `down_entered`, `recovery_observed`, `high_confidence_concern` final event semantics
- 60분 suppression window
- deduplication and period folding
- event severity/title/summary final copy
- event API status mapping
- event `resolvedAt` semantics

5.9 must preserve:

- MVP does not create `operational_events` table.
- MVP reuses `dashboard_snapshots` and bounded helper columns.
- heartbeat success/failure/missing is not an operational event source.
- endpoint timeseries table, materialized view, Redis queue, PostgreSQL outbox are not MVP history sources.

결정 이유:

- 5-8-b marker와 5.9 operational event는 같은 snapshot을 가리킬 수 있지만 같은 contract가 아니다.
- 5-8-b가 marker를 안정화하면 5.9는 promotion/dedup/suppression만 닫으면 된다.
- Event API는 사용자-facing history이며, marker API는 snapshot point annotation이다.

금지:

- 5-8-b markerId를 5.9 eventId로 고정하지 않는다.
- 5-8-b marker severity를 5.9 event severity 최종값으로 강제하지 않는다.
- 5-8-b에서 event dedup/suppression을 미리 구현하지 않는다.

### 14. Regression Guard Contract

5-8-b는 boundary + behavior regression guard를 acceptance/test에 포함한다.

필수 guard:

- detail API가 stored `read_model_json`과 row metadata에서만 response를 만든다.
- detail/marker service가 `DashboardReadModelService`, `MetricBucketRepository`, `StarterHeartbeatTelemetryRepository`, `EndpointPriorityService`를 사용하지 않는다.
- missing snapshot detail은 `404`이며 current dashboard를 재생성하지 않는다.
- marker list는 snapshot absence에서 `200 + markers=[]`를 반환한다.
- `capture_reason` null/unknown/legacy는 조회 실패가 아니며 marker type `stored_snapshot`으로 fallback된다.
- `capture_reason`만으로 severity를 결정하지 않는다.
- recovery marker copy가 "복구 완료", host application down, host process down을 단정하지 않는다.
- `lastHealthyAt`은 previous active snapshot source에서만 normalized value를 만든다.
- accepted bucket gap fallback은 `lastHealthyAt`을 만들지 않는다.
- endpoint evidence anchor는 stored `snapshotEndpointEvidence.items[]` order로 `endpoint-evidence-{n}`을 만든다.
- missing endpoint evidence anchor는 bounded missing ref로 표현하고 detail/trend를 invalid로 만들지 않는다.
- raw snapshot explorer, raw bucket explorer, endpoint timeseries, endpoint p95/p99, `operational_events` table/API를 만들지 않는다.
- UI/controller/repository/DB trigger가 state/rule/p95/p99/endpoint priority/marker/event를 계산하지 않는다.
- heartbeat는 accepted bucket freshness, dashboard snapshot, recovery source, operational event source로 합성되지 않는다.

결정 이유:

- 5-8-b는 읽기 story이지만 current 재계산과 event 승격으로 새기 쉬운 경계가 많다.
- Marker/detail/recovery source는 사용자 copy와 API shape가 직접 연결되므로 copy guard도 테스트해야 한다.
- Anchor resolution은 작은 ref 계약이므로 missing/malformed case를 반드시 허용해야 한다.

금지:

- regression guard를 acceptance 문장에만 남기고 테스트 없이 구현하지 않는다.

## Story 5-8-b Seed Summary

Story 5-8-b should implement snapshot marker, detail, and recovery source read semantics:

- detail endpoint `GET /api/projects/{projectId}/applications/{applicationId}/dashboard/snapshots/{snapshotId}`
- marker endpoint `GET /api/projects/{projectId}/applications/{applicationId}/dashboard/snapshot-markers?since=24h&limit=50`
- detail response wrapper with `snapshot`, `marker`, `previousState`, `lastHealthyAt`, `recoveryMarker`, typed `readModel`, `snapshotEndpointEvidence`, `instanceSummary`, and `readSemantics`
- marker response shape with bounded horizon, `markers[]`, marker `type`, `severity`, `readMeaning`, stored state, previous state, confidence/helper fields, and snapshot link
- marker type seed set: `scheduled_snapshot`, `query_fallback_snapshot`, `state_change`, `state_observation`, `high_confidence_concern`, `short_strong_spike`, `recovery_observed`, `stored_snapshot`
- severity enum: `info`, `warning`, `critical`, derived from stored state/triage/recovery rather than `capture_reason` alone
- capture reason interpretation priority with recovery observed first and null/unknown/legacy fallback
- no current state recalculation and no live accepted bucket/heartbeat/dashboard read model join
- previousState lookup from previous stored snapshot by same application and earlier `current_window_end_utc`
- `lastHealthyAt` from previous active snapshot only; accepted bucket gap and heartbeat never create it
- recovery marker as "회복 관찰 중", not recovery completed
- endpoint evidence anchor generation from stored `snapshotEndpointEvidence.items[]` order as `endpoint-evidence-{n}`
- `endpointEvidenceRefs[]` resolution to `snapshotDetailAnchor` with bounded missing-anchor handling
- detail retention miss as `404`, marker empty retention window as `200 + markers=[]`
- regression tests for raw explorer, endpoint timeseries, endpoint p95/p99, operational event table/API, UI-side calculation, heartbeat synthesis, and current recalculation prohibitions

Story 5-8-b must not implement:

- snapshot writer/scheduler/capture policy
- 5.7 `instanceSummary` path/minimum shape changes
- 5-8-a capture reason write/upsert contract changes
- operational event history list API
- operational event promotion, deduplication, suppression
- `operational_events` table or repository
- raw snapshot explorer, raw bucket explorer, endpoint timeseries
- endpoint p95/p99 or endpoint percentile rollup
- UI-side lifecycle state/rule/p95/p99/endpoint priority/event calculation
- heartbeat-to-snapshot/recovery/event synthesis

## Story 5.9 Handoff Summary

Story 5.9 starts from the 5-8-b marker/detail contract and closes operational event history:

- use stored `dashboard_snapshots` rows and bounded helper columns as event candidate source
- use 5-8-b marker/detail fields only as candidate/handoff, not final event contract
- decide event type promotion from marker/read model candidates
- apply 60분 suppression window and deduplication
- decide event period folding and `resolvedAt`
- return `GET /api/projects/{projectId}/applications/{applicationId}/operational-events?since=24h&limit=50`
- keep snapshot deep links to 5-8-b detail endpoint
- preserve no `operational_events` table, no heartbeat event source, no endpoint timeseries, no raw explorer, no endpoint p95/p99

5-8-b marker and 5.9 operational event may point to the same snapshot. They are not the same contract.
