---
artifactType: story
storyId: "5.8-a"
storyKey: "5-8-a-dashboard-snapshot-writer-and-capture-policy"
epic: "Epic 5. Dashboard Read Model and API"
title: "Dashboard Snapshot Writer and Capture Policy"
architectureStyle: Traditional MVC
status: review
date: 2026-05-27
---

# Story 5.8-a - Dashboard Snapshot Writer and Capture Policy

## User Story

portal 구현자로서, Application Dashboard read model을 정책에 맞게 `dashboard_snapshots`에 저장하는 writer와 capture policy를 제공하길 원한다.

그래야 1시간 coarse snapshot, state/high-confidence/fallback snapshot, bounded endpoint evidence, bounded instance summary가 안정적으로 저장되고, 후속 5-8-b와 5.9가 raw explorer나 current 재계산 없이 stored read model을 읽을 수 있다.

## Source of Truth

`implementation-artifacts/spec-story-5-8-a-dashboard-snapshot-writer-and-capture-policy-contract-decisions.md`가 Story 5.8-a의 최상위 결정 문서다. 2026-06-06 이후 scheduled/fallback 저장 eligibility는 `implementation-artifacts/spec-snapshot-heartbeat-gated-capture.md`가 보완한다.

아래 문서는 모두 읽고 반영한 story context다. 구현 중 충돌처럼 보이는 지점은 5.8-a 전용 결정 문서를 우선하고, Story 5.7이 고정한 `instanceSummary` source path와 minimum shape는 재논의하지 않는다.

1. `implementation-artifacts/spec-story-5-8-a-dashboard-snapshot-writer-and-capture-policy-contract-decisions.md`
2. `implementation-artifacts/spec-story-5-8-dashboard-snapshot-decomposition-contract-decisions.md`
3. `implementation-artifacts/spec-story-5-7-instance-snapshot-trend-contract-decisions.md`
4. `implementation-artifacts/spec-story-5-5-endpoint-priority-contract-decisions.md`
5. `implementation-artifacts/spec-story-5-6-instance-evidence-contract-decisions.md`
6. `implementation-artifacts/spec-story-5-4-triage-zero-insight-recovery-contract-decisions.md`
7. `planning-artifacts/contracts/read-model-contract.md`
8. `planning-artifacts/contracts/operational-event-history.md`
9. `planning-artifacts/database-schema.md`
10. `planning-artifacts/api-surface.md`
11. `planning-artifacts/sprint-plan.md`
12. `implementation-artifacts/sprint-status.yaml`
13. `planning-artifacts/stories/5-5-endpoint-priority-read-model.md`
14. `planning-artifacts/stories/5-6-instance-evidence-read-model.md`
15. `planning-artifacts/stories/5-7-instance-snapshot-trend-projection.md`

닫힌 계약:

1. persisted scheduled capture reason token은 `hourly_scheduled`다. `scheduled`는 새 write에 사용하지 않는다.
2. capture reason priority는 `state_change > high_confidence_concern > short_strong_spike > query_fallback > hourly_scheduled`다.
3. duplicate identity는 `application_id + current_window_end_utc`다.
4. 같은 identity row는 하나만 유지하고, incoming reason이 existing reason보다 높은 priority일 때만 representative row를 update한다.
5. scheduler 대상은 `applications.status=active`이고 snapshot retention horizon 안에 accepted bucket이 있으며 최근 starter heartbeat도 있는 application이다.
6. recent heartbeat 기준은 `starter_heartbeat_telemetry.interval_seconds`로 계산한 `max(90초, interval_seconds * 3)` 안에 `last_received_at_utc`가 있는 경우다.
7. heartbeat-only application, heartbeat row가 없거나 stale인 application, disabled application은 scheduled snapshot 대상이 아니다.
8. dashboard query fallback은 latest snapshot 없음 또는 `latest.generated_at <= queryAt - 65분` 조건과 recent heartbeat 조건을 모두 만족할 때만 snapshot 저장을 시도한다.
9. heartbeat가 missing/stale이면 fallback은 snapshot 저장만 건너뛰고 dashboard current response는 fail-open으로 계속 성공한다.
10. fallback writer는 이미 만든 dashboard read model을 저장하며 `DashboardReadModelService`를 다시 호출하지 않는다.
11. fallback 저장 실패는 짧은 1회 retry 후 fail-open이다.
12. helper column은 `capture_reason`, `primary_rule_id`, `primary_endpoint_key`, `max_confidence`다.
13. 5.8-a가 새로 확정하는 index/constraint는 unique identity와 `application_id + capture_reason + generated_at desc`까지만이다. Story 5.7 existing latest/retention 조회 index는 유지하고, 5.9 helper index는 추가하지 않는다.
14. endpoint evidence max 10 selection은 `endpointPriority.rank ASC`를 우선한다.
15. instance summary max 50 selection은 triage contributors, freshness attention, high request count 순서다.
16. `endpointEvidenceRefs`는 `endpointKey`, optional `method`, optional `route`, optional `relatedApplicationPriorityRank`, optional `relatedRuleIds`만 저장한다.
17. `snapshotDetailAnchor` 생성 규칙, marker/detail/recovery read semantics는 5-8-b로 넘긴다.
18. save failure는 structured log와 bounded meter metric으로 기록한다.
19. MVP scheduled capture는 portal service-layer scheduled task가 맡되, scheduler는 snapshot 생성 로직이 아니라 capture trigger/dispatcher 책임을 가진다.
20. 실제 dashboard read model generation orchestration, bounded evidence/summary fill, priority-aware idempotent upsert는 portal 내부 service/use case와 writer가 소유한다.
21. capture request는 MVP public API가 아니라 내부 service boundary이며, Post-MVP SQS/Lambda message contract 후보로만 남긴다.

## Scope / Out of Scope

포함:

