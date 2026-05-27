---
artifactType: story
storyId: "5.9-b"
storyKey: "5-9-b-operational-event-promotion-suppression-and-period-folding"
epic: "Epic 5. Dashboard Read Model and API"
title: "Operational Event Promotion, Suppression, and Period Folding"
architectureStyle: Traditional MVC
status: review
date: 2026-05-27
---

# Story 5.9-b - Operational Event Promotion, Suppression, and Period Folding

## Status

review

## Story

portal 구현자로서, stored dashboard snapshot/read model 후보를 operational event history feed의 의미 있는 event로 승격하고 중복을 억제하며 period를 접고 싶다.

그래야 사용자가 raw explorer나 current 재계산 없이 최근 운영 흐름, 문제 시작 시점, 해소 조건 관찰, 회복 관찰을 이해할 수 있고, Story 5.9-a가 만든 `domain.history` API skeleton/source boundary/read model shape를 깨지 않고 history API를 완성할 수 있다.

## Source of Truth

`implementation-artifacts/spec-story-5-9-operational-event-history-api-contract-decisions.md`가 Story 5.9-b의 최상위 결정 문서다.

아래 문서는 모두 읽고 반영한 story context다. 구현 중 충돌처럼 보이는 지점은 5.9 contract decision을 우선하고, 5.9-a skeleton/source boundary/compact response shape는 유지한다.

1. `implementation-artifacts/spec-story-5-9-operational-event-history-api-contract-decisions.md`
2. `planning-artifacts/stories/5-9-a-operational-event-history-api-skeleton-and-source-boundary.md`
3. `implementation-artifacts/sprint-status.yaml`
4. `planning-artifacts/contracts/operational-event-history.md`
5. `planning-artifacts/contracts/read-model-contract.md`
6. `planning-artifacts/api-surface.md`
7. `planning-artifacts/stories/5-8-a-dashboard-snapshot-writer-and-capture-policy.md`
8. `planning-artifacts/stories/5-8-b-snapshot-marker-detail-recovery-source.md`
9. `planning-artifacts/epics.md`
10. `planning-artifacts/architecture.md`
11. `_bmad/custom/project-context.md`

코드 확인 대상:

1. `observability-portal/src/main/java/com/observation/portal/domain/history/**`
2. `observability-portal/src/test/java/com/observation/portal/domain/history/**`
3. `observability-portal/src/main/java/com/observation/portal/domain/snapshot/repository/**`
4. `observability-portal/src/test/java/com/observation/portal/architecture/MvcLayerBoundaryTest.java`

닫힌 계약:

1. `5-8-dashboard-snapshot-persistence-and-marker-contract`는 umbrella tracking key이며 story로 create하지 않는다.
2. `5-9-a-operational-event-history-api-skeleton-and-source-boundary`는 done으로 본다.
3. 5.9-a의 endpoint, `domain.history` package, compact response shape, snapshot-derived `eventId`, source boundary를 유지한다.
4. 5.9-b는 `OperationalEventHistoryProjector`의 no-op skeleton을 실제 promotion/dedup/suppression/period folding projection으로 채우는 story다.
5. `GET /api/projects/{projectId}/applications/{applicationId}/operational-events` route, query default/clamp, status mapping은 재설계하지 않는다.
6. Source는 `dashboard_snapshots` row metadata/helper columns, stored `read_model_json`, 5.8-b handoff field로 제한한다.
7. `DashboardReadModelService`, lifecycle state, triage, endpoint priority, p95/p99, heartbeat, raw bucket을 history 조회에서 재계산하지 않는다.
8. `operational_events` table/repository, materialized view, Redis/outbox, endpoint timeseries, raw explorer, 새 migration/index는 기본 범위에 넣지 않는다.

## Scope / Out of Scope

포함:

- stored snapshot/read model candidate를 operational event로 승격하는 rule 구현
- `state_changed`, `degraded_entered`, `degraded_resolved`, `stale_entered`, `down_entered`, `recovery_observed`, `high_confidence_concern` semantics 구현
- 같은 `applicationId + primaryEndpointKey + primaryRuleId` concern의 60분 suppression
- state transition, stale/down/recovery event의 dedup/folding key 구현
- period folding과 시작 event의 nullable `resolvedAt` 채움
- event type 기반 severity/title/summary copy mapping
- stored endpoint evidence anchor, `anchorStatus`, snapshot detail link 보존
- 5.9-a compact response shape, snapshot-derived `eventId`, source boundary regression guard 보강

