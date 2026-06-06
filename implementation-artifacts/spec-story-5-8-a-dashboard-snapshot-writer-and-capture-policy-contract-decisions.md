---
artifactType: contract-decisions
projectName: Spring Boot 운영 첫 화면 포털
storyId: "5.8-a"
storyKey: "5-8-a-dashboard-snapshot-writer-and-capture-policy"
status: closed
date: 2026-05-27
scope: Story 5-8-a pre-story contract closure before BMAD story creation
---

# Story 5-8-a Dashboard Snapshot Writer and Capture Policy Contract Decisions

## Purpose

Story 5-8-a는 dashboard snapshot을 "언제, 어떤 이유로, 어떤 bounded read model로 저장하는가"를 닫는다.

이 문서는 Story 5-8-a story 파일을 만들기 전에 사용자가 직접 결정한 writer/capture 계약을 기록한다. 5-8-a는 snapshot row 저장, portal service-layer scheduled capture trigger/dispatcher, query fallback capture, idempotent upsert, bounded endpoint evidence, bounded instance summary fill을 구현할 수 있게 하는 계약만 닫는다.

5-8-a는 marker/detail/recovery read semantics, operational event promotion/dedup/suppression/API를 구현하지 않는다. 해당 의미는 5-8-b와 5.9로 넘긴다.

## Authority and Non-Reopen Rules

이 문서는 아래 계약을 기준으로 한다.

- `implementation-artifacts/spec-story-5-8-dashboard-snapshot-decomposition-contract-decisions.md`
- `implementation-artifacts/spec-story-5-7-instance-snapshot-trend-contract-decisions.md`
- `implementation-artifacts/spec-story-5-5-endpoint-priority-contract-decisions.md`
- `implementation-artifacts/spec-story-5-6-instance-evidence-contract-decisions.md`
- `implementation-artifacts/spec-story-5-4-triage-zero-insight-recovery-contract-decisions.md`
- `planning-artifacts/contracts/read-model-contract.md`
- `planning-artifacts/contracts/operational-event-history.md`
- `planning-artifacts/database-schema.md`
- `planning-artifacts/api-surface.md`
- `planning-artifacts/sprint-plan.md`
- `implementation-artifacts/sprint-status.yaml`

Story 5.7이 고정한 아래 계약은 5-8-a에서 다시 열지 않는다.

- `dashboard_snapshots.read_model_json.instanceSummary.items[]`가 instance snapshot trend의 stable source path다.
- `instanceSummary.schemaVersion = "1.0"`과 `maxItems = 50`은 유지한다.
- `instanceSummary.items[]` minimum shape와 field meaning은 rename, remove, reinterpret하지 않는다.
- `instanceId`는 canonical matching key이고 `instanceName`은 display metadata다.
- 5.7 trend API는 stored snapshot projection이며 current state, rule, p95/p99, endpoint priority, recovery marker를 재계산하지 않는다.
- `captureReason`은 5.7 trend에서는 nullable opaque metadata로 유지한다.

5-8-a는 아래 금지선을 유지한다.

- marker/detail/recovery read semantics를 구현하지 않는다.
- operational event promotion, deduplication, suppression, API를 구현하지 않는다.
- `operational_events` table, event repository, materialized view, Redis/outbox를 만들지 않는다.
- Spring Batch, SQS, Lambda, 별도 job metadata table을 MVP snapshot capture에 도입하지 않는다.
- raw snapshot explorer, raw bucket explorer, endpoint timeseries를 만들지 않는다.
- endpoint p95/p99, endpoint percentile rollup, endpoint p99 alert 기준을 만들지 않는다.
- UI/controller/repository/DB trigger가 lifecycle state, rule, p95/p99, endpoint priority를 계산하지 않는다.
- heartbeat를 accepted bucket freshness, host application health, dashboard read model/source, recovery source, operational event source로 합성하지 않는다. 최근 heartbeat는 scheduled/fallback snapshot 저장 eligibility gate로만 사용할 수 있다.

## Closed Decisions

### 1. Capture Reason Token Spelling Contract

5-8-a writer가 새로 저장하는 `capture_reason` persisted token은 아래 seed enum으로 고정한다.