- `dashboard_snapshots` writer service와 priority-aware idempotent upsert
- portal service-layer UTC hourly scheduled capture trigger/dispatcher와 recent heartbeat eligibility gate
- state-change, high-confidence concern, short-strong-spike, query fallback capture reason seed enum
- dashboard query fallback capture threshold, recursion guard, retry/fail-open
- 내부 capture request boundary 후보
- `dashboard_snapshots` helper columns와 writer-owned population/update semantics
- duplicate identity `application_id + current_window_end_utc` unique constraint
- bounded endpoint evidence max 10 저장
- bounded instance summary max 50 저장
- Story 5.7 `dashboard_snapshots.read_model_json.instanceSummary.items[]` wrapper와 minimum item shape fill
- `instanceSummary.items[].endpointEvidenceRefs[]` 최소 ref-only field 저장
- save failure structured log와 bounded meter metric
- heartbeat-only snapshot, heartbeat-derived read model, raw explorer, endpoint timeseries, operational event table regression guard tests

제외:

- marker/detail/recovery read semantics 구현
- snapshot detail API response shape, `404` 조건, stored JSON 노출/축약 방식
- marker type, severity, read semantics
- `previousState`, `lastHealthyAt`, recovery marker, recovery source priority
- operational event promotion, deduplication, suppression, API
- `operational_events` table, repository, materialized view, Redis/outbox
- Spring Batch, SQS, Lambda, public capture API, external trigger consumer, 별도 job metadata table
- raw snapshot explorer, raw bucket explorer, arbitrary metric query
- endpoint timeseries table/API
- endpoint p95/p99, endpoint percentile rollup, endpoint p99 alert 기준
- heartbeat를 accepted bucket freshness, host health, dashboard read model/source, recovery source, operational event source로 합성
- UI/controller/repository/DB trigger의 state/rule/p95/p99/endpoint priority 계산
- Story 5.7의 `instanceSummary.items[]` path, `schemaVersion=1.0`, `maxItems=50`, minimum field meaning 재논의

## Acceptance Criteria