제외:

- 새 endpoint skeleton 재설계
- current dashboard read model, lifecycle state, triage, endpoint priority, p95/p99 재계산
- heartbeat success/failure/missing을 recovery/event source로 사용
- raw bucket, accepted bucket, endpoint raw JSON live scan
- `operational_events` table/repository, materialized view, Redis/outbox
- endpoint timeseries table/API
- raw snapshot explorer, raw bucket explorer, arbitrary metric query
- 새 migration/index 기본 추가
- alert delivery log, incident acknowledgement, owner assignment, comment workflow

## Acceptance Criteria

1. Story 5.9-b는 Java/Kotlin/Spring production code를 지금 수정하는 산출물이 아니라, 이 story 파일과 sprint tracking만 생성된 상태에서 `ready-for-dev`가 된다.
2. 구현 story 실행 시 production 변경은 Traditional MVC + Service/Repository Layering과 feature-first package 구조를 따른다.
3. 구현은 `com.observation.portal.domain.history` 아래의 5.9-a controller/service/model boundary를 유지한다.
4. Endpoint는 기존 `GET /api/projects/{projectId}/applications/{applicationId}/operational-events`를 그대로 사용한다.
5. Query default/clamp/status mapping은 5.9-a 계약을 유지한다: default `since=24h`, max `14d`, default `limit=50`, max `100`, invalid query `400`, membership mismatch `404`, projection failure `500`.
6. Response top-level shape는 `generatedAt`, `applicationId`, `source=dashboard_snapshots`, `horizon`, `events`를 유지한다.
7. Event item shape는 `eventId`, `type`, `severity`, `title`, `summary`, `occurredAt`, nullable `resolvedAt`, `stateCode`, nullable `confidence`, `snapshotId`, `evidence`, `links.snapshot`을 유지한다.
8. Response event order는 existing read model contract대로 `occurredAt DESC`, tie-breaker `eventId ASC`다.
9. Service/controller는 `MetricBucketRepository`, `StarterHeartbeatTelemetryRepository`, `DashboardReadModelService`, `LifecycleStateService`, `TriageSummaryService`, `EndpointPriorityService`, `InstanceEvidenceReadModelService`를 주입받지 않는다.
10. Projector source는 `DashboardSnapshotRepository`가 반환한 stored snapshot row metadata/helper columns/stored JSON과 5.8-b handoff field뿐이다.
11. 필요한 경우 stored JSON parser/helper를 재사용할 수 있지만, `DashboardSnapshotDetailService`, `DashboardSnapshotMarkerService`, marker API response model을 operational event source로 우회 주입하지 않는다.
12. `DashboardSnapshotMarkerItem`, marker `type/severity`, `markerId`를 operational event external response shape로 그대로 반환하지 않는다.
13. Current dashboard, accepted bucket, heartbeat, resource sample, raw bucket을 live join해 state/rule/p95/p99/endpoint priority/event를 계산하지 않는다.
14. Event projection은 source rows를 chronological order로 평가한 뒤 response model 생성 시 기존 order contract로 정렬한다.
15. `occurredAt`은 stored snapshot observation time이다. 현재 코드 기준 기본 source는 `DashboardSnapshotDetailRow.generatedAt()`이며, 별도 capturedAt field가 생기기 전까지 current clock이나 query time을 사용하지 않는다.
16. State code, capture reason, rule id, endpoint key는 trim/lowercase normalization으로 비교하되 response copy/evidence에는 stored display value를 보존한다.
17. Null, blank, unknown, legacy `captureReason`은 projection failure가 아니며 event 승격 조건을 만족하지 않으면 조용히 skip한다.
18. `scheduled_snapshot`, `query_fallback_snapshot`, `stored_snapshot`, 단순 `state_observation`은 event로 노출하지 않는다.
19. `short_strong_spike`는 별도 event type이 아니며, high-confidence concern 조건을 만족할 때만 `high_confidence_concern` 후보가 될 수 있다.
20. Event type vocabulary는 `state_changed`, `degraded_entered`, `degraded_resolved`, `stale_entered`, `down_entered`, `recovery_observed`, `high_confidence_concern`만 허용한다.
21. `degraded_entered`는 stored state가 `degraded`로 진입한 transition일 때 만든다. 이전 stored state가 `degraded`가 아니거나, previous source가 없지만 `captureReason=state_change`이고 current state가 `degraded`인 경우가 후보가 된다.
22. `degraded_resolved`는 이전 stored state가 `degraded`이고 현재 stored state가 `degraded`가 아니며, stored concern absence 또는 confidence `< 0.60` 같은 해소 조건이 stored snapshot/read model에서 확인될 때 만든다.
23. `stale_entered`는 stored state가 `stale`로 진입한 transition일 때 만든다. Copy는 accepted bucket freshness 부족으로 stale 관찰이 시작됐다는 의미만 표현한다.
24. `down_entered`는 stored state가 `down`으로 진입한 transition일 때 만든다. Copy는 metric data down/freshness boundary를 표현하고 host application down 확정을 말하지 않는다.
25. `recovery_observed`는 stale/down 이후 stored read model에서 recovery marker signal이 관찰될 때 만든다. 허용 signal은 stored `recovery.isRecovering=true`, stored `zeroInsight.reasonCode=observing_recovery`, 또는 previous stored state `stale|down` + current stored state `unknown` + stored recovery expression이다.
26. `state_changed`는 previous stored state와 current stored state가 다르지만 `degraded_entered`, `degraded_resolved`, `stale_entered`, `down_entered`, `recovery_observed`로 더 구체화되지 않는 transition일 때만 만든다.
27. `high_confidence_concern`은 state transition 없이도 노출할 가치가 있는 stored concern일 때 만든다. 기본 조건은 confidence `>= 0.82`와 rule/endpoint guard 통과다.
28. `high_confidence_concern` confidence source는 우선 `DashboardSnapshotDetailRow.maxConfidence()`, 보조로 stored `triageCards[].confidence` max 또는 bounded endpoint evidence confidence다.
29. `high_confidence_concern`은 `primaryRuleId`와 `primaryEndpointKey`가 helper column 또는 bounded stored evidence에서 모두 확보될 때만 승격한다.
30. `captureReason=high_confidence_concern` 또는 `captureReason=short_strong_spike`는 high-confidence 후보 seed일 뿐이며, confidence/key guard를 통과하지 못하면 event로 만들지 않는다.
31. Endpoint evidence는 stored `snapshotEndpointEvidence.items[]`와 helper columns에서 복사 가능한 bounded field만 사용한다.
32. Event evidence allowed field는 `ruleId`, `endpointKey`, optional `method`, optional `route`, optional `snapshotDetailAnchor`, `anchorStatus`다.
33. Matching endpoint evidence가 있으면 `snapshotDetailAnchor`는 5.8-b anchor rule(`endpoint-evidence-{n}`)을 사용하고 `anchorStatus=resolved`로 둔다.
34. Matching endpoint evidence가 없으면 `snapshotDetailAnchor=null`, `anchorStatus=missing` 또는 evidence block의 null-safe equivalent로 표현하고 event 자체를 invalid로 만들지 않는다.
35. Event item은 raw snapshot JSON, raw bucket list, raw endpoint JSON, endpoint p95/p99, trace id, per-request sample, query string, query key/value를 포함하지 않는다.
36. `eventId`는 5.9-a helper의 snapshot-derived deterministic 형식 `snapshot:{snapshotId}:{eventType}:{normalizedKey}`를 유지한다.
37. `eventId`는 `markerId`를 재사용하지 않고 acknowledgement, owner assignment, comment thread 같은 durable incident workflow 의미를 갖지 않는다.
38. `high_confidence_concern` normalized key는 `{primaryRuleId}:{primaryEndpointKey}` 계열이다.
39. state transition normalized key는 `{fromState}:{toState}` 계열이며 previous source가 없고 `captureReason=state_change`인 경우 fromState는 `unknown_previous` 같은 bounded internal token으로 처리한다.
40. stale/down/recovery normalized key는 `eventType + storedApplicationStateCode` 계열을 사용해 같은 state observation 반복을 접는다.
41. 같은 `applicationId + primaryEndpointKey + primaryRuleId` high-confidence concern은 60분 suppression window 안에서 중복 승격하지 않는다.
42. Suppression window는 first promoted concern `occurredAt` 이상, `occurredAt + 60분` 미만 구간이다. 정확히 60분 이후의 같은 concern은 새 후보가 될 수 있다.
43. Suppression은 snapshot 단위가 아니라 concern key 단위다. 같은 snapshot이나 같은 시간대의 서로 다른 endpoint/rule concern을 숨기지 않는다.
44. Suppression은 `high_confidence_concern` 후보에 적용하고, state transition/stale/down/recovery timeline event를 숨기는 데 사용하지 않는다.
45. State transition dedup/folding key는 `applicationId + fromState + toState + eventType`이다.
46. Stale/down/recovery dedup/folding key는 `applicationId + eventType + storedApplicationStateCode`다.
47. Consecutive same state period는 하나의 start event로 접는다. 같은 state가 유지되는 scheduled/query fallback rows는 새 event가 아니라 period resolution 판단을 위한 source row로만 쓸 수 있다.
48. `degraded_entered`, `stale_entered`, `down_entered`, `high_confidence_concern` 같은 시작 성격 event는 later stored snapshot/read model에서 해소가 확인되면 nullable `resolvedAt`을 채울 수 있다.
49. `resolvedAt`은 synthetic time이 아니라 resolving source row의 `generatedAt`이다.
50. Horizon 안에서 해소가 확인되지 않으면 시작 event의 `resolvedAt=null`이다.
51. `degraded_entered.resolvedAt`은 같은 degraded period를 닫는 `degraded_resolved` source row가 있을 때 그 row의 `generatedAt`으로 채운다.
52. `stale_entered.resolvedAt`과 `down_entered.resolvedAt`은 stored recovery observation 또는 해당 state period exit source row가 있을 때 그 row의 `generatedAt`으로 채운다.
53. `high_confidence_concern.resolvedAt`은 같은 concern key가 later stored snapshot/read model에서 absent이거나 confidence `< 0.60`으로 내려간 것이 확인될 때만 채운다.
54. `degraded_resolved`와 `recovery_observed` event 자체는 기본적으로 `resolvedAt=null`이다.
55. Resolve/recovery event를 별도로 노출하더라도 시작 event의 `resolvedAt` 채움과 중복으로 보지 않는다.
56. Event severity는 event type 기반 mapping을 사용하고 marker severity를 final event severity로 강제하지 않는다.
57. Severity mapping은 `down_entered=critical`, `degraded_entered=warning`, `high_confidence_concern=warning`, `stale_entered=warning`, `degraded_resolved=info`, `recovery_observed=info`, `state_changed=info`다.
58. Event title/summary는 stored snapshot/read model 기준의 관찰과 해소 조건 충족을 표현한다.
59. Allowed copy 방향은 "저장된 snapshot에서 성능 저하가 관찰됐습니다.", "성능 저하 concern 해소 조건이 저장된 snapshot에서 확인됐습니다.", "새 metric bucket이 다시 관찰되며 회복 흐름이 시작됐습니다." 계열이다.
60. Forbidden copy는 "복구 완료", "장애 해결 완료", "앱 정상 확정", heartbeat success/failure/missing을 source로 암시하는 문구다.
61. Empty response는 5.9-a처럼 `events=[]`만 반환하며 "현재 문제 없음", "복구 완료", "장애 해결 완료"를 암시하지 않는다.
62. Projector가 response `limit` 이전에 너무 적은 snapshot source만 읽어 period folding/suppression을 깨지 않도록, service는 event response limit과 source row fetch cap을 분리한다.
63. Source row fetch cap은 bounded이어야 한다. MVP 후보는 `max(336, limit * 4)` 이상, hard cap `500` 안팎이며, response `events` cap은 여전히 query `limit`이다.
64. `findOperationalHistoryRows`는 stored row newest-first 조회와 helper column 운반만 수행하고 event type/severity/promotion/dedup/suppression을 계산하지 않는다.
65. 새 Flyway migration, 새 DB index, 새 table, materialized view, trigger, stored procedure를 추가하지 않는다.
66. `OperationalEventRepository`, `OperationalEventEntity`, `operational_events`, endpoint timeseries, raw explorer class/API를 추가하지 않는다.
67. Existing `MvcLayerBoundaryTest`는 5.9-b projector/parser/helper를 허용하되 forbidden live-source dependency와 forbidden physical surface를 계속 차단한다.
68. Public class, public method, 복잡한 helper에는 AGENTS.md 지침에 따라 한국어 Javadoc/comment를 남긴다.
69. Focused tests는 event type별 promotion, high-confidence threshold/key guard, short-strong-spike non-type behavior를 검증한다.
70. Focused tests는 60분 suppression boundary, 서로 다른 endpoint/rule 동시 concern 보존, state transition/stale/down/recovery folding key를 검증한다.
71. Focused tests는 start event `resolvedAt` fill, unresolved period null, resolve/recovery event 자체 `resolvedAt=null`을 검증한다.
72. Focused tests는 severity/copy mapping과 forbidden copy absence를 검증한다.
73. Focused tests는 raw field/p95/p99/trace/query field가 response model에 없음을 유지한다.
74. Source-boundary tests는 history service/controller/projector constructor에 forbidden live-source dependency가 없음을 검증한다.
75. Regression tests는 `eventId`가 snapshot-derived deterministic id이고 markerId를 재사용하지 않음을 유지한다.
76. `./gradlew :observability-portal:test --tests '*OperationalEvent*'`, `./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest`, relevant snapshot repository tests, full `./gradlew :observability-portal:test`, `git diff --check`가 통과해야 implementation completion으로 볼 수 있다.

