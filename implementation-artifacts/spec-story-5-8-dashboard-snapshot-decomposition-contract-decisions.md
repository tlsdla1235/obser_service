---
artifactType: decomposition-contract
projectName: Spring Boot 운영 첫 화면 포털
storyId: "5.8"
storyKey: "5-8-dashboard-snapshot-decomposition"
status: closed
date: 2026-05-27
scope: Story 5.8 responsibility split and handoff contract before child story creation
---

# Story 5.8 Dashboard Snapshot Decomposition Contract Decisions

## Purpose

Story 5.8을 하나의 큰 구현 story로 만들지 않고, 먼저 snapshot 저장 책임과 snapshot을 읽는 marker/detail/recovery 책임을 분리한다.

이 문서는 세부 구현 story가 아니다. 5-8-a와 5-8-b가 각각 무엇을 닫고, 무엇을 다음 story로 넘기며, 어떤 기존 계약을 절대 다시 열지 않는지 고정하는 handoff 문서다.

## 1. Authority and Non-Reopen Rules

이 decomposition은 아래 문서를 최신 계약 기준으로 사용한다.

- `implementation-artifacts/spec-story-5-7-instance-snapshot-trend-contract-decisions.md`
- `planning-artifacts/source-of-truth/current-product-source-of-truth.md`
- `planning-artifacts/sprint-plan.md`
- `planning-artifacts/epics.md`
- `planning-artifacts/contracts/read-model-contract.md`
- `planning-artifacts/contracts/operational-event-history.md`
- `planning-artifacts/database-schema.md`
- `planning-artifacts/api-surface.md`
- `implementation-artifacts/spec-story-5-4-triage-zero-insight-recovery-contract-decisions.md`
- `implementation-artifacts/spec-story-5-5-endpoint-priority-contract-decisions.md`
- `implementation-artifacts/spec-story-5-6-instance-evidence-contract-decisions.md`

Story 5.7이 고정한 아래 계약은 Story 5.8에서 다시 열지 않는다.

- `dashboard_snapshots.read_model_json.instanceSummary.items[]`가 instance snapshot trend의 stable source path다.
- `instanceSummary.schemaVersion = "1.0"`과 `maxItems = 50`은 유지한다.
- `instanceSummary.items[]` minimum shape와 field meaning은 rename, remove, reinterpret하지 않는다.
- Story 5.7의 `captureReason`은 nullable opaque metadata였고, 5.7 parser는 marker, severity, recovery, operational event 의미로 해석하지 않는다.
- Story 5.7 trend API는 stored snapshot projection이며 current state, rule, p95/p99, endpoint priority, recovery marker를 재계산하지 않는다.

Story 5.8은 이 stable contract 위에 writer/capture/marker/detail/recovery 의미를 추가한다. 추가는 backward-compatible optional field, 별도 block, 별도 read model, opaque ref 방식으로만 한다.

## 2. Decomposition Decision

Story 5.8은 아래 두 child story로 나눈다.

| Child story | 이름 | 주 책임 | 넘기는 것 |
|---|---|---|---|
| `5-8-a` | Dashboard Snapshot Writer and Capture Policy | snapshot row를 안정적으로 저장하고, bounded endpoint evidence와 bounded instance summary를 채운다. | marker/detail/recovery read semantics, operational event promotion |
| `5-8-b` | Snapshot Marker, Detail, and Recovery Source | 저장된 snapshot을 marker/detail/recovery source로 읽는 의미를 닫는다. | operational event API promotion/dedup/suppression |

분해 기준은 단순하다.

- 5-8-a는 "무엇을 언제 저장하는가"를 닫는다.
- 5-8-b는 "저장된 것을 어떻게 읽고 연결하는가"를 닫는다.
- 5.9는 "읽힌 marker/snapshot 후보를 operational event API로 어떻게 승격, 중복 제거, 억제하는가"를 닫는다.

## 3. 5-8-a Ownership: Writer and Capture Policy

5-8-a는 `dashboard_snapshots` writer와 capture policy의 구현 가능 계약을 닫는다.

### 3.1 Responsibilities

5-8-a owns:

- application별 1시간 scheduled dashboard snapshot writer
- dashboard query fallback capture
- state-change, high-confidence concern, short-strong-spike capture eligibility
- capture reason seed enum과 duplicate/upsert priority
- idempotent writer semantics
- snapshot row helper column population
- `read_model_json`에 bounded endpoint evidence 최대 10개 저장
- `read_model_json.instanceSummary.items[]`에 bounded instance summary 최대 50개 저장
- Story 5.7 minimum `instanceSummary.items[]` block 채우기
- snapshot 저장 실패 처리 원칙

