---
artifactType: story
storyId: "5.8-b"
storyKey: "5-8-b-snapshot-marker-detail-recovery-source"
epic: "Epic 5. Dashboard Read Model and API"
title: "Snapshot Marker, Detail, and Recovery Source"
architectureStyle: Traditional MVC
status: done
date: 2026-05-27
---

# Story 5.8-b - Snapshot Marker, Detail, and Recovery Source

## Status

done

## Story

portal 구현자로서, 저장된 dashboard snapshot을 detail API와 marker API, recovery source로 안전하게 읽는 read model을 제공하길 원한다.

그래야 사용자가 특정 시점의 dashboard 상태와 bounded evidence를 current 재계산 없이 확인하고, 후속 Story 5.9가 operational event history promotion/dedup/suppression을 안정적인 snapshot candidate 위에서 구현할 수 있다.

## Source of Truth

`implementation-artifacts/spec-story-5-8-b-snapshot-marker-detail-recovery-contract-decisions.md`가 Story 5.8-b의 최상위 결정 문서다.

아래 문서는 모두 읽고 반영한 story context다. 구현 중 충돌처럼 보이는 지점은 5.8-b 전용 결정 문서를 우선하고, 5.8-a가 고정한 writer/capture/upsert 저장 계약과 Story 5.7이 고정한 `instanceSummary` source path/minimum shape는 재논의하지 않는다.

1. `implementation-artifacts/spec-story-5-8-b-snapshot-marker-detail-recovery-contract-decisions.md`
2. `implementation-artifacts/spec-story-5-8-dashboard-snapshot-decomposition-contract-decisions.md`
3. `implementation-artifacts/spec-story-5-8-a-dashboard-snapshot-writer-and-capture-policy-contract-decisions.md`
4. `implementation-artifacts/spec-story-5-7-instance-snapshot-trend-contract-decisions.md`
5. `implementation-artifacts/spec-story-5-4-triage-zero-insight-recovery-contract-decisions.md`
6. `implementation-artifacts/spec-story-5-5-endpoint-priority-contract-decisions.md`
7. `implementation-artifacts/spec-story-5-6-instance-evidence-contract-decisions.md`
8. `planning-artifacts/contracts/read-model-contract.md`
9. `planning-artifacts/contracts/operational-event-history.md`
10. `planning-artifacts/database-schema.md`
11. `planning-artifacts/api-surface.md`
12. `planning-artifacts/current-product-source-of-truth.md`
13. `planning-artifacts/sprint-plan.md`
14. `implementation-artifacts/sprint-status.yaml`

닫힌 계약:

1. Snapshot detail endpoint는 `GET /api/projects/{projectId}/applications/{applicationId}/dashboard/snapshots/{snapshotId}`다.
2. Snapshot marker endpoint는 `GET /api/projects/{projectId}/applications/{applicationId}/dashboard/snapshot-markers?since=24h&limit=50`다.
3. Detail/marker source는 `dashboard_snapshots` row metadata와 stored `read_model_json`이다.
4. Detail/marker service는 current accepted bucket, heartbeat, current dashboard read model을 live join하지 않는다.
5. Detail/marker service는 current state, lifecycle state, rule, p95/p99, endpoint priority, marker severity, operational event를 재계산하지 않는다.
6. Detail response는 raw `read_model_json` dump가 아니라 typed/bounded wrapper다.
7. Marker는 stored snapshot/read model point annotation이며 operational event item이 아니다.
8. Marker type seed set은 `scheduled_snapshot`, `query_fallback_snapshot`, `state_change`, `state_observation`, `high_confidence_concern`, `short_strong_spike`, `recovery_observed`, `stored_snapshot`이다.
9. Marker severity enum은 `info`, `warning`, `critical`이며 stored state/triage/recovery field에서 읽는다. `capture_reason`만으로 확정하지 않는다.
10. `capture_reason`은 marker 후보 seed일 뿐이며 persisted value는 그대로 표시할 수 있다.
11. Marker type 해석 priority는 `recovery_observed`, `state_change`, `high_confidence_concern`, `short_strong_spike`, `state_observation`, `query_fallback_snapshot`, `scheduled_snapshot`, `stored_snapshot` 순서다.
12. `scheduled` legacy token은 새 writer token이 아니며 read side에서는 null/unknown/legacy와 함께 조회 실패가 아닌 `stored_snapshot` fallback 후보로 처리한다.
13. `previousState`는 같은 application의 strictly earlier `current_window_end_utc` snapshot에서만 가져온다.
14. `lastHealthyAt` normalized value는 같은 application의 previous active snapshot `generated_at`에서만 만든다.
15. `active`만 MVP healthy state set이다.
16. Recovery marker는 `recovery_observed`이며 "회복 관찰 중"이다. 회복 완료/장애 해결 marker가 아니다.
17. accepted bucket gap fallback은 snapshot source보다 낮은 priority이며 detail/marker 조회 중 새로 계산하지 않는다.
18. Retention으로 detail snapshot이 없으면 `404 snapshot_not_found_or_expired`이고 current dashboard로 대체하지 않는다.
19. Marker horizon 안에 snapshot이 없으면 `200 + markers=[] + emptyState.reasonCode=no_snapshots_in_retention`이다.
20. 5.8-a stored endpoint evidence block name은 `snapshotEndpointEvidence`이며, detail anchor는 이 block의 `items[]` stored order에서 `endpoint-evidence-{n}`으로 생성한다.
21. `instanceSummary.items[].endpointEvidenceRefs[]`는 `endpointKey`를 canonical match key로 같은 snapshot의 `snapshotEndpointEvidence.items[]`에 연결한다.
22. Match 성공 시 detail response의 ref projection은 `snapshotDetailAnchor`와 `anchorStatus=resolved`를 제공할 수 있다.
23. Match 실패 시 ref는 유지하고 `snapshotDetailAnchor=null` 또는 `anchorStatus=missing`으로 표현한다. 전체 detail/trend를 invalid로 만들지 않는다.
24. 5.9 handoff field는 snapshot id/state/capture reason/marker/helper/anchor/link 후보를 안정화하되, 5.9 event API와 dedup/suppression은 구현하지 않는다.