## Tasks / Subtasks

- [x] Promotion projector 구현 (AC: 10~35)
  - [x] `OperationalEventHistoryProjector` no-op skeleton을 stored source row 기반 event candidate projection으로 교체한다.
  - [x] Source rows를 chronological order로 평가하고 legacy/unknown capture reason은 skip-safe로 처리한다.
  - [x] `degraded_entered`, `degraded_resolved`, `stale_entered`, `down_entered`, `recovery_observed`, `state_changed`, `high_confidence_concern` promotion rule을 구현한다.
  - [x] `short_strong_spike`는 별도 type 없이 high-confidence guard 통과 시 `high_confidence_concern`으로만 승격한다.
  - [x] Stored JSON signal은 bounded parser/helper로 읽고 current read model/live source 재계산을 하지 않는다.

- [x] Evidence/link/event id mapping 구현 (AC: 31~40)
  - [x] Helper column과 stored `snapshotEndpointEvidence.items[]`에서 bounded evidence field를 만든다.
  - [x] Endpoint evidence anchor는 5.8-b `endpoint-evidence-{n}` rule과 `anchorStatus=resolved|missing`을 유지한다.
  - [x] `OperationalEventIdFactory`를 유지하거나 확장해 type별 normalized key를 안정화한다.
  - [x] `links.snapshot`은 기존 snapshot detail path를 유지한다.