| Token | 의미 |
|---|---|
| `hourly_scheduled` | application별 UTC hourly scheduled snapshot |
| `state_change` | hysteresis 이후 의미 있는 stored application state 변화 |
| `high_confidence_concern` | confidence `>= 0.82` concern capture |
| `short_strong_spike` | confidence `>= 0.90`이고 최근 5개 30초 bucket 중 2개 이상 bad인 실험적 capture 후보 |
| `query_fallback` | dashboard query 시 최신 snapshot이 없거나 명백히 오래되어 fallback으로 저장한 snapshot |

결정 내용:

- 정기 snapshot token은 `hourly_scheduled`로 고정한다.
- `scheduled`는 새 write에서 사용하지 않는다.
- 기존 row의 null, unknown, legacy token은 read side에서 opaque metadata로 허용한다.
- DB enum check 또는 non-null constraint로 `capture_reason`을 강제하지 않는다.

결정 이유:

- `hourly_scheduled`는 1시간 cadence 의미를 token만으로 드러낸다.
- 5.7은 `captureReason`을 opaque metadata로 복사하므로 read compatibility를 유지해야 한다.
- writer enum은 닫되 DB column은 nullable/unknown 허용으로 유지하면 기존 fixture와 후속 marker/event 의미 확장을 깨지 않는다.

금지:

- 새 writer가 `scheduled` token을 저장하지 않는다.
- `capture_reason`만으로 marker severity 또는 operational event type을 확정하지 않는다.

### 2. Capture Reason Priority and Upsert Upgrade Contract

같은 `application_id + current_window_end_utc`에 여러 capture reason이 동시에 성립하면 아래 priority로 대표 `capture_reason`을 결정한다.

Priority:

1. `state_change`
2. `high_confidence_concern`
3. `short_strong_spike`
4. `query_fallback`
5. `hourly_scheduled`

결정 내용:

- reason이 되기 위한 eligibility 판단에는 각 reason의 threshold와 confidence guard를 사용한다.
- 같은 window에서 대표 reason을 고르는 순서는 confidence 숫자 정렬이 아니라 위 fixed priority를 따른다.
- lower-priority reason은 existing higher-priority snapshot semantics를 downgrade하지 않는다.
- higher-priority reason은 같은 duplicate identity row를 enrich 또는 replace할 수 있다.
- 이 priority는 snapshot writer의 row 대표값 결정이며 operational event dedup/suppression이 아니다.

결정 이유:

- `state_change`는 사용자가 history에서 가장 먼저 이해해야 할 운영 흐름이다.
- `high_confidence_concern`은 정식 concern capture이고, `short_strong_spike`는 실험적 후보이므로 그 뒤에 둔다.
- `query_fallback`은 사용자 조회 보완 경로이고, `hourly_scheduled`는 기본 coarse cadence이므로 가장 낮다.

금지:

- confidence가 더 높다는 이유만으로 `short_strong_spike`가 `high_confidence_concern`을 덮어쓰지 않는다.
- 5-8-a에서 60분 suppression window 또는 operational event promotion을 구현하지 않는다.

### 3. Duplicate Identity and Row Update Semantics Contract

5-8-a writer의 duplicate identity는 아래로 고정한다.

```text
application_id + current_window_end_utc
```

결정 내용:

- 같은 identity row가 없으면 insert한다.
- 같은 identity row가 있고 incoming reason priority가 existing reason보다 높으면 existing row를 update한다.
- 같은 identity row가 있고 incoming reason priority가 같거나 낮으면 `capture_reason`을 downgrade하지 않는다.
- 같은 input으로 writer를 반복 실행해도 read-equivalent result를 유지한다.
- `created_at`은 최초 row 생성 시각으로 유지한다.
- higher-priority update 시 `generated_at`, `state_code`, `capture_reason`, `read_model_json`, helper column을 incoming representative read model 기준으로 갱신할 수 있다.
- `generated_at`은 "최초 insert 시각"이 아니라 "현재 row가 대표하는 stored dashboard read model 생성 시각"으로 본다.

결정 이유:

- scheduler, query fallback, state-change capture가 같은 window에 동시에 들어올 수 있다.
- same-window row를 reason별로 여러 개 만들면 5.7 trend point와 coarse hourly history가 흔들린다.
- insert-only 정책은 `hourly_scheduled`가 먼저 저장된 뒤 `state_change`가 누락될 수 있다.

금지:

- 같은 `application_id + current_window_end_utc`에 reason별 row를 여러 개 만들지 않는다.
- lower-priority `query_fallback` 또는 `hourly_scheduled`가 higher-priority row를 덮어쓰지 않는다.

### 4. Scheduled Snapshot Cadence and Eligible Application Contract

5-8-a scheduled snapshot은 dashboard query 여부와 무관하게 portal service-layer scheduled trigger/dispatcher가 UTC hourly boundary에서 실행한다.

결정 내용:

- scheduler cadence는 UTC hourly boundary다.
- scheduler는 snapshot 생성 로직 자체가 아니라 capture trigger/dispatcher 책임을 가진다.
- scheduler는 eligible application과 target `current_window_end_utc`를 정한 뒤 portal 내부 capture service/use case에 요청을 전달한다.
- 실제 dashboard read model generation orchestration, bounded evidence/summary fill, priority-aware idempotent upsert는 portal 내부 service/use case와 writer가 소유한다.
- scheduled snapshot은 1시간 데이터 집계가 아니라 실행 시점 dashboard read model의 current 15분 + baseline 15분 저장이다.
- eligible application은 `applications.status = active`이고 최근 dashboard snapshot retention horizon 안에 accepted bucket이 있으며, 최근 starter heartbeat도 있는 application이다.
- 최근 starter heartbeat는 `starter_heartbeat_telemetry.interval_seconds` 기준 `max(90초, interval_seconds * 3)` 안에 `last_received_at_utc`가 있는 경우다.
- accepted bucket이 한 번도 없는 application은 scheduled snapshot 대상에서 제외한다.
- heartbeat만 있는 application은 scheduled snapshot 대상에서 제외한다.
- disabled application은 scheduled snapshot 대상에서 제외한다.
- 마지막 accepted bucket이 retention horizon 밖이면 scheduled snapshot 대상에서 제외한다.
- heartbeat row가 없거나 stale이면 `hourly_scheduled` snapshot 대상에서 제외한다.

예시:

```text
앱 metric bucket 수신 시작: 11:45
앱 metric bucket 중단: 12:50
starter heartbeat 마지막 수신: 12:59, interval_seconds: 30
UTC hourly scheduler: 13:00 실행
마지막 accepted bucket: 12:50 근처
결과: retention horizon 안에 accepted bucket이 있고 heartbeat가 max(90초, 30초 * 3) 안에 있으므로 13:00 scheduled snapshot 대상이 될 수 있다.
```

13:00 snapshot의 read model window는 아래처럼 저장된다.

```text
generated_at: 13:00
capture_reason: hourly_scheduled
current_window: 12:45 ~ 13:00
baseline_window: 12:30 ~ 12:45
```

결정 이유:

- snapshot/history는 사용자가 dashboard를 보지 않았던 동안의 coarse 운영 흐름을 남겨야 한다.
- heartbeat-only application을 저장하면 heartbeat를 snapshot source로 합성하는 금지선을 깰 수 있다.
- stale/missing heartbeat application을 계속 저장하면 포털이 실제로 관찰하지 못한 smoke/test application의 반복 `down` snapshot이 늘어날 수 있다.
- retention horizon 밖 application을 계속 저장하면 오래 방치된 app의 반복 down snapshot이 무한히 쌓인다.

금지:

- dashboard 조회가 있어야만 hourly snapshot이 생기는 구조로 만들지 않는다.
- heartbeat success만으로 application을 scheduled snapshot eligible로 만들지 않는다. accepted bucket retention 조건도 함께 필요하다.
- heartbeat missing/stale application을 `hourly_scheduled` snapshot 대상으로 삼지 않는다.
- disabled application을 scheduled snapshot 대상으로 삼지 않는다.
- scheduler, Spring Batch job, DB trigger, 외부 worker가 dashboard read model 계산이나 `dashboard_snapshots` 직접 저장 책임을 소유하지 않는다.

### 4-a. Capture Request Boundary and Post-MVP External Trigger Candidate

MVP의 capture request는 public API가 아니라 portal 내부 service boundary 표현이다. 같은 모양은 Post-MVP SQS/Lambda 기반 external trigger message contract 후보로만 남긴다.

MVP 결정 내용:

- portal service-layer scheduled task는 내부 capture request를 만들어 portal 내부 capture service/use case를 호출한다.
- query fallback은 이미 생성된 dashboard read model을 같은 저장 경계에 넘기되, fallback 저장을 위해 dashboard read model을 다시 생성하지 않는다.
- 내부 capture request의 최소 field 후보는 `projectId`, `applicationId`, `captureReason`, `currentWindowEndUtc`, `requestedAt`, optional `triggerSource`다.
- `triggerSource`는 scheduler/fallback/external 같은 low-cardinality 진단 metadata 후보이며 writer priority나 marker/event semantics를 대신하지 않는다.

Post-MVP note:

- SQS/Lambda를 붙이더라도 external trigger는 capture request를 전달하는 역할만 맡는 방향을 권장한다.
- Lambda는 dashboard read model 계산, endpoint/instance evidence 생성, priority-aware upsert, `dashboard_snapshots` 직접 저장을 소유하지 않는다.
- external trigger message contract를 도입하더라도 Story 5.7 `instanceSummary.items[]` stable contract와 5-8-a non-goal은 다시 열지 않는다.

금지:

- MVP acceptance scope에 SQS queue, Lambda consumer, public capture API, Spring Batch, outbox, Redis, materialized view, 별도 job metadata table 구현을 포함하지 않는다.
- capture request를 raw explorer, recovery source, operational event source, heartbeat recovery path로 확장하지 않는다.

### 5. Dashboard Query Fallback Capture Threshold and Recursion Guard Contract

Dashboard query fallback capture는 scheduler 누락과 첫 진입 공백을 보완하는 보험 경로다.

결정 내용:

- dashboard query 시 latest snapshot이 없으면 fallback capture를 시도한다.
- latest snapshot이 있더라도 `latest.generated_at <= queryAt - 65분`이면 fallback capture를 시도한다.
- 단, `query_fallback` snapshot 저장은 최근 starter heartbeat가 있을 때만 시도한다.
- `65분`은 hourly scheduler의 작은 지연과 clock/execution jitter를 허용하기 위한 grace threshold다.
- fallback capture는 query마다 snapshot을 저장하는 경로가 아니다.
- fallback 저장은 이미 생성한 current dashboard read model을 writer에 전달한다.
- snapshot writer는 fallback 저장을 위해 `DashboardReadModelService`를 다시 호출하지 않는다.
- heartbeat가 missing/stale이면 fallback snapshot 저장만 건너뛰고 current dashboard 판단 기준과 response 성공 여부는 바꾸지 않는다.

결정 이유:

- scheduler가 일시적으로 실패하거나 새 application이 방금 관측된 경우 snapshot history 공백을 보완해야 한다.
- latest snapshot exact current window match를 요구하면 dashboard 조회 빈도만큼 `query_fallback` snapshot이 쌓일 수 있다.
- writer가 dashboard service를 재호출하면 recursion과 중복 계산 위험이 생긴다.

금지:

- dashboard query fallback을 주 저장 경로로 사용하지 않는다.
- fallback capture가 accepted bucket을 dashboard snapshot으로 복제하는 경로가 되지 않는다.
- fallback capture가 heartbeat missing/stale을 dashboard response 실패 조건으로 바꾸지 않는다.
- fallback writer가 dashboard current read model을 재생성하지 않는다.

### 6. Query Fallback Save Failure Contract

Query fallback snapshot 저장 실패는 dashboard current response를 실패시키지 않는 fail-open 경로로 둔다.

결정 내용:

- fallback writer는 idempotent upsert를 시도한다.
- transient conflict 또는 duplicate 계열 실패는 짧게 1회 retry할 수 있다.
- heartbeat가 missing/stale이라서 fallback 저장을 건너뛴 경우도 dashboard current response는 실패하지 않는다.
- 최종 실패해도 dashboard current response는 `200`으로 반환한다.
- 실패는 structured log와 metric으로 남긴다.
- 실패한 snapshot row, `snapshotId`, snapshot link, marker/detail/history 후보를 response에 노출하지 않는다.
- 저장 실패로 snapshot row가 없으면 5-8-b detail/marker source도 없는 것이다.

결정 이유:

- fallback capture는 보조 저장 경로이며 current dashboard response의 source of truth는 `DashboardReadModelService` 결과다.
- current read model은 정상 생성됐는데 snapshot persistence 실패만으로 dashboard를 못 보게 하면 사용자 경험이 나빠진다.
- 실패한 snapshot을 성공 marker처럼 노출하는 것이 더 위험하다.