## Scope / Out of Scope

포함:

- snapshot detail API
- snapshot marker API와 response shape
- marker type, severity, read semantics
- `capture_reason`을 marker seed로 해석하는 read-side 규칙
- detail/marker가 stored snapshot/read model만 읽고 current state를 재판정하지 않는 boundary
- 5.7 `endpointEvidenceRefs`가 snapshot detail anchor로 연결되는 방식
- recovery marker
- snapshot source 기반 `previousState`
- `lastHealthyAt` snapshot source priority
- accepted bucket gap fallback vs snapshot source priority
- retention으로 snapshot이 없을 때의 status/copy
- Story 5.9 operational event history handoff
- boundary, regression, copy guard tests

제외:

- snapshot writer, scheduler, capture policy 구현 또는 재정의
- 5.7 `instanceSummary` path/minimum shape 변경
- 5.8-a capture reason 저장/upsert 계약 재정의
- operational event history event list API 구현
- operational event promotion, deduplication, suppression 최종 구현
- 별도 `operational_events` table, event repository, materialized view, Redis/outbox 도입
- raw snapshot explorer, raw bucket explorer, endpoint timeseries 제공
- endpoint p95/p99 계산, endpoint percentile rollup, endpoint p99 alert 기준
- UI-side lifecycle state/rule/p95/p99/endpoint priority/event 계산
- heartbeat를 accepted bucket freshness, host application health, dashboard snapshot, recovery source, operational event source로 합성

## Acceptance Criteria

