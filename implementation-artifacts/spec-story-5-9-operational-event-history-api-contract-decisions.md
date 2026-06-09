---
artifactType: contract-decisions
projectName: Spring Boot 운영 첫 화면 포털
storyId: "5.9"
storyKey: "5-9-operational-event-history-api"
status: closed
date: 2026-05-27
scope: Story 5.9 pre-story contract closure before BMAD story creation
---

# Story 5.9 Operational Event History API Contract Decisions

## Purpose

Story 5.9는 stored dashboard snapshot/read model을 사용자-facing operational event history API로 projection하는 계약을 닫는다.

이 문서는 Story 5.9 story 파일을 만들기 전에 사용자가 직접 결정한 pre-story contract decision이다. 목표는 Story 5.8-a가 저장한 snapshot row와 Story 5.8-b가 안정화한 marker/detail handoff를 바탕으로 event promotion, deduplication, suppression, period folding, response shape, package boundary를 더 이상 재논의하지 않게 하는 것이다.

Story 5.9는 current dashboard 재계산, raw bucket explorer, heartbeat-derived event, endpoint timeseries, 별도 operational event store를 만들지 않는다.

## Authority and Non-Reopen Rules

이 문서는 아래 계약과 산출물을 기준으로 한다.

- `implementation-artifacts/spec-story-5-8-dashboard-snapshot-decomposition-contract-decisions.md`
- `implementation-artifacts/spec-story-5-8-a-dashboard-snapshot-writer-and-capture-policy-contract-decisions.md`
- `implementation-artifacts/spec-story-5-8-b-snapshot-marker-detail-recovery-contract-decisions.md`
- `planning-artifacts/stories/5-8-a-dashboard-snapshot-writer-and-capture-policy.md`
- `planning-artifacts/stories/5-8-b-snapshot-marker-detail-recovery-source.md`
- `planning-artifacts/contracts/operational-event-history.md`
- `planning-artifacts/contracts/read-model-contract.md`
- `planning-artifacts/database-schema.md`
- `planning-artifacts/api-surface.md`
- `planning-artifacts/source-of-truth/current-product-source-of-truth.md`
- `planning-artifacts/sprint-plan.md`
- `implementation-artifacts/sprint-status.yaml`

Story 5.8-a가 고정한 아래 계약은 Story 5.9에서 다시 열지 않는다.

- 새 writer persisted token은 `hourly_scheduled`, `state_change`, `high_confidence_concern`, `short_strong_spike`, `query_fallback`이다.
- `scheduled`는 새 write token이 아니며, 기존 row의 null, unknown, legacy token은 read side에서 opaque metadata로 허용한다.
- duplicate identity는 `application_id + current_window_end_utc`다.
- capture reason priority와 upsert semantics는 writer 책임이며 operational event dedup/suppression이 아니다.
- bounded endpoint evidence는 `read_model_json.snapshotEndpointEvidence.items[]`에 최대 10개 저장된다.
- bounded instance summary는 `read_model_json.instanceSummary.items[]`에 최대 50개 저장된다.
- 5.8-a는 60분 operational event suppression, event promotion, event API를 구현하지 않는다.

Story 5.8-b가 고정한 아래 계약은 Story 5.9에서 다시 열지 않는다.

- Marker/detail은 stored snapshot/read model을 current dashboard 재판정 없이 보여준다.
- Marker는 stored snapshot/read model point annotation이며 operational event item이 아니다.
- `markerId`는 Story 5.9 `eventId`가 아니다.
- Marker severity는 Story 5.9 event severity 최종값이 아니다.
- Marker API는 `eventId`, `resolvedAt`, dedup 결과, suppression 결과를 약속하지 않는다.
- 5.9 handoff candidate field는 `snapshotId`, `applicationId`, `capturedAt`, `currentWindowEndUtc`, `storedApplicationStateCode`, `previousState.stateCode`, `captureReason`, marker `type`, marker `severity`, `primaryRuleId`, `primaryEndpointKey`, `maxConfidence`, recovery marker presence/type, `snapshotEndpointEvidence.items[].anchorId`, `links.snapshot`이다.

Story 5.9는 아래 금지선을 유지한다.

