---
artifactType: story
storyId: "5.9-a"
storyKey: "5-9-a-operational-event-history-api-skeleton-and-source-boundary"
epic: "Epic 5. Dashboard Read Model and API"
title: "Operational Event History API Skeleton and Source Boundary"
architectureStyle: Traditional MVC
status: review
date: 2026-05-27
---

# Story 5.9-a - Operational Event History API Skeleton and Source Boundary

## Status

review

## Story

portal 구현자로서, `domain.history` package에 operational event history API skeleton과 source boundary, compact read model response shape를 제공하길 원한다.

그래야 후속 Story 5.9-b가 promotion, suppression, period folding을 구현할 때 current dashboard나 raw bucket을 다시 계산하지 않고, 저장된 dashboard snapshot/read model source 위에서 안전하게 event history를 완성할 수 있다.

## Source of Truth

`implementation-artifacts/spec-story-5-9-operational-event-history-api-contract-decisions.md`가 Story 5.9-a의 최상위 결정 문서다.

아래 문서는 모두 읽고 반영한 story context다. 구현 중 충돌처럼 보이는 지점은 5.9 contract decision을 우선하고, 5.8-a writer/capture 저장 계약과 5.8-b marker/detail handoff 계약은 재논의하지 않는다.

1. `implementation-artifacts/spec-story-5-9-operational-event-history-api-contract-decisions.md`
2. `implementation-artifacts/sprint-status.yaml`
3. `planning-artifacts/contracts/operational-event-history.md`
4. `planning-artifacts/contracts/read-model-contract.md`
5. `planning-artifacts/api-surface.md`
6. `planning-artifacts/database-schema.md`
7. `planning-artifacts/stories/5-8-a-dashboard-snapshot-writer-and-capture-policy.md`
8. `planning-artifacts/stories/5-8-b-snapshot-marker-detail-recovery-source.md`
9. `planning-artifacts/epics.md`
10. `planning-artifacts/architecture.md`
11. `_bmad/custom/project-context.md`

닫힌 계약:

1. Endpoint는 `GET /api/projects/{projectId}/applications/{applicationId}/operational-events?since=24h&limit=50`다.
2. `since` 기본값은 `24h`, maximum clamp는 `14d`다.
3. `limit` 기본값은 `50`, maximum clamp는 `100`이다.
4. Response event order는 `occurredAt desc`, tie-breaker는 `eventId asc`다.
5. Top-level `source`는 `dashboard_snapshots`다.
6. Source는 `dashboard_snapshots` row metadata, bounded helper columns, stored `read_model_json`, Story 5.8-b handoff field로 제한한다.
7. Service/controller는 current dashboard read model, lifecycle state, triage, endpoint priority, p95/p99, heartbeat, raw bucket을 조회하거나 재계산하지 않는다.
8. `domain.history` feature package에 controller/service/model을 둔다.
9. `domain.snapshot` marker/detail external model을 operational event API response model로 재사용하지 않는다.
10. `markerId`는 `eventId`가 아니며, marker severity는 event severity 최종값이 아니다.
11. `eventId`는 snapshot-derived deterministic id이며 형식은 `snapshot:{snapshotId}:{eventType}:{normalizedKey}` 계열이다.
12. `eventId`에 durable incident workflow 의미를 부여하지 않는다.
13. 5.9-a는 compact event response shape와 deterministic id contract를 닫되, promotion/dedup/suppression/period folding/`resolvedAt` 세부 구현은 5.9-b로 넘긴다.
14. `operational_events` table/repository, materialized view, Redis/outbox, endpoint timeseries, raw explorer를 만들지 않는다.

## Scope / Out of Scope

포함:

- `domain.history` package skeleton
- operational event history controller/service/model/API route
- query parsing, default, max clamp, response horizon metadata
- valid project/application membership status mapping
- `dashboard_snapshots` source row 조회 boundary
- source row metadata/helper/stored JSON/5.8-b handoff field만 쓰는 guard
- compact top-level response shape
- compact event item shape
- snapshot-derived deterministic `eventId` helper/contract
- empty response semantics: valid request + no event는 `200`과 `events=[]`
- source-boundary regression tests
- Story 5.8-a의 blanket operational event class-name guard를 5.9-a 허용 범위에 맞게 정교화