1. Story 5.8-b는 Java/Kotlin/Spring production code를 지금 수정하는 산출물이 아니라, 이 story 파일과 sprint tracking만 생성된 상태에서 `ready-for-dev`가 된다.
2. 구현 story 실행 시 production 변경은 Traditional MVC + Service/Repository Layering과 feature-first package 구조를 따른다.
3. Snapshot detail endpoint는 `GET /api/projects/{projectId}/applications/{applicationId}/dashboard/snapshots/{snapshotId}`로 제공한다.
4. Detail API는 `projectId + applicationId + snapshotId` membership을 검증한다.
5. `{snapshotId}`가 UUID 형식이 아니면 `400`과 `invalid_snapshot_id` 계열 오류로 매핑한다.
6. project/application/snapshot이 없거나 서로 맞지 않으면 `404`로 매핑한다.
7. retention cleanup으로 snapshot row가 없으면 `404`와 `snapshot_not_found_or_expired` copy를 반환한다.
8. retention detail miss message는 "저장된 snapshot detail이 없거나 보관 기간이 지나 더 이상 없습니다." 계열을 사용한다.
9. Detail miss recommended action은 "현재 상태는 application dashboard에서 다시 확인하세요." 계열을 사용한다.
10. Detail lookup 또는 stored JSON projection 실패는 generic `500`으로 매핑하고 current dashboard fallback을 시도하지 않는다.
11. Detail response source는 `dashboard_snapshots` row metadata와 stored `read_model_json`이다.
12. Detail response는 raw `read_model_json` 전체 dump를 별도 field로 반환하지 않는다.
13. Detail response는 typed/bounded wrapper로 `generatedAt`, `source=dashboard_snapshots`, `readSemantics`, `snapshot`, `marker`, `previousState`, `lastHealthyAt`, `recoveryMarker`, `readModel`, `snapshotEndpointEvidence`, `instanceSummary`, `links`를 제공한다.
14. Detail `readSemantics.mode`는 `stored_snapshot_detail`이다.
15. Detail `readSemantics.currentStateRecalculated=false`를 반환하고 테스트로 고정한다.
16. Detail `readSemantics.liveSourcesJoined=[]`를 반환하고 테스트로 고정한다.
17. Detail `readSemantics.rawReadModelJsonExposed=false`를 반환하고 테스트로 고정한다.
18. Detail `snapshot` block은 row metadata와 5.8-a helper column `captureReason`, `storedApplicationStateCode`, `primaryRuleId`, `primaryEndpointKey`, `maxConfidence`를 노출한다.
19. Detail `readModel` block은 stored `read_model_json`의 UI-facing top-level block만 typed projection으로 반환한다.
20. Detail `snapshotEndpointEvidence` block은 stored `read_model_json.snapshotEndpointEvidence`를 bounded projection으로 반환한다.
21. Detail `instanceSummary` block은 stored `read_model_json.instanceSummary`의 5.7 meaning을 보존한다.
22. Detail projection 중 optional block이 없거나 malformed이면 bounded unavailable/omitted 처리하고 raw source에서 rehydrate하지 않는다.
23. Detail service는 `MetricBucketRepository`, `StarterHeartbeatTelemetryRepository`, `LifecycleStateService`, `TriageSummaryService`, `EndpointPriorityService`, `DashboardReadModelService`, `InstanceEvidenceReadModelService`, operational event promotion/dedup/suppression service를 주입받지 않는다.
24. Snapshot marker endpoint는 `GET /api/projects/{projectId}/applications/{applicationId}/dashboard/snapshot-markers?since=24h&limit=50`로 제공한다.
25. Marker API는 project/application membership을 검증하고 mismatch는 `404`로 매핑한다.
26. Marker `since` 생략 시 `24h`를 사용한다.
27. Marker MVP supported `since` token은 `24h`, `7d`, `14d`다.
28. Marker `since`는 configured `dashboard_snapshots` retention으로 clamp한다.
29. Marker `limit` 생략 시 `50`을 사용한다.
30. Marker maximum `limit`은 `336`이고 `limit > 336`은 effective `336`으로 clamp한다.
31. Unsupported `since`, blank/invalid/non-positive `limit`은 `400`으로 매핑한다.
32. Marker response order는 `capturedAt ASC`, tie-breaker `snapshotId ASC`다.
33. Marker horizon 안에 snapshot row가 없으면 `200`, `markers=[]`, `emptyState.reasonCode=no_snapshots_in_retention`을 반환한다.
34. Marker empty copy는 "보관 기간 안에 표시할 snapshot marker가 없습니다." 계열이고, "현재 문제가 없음" 또는 "회복 완료"를 뜻하지 않는다.
35. Marker response는 `generatedAt`, `applicationId`, `source=dashboard_snapshots`, `horizon`, `emptyState`, `markers[]`를 포함한다.
36. Marker item은 `markerId`, `snapshotId`, `capturedAt`, `currentWindowEndUtc`, `type`, `severity`, `readMeaning=stored_read_model_point`, `captureReason`, `storedApplicationStateCode`, `previousState`, title/summary, helper confidence/rule/endpoint field, `links.snapshot`을 포함한다.
37. Marker type enum은 `scheduled_snapshot`, `query_fallback_snapshot`, `state_change`, `state_observation`, `high_confidence_concern`, `short_strong_spike`, `recovery_observed`, `stored_snapshot`으로 제한한다.
38. Marker severity enum은 `info`, `warning`, `critical`으로 제한한다.
39. Severity는 stored row/read model field만 사용한다.
40. Severity는 `capture_reason`만으로 확정하지 않는다.
41. Stored `state_code=down` 또는 stored triage severity `critical`이 있으면 severity는 `critical`이다.
42. Stored `state_code in [degraded, stale, unknown]`, recovery observed, high-confidence concern, short-strong-spike, stored triage severity `warning`은 severity `warning`이다.
43. Stored `state_code in [active, idle, waiting_first_data]`이고 warning/critical 조건이 없으면 severity `info`다.
44. Unknown/legacy stored state는 parse failure가 아니라면 severity `warning`이고 copy는 "저장된 상태를 완전히 해석하지 못했습니다." 계열로 제한한다.
45. `capture_reason=query_fallback`과 `capture_reason=hourly_scheduled`는 neutral reason이며 stored state/triage/recovery가 없으면 severity `info`다.
46. Marker read semantics는 "그 시점에 저장된 dashboard read model이 이렇게 보였다"는 의미다.
47. Marker는 current state confirmation, alert delivery log, incident acknowledgement가 아니다.
48. Marker API는 operational event `eventId`, `resolvedAt`, dedup 결과, suppression 결과를 약속하지 않는다.
49. `capture_reason` canonical interpretation priority는 `recovery_observed`, `state_change`, `high_confidence_concern`, `short_strong_spike`, `state_observation`, `query_fallback_snapshot`, `scheduled_snapshot`, `stored_snapshot` 순서다.
50. Stored `recovery.isRecovering=true` 또는 stored `zeroInsight.reasonCode=observing_recovery`가 있으면 capture reason보다 먼저 `recovery_observed` marker type을 사용한다.
51. `capture_reason=state_change`이거나 previous stored snapshot의 `state_code`와 current snapshot의 `state_code`가 다르면 marker type은 `state_change`다.
52. `capture_reason=high_confidence_concern`이거나 stored `max_confidence >= 0.82` 또는 stored triage confidence가 threshold를 만족하면 marker type은 `high_confidence_concern`이다.
53. `capture_reason=short_strong_spike`이면 marker type은 `short_strong_spike`다.
54. 위 조건은 아니지만 stored `state_code in [degraded, stale, down, unknown]`이면 marker type은 `state_observation`이다.
55. `capture_reason=query_fallback`이면 marker type은 `query_fallback_snapshot`이다.
56. `capture_reason=hourly_scheduled`이면 marker type은 `scheduled_snapshot`이다.
57. Null, blank, unknown, legacy `capture_reason`은 조회 실패가 아니며 marker type `stored_snapshot`으로 fallback한다.
58. 5.8-b는 stored `capture_reason`을 정규화해 DB에 update하지 않는다.
59. Marker `captureReason` field는 persisted value를 그대로 표시할 수 있다.
60. `previousState` lookup은 같은 `application_id` 안에서만 수행한다.
61. `previousState` 후보는 current snapshot보다 `current_window_end_utc`가 strictly earlier인 snapshot만 허용한다.
62. `previousState` 후보 ordering은 `current_window_end_utc DESC`, `generated_at DESC`, `id ASC`다.
63. `previousState.stateCode`는 첫 번째 previous snapshot의 `state_code`다.
64. Previous snapshot이 없거나 retention으로 삭제됐으면 `previousState.stateCode=null`, `source=no_previous_snapshot_in_retention`, `snapshotId=null`, `capturedAt=null`이다.
65. Previous snapshot의 state code가 unknown/legacy이면 raw value를 보존하고 source는 `previous_dashboard_snapshot_unknown_state`를 둘 수 있다.
66. `previousState`는 current accepted bucket, heartbeat, resource hint, raw bucket scan으로 추론하지 않는다.
67. `lastHealthyAt` normalized source는 같은 application의 previous stored snapshot 중 `state_code=active`인 가장 최신 snapshot이다.
68. `lastHealthyAt` lookup ordering은 `current_window_end_utc DESC`, `generated_at DESC`, `id ASC`다.
69. `lastHealthyAt.value`는 selected healthy snapshot의 `generated_at`이다.
70. Previous active snapshot이 retention 안에 없으면 `lastHealthyAt.value=null`, `source=no_previous_active_snapshot_in_retention`, `snapshotId=null`이다.
71. `lastHealthyAt`은 current snapshot accepted bucket freshness, accepted bucket gap fallback, heartbeat recent/missing으로 생성하지 않는다.
72. Stored read model 안의 `recovery.lastHealthyAt` 또는 `application.lastHealthyAt`은 `readModel` block에 그대로 보일 수 있지만 normalized `lastHealthyAt` source를 대체하지 않는다.
73. Recovery marker trigger는 stored `read_model_json.recovery.isRecovering=true`, stored `read_model_json.zeroInsight.reasonCode=observing_recovery`, 또는 previous stored state `stale/down` + current stored state `unknown` + stored recovery block의 recovery 표현이다.
74. Recovery marker type은 `recovery_observed`, severity는 `warning`이다.
75. Recovery marker copy는 "회복 관찰 중", "sample이 충분해지는지 확인" 계열을 사용한다.
76. Recovery marker copy는 "복구 완료", "장애 해결", "앱이 다시 정상" 같은 완료 표현을 사용하지 않는다.
77. Recovery marker는 `lastRecoveredAt`, `recoveredAt`, `resolvedAt` field를 추가하지 않는다.
78. Recovery marker 판단에 heartbeat를 사용하지 않는다.
79. Detail response의 `snapshotEndpointEvidence.items[]`는 stored order를 보존하고 각 item에 `anchorId=endpoint-evidence-{n}`을 1-based display order로 부여한다.
80. `anchorId`는 같은 snapshot 안에서 deterministic/stable하지만 cross-snapshot durable id가 아니다.
81. `endpointEvidenceRefs[]` ref resolution은 `endpointKey`를 canonical match key로 사용한다.
82. Ref와 evidence item 양쪽에 `method`와 `route`가 있으면 보조 검증에 사용한다.
83. 같은 `endpointKey`가 여러 evidence item에 있으면 첫 번째 stored order item에 연결한다.
84. Match 성공 시 ref projection은 `snapshotDetailAnchor`와 `anchorStatus=resolved`를 포함한다.
85. Match 실패 시 ref를 유지하고 `snapshotDetailAnchor=null` 또는 `anchorStatus=missing`을 포함한다.
86. Missing anchor는 detail response 또는 Story 5.7 trend point를 invalid로 만들지 않는다.
87. Detail/marker implementation은 Story 5.7 `dashboard_snapshots.read_model_json.instanceSummary.items[]`, `schemaVersion=1.0`, `maxItems=50`, minimum shape와 field meaning을 rename/remove/reinterpret하지 않는다.
88. `endpointEvidenceRefs[]`에 endpoint evidence body, request/error count body, error rate body, duration buckets, baseline buckets, confidence, score, recommended action body, endpoint p95/p99, raw endpoint JSON을 추가하지 않는다.
89. Story 5.9 handoff candidate field를 안정화한다: `snapshotId`, `applicationId`, `capturedAt`, `currentWindowEndUtc`, `storedApplicationStateCode`, `previousState.stateCode`, `captureReason`, marker `type`, marker `severity`, `primaryRuleId`, `primaryEndpointKey`, `maxConfidence`, recovery marker presence/type, `snapshotEndpointEvidence.items[].anchorId`, `links.snapshot`.
90. 5.8-b는 `GET /api/projects/{projectId}/applications/{applicationId}/operational-events`를 구현하지 않는다.
91. 5.8-b는 operational event type promotion, 60분 suppression window, deduplication, period folding, `resolvedAt` semantics를 구현하지 않는다.
92. 5.8-b는 별도 `operational_events` table/repository/API, endpoint timeseries table/API, materialized view, Redis queue, PostgreSQL outbox를 만들지 않는다.
93. 5.8-b는 raw snapshot explorer, raw bucket explorer, arbitrary metric query를 만들지 않는다.
94. 5.8-b는 endpoint p95/p99, endpoint percentile rollup, endpoint p99 alert 기준을 만들지 않는다.
95. UI/controller/repository/DB trigger는 lifecycle state, rule, p95/p99, endpoint priority, marker/event를 계산하지 않는다.
96. Heartbeat success/failure/missing은 accepted bucket freshness, dashboard snapshot, recovery source, operational event source로 합성되지 않는다.
97. Focused tests가 detail status mapping, marker query/default/clamp/order, marker type/severity priority, previousState, lastHealthyAt, recovery marker copy, anchor resolution, current recalculation prohibition, retention copy, 5.9 handoff non-goals를 검증한다.
98. `./gradlew :observability-portal:test --tests '*DashboardSnapshot*'`, relevant controller/repository/architecture tests, full `./gradlew :observability-portal:test`, `git diff --check`가 통과해야 implementation completion으로 볼 수 있다.