1. Story 5.8-a는 Java/Kotlin/Spring production code를 지금 수정하는 산출물이 아니라, 이 story 파일과 sprint tracking만 생성된 상태에서 `ready-for-dev`가 된다.
2. 구현 story 실행 시 production 변경은 Traditional MVC + Service/Repository Layering과 feature-first package 구조를 따른다.
3. Snapshot capture orchestration과 `dashboard_snapshots` writer는 portal service layer 책임이다. Controller, repository, DB trigger, UI는 state, rule, p95/p99, endpoint priority, capture reason priority를 계산하지 않는다.
4. writer-owned capture reason enum 또는 동등 typed value는 `hourly_scheduled`, `state_change`, `high_confidence_concern`, `short_strong_spike`, `query_fallback`만 새 write token으로 사용한다.
5. scheduled capture persisted token은 정확히 `hourly_scheduled`다.
6. 새 write에서 `scheduled` token을 저장하지 않는다.
7. 기존 row의 `null`, unknown, legacy token은 read-side opaque metadata로 허용한다.
8. DB enum check, non-null constraint, marker mapping constraint로 `capture_reason`을 강제하지 않는다.
9. reason priority는 정확히 `state_change`, `high_confidence_concern`, `short_strong_spike`, `query_fallback`, `hourly_scheduled` 순서다.
10. 같은 window에 여러 reason이 동시에 성립하면 confidence 숫자 정렬이 아니라 fixed priority로 representative `capture_reason`을 결정한다.
11. confidence가 더 높다는 이유만으로 `short_strong_spike`가 `high_confidence_concern`을 덮어쓰지 않는다.
12. `state_change`는 hysteresis 이후 의미 있는 stored application state 변화일 때만 capture reason 후보가 된다.
13. `high_confidence_concern`은 confidence `>= 0.82` concern capture 후보로 제한한다.
14. `short_strong_spike`는 confidence `>= 0.90`이고 최근 5개 30초 bucket 중 2개 이상 bad인 후보로 제한한다.
15. 5.8-a는 60분 operational event suppression, promotion, deduplication을 구현하지 않는다.
16. duplicate identity는 `application_id + current_window_end_utc`다.
17. 같은 identity row가 없으면 insert한다.
18. 같은 identity row가 있고 incoming reason priority가 existing reason보다 높으면 existing row를 update한다.
19. 같은 identity row가 있고 incoming reason priority가 같거나 낮으면 representative row를 no-op으로 유지하고 `capture_reason`을 downgrade하지 않는다.
20. 같은 input으로 writer를 반복 실행해도 read-equivalent result가 유지된다.
21. `created_at`은 최초 row 생성 시각으로 유지한다.
22. higher-priority update 시 `generated_at`, `state_code`, `capture_reason`, `read_model_json`, helper column은 incoming representative read model 기준으로 갱신할 수 있다.
23. `generated_at`은 최초 insert 시각이 아니라 현재 row가 대표하는 stored dashboard read model 생성 시각이다.
24. migration은 next Flyway version으로 helper columns와 unique/index 보강을 추가한다. 기존 migration을 되감거나 배포된 migration을 임의 수정하지 않는다.
25. `primary_rule_id`, `primary_endpoint_key`, `max_confidence` column을 추가한다. 이미 `capture_reason`이 있으므로 length/nullability를 새 writer 계약과 맞춘다.
26. `primary_rule_id`는 representative read model의 가장 행동 가능한 concern rule id 복사값이다. 권장 source는 stored `triageCards` 첫 item의 `ruleId`, 없으면 top `endpointPriority` item의 첫 `ruleIds[]`다.
27. `primary_endpoint_key`는 representative concern endpoint의 low-cardinality endpoint key 복사값이다. 권장 source는 top `endpointPriority.endpointKey`, 없으면 triage `affectedEndpoint`다.
28. `max_confidence`는 stored `triageCards[].confidence`와 `endpointPriority[].confidence` 중 최대값이다. confidence-bearing concern이 없으면 `null`이다.
29. helper column은 raw metric, raw path/query, endpoint timeseries 저장소로 확장하지 않는다.
30. lower-priority incoming write는 helper column도 downgrade하지 않는다.
31. higher-priority incoming write는 helper column을 incoming representative read model 기준으로 함께 갱신한다.
32. 5.8-a가 새로 추가하는 index/constraint는 `unique(application_id, current_window_end_utc)`와 `application_id, capture_reason, generated_at desc` 조회 index뿐이다.
33. Story 5.7 existing latest/retention 조회 index는 유지한다.
34. 5.9 후보 index인 `application_id + primary_rule_id + generated_at desc`, `application_id + primary_endpoint_key + generated_at desc`, `application_id + max_confidence desc + generated_at desc`는 추가하지 않는다.
35. UTC hourly scheduler는 dashboard query 여부와 무관하게 application별 scheduled capture를 dispatch한다.
36. scheduler target `current_window_end_utc`는 UTC hourly boundary다. execution jitter가 있어도 target boundary가 흔들리지 않는다.
37. scheduled snapshot은 1시간 data aggregate가 아니라 target boundary 기준 dashboard current 15분과 baseline 15분 read model을 저장한다.
38. scheduled snapshot 대상은 `applications.status=active` application으로 제한한다.
39. scheduled snapshot 대상은 snapshot retention horizon 안에 accepted bucket이 하나 이상 있는 application으로 제한한다.
40. scheduled snapshot 대상은 recent starter heartbeat가 있는 application으로 제한한다. 기준은 `starter_heartbeat_telemetry.interval_seconds`로 계산한 `max(90초, interval_seconds * 3)` 안에 `last_received_at_utc`가 있는 경우다.
41. accepted bucket이 한 번도 없는 application과 heartbeat-only application은 scheduled snapshot 대상이 아니다.
42. heartbeat row가 없거나 stale인 application은 scheduled snapshot 대상이 아니다.
43. disabled application과 마지막 accepted bucket이 snapshot retention horizon 밖인 application은 scheduled snapshot 대상이 아니다.
44. scheduler eligibility 판단에서 heartbeat는 저장 gate로만 사용하고 accepted bucket freshness, state, read model source로 합성하지 않는다.
45. dashboard query fallback은 latest snapshot이 없고 recent starter heartbeat가 있으면 snapshot 저장을 검토한다.
46. latest snapshot이 있더라도 `latest.generated_at <= queryAt - 65분`이고 recent starter heartbeat가 있으면 fallback capture 저장을 검토한다.
47. `65분` grace threshold는 hourly scheduler jitter 허용값이며 exact current window match 요구로 바꾸지 않는다.
48. fallback capture는 query마다 snapshot을 저장하는 주 저장 경로가 아니다.
49. fallback writer는 dashboard query path에서 이미 생성한 current dashboard read model을 저장한다.
50. fallback writer는 fallback 저장을 위해 `DashboardReadModelService`를 다시 호출하지 않는다.
51. fallback 저장은 current dashboard 판단 기준, HTTP response model, state/triage/endpoint priority 계산 결과를 바꾸지 않는다. heartbeat가 missing/stale이면 snapshot 저장만 건너뛰고 dashboard current response는 fail-open으로 성공한다.
52. fallback 저장에서 transient duplicate/conflict 계열 실패가 나면 짧은 1회 retry를 허용한다.
53. fallback 최종 저장 실패는 dashboard current response를 실패시키지 않고 `200` fail-open으로 둔다.
54. fallback 저장 실패는 response warning field, failed `snapshotId`, snapshot link, marker/detail/history 후보로 노출하지 않는다.
55. scheduled 저장 실패는 application state/read model을 바꾸지 않고 다음 application 또는 다음 run으로 진행한다.
56. save failure는 partial row를 성공 row처럼 남기지 않는다.
57. save failure는 structured log와 bounded meter metric으로 기록한다.
58. metric name 후보는 `dashboard.snapshot.write.success`, `dashboard.snapshot.write.failure`다.
59. metric tag는 low-cardinality로 제한한다. 허용 후보는 `capture_reason`, `operation=insert|update|upsert`, `failure_type=duplicate_conflict|serialization|persistence|unknown`이다.
60. metric tag에 `applicationId`, `snapshotId`, `endpointKey`, raw route 같은 high-cardinality value를 넣지 않는다.
61. writer는 snapshot `read_model_json`에 bounded endpoint evidence block을 저장한다. 구현 중 정확한 block name을 선택하면 Javadoc/test fixture와 5-8-b handoff note에 명시한다.
62. stored endpoint evidence item은 최대 10개다.
63. endpoint evidence selection은 current `endpointPriority` item을 `rank ASC`로 먼저 선택한다.
64. high-confidence concern endpoint를 두 번째 seed로 보강한다.
65. triage `affectedEndpoint` 연결 endpoint를 세 번째 seed로 보강한다.
66. endpoint evidence는 `endpointKey` 기준으로 dedupe한다.
67. stored endpoint evidence allowed field는 `method`, `route`, `endpointKey`, `rank`, `reason`, `ruleIds`, `confidence`, `score`, `requestCount`, `errorRate`, `durationBuckets`, `baselineDurationBuckets`, `bucketDistributionSource`, `freshness`, `recommendedAction`으로 제한한다.
68. stored endpoint evidence에는 raw path, query string, query key/value, trace id, per-request sample, raw `endpoints_json`, endpoint p95/p99, endpoint percentile rollup, endpoint timeseries를 포함하지 않는다.
69. stored endpoint evidence로 endpoint priority를 다시 계산하지 않는다.
70. writer는 `dashboard_snapshots.read_model_json.instanceSummary.items[]`를 반드시 채운다.
71. `instanceSummary` wrapper는 `schemaVersion="1.0"`, `source="bounded_instance_summary"`, `maxItems=50`, `selectionPolicy="triage_contributors_then_freshness_attention_then_high_request_count"`, `items=[]`를 포함한다.
72. `instanceSummary.items[]`는 snapshot당 최대 50개다.
73. instance summary selection order는 application triage에 기여한 instance, freshness attention 대상 instance, current window requestCount가 높은 observed/active instance 순서다.
74. deterministic tie-breaker는 `lastSeenAt desc`, `instanceId asc`다.
75. `instanceId`는 `application_instances.id` UUID string이며 canonical matching key다.
76. `instanceName`은 display metadata이며 matching fallback key가 아니다.
77. item minimum shape는 Story 5.7이 고정한 `instanceId`, `instanceName`, `observationStatus`, `metricData`, `starterConnection`, nullable `starterPercentilePoint`, nullable/empty `resourceHints`, `applicationTriageContribution`, `endpointEvidenceRefs`를 모두 포함한다.
78. `metricData.statusSource`는 `accepted_bucket`이고 accepted bucket axis summary만 담는다.
79. `starterConnection.statusSource`는 `starter_heartbeat`이고 `stateImpact=none`을 유지한다.
80. `starterPercentilePoint`는 source-scoped starter canonical latest point 하나만 저장한다. series, average, max, merge, histogram-derived percentile을 만들지 않는다.
81. `resourceHints`는 latest accepted bucket sample hint로만 저장한다. state, score, root cause 입력으로 만들지 않는다.
82. `applicationTriageContribution`은 application triage와의 bounded bridge로만 저장한다.
83. `endpointEvidenceRefs`는 stored endpoint evidence를 가리키는 bounded reference로만 저장한다.
84. `endpointEvidenceRefs[]`가 저장하는 field는 `endpointKey`, optional `method`, optional `route`, optional `relatedApplicationPriorityRank`, optional `relatedRuleIds`로 제한한다.
85. 5.8-a는 `snapshotDetailAnchor`를 생성하거나 저장하지 않는다. anchor generation rule은 5-8-b로 넘긴다.
86. `endpointEvidenceRefs`에는 request/error count body, error rate body, duration buckets, baseline buckets, confidence, score, recommended action body, endpoint p95/p99, raw endpoint JSON을 넣지 않는다.
87. Story 5.7 `InstanceSnapshotTrendParser`가 의존하는 field 이름과 의미를 rename, remove, reinterpret하지 않는다.
88. `read_model_json.instances[]` live navigation list를 instance trend source로 확장하지 않는다.
89. `read_model_json.snapshot` marker/link 후보를 instance summary source로 사용하지 않는다.
90. snapshot writer는 current dashboard read model을 저장하되 current state, rule, p95/p99, endpoint priority를 새로 계산하는 DB view, trigger, repository path를 만들지 않는다.
91. heartbeat success/missing은 accepted bucket freshness, dashboard read model/source, recovery source, operational event source로 합성하지 않는다. 최근 heartbeat는 scheduled/fallback snapshot 저장 gate로만 사용한다.
92. raw snapshot explorer endpoint를 만들지 않는다.
93. raw bucket explorer endpoint를 만들지 않는다.
94. endpoint timeseries table/API를 만들지 않는다.
95. `operational_events` table/repository/API를 만들지 않는다.
96. snapshot marker/detail/recovery API를 5.8-a에서 만들지 않는다.
97. snapshot 저장 실패나 absence를 5-8-b가 current dashboard regeneration으로 보완하도록 약속하지 않는다.
98. Focused tests가 capture reason token/priority, idempotent upsert, scheduler heartbeat eligibility, fallback threshold/no-write/fail-open, helper column update, endpoint evidence cap, instance summary cap/minimum shape, endpoint ref field, save failure metric/log, regression non-goals를 검증한다.
99. `./gradlew :observability-portal:test`와 `git diff --check`가 통과해야 implementation completion으로 볼 수 있다.