- [x] Suppression, dedup, period folding 구현 (AC: 41~55, 62~64)
  - [x] High-confidence concern suppression key를 `applicationId + primaryEndpointKey + primaryRuleId`로 구현한다.
  - [x] 60분 suppression boundary를 `[firstOccurredAt, firstOccurredAt + 60m)`로 구현한다.
  - [x] State transition/stale/down/recovery folding key를 구현한다.
  - [x] Consecutive same state period를 하나의 start event로 접고 later resolving row로 `resolvedAt`을 채운다.
  - [x] Response `limit`과 source row fetch cap을 분리해 folding/suppression 입력이 event cap에 의해 잘리지 않게 한다.

- [x] Severity/copy mapping 구현 (AC: 56~61)
  - [x] Event type 기반 severity mapping을 고정한다.
  - [x] Event type별 title/summary copy를 stored observation wording으로 구현한다.
  - [x] Recovery/resolve copy가 완료/정상 확정으로 읽히지 않도록 guard한다.

- [x] Regression guard 보강 (AC: 3~13, 35, 65~68, 73~75)
  - [x] 5.9-a endpoint/query/status/compact response shape를 유지한다.
  - [x] `MvcLayerBoundaryTest`에 5.9-b projector/helper allowed boundary와 forbidden dependency/physical surface guard를 보강한다.
  - [x] `operational_events`, endpoint timeseries, materialized view/outbox/Redis/raw explorer가 추가되지 않았음을 유지한다.
  - [x] Javadoc/comment는 한국어로 작성한다.