## Tasks / Subtasks

- [x] Snapshot detail/marker API controller 추가 (AC: 3~10, 24~35, 90~96)
  - [x] `domain.snapshot.controller` 또는 기존 package convention에 맞는 controller를 추가해 detail endpoint를 노출한다.
  - [x] 같은 controller 또는 분리 controller로 marker endpoint를 노출한다.
  - [x] Controller는 UUID/path/query 변환과 HTTP status mapping만 담당하고 repository를 직접 호출하지 않는다.
  - [x] Invalid `snapshotId`, unsupported `since`, invalid `limit`을 `400`으로 매핑한다.
  - [x] Membership mismatch와 retention detail miss를 `404`로 매핑한다.
  - [x] Retention detail miss error body와 marker empty state copy를 계약 문구로 고정한다.

- [x] Snapshot read model service와 query validation 구현 (AC: 11~23, 24~36, 60~72, 97~98)
  - [x] `DashboardSnapshotDetailService` 후보가 membership lookup, snapshot row lookup, previous snapshot lookup, last healthy lookup을 orchestration한다.
  - [x] `DashboardSnapshotMarkerService` 후보가 horizon/default/clamp/limit/order를 적용해 marker list를 만든다.
  - [x] `SnapshotMarkerQuery` 또는 동등 value object로 `since=24h|7d|14d`, `limit`, retention clamp를 닫는다.
  - [x] Service는 stored snapshot/read model projection만 수행하고 current dashboard generation이나 live source join을 하지 않는다.
  - [x] Service constructor와 architecture test에서 forbidden dependencies가 들어오지 않도록 guard한다.
  - [x] Malformed optional block은 bounded unavailable/omitted로 두고, stored JSON root projection 실패는 detail/marker `500`으로 매핑한다.