5-8-a의 writer input은 service layer가 생성한 dashboard read model이다. Repository, DB trigger, frontend는 state, rule, endpoint priority, p95/p99, recovery copy를 계산하지 않는다.

### 3.2 Capture Reason Seed Contract

5-8-a는 capture reason을 nullable opaque metadata에서 writer-owned seed enum으로 승격한다. Decomposition-level seed set은 아래로 둔다.

| Seed reason | 의미 |
|---|---|
| `hourly_scheduled` | application별 기본 1시간 scheduled snapshot |
| `state_change` | hysteresis 이후 의미 있는 state 변화 |
| `high_confidence_concern` | confidence `>= 0.82` concern capture |
| `short_strong_spike` | confidence `>= 0.90`이고 최근 5개 30초 bucket 중 2개 이상 bad인 실험적 capture 후보 |
| `query_fallback` | dashboard query 시 최신/current response용 snapshot이 없거나 명백히 오래되어 fallback으로 저장한 snapshot |

같은 `applicationId + currentWindowEndUtc`에 여러 capture reason이 동시에 성립할 수 있으므로 5-8-a는 deterministic priority를 닫아야 한다. Seed priority는 아래를 기본값으로 둔다.

1. `state_change`
2. `high_confidence_concern`
3. `short_strong_spike`
4. `query_fallback`
5. `hourly_scheduled`

5-8-a pre-story contract는 persisted token spelling을 최종 고정해야 한다. 기존 문서에 `scheduled`와 `hourly_scheduled` 후보가 함께 있으므로, child story 작성 전에 하나로 닫고 API 예시와 migration comment를 함께 맞춘다.

### 3.3 Duplicate, Upsert, and Idempotency Boundary

5-8-a writer는 같은 application/current window에 중복 snapshot row를 계속 늘리지 않는다.

Baseline rule:

- duplicate identity는 `application_id + current_window_end_utc`다.
- 같은 identity에 같은 input으로 writer를 반복 실행해도 read-equivalent result가 유지되어야 한다.
- lower-priority reason은 existing higher-priority snapshot semantics를 downgrade하지 않는다.
- higher-priority reason은 child story에서 닫은 방식에 따라 existing row를 enrich하거나 replace할 수 있다.
- `generated_at`, `state_code`, `capture_reason`, `primary_rule_id`, `primary_endpoint_key`, `max_confidence` update rule은 5-8-a pre-story에서 명시한다.

이 idempotency는 snapshot history 저장 안정성을 위한 것이며 operational event dedup/suppression이 아니다. 60분 suppression window와 event promotion은 Story 5.9 책임이다.

### 3.4 Bounded Endpoint Evidence Storage

5-8-a는 snapshot detail/history에서 읽을 수 있도록 stored endpoint evidence를 최대 10개까지 저장한다.

Selection source and cap:

- source는 current dashboard read model의 typed `endpointPriority`, triage cards, high-confidence concern evidence다.
- Story 5.5 handoff를 우선해 selection seed order는 current `endpointPriority` 상위 item, high-confidence concern endpoint, triage `affectedEndpoint` 연결 endpoint 순서로 둔다.
- endpoint evidence는 최대 10개다.

Allowed stored fields stay bounded:

- `method`
- `route`
- `endpointKey`
- `rank`
- `reason`
- `ruleIds`
- `confidence`
- `score`
- `requestCount`
- `errorRate`
- `durationBuckets`
- `baselineDurationBuckets`
- `bucketDistributionSource`
- `freshness`
- `recommendedAction`

Forbidden:

- raw path, query string, query key/value
- trace id, per-request sample
- raw `endpoints_json`
- endpoint p95/p99, endpoint percentile rollup, endpoint p99 alert 기준
- endpoint timeseries table 또는 raw explorer용 payload

### 3.5 Bounded Instance Summary Storage

5-8-a는 Story 5.7이 고정한 source path를 반드시 채운다.

Fixed path:

```text
dashboard_snapshots.read_model_json.instanceSummary.items[]
```

Fixed summary wrapper:

```json
{
  "instanceSummary": {
    "schemaVersion": "1.0",
    "source": "bounded_instance_summary",
    "maxItems": 50,
    "selectionPolicy": "triage_contributors_then_freshness_attention_then_high_request_count",
    "items": []
  }
}
```

5-8-a writer must:

- `items[]`를 snapshot당 최대 50개로 제한한다.
- Story 5.7 minimum field를 모두 채운다.
- `instanceId`를 canonical matching key로 저장한다.
- `instanceName`은 display metadata로만 저장한다.
- `metricData`는 accepted bucket axis summary로 유지한다.
- `starterConnection`은 starter heartbeat control-plane axis summary로 유지하고 `stateImpact=none` 의미를 유지한다.
- `starterPercentilePoint`는 source-scoped starter canonical latest point만 저장한다.
- `resourceHints`는 latest accepted bucket sample hint로만 저장한다.
- `applicationTriageContribution`은 application triage와의 bounded bridge로만 저장한다.
- `endpointEvidenceRefs`는 stored endpoint evidence를 가리키는 bounded reference로만 저장한다.

5-8-a must not:

- `instanceSummary.items[]`를 instance health score source로 만들지 않는다.
- raw accepted bucket JSON, raw endpoint JSON, full histogram distribution, endpoint evidence body 전체를 trend point에 넣지 않는다.
- 5.7 parser가 의존하는 minimum field 이름이나 의미를 바꾸지 않는다.

### 3.6 Save Failure Boundary

5-8-a는 snapshot 저장 실패를 current read model 판단과 섞지 않는다.

Rules:

- scheduled snapshot 저장 실패는 application state를 바꾸지 않는다.
- 실패한 snapshot을 partial row 또는 성공 marker처럼 노출하지 않는다.
- query fallback capture 실패 시 dashboard current response를 fail-open으로 둘지 fail-closed `500`으로 둘지는 5-8-a pre-story에서 `api-surface.md`와 맞춰 닫는다.
- 저장 실패로 snapshot row가 없으면 5-8-b detail/marker source도 없는 것이다. 5-8-b가 detail 조회 시 current dashboard를 재생성해 빈 detail을 보완하지 않는다.

### 3.7 5-8-a Non-Goals

5-8-a must not implement:

- snapshot marker API 또는 marker severity/read semantics
- snapshot detail API response shape
- recovery marker
- snapshot source 기반 `previousState` 또는 `lastHealthyAt` priority
- operational event history API
- operational event promotion, deduplication, suppression
- `operational_events` table, event repository, materialized view, Redis/outbox
- raw snapshot explorer, raw bucket explorer, endpoint timeseries

## 4. 5-8-b Ownership: Marker, Detail, and Recovery Source

5-8-b는 5-8-a가 저장한 snapshot을 사용자-facing marker/detail/recovery source로 읽는 계약을 닫는다.

### 4.1 Responsibilities

5-8-b owns:

- snapshot detail API shape
- snapshot marker response shape
- marker type, severity, read semantics
- recovery marker semantics
- snapshot source 기반 `previousState`
- `lastHealthyAt` snapshot source priority
- accepted bucket gap fallback과 snapshot source 우선순위
- `endpointEvidenceRefs`와 snapshot detail anchor 연결
- marker/detail이 current state를 재판정하지 않는 테스트 경계

5-8-b reads stored snapshot/read model. It does not produce the snapshot row.

### 4.2 Snapshot Detail Boundary

5-8-b의 detail API는 저장 당시 read model과 bounded evidence를 보여주는 API다.

Candidate endpoint:

```http
GET /api/projects/{projectId}/applications/{applicationId}/dashboard/snapshots/{snapshotId}
```

Detail semantics:

- project/application/snapshot membership을 검증한다.
- snapshot이 없거나 retention으로 삭제됐으면 `404`를 반환할 수 있다.
- 저장된 `read_model_json`, row metadata, bounded endpoint evidence, bounded instance summary/ref를 반환한다.
- current accepted bucket, current heartbeat, current dashboard read model을 live join해 detail을 보강하지 않는다.
- current state, lifecycle state, rule, p95/p99, endpoint priority, operational event를 재계산하지 않는다.

### 4.3 Marker Type, Severity, and Read Semantics

5-8-b marker는 stored snapshot/read model을 읽는 point annotation이다. Operational event API item이 아니다.

Marker rules:

- marker source는 `dashboard_snapshots` row metadata와 stored `read_model_json`이다.
- `capture_reason`은 marker 후보를 고르는 seed가 될 수 있지만, marker severity와 event meaning의 유일한 source가 되어서는 안 된다.
- marker severity는 stored state, triage severity/confidence, recovery state 같은 stored read model field를 기준으로 5-8-b pre-story에서 닫는다.
- marker는 "그 시점에 저장된 dashboard read model이 이렇게 보였다"는 의미다.
- marker read semantics는 current state confirmation이 아니다.
- UI는 marker를 표시할 수 있지만 state/rule/p95/p99/endpoint priority를 계산하지 않는다.

5-8-b marker는 Story 5.9 event promotion을 대신하지 않는다. 같은 snapshot marker가 5.9에서 operational event 후보가 될 수는 있지만, 5-8-b가 60분 suppression이나 event dedup을 수행하지 않는다.