- `operational_events` table, `OperationalEventRepository`, materialized view, Redis queue, PostgreSQL outbox를 만들지 않는다.
- raw snapshot explorer, raw bucket explorer, arbitrary metric/time-series query를 제공하지 않는다.
- endpoint timeseries table/API를 만들지 않는다.
- endpoint p95/p99, endpoint percentile rollup, endpoint p99 alert 기준을 만들지 않는다.
- current dashboard read model, lifecycle state, triage, endpoint priority, p95/p99를 history 조회 시점에 재계산하지 않는다.
- heartbeat success/failure/missing을 accepted bucket freshness, host application health, dashboard snapshot, recovery source, operational event source로 합성하지 않는다.
- UI/controller/repository/DB trigger가 lifecycle state, rule, p95/p99, endpoint priority, operational event를 계산하지 않는다.

## Closed Decisions

### 1. Event Source Contract

Story 5.9 operational event 후보 source는 `dashboard_snapshots` row metadata, bounded helper columns, stored `read_model_json`, 그리고 Story 5.8-b marker/detail handoff field로 제한한다.

5.9 history 조회는 current dashboard read model, lifecycle state, triage, endpoint priority, p95/p99, heartbeat, raw bucket을 재계산하거나 재해석하지 않는다.

구현 지침:

- 5.9 service는 `DashboardSnapshotRepository`를 통해 stored snapshot rows만 조회한다.
- event candidate 생성은 `dashboard_snapshots` row metadata, helper columns, stored `read_model_json`, 5.8-b handoff field만 사용한다.
- 5.9 service/controller는 `MetricBucketRepository`, `StarterHeartbeatTelemetryRepository`, `DashboardReadModelService`, `LifecycleStateService`, `TriageSummaryService`, `EndpointPriorityService`, `InstanceEvidenceReadModelService`를 주입받지 않는다.

결정 이유:

- Operational event history는 새 증거를 수집하거나 과거 판단을 다시 내리는 기능이 아니라 저장된 snapshot/read model 기록을 event list로 접는 기능이다.
- Snapshot detail과 operational event history가 같은 stored source를 가리켜야 사용자가 같은 시점에 대해 서로 다른 현실을 보지 않는다.

### 2. Event Type Promotion Contract

Story 5.9는 stored snapshot/read model candidate를 사용자-facing 의미 기준으로 operational event에 승격한다.

상태 변화와 high-confidence concern만 event 후보가 되며, `scheduled_snapshot`, `query_fallback_snapshot`, `stored_snapshot`, 단순 `state_observation`은 event로 만들지 않는다.

Event type seed set:

| Event Type | 승격 의미 |
|---|---|
| `state_changed` | 사용자에게 의미 있는 stored application state transition이 있으나 더 구체적인 event type으로 표현하기 어려운 경우 |
| `degraded_entered` | stored state가 degraded period로 진입한 경우 |
| `degraded_resolved` | degraded concern이 해소 조건을 만족한 stored snapshot/read model에서 확인된 경우 |
| `stale_entered` | accepted bucket freshness 부족으로 stale state에 진입한 경우 |
| `down_entered` | freshness 부족이 더 길어져 down state에 진입한 경우 |
| `recovery_observed` | stale/down 이후 새 metric observation이 stored snapshot/read model에서 관찰된 경우 |
| `high_confidence_concern` | 상태 변화는 없지만 stored concern confidence가 event 승격 기준을 만족한 경우 |

`short_strong_spike`는 별도 event type을 만들지 않는다. High-confidence concern 조건을 만족하면 `high_confidence_concern`으로 승격할 수 있으며, 조건을 만족하지 않으면 operational event로 노출하지 않는다.

결정 이유:

- Operational event history는 marker list가 아니라 사용자가 나중에 봐야 할 운영 사건 목록이다.
- 내부 저장 사유인 scheduled/query fallback/stored snapshot marker를 event로 노출하면 raw snapshot explorer처럼 보일 수 있다.

### 3. Period Folding and `resolvedAt` Contract

Story 5.9는 같은 operational concern/state period를 하나의 period로 접는다.

`degraded_entered`, `stale_entered`, `down_entered`, `high_confidence_concern`처럼 시작 성격의 event는 후속 stored snapshot/read model에서 해소가 확인되면 `resolvedAt`을 채울 수 있다.

동시에 `degraded_resolved` 또는 `recovery_observed`처럼 해소/회복 관찰 자체가 사용자에게 의미 있는 경우 별도 event로도 노출한다. Resolve 성격의 event 자체는 기본적으로 `resolvedAt = null`이다.