- [x] Snapshot repository read query 확장 (AC: 4, 11, 24~33, 60~72, 79~86)
  - [x] `DashboardSnapshotJpaRepository`에 `projectId + applicationId + snapshotId` membership detail query를 추가한다.
  - [x] Detail/marker projection용 row record를 추가해 id, project/application id, generatedAt, window metadata, stateCode, captureReason, helper columns, readModelJson을 반환한다.
  - [x] Marker horizon query는 effective horizon 안의 rows를 bounded 조회하고 service가 `capturedAt ASC`, `snapshotId ASC`로 반환하게 한다.
  - [x] Previous state query는 same application, `current_window_end_utc < current.current_window_end_utc`, order `current_window_end_utc DESC`, `generated_at DESC`, `id ASC`를 사용한다.
  - [x] Last healthy query는 same application previous snapshots 중 `state_code=active`만 같은 ordering으로 조회한다.
  - [x] Repository/JPA는 marker type, severity, endpoint priority, p95/p99, recovery/event semantics를 계산하지 않는다.

- [x] Detail response DTO/model과 stored JSON parser 구현 (AC: 12~23, 36, 79~88)
  - [x] `DashboardSnapshotDetailReadModel` 또는 동등 record를 추가해 wrapper shape를 typed model로 고정한다.
  - [x] `SnapshotReadSemantics`, `SnapshotMetadata`, `PreviousState`, `LastHealthyAt`, `RecoveryMarker`, `SnapshotEndpointEvidence`, `SnapshotEndpointEvidenceRef`, `SnapshotLinks` model 후보를 추가한다.
  - [x] Stored `read_model_json` parser는 UI-facing top-level block만 projection하고 raw JSON escape hatch를 만들지 않는다.
  - [x] `snapshotEndpointEvidence.items[]`에 `anchorId=endpoint-evidence-{n}`을 stored order 기준으로 추가한다.
  - [x] `instanceSummary.items[].endpointEvidenceRefs[]`는 stored ref-only field를 보존하고 read-side `snapshotDetailAnchor`/`anchorStatus`만 enrich한다.
  - [x] Unknown extra field는 ignore하거나 typed projection 밖에 둔다.

- [x] Marker classifier와 severity mapper 구현 (AC: 37~59, 73~78, 89~96)
  - [x] `SnapshotMarkerType`과 `SnapshotMarkerSeverity` enum 또는 동등 typed value를 추가한다.
  - [x] Capture reason seed priority를 `recovery_observed` 우선 순서로 구현한다.
  - [x] `capture_reason` null/unknown/legacy fallback을 `stored_snapshot`으로 처리한다.
  - [x] Severity mapper는 stored state, stored triage severity/confidence, stored recovery signal만 읽는다.
  - [x] `capture_reason` 단독 severity 결정과 operational event type 직접 mapping을 금지하는 tests를 추가한다.
  - [x] Marker title/summary/recommendedAction copy는 recovery completion 또는 host down 확정 표현을 쓰지 않는다.

- [x] Previous state, lastHealthyAt, recovery source 구현 (AC: 60~78, 97~98)
  - [x] `previousState`는 previous stored snapshot source만 사용한다.
  - [x] `lastHealthyAt`은 previous active snapshot의 `generated_at`만 normalized value로 사용한다.
  - [x] No previous/no active retention gap shape와 source code를 고정한다.
  - [x] Stored `recovery`와 `zeroInsight.reasonCode=observing_recovery`를 recovery marker trigger로 읽는다.
  - [x] accepted bucket gap fallback은 새 조회/계산 없이 stored read model context 설명으로만 사용한다.
  - [x] Heartbeat source는 recovery marker, previousState, lastHealthyAt에 사용하지 않는다.

- [x] 5.9 handoff와 non-goal guard 구현 (AC: 89~96)
  - [x] Marker/detail response에 5.9 handoff candidate fields를 안정적으로 포함한다.
  - [x] 5.8-b markerId를 5.9 eventId로 고정하지 않는다.
  - [x] Operational event endpoint, event promotion/dedup/suppression, `resolvedAt` semantics를 추가하지 않는다.
  - [x] `MvcLayerBoundaryTest` 또는 동등 architecture test에 5.8-b forbidden surface를 추가한다.
  - [x] raw explorer, endpoint timeseries, `operational_events` table/repository/API absence를 regression guard로 유지한다.

- [x] Focused tests와 full verification (AC: 1~98)
  - [x] Detail controller/service tests가 `200`, invalid UUID `400`, membership/retention `404`, projection failure `500`, no fallback을 검증한다.
  - [x] Marker controller/service tests가 default/clamp/order/emptyState/query validation/status mapping을 검증한다.
  - [x] Marker classifier tests가 type priority, capture reason seed, severity, unknown/legacy fallback을 검증한다.
  - [x] PreviousState/LastHealthyAt tests가 ordering, null shape, no accepted bucket/heartbeat fallback을 검증한다.
  - [x] Anchor resolver tests가 `endpoint-evidence-{n}`, duplicate endpointKey first match, missing anchor bounded response를 검증한다.
  - [x] Architecture tests가 forbidden dependencies와 non-goal surfaces를 검증한다.
  - [x] Full portal test와 `git diff --check`를 실행한다.

## Dev Notes

### Contract Priority