### 4.4 Recovery Source Priority Boundary

5-8-b는 Story 5.4의 lightweight accepted bucket gap recovery를 snapshot source가 생긴 이후의 우선순위로 재정렬한다.

Priority:

1. 같은 application의 stored previous dashboard snapshot/read model을 우선 사용한다.
2. `previousState`는 현재 snapshot 또는 current read model 기준 이전 stored snapshot의 `state_code`를 우선 source로 삼는다.
3. `lastHealthyAt`은 이전 stored snapshot/read model 중 5-8-b pre-story가 healthy로 정의한 state의 timestamp에서만 얻는다.
4. accepted bucket gap fallback은 snapshot/previous read model source가 없거나 불충분할 때 recovery trigger 후보를 설명하는 보조 source다.
5. current request의 accepted bucket만으로 `lastHealthyAt`을 추론하지 않는다.

Accepted bucket gap fallback may:

- previous state 후보가 `stale` 또는 `down`이었다고 판단하는 데 도움을 줄 수 있다.
- `recovery.isRecovering=true`와 `zeroInsight.reasonCode=observing_recovery` 판단의 fallback context가 될 수 있다.

Accepted bucket gap fallback must not:

- `lastHealthyAt`을 생성하지 않는다.
- host application down, host process down, 앱 내려감 같은 확정 표현을 만들지 않는다.
- heartbeat success/missing을 recovery source나 operational event source로 승격하지 않는다.

### 4.5 Recovery Marker Boundary

5-8-b recovery marker는 "recovery observed" 계열의 marker다.

Rules:

- recovery marker는 회복 완료를 뜻하지 않는다.
- recovery marker는 stale/down 이후 accepted bucket freshness가 current로 돌아왔지만 sample이 부족해 관찰 중인 상태를 표현한다.
- `lastRecoveredAt` 같은 회복 완료 시각 field는 MVP current/detail recovery 계약에 추가하지 않는다.
- marker copy는 Story 5.4 원칙처럼 host application down을 확정하지 않는다.
- recovery marker가 operational event로 승격되는 기준과 dedup/suppression은 Story 5.9가 닫는다.

### 4.6 Endpoint Evidence Ref and Detail Anchor Boundary

5-8-b는 `instanceSummary.items[].endpointEvidenceRefs[]`가 snapshot detail의 bounded endpoint evidence로 이동할 수 있는 anchor 계약을 닫는다.

Rules:

- `endpointEvidenceRefs`는 endpoint evidence body가 아니라 참조다.
- ref는 `endpointKey`, optional `method`, optional `route`, optional `relatedApplicationPriorityRank`, optional `relatedRuleIds`, optional `snapshotDetailAnchor` 정도로 제한한다.
- detail anchor는 같은 snapshot 안에서 deterministic하고 stable해야 한다.
- ref가 detail anchor를 찾지 못해도 trend point 전체를 invalid로 만들지 않는다. UI/API는 missing anchor를 bounded missing reference로 표현한다.
- anchor 연결을 이유로 raw endpoint body, endpoint timeseries, endpoint p95/p99를 trend point에 넣지 않는다.

Exact anchor generation rule은 5-8-b pre-story에서 닫는다.

### 4.7 5-8-b Non-Goals

5-8-b must not implement:

- snapshot scheduler 또는 writer
- capture policy trigger evaluation
- snapshot row upsert/idempotency
- bounded endpoint/instance summary writer selection
- operational event API
- operational event promotion, deduplication, suppression
- `operational_events` table 또는 event repository
- UI-side state/rule/p95/p99/endpoint priority calculation
- raw explorer, endpoint timeseries, arbitrary time-series query

## 5. Story 5.9 Handoff

Story 5.9 starts after 5-8-a and 5-8-b have provided stable stored snapshot and marker/detail semantics.

Story 5.9 owns:

- operational event history API
- event type promotion from stored snapshot/read model candidates
- deduplication
- suppression window
- event item shape and links
- event API status mapping

Story 5.9 must keep existing physical boundary:

- MVP does not create an `operational_events` table.
- MVP reuses `dashboard_snapshots` and bounded helper columns.
- heartbeat success/failure/missing is not an operational event source.
- endpoint timeseries table, materialized view, Redis queue, PostgreSQL outbox are not MVP history sources.

5-8-b marker and 5.9 operational event can point to the same snapshot. They are not the same contract.

## 6. Global Forbidden Lines

The 5.8 split must preserve these prohibitions across both child stories.