예시:

```text
12:10 degraded_entered, resolvedAt=12:50
12:50 degraded_resolved, resolvedAt=null
```

결정 이유:

- 시작 event에 `resolvedAt`이 있으면 사용자가 문제 기간을 빠르게 이해할 수 있다.
- 별도 resolve/recovery event를 유지하면 회복 관찰 순간을 timeline item으로 표현할 수 있다.

### 4. Deduplication and Suppression Key Contract

Story 5.9는 high-confidence concern 계열 event에 대해 `applicationId + primaryEndpointKey + primaryRuleId`를 suppression key로 사용하고, 같은 key는 60분 안에 중복 승격하지 않는다.

State transition 계열은 `applicationId + fromState + toState + eventType` 기준으로 period folding한다.

Stale/down/recovery처럼 endpoint/rule이 없는 event는 `applicationId + eventType + storedApplicationStateCode` 기준으로 중복을 접는다.

Snapshot 단위 suppression으로 서로 다른 endpoint/rule concern을 숨기지 않는다.

결정 이유:

- 같은 endpoint/rule concern 반복은 history noise를 만들지만, 같은 snapshot 또는 같은 시간대의 서로 다른 endpoint/rule concern은 사용자에게 다른 사건일 수 있다.
- 60분 suppression은 event promotion 책임이며 5.8-a writer upsert priority나 5.8-b marker priority와 다르다.

### 5. Event ID Contract

Story 5.9의 `eventId`는 별도 event store id가 아니라 snapshot-derived deterministic id다.

형식은 `snapshot:{snapshotId}:{eventType}:{normalizedKey}` 계열로 두며, 같은 snapshot에서 같은 event candidate가 같은 id를 만들도록 한다.

`markerId`를 `eventId`로 재사용하지 않고, 이 id에 incident acknowledgement, owner assignment, comment thread 같은 durable workflow 의미를 부여하지 않는다.

예시:

```text
snapshot:018f6b9a-2e1a-7d2b-9b2f-4db69d92c241:high_confidence_concern:endpoint_latency_spike:POST_orders
```

결정 이유:

- MVP는 별도 event table이 없으므로 durable incident id를 만들 수 없다.
- Snapshot-derived deterministic id는 UI key, test fixture, repeated query stability를 충분히 만족한다.

### 6. API Query and Ordering Contract

Story 5.9 operational event API는 recent event feed로 동작한다.

Endpoint:

```http
GET /api/projects/{projectId}/applications/{applicationId}/operational-events?since=24h&limit=50
Accept: application/json
```

Query policy:

| Parameter | Default | Max/Clamp | 의미 |
|---|---:|---:|---|
| `since` | `24h` | `14d` | dashboard snapshot retention 안에서만 조회 |
| `limit` | `50` | `100` | event feed cap |

응답 event 정렬은 `occurredAt desc`, tie-breaker는 `eventId asc`로 고정한다.

이 API는 raw snapshot list나 arbitrary time-series query가 아니므로 14일 전체 snapshot을 모두 반환하지 않는다.

결정 이유:

- Operational event history는 최근 운영 이력 feed이므로 최신 사건을 먼저 보여주는 것이 기본 사용 흐름에 맞다.
- Marker/trend API가 `capturedAt_asc`를 사용하더라도 5.9 event API는 별도 read model이므로 `occurredAt_desc`를 사용한다.

### 7. Response Shape Contract

Story 5.9 operational event response는 compact event item과 bounded evidence만 포함한다.

Top-level response shape:

```json
{
  "generatedAt": "2026-05-27T06:30:00Z",
  "applicationId": "5c942671-e251-4f7f-b610-18ae6ca4ef65",
  "source": "dashboard_snapshots",
  "horizon": {
    "since": "2026-05-26T06:30:00Z",
    "until": "2026-05-27T06:30:00Z",
    "requestedSince": "24h",
    "defaultSince": "24h",
    "maxSince": "14d",
    "limit": 50,
    "maxLimit": 100,
    "order": "occurredAt_desc"
  },
  "events": []
}
```

Event item shape:

```json
{
  "eventId": "snapshot:018f6b9a-2e1a-7d2b-9b2f-4db69d92c241:high_confidence_concern:endpoint_latency_spike:POST_orders",
  "type": "high_confidence_concern",
  "severity": "warning",
  "title": "POST /orders 응답 지연 증가",
  "summary": "저장된 snapshot에서 endpoint_latency_spike concern이 high confidence로 관찰됐습니다.",
  "occurredAt": "2026-05-14T07:42:30Z",
  "resolvedAt": null,
  "stateCode": "degraded",
  "confidence": 0.84,
  "snapshotId": "018f6b9a-2e1a-7d2b-9b2f-4db69d92c241",
  "evidence": {
    "ruleId": "endpoint_latency_spike",
    "endpointKey": "POST /orders",
    "method": "POST",
    "route": "/orders",
    "snapshotDetailAnchor": "endpoint-evidence-1",
    "anchorStatus": "resolved"
  },
  "links": {
    "snapshot": "/api/projects/{projectId}/applications/{applicationId}/dashboard/snapshots/{snapshotId}"
  }
}
```

Event item은 아래 field를 제공한다.

- `eventId`
- `type`
- `severity`
- `title`
- `summary`
- `occurredAt`
- nullable `resolvedAt`
- `stateCode`
- nullable `confidence`
- `snapshotId`
- `evidence`
- `links.snapshot`

Evidence는 stored snapshot/read model에서 복사 가능한 bounded field로 제한한다. Endpoint event는 `ruleId`, `endpointKey`, optional `method`, optional `route`, optional `snapshotDetailAnchor`, `anchorStatus`를 포함할 수 있다.

금지:

- raw snapshot JSON
- raw bucket list
- endpoint p95/p99
- per-request sample
- trace id
- query string, query key/value
- endpoint timeseries payload

결정 이유:

- Event list는 사용자가 "무슨 일이 있었나"를 이해하고 snapshot detail로 이동할 수 있을 정도만 제공한다.
- 상세 evidence body는 5.8-b snapshot detail 책임으로 유지한다.

### 8. Severity and Copy Contract

Story 5.9 event severity는 marker severity를 그대로 재사용하지 않고 event type 의미로 매핑한다.

기본 mapping:

| Event Type | Severity |
|---|---|
| `down_entered` | `critical` |
| `degraded_entered` | `warning` |
| `high_confidence_concern` | `warning` |
| `stale_entered` | `warning` |
| `degraded_resolved` | `info` |
| `recovery_observed` | `info` |
| `state_changed` | `info` |

Event copy는 stored snapshot/read model 기준의 관찰과 해소 조건 충족을 표현한다.

허용 copy 방향:

- "저장된 snapshot에서 성능 저하가 관찰됐습니다."
- "성능 저하 concern 해소 조건이 저장된 snapshot에서 확인됐습니다."
- "새 metric bucket이 다시 관찰되며 회복 흐름이 시작됐습니다."

금지 copy:

- "복구 완료"
- "장애 해결 완료"
- "앱 정상 확정"
- heartbeat success/failure/missing을 event title/summary/recommended copy의 source로 사용하는 문구

결정 이유:

- Recovery 계열은 회복 관찰이지 host application 정상 확정이 아니다.
- Heartbeat는 accepted bucket freshness, dashboard snapshot, recovery, operational event source가 아니다.

### 9. Physical Object and Index Contract

Story 5.9는 `operational_events` table, `OperationalEventRepository`, materialized view, Redis queue, PostgreSQL outbox를 만들지 않는다.

기본 구현은 기존 `dashboard_snapshots` table, row metadata, bounded helper columns, existing indexes를 사용한다. 새 migration/index는 5.9 기본 범위에 포함하지 않는다.

성능 테스트나 query plan상 필요가 확인된 경우에만 아래 bounded helper index를 후속 보강으로 검토할 수 있다.

- `application_id + primary_rule_id + generated_at desc`
- `application_id + primary_endpoint_key + generated_at desc`
- `application_id + max_confidence desc + generated_at desc`

이 index도 event store가 아니라 stored read model search helper다.

결정 이유:

- API 기본값은 `since=24h`, `limit=50`, 최대 `since=14d`, `limit=100`이므로 기존 application/generated_at 조회 기반으로 MVP를 시작할 수 있다.
- 별도 event store는 acknowledgement, durable event id, owner assignment, comment thread, long-term event retention이 필요해질 때 후속 story에서 검토한다.

### 10. Package Boundary Contract

Story 5.9는 `domain.history` feature package에 operational event history service/controller/model을 둔다.