제외:

- 5.9-b의 event type promotion 세부 구현
- 5.9-b의 deduplication, suppression window, period folding 구현
- 5.9-b의 `resolvedAt` 채우기와 resolve/recovery event 세부 알고리즘
- current dashboard read model 재생성 또는 재조회
- lifecycle state, triage, endpoint priority, p95/p99 재계산
- heartbeat success/failure/missing을 event source나 copy source로 사용
- raw bucket, accepted bucket, endpoint raw JSON live scan
- `operational_events` table/repository, materialized view, Redis queue, PostgreSQL outbox
- endpoint timeseries table/API
- raw snapshot explorer, raw bucket explorer, arbitrary metric query
- alert delivery log, acknowledgement, owner assignment, comment thread workflow

## Acceptance Criteria

1. Story 5.9-a는 Java/Kotlin/Spring production code를 지금 수정하는 산출물이 아니라, 이 story 파일과 sprint tracking만 생성된 상태에서 `ready-for-dev`가 된다.
2. 구현 story 실행 시 production 변경은 Traditional MVC + Service/Repository Layering과 feature-first package 구조를 따른다.
3. Operational event history implementation package는 `com.observation.portal.domain.history` 아래에 둔다.
4. 후보 package는 `domain.history.controller`, `domain.history.service`, `domain.history.model`이다.
5. `application`, `port`, `adapter`, `adapter.in`, `adapter.out` package를 만들지 않는다.
6. Controller는 service를 호출하고 repository를 직접 호출하지 않는다.
7. Endpoint는 `GET /api/projects/{projectId}/applications/{applicationId}/operational-events`다.
8. Query parameter `since` 생략 시 `24h`를 사용한다.
9. Query parameter `limit` 생략 시 `50`을 사용한다.
10. `since`는 retention boundary 안에서 maximum `14d`로 clamp한다.
11. `since` parser는 최소한 positive integer + `h`/`d` token을 지원하고, `24h`, `7d`, `14d`, `30d` clamp case를 test fixture로 둔다.
12. `limit > 100`은 effective `100`으로 clamp한다.
13. Blank, malformed, non-positive `since`는 `400`으로 매핑한다.
14. Blank, malformed, non-positive `limit`은 `400`으로 매핑한다.
15. Valid project/application이 아니거나 membership이 맞지 않으면 `404`로 매핑한다.
16. Stored snapshot 조회/projection 실패는 generic `500`으로 매핑한다.
17. Valid project/application + horizon 안에 snapshot/event가 없으면 `200`과 `events=[]`를 반환한다.
18. Empty response copy는 "현재 문제 없음", "복구 완료", "장애 해결 완료"를 암시하지 않는다.
19. Top-level response는 `generatedAt`, `applicationId`, `source`, `horizon`, `events`를 포함한다.
20. Top-level `source`는 정확히 `dashboard_snapshots`다.
21. `horizon`은 effective `since`, `until`, `requestedSince`, `defaultSince=24h`, `maxSince=14d`, `limit`, `maxLimit=100`, `order=occurredAt_desc`를 포함한다.
22. Response event list는 `occurredAt DESC`, tie-breaker `eventId ASC`로 정렬한다.
23. 5.9-a implementation은 sorting contract를 empty list와 fixture event list 양쪽에서 검증한다.
24. Event item shape는 `eventId`, `type`, `severity`, `title`, `summary`, `occurredAt`, nullable `resolvedAt`, `stateCode`, nullable `confidence`, `snapshotId`, `evidence`, `links.snapshot`을 제공한다.
25. Event evidence는 bounded stored field만 포함한다.
26. Endpoint evidence 후보 field는 `ruleId`, `endpointKey`, optional `method`, optional `route`, optional `snapshotDetailAnchor`, `anchorStatus`다.
27. Event item은 raw snapshot JSON, raw bucket list, raw endpoint JSON, endpoint p95/p99, trace id, per-request sample, query string, query key/value를 포함하지 않는다.
28. `eventId`는 snapshot-derived deterministic id다.
29. `eventId` 기본 형식은 `snapshot:{snapshotId}:{eventType}:{normalizedKey}` 계열이다.
30. 같은 snapshot에서 같은 event candidate는 같은 `eventId`를 만든다.
31. `markerId`를 `eventId`로 재사용하지 않는다.
32. `eventId`에 acknowledgement, owner assignment, comment thread 같은 durable workflow 의미를 부여하지 않는다.
33. Event `occurredAt` source는 stored snapshot observation time이다. 기본 후보는 5.8-b handoff의 `capturedAt` 또는 row `generatedAt`이다.
34. Event `links.snapshot`은 `GET /api/projects/{projectId}/applications/{applicationId}/dashboard/snapshots/{snapshotId}` path를 가리킨다.
35. Event severity skeleton은 event type 기반 mapping을 받을 수 있는 전용 field로 둔다.
36. Marker severity를 final event severity로 강제하지 않는다.
37. Event title/summary skeleton은 stored snapshot/read model 기준의 관찰을 표현할 공간을 둔다.
38. Recovery copy는 "복구 완료", "장애 해결 완료", "앱 정상 확정"을 사용하지 않는다.
39. Heartbeat success/failure/missing을 event title, summary, recommended copy source로 사용하지 않는다.
40. Service source row 조회는 `DashboardSnapshotRepository`를 재사용한다.
41. Service가 읽을 수 있는 source는 `dashboard_snapshots` row metadata, helper columns, stored `read_model_json`, 5.8-b handoff field뿐이다.
42. 필요한 경우 `DashboardSnapshotDetailRow`와 stored JSON parser/helper를 재사용할 수 있지만, operational event external model은 `domain.history.model`에 별도로 둔다.
43. `DashboardSnapshotMarkerReadModel`, `DashboardSnapshotMarkerItem`, marker `type/severity`를 operational event response shape로 그대로 반환하지 않는다.
44. `DashboardSnapshotDetailService` 또는 `DashboardSnapshotMarkerService`를 operational event source로 주입해 detail/marker API 의미를 우회 재사용하지 않는다.
45. 5.9-a는 source row 조회와 response skeleton을 닫지만, state transition/high-confidence concern 승격 알고리즘은 5.9-b로 남긴다.
46. 5.9-a에서 event projector가 no-op skeleton이면, valid snapshot rows가 있어도 `events=[]`를 반환할 수 있고 이 behavior를 5.9-b handoff note에 명시한다.
47. 5.9-a가 최소 fixture event를 생성하는 helper를 만들 경우에도 dedup/suppression/period folding을 구현하지 않는다.
48. `resolvedAt` field는 response shape에 nullable로 존재할 수 있지만, 5.9-a가 period folding으로 값을 채우지 않는다.
49. Resolve/recovery event 자체의 `resolvedAt=null` 세부 rule은 5.9-b acceptance로 넘긴다.
50. 5.9-a는 `degraded_entered`, `degraded_resolved`, `stale_entered`, `down_entered`, `recovery_observed`, `high_confidence_concern`, `state_changed` 같은 event type vocabulary를 모델 상수/enum 후보로 둘 수 있다.
51. 5.9-a는 `short_strong_spike`를 별도 event type으로 노출하지 않는다. 5.9-b가 high-confidence concern 조건을 만족할 때만 승격 여부를 판단한다.
52. Service/controller는 `MetricBucketRepository`를 주입받지 않는다.
53. Service/controller는 `StarterHeartbeatTelemetryRepository`를 주입받지 않는다.
54. Service/controller는 `DashboardReadModelService`를 주입받지 않는다.
55. Service/controller는 `LifecycleStateService`를 주입받지 않는다.
56. Service/controller는 `TriageSummaryService`를 주입받지 않는다.
57. Service/controller는 `EndpointPriorityService`를 주입받지 않는다.
58. Service/controller는 `InstanceEvidenceReadModelService`를 주입받지 않는다.
59. Repository/JPA query, DB view/trigger, controller는 lifecycle state, rule, p95/p99, endpoint priority, operational event promotion을 계산하지 않는다.
60. New migration을 추가하지 않는다.
61. `operational_events` table을 추가하지 않는다.
62. `OperationalEventRepository` 또는 동등한 event store repository를 만들지 않는다.
63. Materialized view, Redis queue, PostgreSQL outbox를 추가하지 않는다.
64. Endpoint timeseries table/API를 만들지 않는다.
65. Raw snapshot explorer/raw bucket explorer/arbitrary query endpoint를 만들지 않는다.
66. Existing `MvcLayerBoundaryTest`의 Story 5.8-a non-goal guard는 5.9-a의 allowed `domain.history` operational event API classes를 막지 않도록 정교화한다.
67. Refined architecture guard는 `OperationalEventRepository`, `operational_events`, endpoint timeseries, raw explorer, forbidden live-source dependency를 계속 막는다.
68. Focused tests가 query default/clamp/invalid status, empty response, response shape, deterministic event id, source dependency prohibition, non-goal physical surface absence를 검증한다.
69. `./gradlew :observability-portal:test --tests '*OperationalEvent*'`, relevant controller/repository/architecture tests, full `./gradlew :observability-portal:test`, `git diff --check`가 통과해야 implementation completion으로 볼 수 있다.