금지:

- fallback 저장 실패를 dashboard response warning field로 노출하지 않는다.
- partial row를 성공 row처럼 남기지 않는다.
- 5-8-b가 저장 실패로 없는 snapshot을 current dashboard 재생성으로 보완하지 않는다.

### 7. Snapshot Helper Column and Index Contract

5-8-a는 writer가 채울 helper column과 idempotent upsert identity를 닫되, 5.9 history query 최적화 index는 후속으로 남긴다.

5-8-a에서 확정하는 column:

- `capture_reason`
- `primary_rule_id`
- `primary_endpoint_key`
- `max_confidence`

5-8-a에서 확정하는 constraint/index:

- unique identity: `application_id + current_window_end_utc`
- `application_id + capture_reason + generated_at desc` 조회 index
- existing latest 조회 index는 유지한다.

5.9로 유보하는 index:

- `application_id + primary_rule_id + generated_at desc`
- `application_id + primary_endpoint_key + generated_at desc`
- `application_id + max_confidence desc + generated_at desc`

결정 이유:

- helper column은 writer가 저장 시점에 함께 채워야 후속 marker/history 후보가 JSON parsing에만 의존하지 않는다.
- 5-8-a는 operational event history API를 구현하지 않으므로 event query 전용 index를 과하게 선반영하지 않는다.
- unique identity는 duplicate/upsert/idempotency의 물리적 안전장치다.

금지:

- helper column을 raw metric, endpoint timeseries, raw path/query 저장소로 확장하지 않는다.
- DB trigger/view/materialized view가 `primary_rule_id`, `primary_endpoint_key`, `max_confidence`를 계산하지 않는다.

### 8. Stored Endpoint Evidence Selection and Field Cap Contract

5-8-a는 snapshot `read_model_json`에 bounded endpoint evidence를 최대 10개까지 저장한다.

Selection order:

1. current `endpointPriority` item, `rank ASC`
2. high-confidence concern endpoint
3. triage `affectedEndpoint` 연결 endpoint

결정 내용:

- `endpointKey` 기준으로 dedupe한다.
- 최대 10개까지만 저장한다.
- Story 5.5의 `endpointPriority`가 canonical endpoint ranking source다.
- triage `affectedEndpoint`는 optional hint이므로 priority list와 1:1로 맞지 않아도 오류가 아니다.

허용 stored field:

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

금지 field:

- raw path
- query string
- query key/value
- trace id
- per-request sample
- raw `endpoints_json`
- endpoint p95/p99
- endpoint percentile rollup
- endpoint timeseries

결정 이유:

- 5.5가 이미 endpoint priority ranking과 item shape를 닫았다.
- snapshot endpoint evidence는 과거 시점의 bounded copy이지 raw explorer가 아니다.
- high-confidence/triage endpoint는 handoff 추적성을 위해 보강하되 canonical ranking을 대체하지 않는다.

금지:

- endpoint evidence cap 10을 넘기지 않는다.
- stored endpoint evidence로 endpoint priority를 다시 계산하지 않는다.

### 9. Bounded Instance Summary Selection and 5.7 Minimum Shape Fill Contract

5-8-a writer는 Story 5.7이 고정한 source path를 반드시 채운다.

Fixed path:

```text
dashboard_snapshots.read_model_json.instanceSummary.items[]
```

Fixed wrapper:

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

Selection order:

1. application triage에 기여한 instance
2. freshness attention 대상 instance
3. current window requestCount가 높은 observed/active instance
4. deterministic tie-breaker: `lastSeenAt desc`, `instanceId asc`

결정 내용:

- snapshot당 `items[]`는 최대 50개다.
- Story 5.7 minimum shape를 모두 채운다.
- `instanceId`를 canonical matching key로 저장한다.
- `instanceName`은 display metadata로 저장한다.
- `metricData`는 accepted bucket axis summary다.
- `starterConnection`은 starter heartbeat control-plane axis summary이며 `stateImpact=none`을 유지한다.
- `starterPercentilePoint`는 source-scoped starter canonical latest point 하나만 저장한다.
- `resourceHints`는 latest accepted bucket sample hint로만 저장한다.
- `applicationTriageContribution`은 application triage와의 bounded bridge로만 저장한다.
- `endpointEvidenceRefs`는 stored endpoint evidence를 가리키는 bounded reference로만 저장한다.