## Tasks / Subtasks

- [x] Snapshot schema 보강과 persistence model 확장 (AC: 16~34, 57~60, 90, 98~99)
  - [x] next Flyway migration으로 `primary_rule_id`, `primary_endpoint_key`, `max_confidence`를 추가한다.
  - [x] `uk_dashboard_snapshots_application_current_window_end` 또는 동등한 unique constraint를 `application_id, current_window_end_utc`에 추가한다.
  - [x] `idx_dashboard_snapshots_app_capture_generated` 또는 동등한 index를 `application_id, capture_reason, generated_at desc`에 추가한다.
  - [x] 5.9 helper index와 `operational_events` physical object를 추가하지 않는다.
  - [x] `DashboardSnapshotEntity` mapping을 helper columns, full window metadata, nullable capture reason에 맞게 확장한다.
  - [x] schema integration test가 table/column/constraint/index/comment를 검증한다.

- [x] Capture reason와 writer command/read model serialization 경계 구현 (AC: 3~15, 21~31, 57~60, 90)
  - [x] writer-owned `DashboardSnapshotCaptureReason` 또는 동등 enum/value object를 추가하고 persisted token을 고정한다.
  - [x] fixed priority comparator를 구현한다.
  - [x] writer input command는 application/project/window metadata, `ApplicationDashboardReadModel`, capture reason 후보, target `currentWindowEndUtc`를 받는다.
  - [x] 내부 capture request boundary의 최소 field 후보는 `projectId`, `applicationId`, `captureReason`, `currentWindowEndUtc`, `requestedAt`, optional `triggerSource`로 둔다.
  - [x] helper column extractor를 구현해 `primaryRuleId`, `primaryEndpointKey`, `maxConfidence`를 representative read model에서 뽑는다.
  - [x] `ObjectMapper` serialization 실패를 structured failure로 기록하고 partial row를 남기지 않는다.

- [x] Priority-aware idempotent upsert 구현 (AC: 16~23, 30~34, 52~60, 98~99)
  - [x] `DashboardSnapshotRepository`에 latest lookup, identity lookup, insert/update/upsert method를 추가한다.
  - [x] same identity existing row의 priority를 비교해 lower/equal priority는 no-op으로 유지한다.
  - [x] higher priority는 representative metadata, `read_model_json`, helper column을 update하되 `created_at`은 유지한다.
  - [x] duplicate/conflict race는 unique constraint와 retry-safe transaction으로 수렴한다.
  - [x] idempotency service test와 repository integration test를 추가한다.

- [x] UTC hourly scheduled capture trigger/dispatcher 구현 (AC: 35~44, 55, 57~60, 90~96, 98~99)
  - [x] portal service-layer UTC hourly boundary scheduler를 추가한다. Spring `@Scheduled` 사용 시 zone은 UTC로 고정한다.
  - [x] execution jitter와 무관하게 target `currentWindowEndUtc`는 해당 hourly boundary로 고정한다.
  - [x] active application, retention horizon 안 accepted bucket, recent starter heartbeat 조건을 repository/service에서 조회한다.
  - [x] heartbeat-only application, accepted bucket 없는 application, heartbeat row가 없거나 stale인 application, disabled application을 제외한다.
  - [x] scheduled dispatcher는 내부 capture request를 portal capture service/use case에 전달한다.
  - [x] portal capture service/use case는 `hourly_scheduled` reason으로 read model을 한 번 생성해 writer에 전달한다.
  - [x] scheduled save failure는 structured log/metric 후 다음 대상 또는 다음 run으로 넘어간다.
  - [x] MVP 구현에 Spring Batch, SQS, Lambda, outbox, Redis, materialized view, 별도 job metadata table을 추가하지 않는다.