## Tasks / Subtasks

- [x] `domain.history` API skeleton 추가 (AC: 2~18, 40~49, 52~59)
  - [x] `OperationalEventHistoryController` 후보를 `domain.history.controller`에 추가한다.
  - [x] `OperationalEventHistoryService` 후보를 `domain.history.service`에 추가한다.
  - [x] Controller는 UUID/path/query 변환과 HTTP status mapping만 담당한다.
  - [x] Service는 membership 확인 후 `DashboardSnapshotRepository`에서 source rows를 조회한다.
  - [x] Service는 current dashboard, accepted bucket, heartbeat, lifecycle/triage/endpoint priority service를 주입받지 않는다.

- [x] Query policy와 response horizon 구현 (AC: 7~23)
  - [x] `since` default `24h`, max `14d` clamp를 구현한다.
  - [x] `limit` default `50`, max `100` clamp를 구현한다.
  - [x] malformed/blank/non-positive query는 `400`으로 매핑한다.
  - [x] horizon block에 requested/effective/default/max/order metadata를 채운다.
  - [x] response order는 `occurredAt desc`, tie-breaker `eventId asc`로 고정한다.

- [x] Compact read model과 deterministic event id contract 구현 (AC: 19~39, 50~51)
  - [x] `OperationalEventHistoryReadModel` 후보를 추가한다.
  - [x] `OperationalEventItem`, `OperationalEventEvidence`, `OperationalEventLinks`, `OperationalEventType`, `OperationalEventSeverity` 후보를 추가한다.
  - [x] `OperationalEventIdFactory` 또는 동등 helper를 추가해 `snapshot:{snapshotId}:{eventType}:{normalizedKey}` id를 생성한다.
  - [x] raw JSON, raw bucket, p95/p99, trace/per-request/query field가 response model에 없음을 test로 고정한다.
  - [x] `resolvedAt`은 nullable shape로만 두고 5.9-a에서 period folding으로 채우지 않는다.