Minimum item shape:

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
  "starterPercentilePoint": null,
  "resourceHints": null,
  "applicationTriageContribution": {
    "status": "available",
    "contributed": false,
    "relatedRuleIds": [],
    "reason": "no_action_needed"
  },
  "endpointEvidenceRefs": []
}
```

결정 이유:

- 5.7 trend projection은 이 path와 minimum shape에 의존한다.
- triage/freshness/request-count 순서는 문제 상황에서 사용자가 확인해야 할 instance가 trend에 남을 가능성을 높인다.
- heartbeat와 metric axis를 분리해야 instance state나 host health를 새로 만들지 않는다.

금지:

- `instanceSummary.items[]`를 instance health score source로 만들지 않는다.
- raw accepted bucket JSON, raw endpoint JSON, full histogram distribution, endpoint evidence body 전체를 trend point에 넣지 않는다.
- 5.7 parser가 의존하는 minimum field 이름이나 의미를 바꾸지 않는다.

### 10. Endpoint Evidence Reference and 5-8-b Anchor Handoff Contract

5-8-a는 `instanceSummary.items[].endpointEvidenceRefs[]`에 endpoint evidence body가 아니라 최소 ref만 저장한다.

5-8-a가 저장하는 ref field:

- `endpointKey`
- optional `method`
- optional `route`
- optional `relatedApplicationPriorityRank`
- optional `relatedRuleIds`

5-8-b로 넘기는 field/decision:

- optional `snapshotDetailAnchor`
- anchor generation rule
- missing anchor response
- detail anchor와 stored endpoint evidence 연결 semantics

결정 이유:

- trend point는 endpoint evidence body를 담지 않고 snapshot detail로 이동할 수 있는 작은 참조만 가져야 한다.
- snapshot detail response shape와 anchor generation은 5-8-b 책임이다.
- 5-8-a에서 anchor naming을 앞당기면 detail contract를 먼저 고정하게 된다.

금지:

- `endpointEvidenceRefs`에 request/error count body, error rate body, duration buckets, baseline buckets, confidence, score, recommended action body를 넣지 않는다.
- endpoint p95/p99, raw `endpoints_json`, raw path/query/trace/per-request sample을 넣지 않는다.
- ref가 detail anchor를 찾지 못한다고 trend point 전체를 invalid로 만들지 않는다.

### 11. Save Failure Logging, Metric, and Test Strategy Contract

Snapshot save failure는 structured log와 bounded meter metric으로 기록한다. 새 failure table, API, response warning field는 만들지 않는다.

결정 내용:

- 성공 시 optional debug log 또는 success counter는 허용한다.
- 실패 시 warn/error structured log를 남긴다.
- 실패 시 bounded counter metric을 증가시킨다.
- metric tag는 낮은 cardinality로 제한한다.
- `applicationId`, `snapshotId`, endpointKey 같은 high-cardinality value는 metric tag로 사용하지 않는다.

권장 metric:

```text
dashboard.snapshot.write.success
dashboard.snapshot.write.failure
```

허용 tag 후보:

- `capture_reason`
- `operation = insert | update | upsert`
- `failure_type = duplicate_conflict | serialization | persistence | unknown`

필수 테스트:

- scheduled snapshot 저장 실패가 application state/read model을 바꾸지 않는다.
- query fallback 저장 실패가 dashboard response `200`을 막지 않는다.
- 저장 실패 시 `dashboard_snapshots` partial row가 남지 않는다.
- 실패한 `snapshotId`, link, marker가 response에 노출되지 않는다.

결정 이유:

- 실패를 운영자가 추적할 수 있어야 하지만, 5-8-a가 별도 failure audit/event store를 만들면 scope가 커진다.
- response warning field는 dashboard current response shape를 snapshot persistence 상태로 오염시킨다.

금지:

- snapshot failure audit table을 만들지 않는다.
- 실패를 operational event로 승격하지 않는다.
- failure metric에 high-cardinality tag를 붙이지 않는다.

### 12. Regression Guard Contract

5-8-a는 boundary + behavior regression guard를 acceptance/test에 포함한다.

필수 guard:

- heartbeat-only application은 scheduled snapshot 대상이 아니다.
- heartbeat row가 없거나 stale인 application은 `hourly_scheduled` snapshot 대상이 아니다.
- dashboard query fallback은 heartbeat가 missing/stale이면 snapshot 저장만 건너뛰고 current response는 fail-open으로 성공한다.
- heartbeat는 accepted bucket freshness, dashboard read model/source, recovery source, operational event source로 합성되지 않는다.
- raw snapshot explorer endpoint를 만들지 않는다.
- raw bucket explorer endpoint를 만들지 않는다.
- endpoint timeseries table/API를 만들지 않는다.
- `operational_events` table/repository/API를 만들지 않는다.
- snapshot detail/marker/recovery API를 5-8-a에서 만들지 않는다.
- UI/controller/repository/DB trigger가 state/rule/p95/p99/endpoint priority를 계산하지 않는다.
- endpoint evidence max 10과 금지 field를 검증한다.
- `instanceSummary.items[]` max 50과 Story 5.7 minimum shape를 검증한다.
- query fallback 저장은 이미 만든 dashboard read model을 사용하고 service 재호출 recursion이 없다.

결정 이유:

- 5-8-a는 writer를 추가하면서 기존 금지선을 건드리기 쉬운 story다.
- 특히 heartbeat-only snapshot, heartbeat-derived state/read model, raw explorer/endpoint timeseries/operational event table 추가는 프로젝트 전역 계약을 깨뜨린다.
- cap/shape/recursion은 문서만으로는 깨지기 쉬워 behavior test가 필요하다.

금지:

- regression guard를 acceptance 문장에만 남기고 테스트 없이 구현하지 않는다.

## 5-8-b Handoff

5-8-a는 아래 결정을 5-8-b로 명시적으로 넘긴다.

- snapshot detail response wrapper shape와 stored `read_model_json` 노출/축약 방식
- snapshot detail `404` 조건
- marker type, severity, read semantics
- `capture_reason`과 marker type의 관계
- recovery marker shape, copy, severity
- snapshot source 기반 `previousState` lookup
- `lastHealthyAt` healthy state set과 source priority
- accepted bucket gap fallback이 채울 수 있는 field
- `endpointEvidenceRefs[].snapshotDetailAnchor` generation rule
- missing anchor response
- snapshot marker/detail이 Story 5.9 operational event promotion으로 넘기는 최소 handoff field

5-8-a가 저장하는 snapshot row와 bounded evidence는 5-8-b가 읽을 수 있는 substrate다. 5-8-a 자체는 marker/detail/recovery source semantics를 닫지 않는다.

## Story 5-8-a Seed Summary

Story 5-8-a should implement dashboard snapshot writer and capture policy:

- writer-owned capture reason enum with canonical `hourly_scheduled`
- reason priority and priority-aware upsert
- duplicate identity `application_id + current_window_end_utc`
- portal service-layer UTC hourly scheduled capture trigger/dispatcher for active applications with accepted bucket inside snapshot retention horizon and recent starter heartbeat
- dashboard query fallback capture when latest snapshot is missing or older than 65 minutes and starter heartbeat is recent
- fallback fail-open after short retry on transient conflict/duplicate failures
- internal capture request boundary candidate with `projectId`, `applicationId`, `captureReason`, `currentWindowEndUtc`, `requestedAt`, optional `triggerSource`
- helper columns `capture_reason`, `primary_rule_id`, `primary_endpoint_key`, `max_confidence`
- unique identity and bounded capture reason index
- stored endpoint evidence max 10
- stored bounded instance summary max 50 at `read_model_json.instanceSummary.items[]`
- Story 5.7 minimum instance summary shape fill
- minimal `endpointEvidenceRefs` ref-only field
- structured log + bounded metric for save failure
- regression tests for heartbeat-only snapshot, heartbeat-derived read model, raw explorer, endpoint timeseries, operational_events, marker/detail/recovery non-goals

Story 5-8-a must not implement:

- SQS/Lambda external trigger, public capture API, Spring Batch, outbox, Redis, materialized view, or separate job metadata table
- snapshot marker API
- snapshot detail API response semantics
- recovery marker semantics
- operational event history API
- operational event promotion, deduplication, suppression
- `operational_events` table or repository
- raw explorer
- endpoint timeseries
- UI-side state/rule/p95/p99/endpoint priority calculation