- [x] Dashboard query fallback capture 구현 (AC: 45~54, 57~60, 90~97)
  - [x] dashboard current query path에서 latest snapshot 없음 또는 latest `generated_at <= queryAt - 65분` 조건과 recent starter heartbeat 조건을 모두 만족할 때만 fallback capture 저장을 시도한다.
  - [x] heartbeat가 missing/stale이면 snapshot 저장만 건너뛰고 dashboard response는 fail-open으로 둔다.
  - [x] fallback writer에는 이미 생성한 `ApplicationDashboardReadModel`을 전달한다.
  - [x] fallback writer 또는 helper가 `DashboardReadModelService`를 다시 호출하지 않도록 dependency/test guard를 둔다.
  - [x] transient duplicate/conflict 실패는 짧은 1회 retry 후 fail-open으로 둔다.
  - [x] fallback 실패 시 dashboard response status와 body를 오염시키지 않는다.

- [x] Bounded endpoint evidence 저장 구현 (AC: 61~69, 84~86, 92~96, 98~99)
  - [x] stored endpoint evidence block을 writer-owned top-level JSON extension으로 추가한다.
  - [x] `endpointPriority.rank ASC`, high-confidence concern endpoint, triage `affectedEndpoint` 순서로 seed를 모은다.
  - [x] `endpointKey` 기준 dedupe와 max 10 cap을 적용한다.
  - [x] allowed field만 serialization하고 raw endpoint/body/timeseries/p95/p99 field absence를 test로 고정한다.
  - [x] `endpointEvidenceRefs`가 endpoint evidence body를 복사하지 않도록 ref-only 변환을 분리한다.

- [x] Bounded instance summary 저장 구현 (AC: 70~89, 91, 98~99)
  - [x] `read_model_json.instanceSummary` wrapper를 Story 5.7 contract 그대로 추가한다.
  - [x] max 50 cap과 `triage_contributors_then_freshness_attention_then_high_request_count` selection policy를 구현한다.
  - [x] tie-breaker `lastSeenAt desc`, `instanceId asc`를 적용한다.
  - [x] Story 5.7 minimum item shape를 모두 채운다.
  - [x] `endpointEvidenceRefs`에는 최소 ref field만 넣고 `snapshotDetailAnchor`를 생성하지 않는다.
  - [x] `InstanceSnapshotTrendParserTest` 또는 writer fixture test로 5.7 parser가 stored summary를 projection할 수 있음을 검증한다.

- [x] Regression guard와 full verification (AC: 90~99)
  - [x] heartbeat-only application과 heartbeat missing/stale application이 scheduled snapshot 대상이 아님을 테스트한다.
  - [x] heartbeat success/missing이 accepted bucket freshness, dashboard read model/source, recovery/event source로 합성되지 않음을 테스트한다.
  - [x] raw snapshot explorer/raw bucket explorer/endpoint timeseries/operational event table/API가 추가되지 않았음을 architecture or smoke test로 확인한다.
  - [x] controller/repository/DB trigger가 state/rule/p95/p99/endpoint priority를 계산하지 않는 boundary guard를 유지한다.
  - [x] focused tests, relevant repository integration tests, `MvcLayerBoundaryTest`, full portal test, `git diff --check`를 실행한다.

## Dev Notes

### Contract Priority

- 최우선 source는 `implementation-artifacts/spec-story-5-8-a-dashboard-snapshot-writer-and-capture-policy-contract-decisions.md`다.
- 2026-06-06 이후 scheduled/fallback 저장 eligibility는 `implementation-artifacts/spec-snapshot-heartbeat-gated-capture.md`가 보완한다.
- `read-model-contract.md`, `operational-event-history.md`, `database-schema.md`, `api-surface.md`에는 5.8-a 이전 후보 표현이 남아 있다. 예를 들어 older helper 후보의 `scheduled` token, endpoint evidence selection order, dashboard fallback `500` rough mapping은 5.8-a 결정 문서가 덮어쓴다.
- Story 5.7의 `instanceSummary.items[]` path, `schemaVersion=1.0`, `maxItems=50`, minimum field meaning은 절대 다시 열지 않는다.
- 5.8 decomposition 문서 기준으로 5.8-a는 "무엇을 언제 저장하는가"만 닫는다. "저장된 것을 어떻게 읽고 marker/detail/recovery로 연결하는가"는 5-8-b다.

### Current Code State