- [x] Dashboard snapshot source row boundary 추가 (AC: 40~49, 52~59)
  - [x] `DashboardSnapshotRepository`에 operational history source row query가 필요하면 추가한다.
  - [x] Source row는 snapshot id, project/application id, generatedAt/capturedAt, currentWindowEndUtc, stateCode, captureReason, primaryRuleId, primaryEndpointKey, maxConfidence, readModelJson만 운반한다.
  - [x] Repository는 event type/severity/promotion/dedup/suppression을 계산하지 않는다.
  - [x] 5.8-b handoff field를 읽어도 marker external response model을 event response로 그대로 반환하지 않는다.
  - [x] 5.9-b가 채울 projector 확장 지점을 명시하되 5.9-a에서는 no-op 또는 shape-only helper로 제한한다.

- [x] Empty/status mapping 구현 (AC: 13~18, 68~69)
  - [x] project/application membership mismatch는 `404`로 매핑한다.
  - [x] valid request + no snapshots 또는 no promoted events는 `200`과 `events=[]`로 반환한다.
  - [x] projection failure는 generic `500`으로 매핑한다.
  - [x] empty response가 health/recovery completion을 단정하지 않는지 test로 고정한다.

- [x] Source-boundary regression guard 정교화 (AC: 52~67)
  - [x] `MvcLayerBoundaryTest`의 Story 5.8-a blanket `operationalevent` class-name 금지를 5.9-a 허용 범위에 맞게 수정한다.
  - [x] `domain.history` controller/service/model은 허용하되 `OperationalEventRepository`, event store entity, `operational_events`, endpoint timeseries, raw explorer는 계속 금지한다.
  - [x] `OperationalEventHistoryService` constructor dependency에 forbidden live-source service/repository가 없는지 reflection/ArchUnit test를 추가한다.
  - [x] migration 파일에 `operational_events`, `endpoint_timeseries`, materialized view/outbox/Redis 관련 physical surface가 없는지 guard한다.