- 최우선 source는 `implementation-artifacts/spec-story-5-8-b-snapshot-marker-detail-recovery-contract-decisions.md`다.
- `read-model-contract.md`, `operational-event-history.md`, `database-schema.md`, `api-surface.md`에는 5.8-a/5.8-b 이전 후보 표현이 남아 있다. 예를 들어 endpoint evidence selection order, `scheduled` token 후보, operational event candidate shape는 5.8-a/5.8-b 전용 결정 문서가 덮어쓴다.
- 5.8 decomposition 문서 기준으로 5.8-a는 "무엇을 언제 저장하는가"를 닫았고, 5.8-b는 "저장된 것을 어떻게 읽고 marker/detail/recovery로 연결하는가"를 닫는다.
- Story 5.9는 "읽힌 marker/snapshot 후보를 operational event API로 어떻게 승격, 중복 제거, 억제하는가"를 닫는다.

### 5.7 Stable Path

- Story 5.7의 stable source path는 `dashboard_snapshots.read_model_json.instanceSummary.items[]`다.
- `instanceSummary.schemaVersion="1.0"`, `maxItems=50`, minimum field meaning은 5.8-b에서 rename/remove/reinterpret하지 않는다.
- 5.7 trend API는 `captureReason`을 nullable opaque metadata로 복사하며 marker, severity, recovery, operational event 의미로 해석하지 않는다.
- 5.8-b detail response는 ref에 `snapshotDetailAnchor`/`anchorStatus`를 read-side enrichment로 붙일 수 있지만, stored `endpointEvidenceRefs[]`에 body를 복사하거나 5.7 parser source를 바꾸지 않는다.
- 현재 `InstanceSnapshotTrendParser`는 `endpointEvidenceRefs[].snapshotDetailAnchor`를 optional text로 읽을 수 있다. 5.8-b는 이 optional field를 detail response에서 해석하되 trend point를 invalid로 만들지 않는다.

### 5.8-a Handoff

- 5.8-a writer/capture story는 persisted token `hourly_scheduled`, `state_change`, `high_confidence_concern`, `short_strong_spike`, `query_fallback`을 고정했다.
- 5.8-a duplicate identity는 `application_id + current_window_end_utc`이고 same-window representative row는 priority-aware upsert로 유지된다.
- 5.8-a가 저장하는 bounded endpoint evidence top-level block name은 `snapshotEndpointEvidence`다.
- 5.8-a가 저장하는 bounded instance summary는 `instanceSummary.items[]` max 50이며 Story 5.7 minimum shape를 채운다.
- 5.8-a가 저장하는 `endpointEvidenceRefs[]` field는 `endpointKey`, optional `method`, optional `route`, optional `relatedApplicationPriorityRank`, optional `relatedRuleIds`로 제한된다.
- 5.8-a save failure로 snapshot row가 없으면 5.8-b detail/marker source도 없다. 5.8-b가 current dashboard regeneration으로 보완하지 않는다.
- 5.8-b는 writer, scheduler, fallback capture threshold, upsert priority, helper column write semantics를 재정의하지 않는다.

### Current Recalculation Prohibition

- Detail/marker service는 stored snapshot projection service다.
- Allowed dependencies는 project/application membership lookup repository, `DashboardSnapshotRepository` read query, stored `read_model_json` parser/projection helper, same application previous snapshot lookup이다.
- Forbidden dependencies는 `MetricBucketRepository`, `StarterHeartbeatTelemetryRepository`, `LifecycleStateService`, `TriageSummaryService`, `EndpointPriorityService`, `DashboardReadModelService`, `InstanceEvidenceReadModelService`, operational event promotion/dedup/suppression service다.
- Snapshot absence는 accepted bucket lookup, heartbeat lookup, dashboard current generation, synthetic marker/detail/recovery generation을 트리거하지 않는다.
- Repository/JPA query, DB view/trigger, controller, UI는 lifecycle state, rule, marker severity, endpoint priority, p95/p99, event를 계산하지 않는다.

### Current Code State

- `dashboard_snapshots` table과 read-side projection substrate는 Story 5.7/5.8-a worktree에 존재한다.
- `DashboardSnapshotEntity`는 `captureReason`, `primaryRuleId`, `primaryEndpointKey`, `maxConfidence`, `readModelJson`, window metadata를 mapping한다.
- `DashboardSnapshotJpaRepository`는 instance trend newest-first query, writer identity lock query, latest snapshot metadata query를 제공한다.
- `DashboardSnapshotRepository`는 trend row 조회, writer identity lookup/insert/update, latest lookup facade를 제공한다. 5.8-b는 detail membership query, marker horizon query, previous snapshot query, previous active snapshot query를 추가해야 한다.
- `DashboardSnapshotCaptureReason`은 writer token/priority enum이다. 5.8-b marker classifier는 이 enum을 저장 계약 변경 없이 read-side seed 해석에 활용할 수 있다.
- `DashboardSnapshotReadModelEnricher.SNAPSHOT_ENDPOINT_EVIDENCE_FIELD`는 `snapshotEndpointEvidence`로 고정되어 있다. 상수 visibility가 package-private이면 5.8-b가 같은 package에 helper를 두거나 public read constant를 신중히 추가할 수 있다.
- `DashboardController`와 `InstanceSnapshotTrendController`는 controller가 service만 호출하고 status mapping을 담당하는 기존 패턴을 보여준다.
- `MvcLayerBoundaryTest`에는 5.8-a forbidden surface guard가 있다. 5.8-b는 detail/marker read service의 forbidden dependencies와 operational event/raw explorer non-goal을 추가하는 후보다.
- 현재 worktree에는 Story 5.8-a 구현 관련 변경과 계약 문서가 이미 존재한다. 5.8-b dev-story는 이 변경을 되돌리지 말고, 같은 feature-first MVC style 위에서 필요한 read-side 파일만 추가/확장해야 한다.

### Previous Story Intelligence