- `dashboard_snapshots` table은 Story 5.7의 `V006__create_dashboard_snapshots.sql`로 이미 있다.
- 현재 `dashboard_snapshots`에는 `capture_reason` nullable column과 read-side indexes가 있지만, 5.8-a helper columns와 unique `application_id + current_window_end_utc` constraint는 없다.
- `DashboardSnapshotEntity`는 read-side entity이고 setter/constructor가 없어 writer insert/update에는 확장이 필요하다.
- `DashboardSnapshotRepository`와 `DashboardSnapshotJpaRepository`는 instance trend projection용 newest-first read query만 제공한다.
- `InstanceSnapshotTrendParser`는 `dashboard_snapshots.read_model_json.instanceSummary.items[]`만 읽고, 5.8 writer가 이 minimum shape를 채워야 한다는 Javadoc을 이미 갖고 있다.
- `ApplicationDashboardReadModel`은 typed `endpointPriority`, `triageCards`, `instances` navigation hint를 포함한다. `snapshot` field는 현재 `null` placeholder다.
- `DashboardReadModelService.getDashboard(projectId, applicationId)`는 clock 기반 query instant를 UTC 30초 boundary로 floor해 current/baseline 15분 window를 만든다.
- scheduled snapshot은 hourly boundary target을 고정해야 하므로, dev-story에서 `DashboardReadModelService`에 supplied evaluation instant를 받는 internal method/command를 추가할지 검토해야 한다. Query current behavior는 바꾸지 않는다.
- `MetricBucketRepository`는 application/latest bucket, current/baseline aggregate, endpoint rows, recent five buckets, instance-scope evidence projections를 제공한다.
- `EndpointEvidenceAggregationService`는 Story 5.5/5.6에서 endpoint JSON parsing, safe route validation, histogram boundary merge를 공유한다. Snapshot endpoint evidence도 raw JSON parsing drift를 만들지 않도록 재사용 후보로 본다.
- `ApplicationRepository.findActiveApplicationsEligibleForScheduledSnapshot(...)`는 `status=active`, accepted bucket retention/cutoff, recent starter heartbeat 조건을 함께 조회한다.
- `ApplicationEntity`는 `status` field를 갖지만 accessor가 없다. Scheduler eligibility 구현 시 repository query 또는 `status()` accessor가 필요할 수 있다.
- `ApplicationInstanceRepository.findByApplicationIdOrderByLastSeenAtDescInstanceNameAsc`는 dashboard navigation entry max 50 source다. Stored instance summary는 더 깊은 minimum shape가 필요하므로 이 shallow entry만 복사하면 Story 5.7 contract를 만족하지 못한다.

### Implementation File Candidates

아래는 dev-story 구현 후보이며, 이 create-story 작업에서는 수정하지 않았다.

- `observability-portal/src/main/resources/db/migration/V007__extend_dashboard_snapshots_for_writer_policy.sql`
  - helper columns, unique identity, capture reason index 추가 후보
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/entity/DashboardSnapshotEntity.java`
  - writer insert/update mapping 확장 후보
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/repository/DashboardSnapshotJpaRepository.java`
  - identity lookup, latest lookup, update query 또는 locking query 추가 후보
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/repository/DashboardSnapshotRepository.java`
  - priority-aware upsert facade 추가 후보
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/*`
  - `DashboardSnapshotCaptureRequest`, `DashboardSnapshotWriteCommand`, `DashboardSnapshotCaptureReason`, stored evidence DTO 후보
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotCaptureService.java`
  - scheduled trigger가 전달한 capture request를 portal 내부에서 처리하는 use case 후보. Post-MVP external trigger도 같은 use case 호출 후보로만 남긴다.
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotWriterService.java`
  - priority-aware idempotent upsert writer 후보
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotScheduler.java`
  - UTC hourly scheduled capture trigger/dispatcher 후보
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotCapturePolicy.java`
  - reason eligibility, priority, fallback threshold 후보
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotReadModelEnricher.java`
  - endpoint evidence block과 instance summary block fill 후보
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/DashboardReadModelService.java`
  - supplied evaluation instant, fallback capture hook, already-created read model handoff 후보
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/repository/ApplicationRepository.java`
  - active eligible application 조회 후보
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/MetricBucketRepository.java`
  - retention horizon accepted bucket exists/latest lookup 보강 후보
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/EndpointEvidenceAggregationService.java`
  - snapshot endpoint evidence parsing/merge reuse 후보
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/**`
  - writer/repository/scheduler/capture policy focused tests 후보
- `observability-portal/src/test/java/com/observation/portal/domain/instance/service/InstanceSnapshotTrendParserTest.java`
  - 5.8 writer fixture와 5.7 parser compatibility regression 후보
- `observability-portal/src/test/java/com/observation/portal/architecture/MvcLayerBoundaryTest.java`
  - no raw explorer/no operational events/no endpoint timeseries/package boundary guard 후보

### Design Notes

- MVP scheduled capture는 portal service-layer scheduled task가 내부 trigger/dispatcher 역할을 맡는다. 이 scheduler는 eligible application과 target hourly boundary를 정하고, 실제 dashboard read model generation orchestration과 idempotent upsert는 portal 내부 capture service/use case와 writer에 위임한다.
- 내부 capture request 후보 field는 `projectId`, `applicationId`, `captureReason`, `currentWindowEndUtc`, `requestedAt`, optional `triggerSource`다. 이는 public API가 아니며, Post-MVP SQS/Lambda message contract 후보로만 재사용할 수 있다.
- Post-MVP에 SQS/Lambda external trigger를 붙이더라도 Lambda는 capture request 전달만 맡고 dashboard read model 계산, endpoint/instance evidence 생성, `dashboard_snapshots` 직접 저장을 소유하지 않는다.
- Scheduled capture와 query fallback은 둘 다 writer를 사용하지만 input source가 다르다. Scheduled capture는 eligible application마다 target hourly boundary 기준 dashboard read model을 한 번 생성한다. Query fallback은 query path에서 이미 생성한 read model을 그대로 writer에 넘긴다.
- fallback recursion guard는 dependency와 call path 양쪽에서 검증한다. `DashboardSnapshotWriterService`는 `DashboardReadModelService`를 주입받지 않는 구조가 가장 안전하다.
- same-window `query_fallback`이 먼저 저장되고 이후 `state_change`가 들어오면 같은 row를 higher priority로 update한다. 반대로 `state_change` 이후 `hourly_scheduled`가 들어와도 row는 downgrade하지 않는다.
- `ApplicationDashboardReadModel.snapshot` field에 snapshot link/id를 넣는 것은 조심해야 한다. fallback save failure는 response를 오염시키지 않아야 하며, snapshot link/detail semantics는 5-8-b 책임이다.
- `read_model_json`에 instance summary를 추가할 때 live dashboard `instances[]` navigation entry와 혼동하지 않는다. `instances[]`는 shallow navigation hint이고 `instanceSummary.items[]`가 stored trend source다.
- endpoint evidence block의 exact top-level field name은 5.8-a 구현 중 고정해야 한다. 단 `endpointEvidenceRefs`에는 body가 아니라 ref만 저장하고, 5-8-b가 detail anchor rule을 닫을 수 있게 handoff note/test fixture를 남긴다.

### Previous Story Intelligence