- [x] Verification (AC: 68~69)
  - [x] Controller test가 default/clamp/invalid query, 404 membership, empty `200` response를 검증한다.
  - [x] Model/helper test가 event id deterministic behavior와 sorting contract를 검증한다.
  - [x] Service test가 snapshot repository source만 사용하고 no-op/shape-only skeleton behavior를 검증한다.
  - [x] Architecture test와 full portal test를 실행한다.

## Dev Notes

### Contract Priority

- 최우선 source는 `implementation-artifacts/spec-story-5-9-operational-event-history-api-contract-decisions.md`다.
- `operational-event-history.md`, `api-surface.md`, `database-schema.md`에는 5.9 전체 후보 표현이 들어 있다. 5.9-a는 여기서 endpoint/source/shape만 가져오고, promotion/dedup/suppression/period folding은 5.9-b로 미룬다.
- `planning-artifacts/stories/5-8-a-dashboard-snapshot-writer-and-capture-policy.md`가 저장 substrate를 닫았고, `planning-artifacts/stories/5-8-b-snapshot-marker-detail-recovery-source.md`가 marker/detail handoff field를 닫았다.
- `5-8-dashboard-snapshot-persistence-and-marker-contract`는 sprint-status상 umbrella tracking key이며 story로 create하지 않는다.

### 5.8-a Handoff

- Writer persisted token은 `hourly_scheduled`, `state_change`, `high_confidence_concern`, `short_strong_spike`, `query_fallback`이다.
- `scheduled`는 새 write token이 아니며, read side에서 null/unknown/legacy token은 opaque metadata로 허용한다.
- Duplicate identity는 `application_id + current_window_end_utc`다.
- 5.8-a helper column은 `capture_reason`, `primary_rule_id`, `primary_endpoint_key`, `max_confidence`다.
- `snapshotEndpointEvidence.items[]`는 max 10이고, `instanceSummary.items[]`는 max 50이다.
- 5.8-a는 60분 operational event suppression, event promotion, event API를 구현하지 않았다.

### 5.8-b Handoff

- Marker/detail source는 `dashboard_snapshots` row metadata와 stored `read_model_json`이다.
- Marker는 stored read model point annotation이며 operational event item이 아니다.
- Marker API는 `eventId`, `resolvedAt`, dedup 결과, suppression 결과를 약속하지 않는다.
- `markerId`는 Story 5.9 `eventId`가 아니다.
- Marker severity는 Story 5.9 event severity 최종값이 아니다.
- 5.9 handoff candidate field는 `snapshotId`, `applicationId`, `capturedAt`, `currentWindowEndUtc`, `storedApplicationStateCode`, `previousState.stateCode`, `captureReason`, marker `type`, marker `severity`, `primaryRuleId`, `primaryEndpointKey`, `maxConfidence`, recovery marker presence/type, `snapshotEndpointEvidence.items[].anchorId`, `links.snapshot`이다.
- 5.8-b는 operational event endpoint, event promotion/dedup/suppression, `resolvedAt` semantics를 구현하지 않았다.

### Current Code State