후보 package:

- `com.observation.portal.domain.history.model`
- `com.observation.portal.domain.history.service`
- `com.observation.portal.domain.history.controller`

Service는 `DashboardSnapshotRepository`를 재사용해 stored snapshot rows를 읽지만, 아래 service/repository를 주입받지 않는다.

- `DashboardReadModelService`
- `LifecycleStateService`
- `TriageSummaryService`
- `EndpointPriorityService`
- `MetricBucketRepository`
- `StarterHeartbeatTelemetryRepository`
- `InstanceEvidenceReadModelService`

`domain.snapshot`의 marker/detail model을 event API external model로 재사용하지 않고, 5.9 전용 compact event read model을 둔다.

결정 이유:

- 5.9는 current dashboard 계산도 아니고 snapshot marker/detail도 아니다.
- Stored snapshot을 operational history feed로 projection하는 기능이므로 `domain.history`가 가장 명확하다.
- 이 경계는 marker와 event 의미가 다시 섞이는 회귀를 줄인다.

## Acceptance Guard Candidates

Story 5.9 story 생성 시 아래 guard를 acceptance criteria 또는 testing section에 포함한다.

1. `GET /api/projects/{projectId}/applications/{applicationId}/operational-events`가 `since=24h`, `limit=50`, max `14d/100`, `occurredAt desc` contract를 지킨다.
2. Service가 stored snapshot rows와 bounded helper/read model JSON만 사용해 event를 만든다.
3. Service/controller가 `MetricBucketRepository`, `StarterHeartbeatTelemetryRepository`, `DashboardReadModelService`, `LifecycleStateService`, `TriageSummaryService`, `EndpointPriorityService`, `InstanceEvidenceReadModelService`를 주입받지 않는다.
4. `scheduled_snapshot`, `query_fallback_snapshot`, `stored_snapshot`, 단순 `state_observation`은 event로 노출되지 않는다.
5. `short_strong_spike`는 별도 event type이 아니며, high-confidence concern 조건을 만족할 때만 `high_confidence_concern`으로 승격할 수 있다.
6. 같은 `applicationId + primaryEndpointKey + primaryRuleId` concern은 60분 안에서 중복 event로 승격되지 않는다.
7. 시작 성격 event는 해소가 확인되면 `resolvedAt`을 가질 수 있고, resolve/recovery event 자체는 기본적으로 `resolvedAt=null`이다.
8. `eventId`는 snapshot-derived deterministic id이고 `markerId`를 재사용하지 않는다.
9. Response event item은 compact bounded shape를 유지하고 raw snapshot JSON, raw bucket list, endpoint p95/p99, trace/per-request sample을 포함하지 않는다.
10. Event severity는 event type 기반 mapping을 사용하고 marker severity를 그대로 최종값으로 강제하지 않는다.
11. Recovery copy는 "복구 완료", "장애 해결 완료", "앱 정상 확정"을 말하지 않는다.
12. Heartbeat success/failure/missing은 operational event source나 copy source가 아니다.
13. `operational_events` table/repository, materialized view, Redis queue, PostgreSQL outbox, endpoint timeseries table/API가 추가되지 않는다.
14. UI/controller/repository/DB trigger가 lifecycle state, rule, p95/p99, endpoint priority, operational event를 계산하지 않는다.

## Story Creation Handoff Summary

Story 5.9 story file creation should use this document as the closed pre-story contract.

Recommended story scope:

- Build `domain.history` operational event read model/API.
- Read only stored `dashboard_snapshots` rows, bounded helper columns, stored `read_model_json`, and 5.8-b handoff fields.
- Promote only meaningful state transitions and high-confidence concerns.
- Apply 60분 suppression for same `applicationId + primaryEndpointKey + primaryRuleId` concern.
- Fold event periods and set `resolvedAt` on start events when a later stored snapshot confirms resolution.
- Return compact event response with bounded evidence and snapshot deep link.
- Preserve no new physical event store, no heartbeat-derived event, no raw explorer, no current recalculation.

Non-goals:

- Current dashboard recomputation
- Raw bucket/history explorer
- Raw snapshot list explorer
- Endpoint timeseries
- Endpoint p95/p99 calculation
- Operational event table/repository
- Alert delivery log
- Incident acknowledgement/owner/comment workflow
- Heartbeat-derived event or recovery source