- Story 5.5는 `endpointPriority`를 canonical endpoint ranking source로 닫았다. 5.8-a endpoint evidence selection은 이 ranking을 먼저 사용하고 새 endpoint priority를 계산하지 않는다.
- Story 5.5는 stale/down 직전 endpoint evidence를 current priority로 되살리지 않고 snapshot/history handoff로 남겼다. 5.8-a가 그 stored handoff substrate를 처음 채운다.
- Story 5.6은 instance evidence의 accepted bucket axis와 starter heartbeat axis를 분리했다. 5.8-a instance summary도 `metricData`와 `starterConnection`을 합쳐 instance health를 만들지 않는다.
- Story 5.6은 `endpointEvidence`를 selected instance drill-down evidence로만 두고 새 rank/confidence/action을 만들지 않았다. 5.8-a `endpointEvidenceRefs`도 ref-only로 제한한다.
- Story 5.7은 `dashboard_snapshots` read-side substrate와 `InstanceSnapshotTrendParser`를 만들었다. 5.8-a는 writer/capture를 추가하지만 5.7 parser source path와 opaque `captureReason` behavior를 깨지 않는다.
- Story 5.7은 missing snapshot/source/item을 empty trend로 처리한다. 5.8-a save failure는 없는 snapshot을 성공처럼 노출하지 않아야 한다.

### Architecture Constraints

- Active baseline은 Traditional MVC + Service/Repository Layering이다.
- `domain`은 feature grouping namespace이며 pure DDD domain layer가 아니다.
- `application`, `port`, `adapter`, `adapter.in`, `adapter.out` package를 만들지 않는다.
- Controller는 service를 호출하고 repository를 직접 호출하지 않는다.
- Service는 필요하면 Spring Data repository와 JPA entity를 직접 사용할 수 있지만 JPA entity를 API/service external model로 직접 반환하지 않는다.
- Flyway SQL migration이 schema source of truth다. Hibernate DDL auto create/update는 사용하지 않는다.
- DB view, materialized view, trigger, stored procedure에 state, rule, priority, confidence, action, p95/p99, endpoint priority, capture policy 계산을 숨기지 않는다.
- MVP snapshot capture에 Spring Batch, SQS, Lambda, outbox, Redis, materialized view, 별도 job metadata table을 도입하지 않는다.
- 공개 클래스/메서드/핵심 helper Javadoc/comment는 AGENTS.md 지침에 따라 한국어로 작성한다.

## Test Strategy

Focused test 대상 후보:

- `DashboardSnapshotCaptureReasonTest`
- `DashboardSnapshotCapturePolicyTest`
- `DashboardSnapshotWriterServiceTest`
- `DashboardSnapshotRepositoryIntegrationTest`
- `DashboardSnapshotSchedulerTest`
- `DashboardSnapshotFallbackCaptureTest`
- `DashboardSnapshotReadModelEnricherTest`
- `InstanceSnapshotTrendParserTest` compatibility fixture
- `CatalogSchemaMigrationIntegrationTest`
- `MvcLayerBoundaryTest`

필수 scenario:

- `hourly_scheduled` token이 정확히 저장되고 `scheduled` token은 새 write에 사용되지 않는다.
- reason priority가 `state_change > high_confidence_concern > short_strong_spike > query_fallback > hourly_scheduled` 순서다.
- same identity insert 후 lower/equal priority write는 no-op이고 higher priority write만 representative row를 update한다.
- `created_at`은 preserve되고 `generated_at`은 representative read model generated time으로 update된다.
- helper columns는 insert와 higher-priority update 때 채워지고 lower-priority write로 downgrade되지 않는다.
- unique `application_id + current_window_end_utc`가 duplicate row를 막는다.
- active application이며 retention horizon 안 accepted bucket과 recent starter heartbeat가 모두 있는 경우만 scheduled target이다.
- recent starter heartbeat 기준은 heartbeat row의 `interval_seconds`로 계산한 `max(90초, interval_seconds * 3)` 안에 `last_received_at_utc`가 있는 경우다.
- accepted bucket 없음, heartbeat-only, heartbeat missing/stale, disabled, retention horizon 밖 last bucket은 scheduled target이 아니다.
- scheduled target hourly boundary와 current/baseline 15분 window가 UTC 기준으로 계산된다.
- query fallback은 latest snapshot 없음 또는 latest generatedAt이 queryAt보다 65분 이상 오래된 조건과 recent starter heartbeat 조건을 모두 만족할 때만 snapshot 저장을 시도한다.
- query fallback에서 heartbeat가 missing/stale이면 snapshot 저장만 건너뛰고 dashboard response는 fail-open으로 성공한다.
- query fallback writer가 `DashboardReadModelService`를 재호출하지 않는다.
- fallback duplicate/conflict transient failure는 1회 retry 후 fail-open이다.
- fallback final failure에도 dashboard response는 200이고 warning/snapshot link/failed snapshot id를 노출하지 않는다.
- save failure는 partial row를 남기지 않고 structured log와 bounded meter metric을 기록한다.
- endpoint evidence는 max 10, endpointPriority rank ASC 우선, high-confidence concern, triage affectedEndpoint 순서와 dedupe를 따른다.
- endpoint evidence에는 raw path/query/trace/per-request sample/raw `endpoints_json`/endpoint p95/p99가 없다.
- instance summary wrapper는 `schemaVersion=1.0`, `source=bounded_instance_summary`, `maxItems=50`, expected selectionPolicy를 포함한다.
- instance summary items는 max 50이고 Story 5.7 minimum shape를 모두 채운다.
- `metricData.statusSource=accepted_bucket`, `starterConnection.statusSource=starter_heartbeat`, `stateImpact=none`이다.
- `starterPercentilePoint`는 latest one point이며 series/average/max/merge/histogram percentile이 아니다.
- `endpointEvidenceRefs`는 최소 ref-only field만 포함하고 `snapshotDetailAnchor`를 생성하지 않는다.
- 5.7 parser가 5.8-a writer fixture의 `instanceSummary.items[]`를 projection한다.
- raw snapshot explorer, raw bucket explorer, endpoint timeseries table/API, operational_events table/API/repository가 추가되지 않았다.
- controller/repository/DB trigger/UI가 state/rule/p95/p99/endpoint priority를 계산하지 않는다.
- Spring Batch, SQS, Lambda, outbox, Redis, materialized view, 별도 job metadata table이 MVP scheduled capture 구현에 추가되지 않았다.