- `domain.history` package는 현재 없다. 5.9-a가 새 feature package를 만든다.
- `DashboardSnapshotController`는 `/dashboard/snapshots/{snapshotId}`와 `/dashboard/snapshot-markers`를 제공한다. 5.9-a endpoint는 `/applications/{applicationId}/operational-events`로 dashboard sub-path 아래가 아니다.
- `DashboardSnapshotDetailService`와 `DashboardSnapshotMarkerService`는 stored snapshot projection service이며 current recalculation dependency를 주입받지 않는다.
- `DashboardSnapshotRepository`는 detail row, marker horizon row, previous snapshot, previous active snapshot query를 이미 제공한다.
- `DashboardSnapshotDetailRow`는 snapshot id, project/application id, generatedAt, window metadata, stateCode, captureReason, primaryRuleId, primaryEndpointKey, maxConfidence, readModelJson을 운반한다.
- Operational history source row query는 `DashboardSnapshotDetailRow`를 재사용할 수도 있고, `domain.history` 전용 row model을 추가할 수도 있다. 어느 쪽이든 external event API model로 snapshot marker model을 반환하지 않는다.
- `DashboardSnapshotMarkerClassifier`는 marker type/severity/copy만 만든다. 5.9-a는 이 classifier 결과를 event promotion으로 간주하지 않는다.
- `MvcLayerBoundaryTest.story58aNonGoalSurfacesAreNotPresent()`는 현재 class name에 `operationalevent`가 포함되면 금지한다. 5.9-a 구현 전 이 guard를 정교화해야 `OperationalEventHistoryService` 같은 허용 class가 막히지 않는다.
- Existing migrations include `V007__extend_dashboard_snapshots_for_writer_policy.sql`; 5.9-a는 새 table/index/migration을 추가하지 않는다.

### Implementation File Candidates

아래는 dev-story 구현 후보이며, 이 create-story 작업에서는 수정하지 않았다.