- Story 5.5는 `endpointPriority`를 current-only canonical endpoint ranking source로 닫았다. 5.8-b는 stored `snapshotEndpointEvidence`를 보여줄 수 있지만 endpoint priority를 다시 계산하지 않는다.
- Story 5.5는 stale/down 직전 endpoint evidence를 current priority로 되살리지 않고 snapshot/history handoff로 남겼다. 5.8-b detail/marker가 그 stored evidence를 읽는다.
- Story 5.6은 instance evidence의 accepted bucket axis와 starter heartbeat axis를 분리했다. 5.8-b는 stored instance summary의 두 축을 합쳐 instance health나 host health를 만들지 않는다.
- Story 5.6은 `endpointEvidence`를 selected instance drill-down evidence로만 두고 새 rank/confidence/action을 만들지 않았다. 5.8-b `endpointEvidenceRefs` anchor도 stored evidence 연결만 수행한다.
- Story 5.7은 missing snapshot/source/item을 empty trend로 처리한다. 5.8-b detail direct link는 특정 row를 가리키므로 missing/retention을 `404`로 처리하고, marker collection은 empty list를 `200`으로 처리한다.
- Story 5.8-a는 writer/capture/save failure boundary를 닫았다. 5.8-b는 absent row를 current dashboard로 재생성하지 않는다.

### Implementation File Candidates

아래는 dev-story 구현 후보이며, 이 create-story 작업에서는 수정하지 않는다.

- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/controller/DashboardSnapshotController.java`
  - detail/marker endpoint HTTP boundary 후보
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotDetailReadModel.java`
  - snapshot detail typed wrapper 후보
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotMarkerReadModel.java`
  - marker list response 후보
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotMarkerType.java`
  - read-side marker type enum 후보
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotMarkerSeverity.java`
  - marker severity enum 후보
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotDetailRow.java`
  - repository projection row 후보
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotMarkerRow.java`
  - marker horizon projection row 후보
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotDetailService.java`
  - membership/detail/previous/lastHealthy orchestration 후보
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotMarkerService.java`
  - marker query/default/clamp/order orchestration 후보
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotMarkerClassifier.java`
  - marker type/severity/copy classifier 후보
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotDetailProjectionParser.java`
  - stored `read_model_json` typed projection 후보
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/SnapshotEndpointEvidenceAnchorResolver.java`
  - `snapshotEndpointEvidence.items[]`와 `endpointEvidenceRefs[]` 연결 후보
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/InvalidSnapshotMarkerQueryException.java`
  - marker query validation exception 후보
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/repository/DashboardSnapshotJpaRepository.java`
  - detail row, marker horizon, previous row, previous active row query 추가 후보
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/repository/DashboardSnapshotRepository.java`
  - read facade method 추가 후보
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/**`
  - focused model/service/controller/repository tests 후보
- `observability-portal/src/test/java/com/observation/portal/architecture/MvcLayerBoundaryTest.java`
  - forbidden dependency/non-goal guard 후보

### Architecture Constraints

- Active baseline은 Traditional MVC + Service/Repository Layering이다.
- `domain`은 feature grouping namespace이며 pure DDD domain layer가 아니다.
- `application`, `port`, `adapter`, `adapter.in`, `adapter.out` package를 만들지 않는다.
- Controller는 service를 호출하고 repository를 직접 호출하지 않는다.
- Service는 필요하면 Spring Data repository와 JPA entity를 직접 사용할 수 있지만 JPA entity를 API/service external model로 직접 반환하지 않는다.
- Flyway SQL migration이 schema source of truth다. 5.8-b는 새 table을 만들지 않는다.
- DB view, materialized view, trigger, stored procedure에 state, rule, marker, severity, priority, confidence, action, p95/p99, endpoint priority, event 계산을 숨기지 않는다.
- 공개 클래스/메서드/핵심 helper Javadoc/comment는 AGENTS.md 지침에 따라 한국어로 작성한다.

### Story 5.9 Handoff

- 5.8-b marker/detail은 5.9 event candidate source가 될 수 있지만 operational event contract 자체가 아니다.
- 5.9가 owns: operational event endpoint, event type promotion, 60분 suppression, deduplication, period folding, event severity/title/summary final copy, event `resolvedAt`.
- 5.9가 preserve: MVP는 `operational_events` table을 만들지 않고 `dashboard_snapshots`와 bounded helper columns를 재사용한다.
- 5.9가 preserve: heartbeat success/failure/missing은 operational event source가 아니다.
- 5.9가 preserve: endpoint timeseries table, materialized view, Redis queue, PostgreSQL outbox는 MVP history source가 아니다.
- 5.8-b는 markerId를 eventId로 고정하지 않고, marker severity를 5.9 event final severity로 강제하지 않는다.

## Testing

Focused test 대상 후보:

- `DashboardSnapshotControllerTest`
- `DashboardSnapshotDetailServiceTest`
- `DashboardSnapshotMarkerServiceTest`
- `DashboardSnapshotMarkerClassifierTest`
- `DashboardSnapshotDetailProjectionParserTest`
- `SnapshotEndpointEvidenceAnchorResolverTest`
- `DashboardSnapshotRepositoryIntegrationTest`
- `DashboardSnapshotReadModelShapeTest`
- `MvcLayerBoundaryTest`

필수 scenario:

- Detail API `200` wrapper가 row metadata, `readSemantics`, marker, previousState, lastHealthyAt, bounded readModel, `snapshotEndpointEvidence`, `instanceSummary`, links를 반환한다.
- Invalid snapshot UUID는 `400 invalid_snapshot_id`로 매핑한다.
- Project/application/snapshot membership mismatch는 `404`다.
- Retention/missing direct detail link는 `404 snapshot_not_found_or_expired` copy를 반환한다.
- Detail missing/projection failure는 current dashboard regeneration이나 query fallback capture를 시도하지 않는다.
- Marker API default는 `since=24h`, `limit=50`이다.
- Marker API는 supported `since=24h|7d|14d`, retention clamp, max `limit=336`, order `capturedAt ASC`, tie-breaker `snapshotId ASC`를 적용한다.
- Marker horizon empty는 `200 + markers=[] + emptyState.reasonCode=no_snapshots_in_retention`이다.
- Capture reason priority는 marker seed로만 작동하고 severity/event 의미를 단독 결정하지 않는다.
- Null/unknown/legacy `capture_reason`은 marker type `stored_snapshot` fallback이며 조회 실패가 아니다.
- Recovery observed가 capture reason보다 marker type priority에서 앞선다.
- Stored down/critical triage는 severity `critical`, degraded/stale/unknown/recovery/high-confidence/spike/warning triage는 severity `warning`, active/idle/waiting-first-data neutral state는 `info`다.
- `previousState`는 strictly earlier same-application snapshot ordering으로 만든다.
- `lastHealthyAt`은 previous active snapshot의 `generated_at`만 사용한다.
- Accepted bucket gap fallback과 heartbeat는 `previousState`/`lastHealthyAt`/recovery marker source를 새로 만들지 않는다.
- Recovery marker copy는 "회복 관찰 중"이며 "복구 완료", "장애 해결", host application down/process down을 단정하지 않는다.
- `snapshotEndpointEvidence.items[]` anchor는 stored order 기준 `endpoint-evidence-{n}`이다.
- `endpointEvidenceRefs[]`는 endpointKey canonical match로 resolved/missing 상태를 받으며 missing anchor가 detail/trend를 invalid로 만들지 않는다.
- Raw snapshot explorer, raw bucket explorer, endpoint timeseries table/API, operational event table/API/repository가 추가되지 않는다.
- UI/controller/repository/DB trigger가 state/rule/p95/p99/endpoint priority/marker/event를 계산하지 않는다.
- Detail/marker service가 forbidden dependencies를 주입받지 않는다.
- Heartbeat는 accepted bucket freshness, dashboard snapshot, recovery source, operational event source로 합성되지 않는다.

Suggested commands:

```bash
./gradlew :observability-portal:test --tests '*DashboardSnapshot*'
./gradlew :observability-portal:test --tests '*SnapshotEndpointEvidenceAnchorResolver*'
./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest
./gradlew :observability-portal:test
git diff --check
```

## Change Log

- 2026-05-27: Story 5.8-b Snapshot Marker, Detail, and Recovery Source create-story 산출물을 생성했다.
- 2026-05-27: Snapshot detail/marker read semantics 구현을 완료하고 story 상태를 review로 변경했다.

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- 2026-05-27: BMAD create-story workflow 설정과 project-context를 확인했다.
- 2026-05-27: 5.8-b/5.8 decomposition/5.8-a/5.7/5.6/5.5/5.4 결정 문서, read-model/operational-event/database/api/product/sprint/status 문서를 확인했다.
- 2026-05-27: 기존 Story 5.8-a 문서 스타일과 현재 snapshot/dashboard/instance trend 코드 후보를 확인했다.
- 2026-05-27: Story 5.8-b create-story 산출물을 `planning-artifacts/stories/5-8-b-snapshot-marker-detail-recovery-source.md`에 생성했다.
- 2026-05-27: BMAD dev-story workflow와 project-context를 확인하고 `implementation-artifacts/sprint-status.yaml`에서 story를 in-progress로 전환했다.
- 2026-05-27: snapshot detail/marker controller, service, repository projection, parser, marker classifier, endpoint evidence anchor resolver를 구현했다.
- 2026-05-27: focused snapshot tests, MVC boundary test, full portal test, `git diff --check`를 실행했다.

### Completion Notes List

- Snapshot detail API와 marker API를 feature-first MVC 구조로 추가했고, controller는 status mapping과 query/path 변환만 맡도록 유지했다.
- Detail/marker service는 stored `dashboard_snapshots` row와 stored `read_model_json` projection만 사용하며 current dashboard, accepted bucket, heartbeat, lifecycle/rule/endpoint priority 계산 dependency를 주입받지 않는다.
- Detail wrapper는 read semantics, snapshot metadata, marker, previousState, lastHealthyAt, recoveryMarker, bounded readModel, snapshotEndpointEvidence, instanceSummary, links를 제공한다.
- Marker classifier는 recovery observed 우선 priority, capture reason seed fallback, stored state/triage/recovery 기반 severity, unknown/legacy state bounded copy를 구현했다.
- Endpoint evidence anchor는 stored order 기준 `endpoint-evidence-{n}`을 만들고 `endpointEvidenceRefs[]`에 `snapshotDetailAnchor`/`anchorStatus`만 read-side로 보강한다.
- PreviousState와 lastHealthyAt은 같은 application의 strictly earlier stored snapshot source만 사용한다.
- Operational event API, promotion/dedup/suppression, `operational_events` table, raw explorer, endpoint timeseries, p95/p99 계산은 추가하지 않았다.

### File List

- `implementation-artifacts/sprint-status.yaml`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/controller/DashboardSnapshotController.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotDetailReadModel.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotDetailRow.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotMarkerItem.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotMarkerReadModel.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotMarkerSeverity.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotMarkerType.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotSourceRow.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotStoredReadModelProjection.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/repository/DashboardSnapshotJpaRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/repository/DashboardSnapshotRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotDetailProjectionParser.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotDetailService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotMarkerClassifier.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotMarkerService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotProjectionException.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/InvalidSnapshotMarkerQueryException.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/SnapshotEndpointEvidenceAnchorResolver.java`
- `observability-portal/src/test/java/com/observation/portal/architecture/MvcLayerBoundaryTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/controller/DashboardSnapshotControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/repository/DashboardSnapshotRepositoryIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotDetailProjectionParserTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotDetailServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotMarkerClassifierTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotMarkerServiceTest.java`
- `planning-artifacts/stories/5-8-b-snapshot-marker-detail-recovery-source.md`