Suggested commands:

```bash
./gradlew :observability-portal:test --tests '*DashboardSnapshot*'
./gradlew :observability-portal:test --tests '*InstanceSnapshotTrendParser*'
./gradlew :observability-portal:test --tests '*DashboardReadModel*'
./gradlew :observability-portal:test --tests com.observation.portal.domain.catalog.repository.CatalogSchemaMigrationIntegrationTest
./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest
./gradlew :observability-portal:test
git diff --check
```

## 5-8-b Handoff

5.8-a는 아래를 5-8-b로 넘긴다.

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

5.8-a가 5-8-b에 제공하는 substrate:

- unique identity로 안정화된 `dashboard_snapshots` row
- persisted `capture_reason` token과 helper columns
- stored dashboard read model JSON
- bounded endpoint evidence max 10
- Story 5.7 compatible `instanceSummary.items[]` max 50
- ref-only `endpointEvidenceRefs[]`

5.8-a 자체는 marker/detail/recovery source semantics를 닫지 않는다.

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- 2026-05-27: BMAD create-story workflow 설정, project-context, sprint status, 5.8-a/5.8 decomposition/5.7/5.6/5.5/5.4 결정 문서, read-model/operational-event/database/api/sprint plan, 기존 5.5/5.6/5.7 story를 확인했다.
- 2026-05-27: 현재 코드의 `dashboard_snapshots` V006 substrate, `DashboardSnapshotRepository`, `InstanceSnapshotTrendParser`, `DashboardReadModelService`, endpoint aggregation/current read model 구조를 확인했다.
- 2026-05-27: Story 5.8-a create-story 산출물을 `planning-artifacts/stories/5-8-a-dashboard-snapshot-writer-and-capture-policy.md`에 생성했다.
- 2026-05-27: 기존 diff와 신규 snapshot writer/capture/service/test 파일을 읽고 Java time 직렬화 실패를 재현했다.
- 2026-05-27: `./gradlew :observability-portal:test --tests '*DashboardSnapshot*'` 실패 원인을 `PortalJsonConfiguration`의 plain `ObjectMapper`와 JSR-310 module 부재로 확인했다.
- 2026-05-27: focused snapshot tests, scheduler eligibility integration test, schema/architecture guard, full `./gradlew :observability-portal:test`, `git diff --check`를 실행해 통과를 확인했다.

### Completion Notes List

- create-story 산출물 위에서 production/test 구현을 이어 진행했다.
- Story 5.8-a scope, acceptance criteria, dev notes, 구현 파일 후보, 테스트 전략, 5-8-b handoff를 구현 기준으로 사용했다.
- sprint-status workflow 관례에 따라 `5-8-a-dashboard-snapshot-writer-and-capture-policy`는 review로 전환한다.
- `dashboard_snapshots` writer policy용 V007 migration, helper column mapping, unique identity, capture reason index를 추가했다.
- writer-owned capture reason enum, capture request/write command/value/result/latest row 모델과 priority-aware idempotent writer를 구현했다.
- scheduled capture dispatcher, internal capture use case, query fallback fail-open capture, Java time JSON configuration, bounded write metrics/logging을 추가했다.
- `snapshotEndpointEvidence` top-level block과 Story 5.7 compatible `instanceSummary.items[]` wrapper/minimum shape fill을 writer enricher에서 구현했다.
- state-change/high-confidence/short-strong-spike eligibility policy와 fallback duplicate retry 범위, scheduler heartbeat eligibility, non-goal regression guard를 focused tests로 고정했다.
- 검증: `./gradlew :observability-portal:test --tests '*DashboardSnapshot*'`, `./gradlew :observability-portal:test --tests '*DashboardSnapshot*' --tests com.observation.portal.domain.catalog.repository.ApplicationRepositoryIntegrationTest --tests com.observation.portal.domain.catalog.repository.CatalogSchemaMigrationIntegrationTest --tests com.observation.portal.architecture.MvcLayerBoundaryTest`, `./gradlew :observability-portal:test`, `git diff --check`.

### File List

- `planning-artifacts/stories/5-8-a-dashboard-snapshot-writer-and-capture-policy.md`
- `implementation-artifacts/sprint-status.yaml`
- `implementation-artifacts/spec-story-5-8-a-dashboard-snapshot-writer-and-capture-policy-contract-decisions.md`
- `implementation-artifacts/spec-story-5-8-dashboard-snapshot-decomposition-contract-decisions.md`
- `observability-portal/build.gradle`
- `observability-portal/src/main/java/com/observation/portal/PortalApplication.java`
- `observability-portal/src/main/java/com/observation/portal/config/PortalJsonConfiguration.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/entity/ApplicationEntity.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/repository/ApplicationRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/DashboardReadModelService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/entity/DashboardSnapshotEntity.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotCaptureReason.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotCaptureRequest.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotLatestRow.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotWriteCommand.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotWriteResult.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotWriteValues.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/repository/DashboardSnapshotJpaRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/repository/DashboardSnapshotRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotCapturePolicy.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotCaptureService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotFallbackCaptureService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotReadModelEnricher.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotScheduler.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotWriteException.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotWriteMetrics.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotWriterService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/package-info.java`
- `observability-portal/src/main/resources/db/migration/V007__extend_dashboard_snapshots_for_writer_policy.sql`
- `observability-portal/src/test/java/com/observation/portal/architecture/MvcLayerBoundaryTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/catalog/repository/ApplicationRepositoryIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/catalog/repository/CatalogSchemaMigrationIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotCaptureReasonTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/repository/DashboardSnapshotRepositoryIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotCapturePolicyTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotFallbackCaptureServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotSchedulerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotWriterServiceIntegrationTest.java`

### Change Log

- 2026-05-27: Story 5.8-a Dashboard Snapshot Writer and Capture Policy create-story 산출물을 생성했다.
- 2026-05-27: Dashboard snapshot writer/capture policy 구현, bounded evidence/instance summary fill, scheduler/fallback path, regression guards를 완료했다.

## Status

review