- `observability-portal/src/main/java/com/observation/portal/domain/history/controller/OperationalEventHistoryController.java`
- `observability-portal/src/main/java/com/observation/portal/domain/history/model/OperationalEventHistoryReadModel.java`
- `observability-portal/src/main/java/com/observation/portal/domain/history/model/OperationalEventItem.java`
- `observability-portal/src/main/java/com/observation/portal/domain/history/model/OperationalEventEvidence.java`
- `observability-portal/src/main/java/com/observation/portal/domain/history/model/OperationalEventType.java`
- `observability-portal/src/main/java/com/observation/portal/domain/history/model/OperationalEventSeverity.java`
- `observability-portal/src/main/java/com/observation/portal/domain/history/service/OperationalEventHistoryService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/history/service/OperationalEventHistoryQuery.java`
- `observability-portal/src/main/java/com/observation/portal/domain/history/service/OperationalEventIdFactory.java`
- `observability-portal/src/main/java/com/observation/portal/domain/history/service/InvalidOperationalEventHistoryQueryException.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/repository/DashboardSnapshotRepository.java`
  - operational history source row query가 필요할 때만 추가한다.
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/repository/DashboardSnapshotJpaRepository.java`
  - stored snapshot rows를 `generatedAt desc` 기준으로 bounded 조회하는 query 후보.
- `observability-portal/src/test/java/com/observation/portal/domain/history/**`
  - controller/service/model/id helper focused tests 후보.
- `observability-portal/src/test/java/com/observation/portal/architecture/MvcLayerBoundaryTest.java`
  - 5.9-a allowed/forbidden boundary guard 보강 후보.

### Architecture Constraints

- Active baseline은 Traditional MVC + Service/Repository Layering이다.
- `domain`은 feature grouping namespace이며 pure DDD domain layer가 아니다.
- Controller는 service를 호출하고 repository를 직접 호출하지 않는다.
- Service는 필요하면 Spring Data repository와 JPA entity를 직접 사용할 수 있지만 JPA entity를 API/service external model로 직접 반환하지 않는다.
- Flyway SQL migration이 schema source of truth다. 5.9-a는 새 migration을 만들지 않는다.
- DB view, materialized view, trigger, stored procedure에 state, rule, priority, confidence, p95/p99, endpoint priority, event promotion을 숨기지 않는다.
- 공개 클래스/메서드/핵심 helper Javadoc/comment는 AGENTS.md 지침에 따라 한국어로 작성한다.

### 5.9-b Handoff

- 5.9-b가 owns: event type promotion, high-confidence concern 승격, 60분 suppression, deduplication, period folding, `resolvedAt`, final event severity/title/summary copy.
- 5.9-b가 preserve: source는 stored `dashboard_snapshots` row/read model/handoff field다.
- 5.9-b가 preserve: heartbeat success/failure/missing은 operational event source가 아니다.
- 5.9-b가 preserve: `markerId`는 `eventId`가 아니며 marker severity는 final event severity가 아니다.
- 5.9-b가 preserve: 별도 event store, endpoint timeseries, materialized view, Redis/outbox는 MVP history source가 아니다.

## Testing

Focused test 대상 후보:

- `OperationalEventHistoryControllerTest`
- `OperationalEventHistoryServiceTest`
- `OperationalEventHistoryReadModelTest`
- `OperationalEventIdFactoryTest`
- `OperationalEventHistoryQueryTest`
- `DashboardSnapshotRepositoryIntegrationTest`
- `MvcLayerBoundaryTest`

필수 scenario:

- API default는 `since=24h`, `limit=50`이다.
- `since`가 `14d`를 넘으면 effective horizon이 `14d`로 clamp된다.
- `limit > 100`은 `100`으로 clamp된다.
- blank/malformed/non-positive `since`와 `limit`은 `400`이다.
- project/application membership mismatch는 `404`다.
- valid empty horizon은 `200`, `source=dashboard_snapshots`, `events=[]`다.
- response horizon은 `requestedSince`, `defaultSince=24h`, `maxSince=14d`, `limit`, `maxLimit=100`, `order=occurredAt_desc`를 담는다.
- event sorting은 `occurredAt DESC`, tie-breaker `eventId ASC`다.
- `eventId` helper는 같은 snapshot/type/key에 대해 deterministic id를 만든다.
- `eventId` helper는 markerId를 입력/source로 요구하지 않는다.
- event item shape에 raw snapshot JSON, raw bucket list, raw endpoint JSON, endpoint p95/p99, trace/per-request/query field가 없다.
- `resolvedAt`은 nullable field로만 존재하며 5.9-a에서 period folding으로 채워지지 않는다.
- `OperationalEventHistoryService`가 `DashboardSnapshotRepository` 외 forbidden live-source dependency를 주입받지 않는다.
- `MetricBucketRepository`, `StarterHeartbeatTelemetryRepository`, `DashboardReadModelService`, `LifecycleStateService`, `TriageSummaryService`, `EndpointPriorityService`, `InstanceEvidenceReadModelService`가 history service/controller dependency에 없다.
- `operational_events` table/repository, materialized view, Redis/outbox, endpoint timeseries, raw explorer가 추가되지 않았다.
- Story 5.8-a architecture guard가 `domain.history` API skeleton을 허용하도록 정교화됐고, forbidden physical/source surfaces는 계속 차단한다.

Suggested commands:

```bash
./gradlew :observability-portal:test --tests '*OperationalEvent*'
./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest
./gradlew :observability-portal:test --tests '*DashboardSnapshotRepositoryIntegrationTest'
./gradlew :observability-portal:test
git diff --check
```

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- 2026-05-27: BMAD create-story workflow 설정과 project-context를 확인했다.
- 2026-05-27: 5.9 contract decision, sprint-status, operational-event-history/read-model/api/database contracts, 5.8-a/5.8-b stories를 확인했다.
- 2026-05-27: Epic 5/architecture 문서에서 operational history가 stored dashboard snapshot/read model 기반 service-layer surface임을 확인했다.
- 2026-05-27: 현재 snapshot controller/service/repository/model과 `MvcLayerBoundaryTest` guard 상태를 확인했다.
- 2026-05-27: Story 5.9-a create-story 산출물을 `planning-artifacts/stories/5-9-a-operational-event-history-api-skeleton-and-source-boundary.md`에 생성했다.
- 2026-05-27: BMAD dev-story workflow로 target story key를 명시적으로 사용하고 sprint-status에서 5.9-a만 in-progress로 전환했다.
- 2026-05-27: `domain.history` controller/service/query/model/id helper/no-op projector와 dashboard snapshot operational history source row query를 구현했다.
- 2026-05-27: Source-boundary architecture guard와 focused controller/service/model/helper/repository tests를 추가했다.
- 2026-05-27: `./gradlew :observability-portal:test --tests '*OperationalEvent*'`, `./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest`, `./gradlew :observability-portal:test --tests '*DashboardSnapshotRepositoryIntegrationTest'`, full `./gradlew :observability-portal:test`, `git diff --check`를 실행해 통과를 확인했다.

### Completion Notes List

- Story 5.9-a scope, acceptance criteria, implementation candidates, source-boundary guard, 5.9-b handoff를 구현 기준으로 정리했다.
- sprint-status workflow 관례에 따라 `5-9-a-operational-event-history-api-skeleton-and-source-boundary`는 `review`로 전환한다.
- `GET /api/projects/{projectId}/applications/{applicationId}/operational-events` endpoint와 `domain.history` service/model skeleton을 추가했다.
- `since` positive integer + `h`/`d` parser, default `24h`, max `14d` clamp, `limit` default `50`, max `100` clamp, invalid query `400` mapping을 구현했다.
- Response top-level source/horizon/empty `events=[]` shape와 `occurredAt desc`, `eventId asc` sorting contract를 구현했다.
- Snapshot-derived deterministic `eventId` helper와 compact event item/evidence/link model을 추가했다.
- `DashboardSnapshotRepository`에 stored snapshot row metadata/helper/readModelJson만 운반하는 operational history source query를 추가했다.
- 5.9-a projector는 no-op skeleton으로 유지되어 valid snapshot rows가 있어도 `events=[]`를 반환할 수 있으며, promotion/dedup/suppression/period folding/`resolvedAt` 채우기는 5.9-b로 남겼다.
- Forbidden live-source dependency, operational event store/table, endpoint timeseries, raw explorer, materialized view/outbox/Redis regression guard를 추가했다.

### File List

- `planning-artifacts/stories/5-9-a-operational-event-history-api-skeleton-and-source-boundary.md`
- `implementation-artifacts/sprint-status.yaml`
- `observability-portal/src/main/java/com/observation/portal/domain/history/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/history/controller/OperationalEventHistoryController.java`
- `observability-portal/src/main/java/com/observation/portal/domain/history/model/OperationalEventEvidence.java`
- `observability-portal/src/main/java/com/observation/portal/domain/history/model/OperationalEventHistoryReadModel.java`
- `observability-portal/src/main/java/com/observation/portal/domain/history/model/OperationalEventItem.java`
- `observability-portal/src/main/java/com/observation/portal/domain/history/model/OperationalEventLinks.java`
- `observability-portal/src/main/java/com/observation/portal/domain/history/model/OperationalEventSeverity.java`
- `observability-portal/src/main/java/com/observation/portal/domain/history/model/OperationalEventType.java`
- `observability-portal/src/main/java/com/observation/portal/domain/history/service/InvalidOperationalEventHistoryQueryException.java`
- `observability-portal/src/main/java/com/observation/portal/domain/history/service/OperationalEventHistoryProjectionException.java`
- `observability-portal/src/main/java/com/observation/portal/domain/history/service/OperationalEventHistoryProjector.java`
- `observability-portal/src/main/java/com/observation/portal/domain/history/service/OperationalEventHistoryQuery.java`
- `observability-portal/src/main/java/com/observation/portal/domain/history/service/OperationalEventHistoryService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/history/service/OperationalEventIdFactory.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/repository/DashboardSnapshotJpaRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/repository/DashboardSnapshotRepository.java`
- `observability-portal/src/test/java/com/observation/portal/architecture/MvcLayerBoundaryTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/history/controller/OperationalEventHistoryControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/history/model/OperationalEventHistoryReadModelTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/history/service/OperationalEventHistoryQueryTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/history/service/OperationalEventHistoryServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/history/service/OperationalEventIdFactoryTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/repository/DashboardSnapshotRepositoryIntegrationTest.java`

### Change Log

- 2026-05-27: Story 5.9-a Operational Event History API Skeleton and Source Boundary create-story 산출물을 생성했다.
- 2026-05-27: Operational event history API skeleton/source boundary/compact response shape와 regression tests를 구현하고 story 상태를 review로 변경했다.

## Status

review