- [x] Verification (AC: 69~76)
  - [x] `OperationalEventHistoryProjectorTest` 또는 동등 focused service test를 추가한다.
  - [x] `OperationalEventHistoryServiceTest`에서 source cap 분리와 source repository boundary를 검증한다.
  - [x] `OperationalEventHistoryReadModelTest`에서 response shape/order/raw-field absence를 유지한다.
  - [x] `OperationalEventIdFactoryTest`에서 type별 key와 markerId 미사용을 검증한다.
  - [x] Controller/status mapping regression을 유지한다.
  - [x] Suggested commands를 실행한다.

## Dev Notes

### Contract Priority

- 최우선 source는 `implementation-artifacts/spec-story-5-9-operational-event-history-api-contract-decisions.md`다.
- 5.9-b는 5.9-a가 남긴 handoff를 구현한다. API skeleton, query parser, response wrapper, deterministic event id contract를 다시 설계하지 않는다.
- 5.8-a writer/capture policy와 5.8-b marker/detail/recovery source semantics는 재논의하지 않는다.

### 5.9-a Handoff

- 현재 `domain.history` package는 존재한다.
- `OperationalEventHistoryController`는 `/api/projects/{projectId}/applications/{applicationId}/operational-events` route와 status mapping을 제공한다.
- `OperationalEventHistoryService`는 membership 확인 후 `DashboardSnapshotRepository.findOperationalHistoryRows(...)`를 호출한다.
- `OperationalEventHistoryProjector`는 5.9-a에서 no-op skeleton이며 `events=[]`를 반환한다.
- `OperationalEventHistoryReadModel`은 event ordering과 top-level `source/horizon/events` shape를 고정한다.
- `OperationalEventIdFactory`는 `snapshot:{snapshotId}:{eventType}:{normalizedKey}` id를 만든다.
- Existing focused tests는 query default/clamp/invalid, empty response, response shape, deterministic id, forbidden dependency를 검증한다.