- 5.7 `instanceSummary.items[]` source path and minimum shape를 다시 열지 않는다.
- raw bucket explorer를 만들지 않는다.
- raw snapshot JSON list/explorer를 만들지 않는다.
- endpoint timeseries를 만들지 않는다.
- endpoint별 p95/p99, endpoint percentile rollup, endpoint p99 alert 기준을 만들지 않는다.
- UI-side lifecycle state, rule, p95/p99, endpoint priority, marker/event calculation을 만들지 않는다.
- heartbeat를 accepted bucket freshness, host application health, dashboard snapshot, recovery source, operational event source로 합성하지 않는다.
- `operational_events` table, event repository, materialized view, Redis/outbox를 MVP에 도입하지 않는다.
- 30초 bucket마다 dashboard snapshot을 장기 보관하지 않는다.
- current detail/history 조회에서 current state를 재판정하지 않는다.

## 7. 5-8-a Pre-Story Decision Queue

5-8-a story 파일 작성 전에 아래 결정을 닫는다.

1. Capture reason persisted token spelling: `hourly_scheduled`와 `scheduled` 후보 중 하나를 고정한다.
2. Capture reason priority와 upsert downgrade/upgrade rule을 테스트 가능한 표로 닫는다.
3. `application_id + current_window_end_utc` duplicate identity에서 `generated_at`, `state_code`, helper column update semantics를 닫는다.
4. Scheduler cadence alignment를 UTC 기준으로 닫고 disabled application 처리 방식을 정한다.
5. Dashboard query fallback capture staleness threshold와 recursion 방지 방식을 닫는다.
6. Query fallback capture 저장 실패를 fail-open으로 둘지 fail-closed로 둘지 `api-surface.md`와 맞춰 닫는다.
7. Snapshot helper columns 실제 추가 범위와 index 범위를 닫는다.
8. Endpoint evidence max 10 selection order를 Story 5.5 handoff와 최종 정렬한다.
9. Instance summary max 50 selection policy tie-breaker를 deterministic하게 닫는다.
10. `endpointEvidenceRefs`에 5-8-a가 저장할 최소 ref field와 5-8-b가 나중에 채울 anchor field를 분리한다.
11. Save failure logging/metric/test strategy를 닫되, 실패가 marker/detail 성공처럼 보이지 않게 한다.
12. No heartbeat-to-snapshot, no raw explorer, no operational event table regression tests를 둔다.

## 8. 5-8-b Pre-Story Decision Queue

5-8-b story 파일 작성 전에 아래 결정을 닫는다.

1. Snapshot detail response wrapper shape와 저장된 `read_model_json` 노출/축약 방식을 닫는다.
2. Snapshot detail `404` 조건을 retention deletion, project/application mismatch, missing snapshot별로 닫는다.
3. Marker type seed set과 marker severity mapping을 stored state/triage/recovery 기준으로 닫는다.
4. `capture_reason`과 marker type의 관계를 닫되, operational event type과 혼동되지 않게 한다.
5. `previousState` source lookup window, ordering, tie-breaker를 닫는다.
6. `lastHealthyAt`의 healthy state set을 닫는다. 현재 accepted bucket만으로 만들지 않는 원칙은 유지한다.
7. Snapshot source가 없을 때 accepted bucket gap fallback이 어떤 field까지 채울 수 있는지 닫는다.
8. Recovery marker shape, copy, severity, `observing_recovery`와의 관계를 닫는다.
9. `endpointEvidenceRefs[].snapshotDetailAnchor` generation rule과 missing anchor response를 닫는다.
10. Snapshot marker/detail이 Story 5.9 operational event promotion으로 넘기는 최소 handoff field를 닫는다.
11. Current state 재판정 금지, UI-side calculation 금지, raw detail 노출 금지 테스트를 둔다.

## Closure Summary

Story 5.8은 아래와 같이 분해한다.

- `5-8-a-dashboard-snapshot-writer-and-capture-policy`: 저장 책임. writer, scheduler, fallback capture, capture reason, upsert/idempotency, bounded endpoint evidence, bounded instance summary, Story 5.7 `instanceSummary.items[]` fill.
- `5-8-b-snapshot-marker-detail-and-recovery-source`: 읽기 책임. snapshot detail, marker type/severity/read semantics, recovery marker, previousState, lastHealthyAt source priority, accepted bucket gap fallback priority, endpointEvidenceRefs anchor.
- `5.9-operational-event-history-api`: event 책임. promotion, deduplication, suppression, operational event API.

이 split은 5.7 stable instance trend source를 보존하면서 5.8의 writer/capture와 marker/detail/recovery 책임을 작게 나누기 위한 계약이다.