### Current Code State

- `OperationalEventHistoryService`는 현재 query `limit`을 source row query limit으로 그대로 전달한다. 5.9-b는 folding/suppression 입력이 event response limit에 잘리지 않도록 source fetch cap과 response event cap을 분리해야 한다.
- `DashboardSnapshotRepository.findOperationalHistoryRows(...)`와 `DashboardSnapshotJpaRepository.findOperationalHistoryRows(...)`는 row metadata/helper columns/stored JSON을 newest-first로 조회한다.
- `DashboardSnapshotDetailRow`는 `snapshotId`, `projectId`, `applicationId`, `generatedAt`, current/baseline window, `stateCode`, `captureReason`, `primaryRuleId`, `primaryEndpointKey`, `maxConfidence`, `readModelJson`을 운반한다.
- `DashboardSnapshotDetailProjectionParser`는 stored `read_model_json`에서 `snapshotEndpointEvidence`, `instanceSummary`, recovery/zeroInsight/triage signal을 bounded projection으로 읽는다. 이 parser/helper 재사용은 가능하지만 marker/detail service를 event source로 주입하지 않는다.
- `SnapshotEndpointEvidenceAnchorResolver`는 endpointKey 기반으로 `snapshotDetailAnchor`와 `anchorStatus`를 보강한다. Event evidence는 이 bounded anchor semantics를 보존해야 한다.
- `DashboardSnapshotMarkerClassifier`는 marker type/severity/copy classifier다. 5.9-b event severity/copy는 event type 기반 mapping을 사용해야 하므로 marker severity/copy를 final event semantics로 재사용하지 않는다.
- `MvcLayerBoundaryTest`는 5.9-a controller/service forbidden live-source dependency와 physical surface absence를 이미 검증한다. 5.9-b 구현 시 projector/helper까지 guard 범위를 넓힌다.

### 5.8-a / 5.8-b Handoff

- 5.8-a writer token은 `hourly_scheduled`, `state_change`, `high_confidence_concern`, `short_strong_spike`, `query_fallback`이다.
- `scheduled`는 새 write token이 아니며 read side에서 legacy/opaque fallback으로만 다룬다.
- Duplicate identity는 `application_id + current_window_end_utc`이며 operational event suppression이 아니다.
- Helper columns는 `capture_reason`, `primary_rule_id`, `primary_endpoint_key`, `max_confidence`다.
- Stored endpoint evidence block name은 `snapshotEndpointEvidence`; item anchor는 stored order 기준 `endpoint-evidence-{n}`이다.
- Marker는 stored read model point annotation이며 operational event item이 아니다.
- Marker API는 `eventId`, `resolvedAt`, dedup 결과, suppression 결과를 약속하지 않는다.
- `markerId`는 Story 5.9 `eventId`가 아니며 marker severity는 event severity 최종값이 아니다.
- 5.8-b handoff field는 `snapshotId`, `applicationId`, `capturedAt/generatedAt`, `currentWindowEndUtc`, `storedApplicationStateCode`, previous state 후보, `captureReason`, marker signal, `primaryRuleId`, `primaryEndpointKey`, `maxConfidence`, recovery signal, `snapshotEndpointEvidence.items[].anchorId`, `links.snapshot`이다.

### Architecture Constraints

- Active baseline은 Traditional MVC + Service/Repository Layering이다.
- `domain`은 feature grouping namespace이며 pure DDD domain layer가 아니다.
- `application`, `port`, `adapter`, `adapter.in`, `adapter.out` package를 만들지 않는다.
- Controller는 service를 호출하고 repository를 직접 호출하지 않는다.
- Service는 필요하면 Spring Data repository와 JPA entity를 직접 사용할 수 있지만 JPA entity를 API/service external model로 직접 반환하지 않는다.
- Flyway SQL migration이 schema source of truth다. 5.9-b는 새 migration/index를 기본 범위에 넣지 않는다.
- Repository/JPA query, DB view/trigger, controller, UI는 lifecycle state, rule, p95/p99, endpoint priority, operational event promotion을 계산하지 않는다.
- 공개 클래스/메서드/핵심 helper Javadoc/comment는 AGENTS.md 지침에 따라 한국어로 작성한다.

### Non-Goals To Preserve

- Current dashboard read model recomputation
- Lifecycle state/triage/endpoint priority/p95/p99 recomputation
- Heartbeat-derived event or recovery source
- Raw snapshot explorer/raw bucket explorer/arbitrary query endpoint
- Endpoint timeseries table/API
- `operational_events` table/repository/entity
- Materialized view, Redis queue, PostgreSQL outbox
- Alert delivery log, incident acknowledgement, owner assignment, comment thread workflow

## Testing

Focused test 대상 후보:

- `OperationalEventHistoryProjectorTest`
- `OperationalEventHistoryServiceTest`
- `OperationalEventHistoryReadModelTest`
- `OperationalEventIdFactoryTest`
- `OperationalEventHistoryControllerTest`
- `DashboardSnapshotRepositoryIntegrationTest`
- `MvcLayerBoundaryTest`

필수 scenario:

- `degraded_entered`, `degraded_resolved`, `stale_entered`, `down_entered`, `recovery_observed`, `state_changed`, `high_confidence_concern` promotion이 각각 기대 event type으로 생성된다.
- `scheduled_snapshot`, `query_fallback_snapshot`, `stored_snapshot`, 단순 `state_observation` 후보는 event로 노출되지 않는다.
- `short_strong_spike`는 별도 event type이 아니고 high-confidence guard 통과 시 `high_confidence_concern`으로만 승격된다.
- `high_confidence_concern`은 confidence `>= 0.82`와 rule/endpoint key guard가 필요하다.
- 같은 `applicationId + endpointKey + ruleId` concern은 60분 안에서 suppressed되고, 정확히 60분 이후는 새 후보가 된다.
- 같은 snapshot 안의 서로 다른 endpoint/rule concern은 suppression으로 숨겨지지 않는다.
- State transition/stale/down/recovery folding key가 repeated scheduled/query fallback rows를 중복 event로 만들지 않는다.
- 시작 event는 resolving row가 있으면 `resolvedAt`을 갖고, 없으면 `null`이다.
- `degraded_resolved`와 `recovery_observed` event 자체는 `resolvedAt=null`이다.
- Event severity는 type mapping을 따르고 marker severity를 final event severity로 강제하지 않는다.
- Copy에는 "복구 완료", "장애 해결 완료", "앱 정상 확정", heartbeat-derived source wording이 없다.
- Event evidence는 bounded helper/stored evidence field와 anchor status만 포함한다.
- Event item shape에 raw snapshot JSON, raw bucket list, raw endpoint JSON, endpoint p95/p99, trace/per-request/query field가 없다.
- `eventId`는 snapshot-derived deterministic id이고 markerId를 재사용하지 않는다.
- History service/controller/projector가 forbidden live-source dependency를 주입받지 않는다.
- `operational_events`, endpoint timeseries, materialized view/outbox/Redis/raw explorer physical surface가 없다.

Suggested commands:

```bash
./gradlew :observability-portal:test --tests '*OperationalEvent*'
./gradlew :observability-portal:test --tests '*DashboardSnapshotRepositoryIntegrationTest'
./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest
./gradlew :observability-portal:test
git diff --check
```

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- 2026-05-27: BMAD create-story workflow 설정, project-context, config를 확인했다.
- 2026-05-27: 사용자가 명시한 target story key `5-9-b-operational-event-promotion-suppression-and-period-folding`를 사용했고 sprint-status 자동 발견에 의존하지 않았다.
- 2026-05-27: 5.9 contract decision, 5.9-a story, sprint-status, operational-event-history/read-model/api contracts, 5.8-a/5.8-b stories를 확인했다.
- 2026-05-27: Epic 5/architecture 문서에서 operational history가 stored dashboard snapshot/read model 기반 service-layer surface임을 확인했다.
- 2026-05-27: 현재 `domain.history` code, history focused tests, snapshot repository, stored JSON parser/anchor resolver, `MvcLayerBoundaryTest`를 확인했다.
- 2026-05-27: Story 5.9-b create-story 산출물을 `planning-artifacts/stories/5-9-b-operational-event-promotion-suppression-and-period-folding.md`에 생성했다.
- 2026-05-27: `OperationalEventHistoryProjector`를 stored `dashboard_snapshots` source 기반 promotion/suppression/folding projector로 교체했다.
- 2026-05-27: `OperationalEventHistoryService`에서 response event limit과 source row fetch cap을 분리했다.
- 2026-05-27: Focused operational event tests, snapshot repository integration, MVC boundary, full portal test, diff whitespace check를 실행했다.

### Completion Notes List

- Stored snapshot row를 시간순으로 평가해 `state_changed`, `degraded_entered`, `degraded_resolved`, `stale_entered`, `down_entered`, `recovery_observed`, `high_confidence_concern`을 승격한다.
- High-confidence concern은 `applicationId + endpointKey + ruleId` 기준 60분 suppression을 적용하고, later stored snapshot에서 absence 또는 confidence `< 0.60`이 확인되면 `resolvedAt`을 채운다.
- State/stale/down/recovery period folding과 시작 성격 event의 `resolvedAt` 채움, type 기반 severity/title/summary mapping을 구현했다.
- 5.9-a endpoint/query/status/compact response/eventId/source boundary를 유지했고, forbidden live-source dependency와 forbidden physical surface guard를 보강했다.
- 검증 명령은 모두 통과했으며 story와 sprint status를 `review`로 전환했다.

### File List

- `planning-artifacts/stories/5-9-b-operational-event-promotion-suppression-and-period-folding.md`
- `implementation-artifacts/sprint-status.yaml`
- `observability-portal/src/main/java/com/observation/portal/domain/history/service/OperationalEventHistoryProjector.java`
- `observability-portal/src/main/java/com/observation/portal/domain/history/service/OperationalEventHistoryService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/history/model/OperationalEventItem.java`
- `observability-portal/src/test/java/com/observation/portal/domain/history/service/OperationalEventHistoryProjectorTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/history/service/OperationalEventHistoryServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/history/service/OperationalEventIdFactoryTest.java`
- `observability-portal/src/test/java/com/observation/portal/architecture/MvcLayerBoundaryTest.java`

### Change Log

- 2026-05-27: Story 5.9-b Operational Event Promotion, Suppression, and Period Folding create-story 산출물을 생성했다.
- 2026-05-27: Stored snapshot 기반 operational event promotion, suppression, period folding, source cap 분리, regression guard를 구현했다.
