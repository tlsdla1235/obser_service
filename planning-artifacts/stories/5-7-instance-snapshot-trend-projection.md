---
artifactType: story
storyId: "5.7"
storyKey: "5-7-instance-snapshot-trend-projection"
epic: "Epic 5. Dashboard Read Model and API"
title: "Instance Snapshot Trend Projection"
architectureStyle: Traditional MVC
status: done
date: 2026-05-26
---

# Story 5.7 - Instance Snapshot Trend Projection

## User Story

portal 구현자로서, selected application instance의 최근 7일/14일 관찰 흐름을 저장된 dashboard snapshot에서만 projection하는 read model API를 제공하길 원한다.

그래야 Instance Detail 화면과 후속 UI가 raw bucket explorer나 current state 재판정 없이, `dashboard_snapshots.read_model_json.instanceSummary.items[]`에 저장된 bounded instance summary만 시간 순서로 확인할 수 있다.

## Source of Truth

`implementation-artifacts/spec-story-5-7-instance-snapshot-trend-contract-decisions.md`가 Story 5.7의 최상위 결정 문서다.

이 story와 `read-model-contract.md`, `operational-event-history.md`, `api-surface.md`, `database-schema.md`, Epic 5 문서, 선행 Story 5.6 문서/코드가 충돌하면 Story 5.7 결정 문서를 우선한다. 구현자는 아래 결정을 재논의하지 않는다.

1. Story 5.7은 `dashboard_snapshots` table, read-side JPA entity/repository, seeded snapshot projection test, `InstanceSnapshotTrendService` projection을 소유한다.
2. Trend source는 오직 `dashboard_snapshots.read_model_json.instanceSummary.items[]`다.
3. `read_model_json.instances[]`는 live dashboard navigation list이며 trend source로 확장하지 않는다.
4. `read_model_json.snapshot`은 marker/link semantics 후보이며 instance trend source가 아니다.
5. `instanceSummary.schemaVersion`은 필수이고 MVP Story 5.7은 `"1.0"`만 지원한다.
6. `instanceSummary.items[]`는 snapshot당 최대 50개 bounded summary다. Story 5.7 parser는 초과 item을 실패시키지 않고 projection을 bounded하게 유지한다.
7. `{instanceId}` path variable은 `application_instances.id` UUID이며 `items[].instanceId`와 exact match한다.
8. `instanceName`은 표시 metadata일 뿐 matching fallback key가 아니다.
9. Missing snapshot/source/item은 정상 empty trend이며 `200 + points=[]`다.
10. project/application/instance membership 실패만 `404`다.
11. `InstanceSnapshotTrendService`는 stored snapshot projection만 수행한다.
12. Story 5.7은 snapshot writer, scheduler, capture policy, marker/detail API, recovery marker, operational event history를 구현하지 않는다.
13. Story 5.8은 writer/capture/marker/detail/recovery 계약을 이어받고, Story 5.9는 operational event history API를 이어받는다.
14. Story 5.8은 Story 5.7이 고정한 `instanceSummary` minimum shape를 rename, remove, reinterpret하지 않는다.

## Scope / Out of Scope

포함:

- `GET /api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/snapshot-trend?since=7d&limit=168` read-only API
- `{projectId}`, `{applicationId}`, `{instanceId}` UUID path membership 검증
- `dashboard_snapshots` physical table이 아직 없으므로 next Flyway migration으로 최소 read-side substrate 생성
- `capture_reason` nullable column을 opaque read metadata로 포함
- `dashboard_snapshots` read-side JPA entity/repository mapping
- `projectId + applicationId + generatedAt` horizon 조회와 deterministic ordering
- `read_model_json.instanceSummary.items[]` parser/projection helper
- `InstanceSnapshotTrendService` stored snapshot projection
- seeded snapshot projection unit/integration tests
- missing snapshot/source/malformed item을 `200 + points=[]` 또는 skip으로 수렴하는 behavior
- Story 5.6 `links.snapshotTrend`를 실제 endpoint path로 연결하는 보수적 보강은 허용

제외:

- scheduled snapshot writer
- dashboard query fallback snapshot capture
- final snapshot capture policy
- snapshot marker API
- snapshot detail API
- recovery marker source priority
- previous state, `lastHealthyAt`, `lastRecoveredAt`, recovery semantics
- operational event promotion, deduplication, suppression, event API
- snapshot/history UI, marker UI, deep link UX
- raw bucket explorer, raw snapshot JSON list, endpoint timeseries, arbitrary metric query
- current state, lifecycle state, insight rule, endpoint priority, p95/p99, health score 재판정
- `MetricBucketRepository`, `StarterHeartbeatTelemetryRepository`, `LifecycleStateService`, `TriageSummaryService`, `EndpointPriorityService`, `DashboardReadModelService`, `InstanceEvidenceReadModelService` 호출
- ingest/heartbeat request schema, accepted bucket write path, starter heartbeat write path 변경
- endpoint evidence body, full snapshot detail payload, raw accepted bucket JSON, raw `endpoints_json`
- raw instance timeseries table, endpoint timeseries table, materialized view, Redis/outbox, `operational_events` table

## Acceptance Criteria

1. `GET /api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/snapshot-trend` endpoint를 제공한다.
2. `{projectId}`, `{applicationId}`, `{instanceId}`는 모두 UUID path variable이다.
3. `{instanceId}`는 `application_instances.id`이며 `instanceName` 문자열 path나 fallback lookup을 만들지 않는다.
4. service는 `projectId + applicationId + instanceId` membership을 검증한다.
5. project 없음, application 없음, project/application mismatch, instance 없음, instance/application mismatch는 모두 `404`로 매핑한다.
6. snapshot row 부재, horizon 안 snapshot 없음, snapshot row에 `instanceSummary` 없음, unsupported schema, target instance item 없음, malformed item만 있음은 모두 `200`과 `points=[]`로 반환한다.
7. `since` query parameter 생략 시 `7d`를 사용한다.
8. MVP에서 지원하는 `since` token은 `7d`, `14d`다.
9. invalid duration format 또는 unsupported `since` token은 `400`으로 매핑한다.
10. effective horizon은 configured `dashboard_snapshots` retention을 넘지 않는다. 별도 설정이 없으면 기본 retention은 14일이다.
11. `limit` query parameter 생략 시 `168`을 사용한다.
12. `limit > 336`은 `336`으로 clamp한다.
13. `limit <= 0` 또는 정수로 해석할 수 없는 값은 `400`으로 매핑한다.
14. response order는 `capturedAt ASC`다.
15. 같은 `capturedAt` 안 response tie-breaker는 `snapshotId ASC`다.
16. repository 구현은 effective horizon 안 newest snapshot을 먼저 읽고 limit을 적용한 뒤, service에서 selected points를 ascending으로 정렬할 수 있다.
17. retention gap과 missing hourly snapshot은 보간하지 않는다.
18. additional event snapshot이 limit 안에서 더 많은 point를 차지해 실제 조회 거리가 짧아지는 것은 허용한다.
19. top-level response는 `generatedAt`, `application`, `instance`, `source`, `horizon`, `points`를 포함한다.
20. `source`는 정확히 `dashboard_snapshots.read_model_json.instanceSummary.items` 또는 동등하게 item path가 드러나는 string이다.
21. `application`은 `projectId`, `applicationId`, `name`, `environment`, `links.dashboard`를 포함한다.
22. `instance`는 `instanceId`, `instanceName`, nullable/known catalog seen timestamps, `links.evidence`를 포함할 수 있다.
23. `horizon`은 effective `since`, `until`, `requestedSince`, `defaultSince=7d`, `maxSince=14d`, effective `limit`, `maxLimit=336`, `order=capturedAt_asc`를 포함한다.
24. `points[]` item은 row metadata `snapshotId`, `capturedAt`, `currentWindowEndUtc`, `storedApplicationStateCode`, nullable `captureReason`을 포함한다.
25. `capturedAt`은 `dashboard_snapshots.generated_at`에서 온다.
26. `currentWindowEndUtc`는 `dashboard_snapshots.current_window_end_utc`에서 온다.
27. `storedApplicationStateCode`는 `dashboard_snapshots.state_code`에서 온 application-level stored state copy다.
28. API와 UI가 `storedApplicationStateCode`를 instance lifecycle state나 health score로 재해석할 수 없도록 response/model Javadoc 또는 Dev Note에 명시한다.
29. `captureReason`은 `dashboard_snapshots.capture_reason` 값을 nullable opaque string으로 복사한다.
30. `captureReason`은 ordering, filtering, marker type, severity, operational event meaning, recovery semantics에 사용하지 않는다.
31. final capture reason enum과 marker mapping은 Story 5.8/5.9 책임으로 남긴다.
32. parser는 `dashboard_snapshots.read_model_json.instanceSummary`만 trend source로 읽는다.
33. parser는 `read_model_json.instances[]`, `read_model_json.snapshot`, current dashboard response generation result를 trend source로 사용하지 않는다.
34. `instanceSummary.schemaVersion`이 없거나 `"1.0"`이 아니면 해당 snapshot은 skip한다.
35. `instanceSummary` 또는 item의 unknown extra field는 무시한다.
36. `instanceSummary.items[]`가 50개를 초과해도 request 전체를 실패시키지 않고 처음 50개 또는 bounded subset만 평가한다.
37. target instance match는 `items[].instanceId` exact UUID string match만 사용한다.
38. item의 `instanceId`가 missing, blank, invalid UUID이면 해당 item만 skip한다.
39. `items[].instanceName`은 display metadata로만 projection하며 matching fallback으로 사용하지 않는다.
40. projected item은 `metricData`, `starterConnection`, nullable `starterPercentilePoint`, nullable/empty `resourceHints`, `applicationTriageContribution`, `endpointEvidenceRefs`를 저장된 값에서만 복사한다.
41. `metricData.statusSource`는 stored `accepted_bucket` axis 의미를 유지한다.
42. `starterConnection.statusSource`는 stored `starter_heartbeat` axis 의미를 유지하고 `stateImpact=none` 의미를 보존한다.
43. `starterPercentilePoint`는 stored single latest point이며 series, average, max, merge, histogram-derived percentile이 아니다.
44. `resourceHints`는 stored latest accepted bucket sample hint이며 state, score, root cause 입력이 아니다.
45. `applicationTriageContribution`은 stored bounded bridge이며 새 rule/confidence/action을 만들지 않는다.
46. `endpointEvidenceRefs`는 bounded reference list만 노출하고 endpoint evidence body를 포함하지 않는다.
47. endpoint ref 허용 field는 `endpointKey`, optional `method`, optional `route`, optional `relatedApplicationPriorityRank`, optional `relatedRuleIds`, optional `snapshotDetailAnchor`로 제한한다.
48. endpoint ref에는 request/error count body, error rate body, duration buckets, baseline buckets, confidence/score/recommended action body, endpoint p95/p99, raw `endpoints_json`, raw path/query/trace/per-request sample을 포함하지 않는다.
49. `read_model_json.instanceSummary.items[]` minimum shape는 Story 5.7 parser contract로 고정한다.
50. Story 5.8 writer는 이 minimum field를 채워야 하며 rename/remove/reinterpret하지 않는다는 handoff note를 story와 model/parser Javadoc 또는 tests에 남긴다.
51. Story 5.8은 backward-compatible optional field만 추가할 수 있고, Story 5.7 parser는 unknown field에 tolerant해야 한다.
52. `InstanceSnapshotTrendService`는 project/application/instance membership lookup repository와 `DashboardSnapshotRepository` read query, JSON parser/projection helper만 사용한다.
53. `InstanceSnapshotTrendService`는 `MetricBucketRepository`를 호출하지 않는다.
54. `InstanceSnapshotTrendService`는 `StarterHeartbeatTelemetryRepository`를 호출하지 않는다.
55. `InstanceSnapshotTrendService`는 `LifecycleStateService`, `TriageSummaryService`, `EndpointPriorityService`를 호출하지 않는다.
56. `InstanceSnapshotTrendService`는 `DashboardReadModelService` 또는 `InstanceEvidenceReadModelService`를 호출하지 않는다.
57. snapshot absence는 live accepted bucket lookup, heartbeat lookup, current dashboard generation, instance evidence generation, synthetic current point creation을 trigger하지 않는다.
58. API는 raw 30초 bucket list, raw snapshot JSON list, endpoint timeseries, arbitrary metric query parameter를 제공하지 않는다.
59. API는 current state, lifecycle state, insight rule, endpoint priority, operational event를 재계산하지 않는다.
60. API는 `previousState`, `lastHealthyAt`, `recoveryMarker`, `recoveredAt`, `lastRecoveredAt`, recovery source priority field를 포함하지 않는다.
61. `dashboard_snapshots` table이 없으면 next Flyway migration으로 생성한다. 현재 migration `V005__create_starter_heartbeat_telemetry.sql`가 이미 있으므로 새 migration은 다음 번호를 사용한다.
62. `dashboard_snapshots`는 최소 column `id`, `project_id`, `application_id`, `generated_at`, `current_window_start_utc`, `current_window_end_utc`, `baseline_window_start_utc`, `baseline_window_end_utc`, nullable `last_accepted_ingest_at`, nullable `last_observed_at`, `state_code`, nullable `capture_reason`, `read_model_json`, `created_at`을 가진다.
63. `read_model_json`은 PostgreSQL `jsonb not null`이고 object check constraint를 가진다.
64. `state_code`는 existing dashboard lifecycle state code 후보만 허용한다.
65. `capture_reason`은 nullable이며 non-null constraint, enum check constraint, marker mapping constraint를 추가하지 않는다.
66. Story 5.7은 `capture_reason` 전용 index나 marker/detail/history helper index를 요구하지 않는다.
67. 기본 index는 application generated time 조회에 필요한 `application_id, generated_at desc` 계열을 포함한다.
68. JPA entity는 API response, controller response DTO, service external return model로 직접 노출하지 않는다.
69. repository는 read-only neutral projection만 제공하며 JSON parsing, state/rule/priority/confidence/action/p95/p99 계산을 하지 않는다.
70. Controller는 query parsing/status mapping/service 위임만 담당하고 repository/ObjectMapper/projection logic을 직접 수행하지 않는다.
71. Response model/record constructor는 required field, bounded collection, non-negative count, max point count, defensive copy를 검증한다.
72. `links.snapshotTrend`가 Story 5.6 instance evidence response에 있다면 Story 5.7 endpoint path로 연결한다. 이 보강은 current evidence semantics를 변경하지 않는다.
73. Existing instance evidence endpoint behavior와 dashboard instance entry behavior는 regression 없이 유지한다.
74. 새 공개 클래스, 공개 메서드, API model, 핵심 helper는 AGENTS.md 지침에 따라 한국어 Javadoc/docstring으로 역할, 사용 맥락, 중요한 입력/반환/제약을 설명한다.
75. Focused tests가 membership, query clamp, missing snapshot empty response, schema skip, exact `instanceId` match, malformed item skip, point ordering, opaque `captureReason`, forbidden dependency guard, response shape, raw field absence를 검증한다.
76. Seeded snapshot projection test는 실제 또는 slice repository에 `read_model_json.instanceSummary.items[]` fixture를 저장하고 target instance trend point가 projection되는지 검증한다.
77. Existing `CatalogSchemaMigrationIntegrationTest`는 새 migration count와 `dashboard_snapshots` existence/constraints/index/comments에 맞게 갱신한다.
78. `./gradlew :observability-portal:test`와 `git diff --check`가 통과한다.

## Tasks/Subtasks

- [x] Dashboard snapshot substrate 생성 (AC: 61~67, 77)
  - [x] 현재 `V005__create_starter_heartbeat_telemetry.sql`가 있으므로 next Flyway version으로 `dashboard_snapshots` migration을 추가한다.
  - [x] base table column, FK, primary key, read_model_json object check, state_code check, Korean table/column comments를 추가한다.
  - [x] nullable `capture_reason`을 추가하되 non-null/enum/check/marker index를 만들지 않는다.
  - [x] application generated time 조회 index와 retention cleanup 후보 index를 추가한다.
  - [x] `CatalogSchemaMigrationIntegrationTest`의 migration count와 `dashboard_snapshots` 존재/constraint/comment 검증을 갱신한다.

- [x] Snapshot read repository 구현 (AC: 14~18, 24~31, 68~69)
  - [x] `domain.snapshot` feature package 아래에 `DashboardSnapshotEntity`와 `DashboardSnapshotRepository` 또는 기존 패턴에 맞는 JPA facade를 추가한다.
  - [x] entity는 `dashboard_snapshots` persistence model로만 사용하고 API/service return model로 노출하지 않는다.
  - [x] repository는 `projectId + applicationId + generatedAt >= effectiveSince` 범위를 newest-first로 읽고 limit 적용이 가능하게 한다.
  - [x] repository projection은 `id`, `generatedAt`, `currentWindowEndUtc`, `stateCode`, nullable `captureReason`, `readModelJson`만 service에 전달한다.
  - [x] repository/JPA method Javadoc에 read-only projection이며 state/rule/priority/p95/p99/marker semantics를 계산하지 않는다고 명시한다.

- [x] Instance snapshot trend response model 확정 (AC: 19~31, 40~48, 60, 71, 74)
  - [x] `domain.instance.model` 또는 `domain.snapshot.model`에 `InstanceSnapshotTrendReadModel` typed record/class를 추가한다.
  - [x] top-level `generatedAt`, `application`, `instance`, `source`, `horizon`, `points`를 고정한다.
  - [x] point model에 row metadata와 stored item blocks만 담고 recovery/current/state recalculation field를 제외한다.
  - [x] endpoint evidence refs는 reference-only shape로 제한한다.
  - [x] constructors에서 max point count, defensive copy, required values, bounded strings를 검증한다.
  - [x] 공개 record/class/method에 한국어 Javadoc을 작성한다.

- [x] `instanceSummary` parser/projection helper 구현 (AC: 32~51, 74~76)
  - [x] JSON path를 `read_model_json.instanceSummary.items[]`로 고정한다.
  - [x] `schemaVersion="1.0"`만 projection하고 missing/unsupported schema는 snapshot skip으로 처리한다.
  - [x] unknown field는 ignore한다.
  - [x] item cap 50을 초과해도 request 실패 없이 bounded subset만 평가한다.
  - [x] `items[].instanceId` exact UUID match만 사용하고 malformed item은 item skip으로 처리한다.
  - [x] `metricData`, `starterConnection`, `starterPercentilePoint`, `resourceHints`, `applicationTriageContribution`, `endpointEvidenceRefs`를 stored value에서 bounded model로 옮긴다.
  - [x] parser test에서 missing path, unsupported schema, invalid UUID, target 없는 snapshot, unknown extra field, cap 초과, malformed endpoint ref를 검증한다.

- [x] `InstanceSnapshotTrendService` 구현 (AC: 4~18, 24~31, 52~60)
  - [x] `ApplicationRepository.findByIdAndProjectId`로 project/application membership을 검증한다.
  - [x] `ApplicationInstanceRepository.findByIdAndApplicationId`로 instance UUID membership을 검증한다.
  - [x] membership 실패는 `Optional.empty()` 또는 service-level not found 결과로 수렴해 controller가 404로 매핑하게 한다.
  - [x] `since`/`limit` effective horizon과 retention clamp를 계산한다.
  - [x] snapshot rows를 repository에서 읽고 parser로 target instance point만 projection한다.
  - [x] zero point는 error가 아니라 empty points response로 반환한다.
  - [x] selected points를 `capturedAt ASC`, `snapshotId ASC`로 정렬한다.
  - [x] service constructor/import/test에서 forbidden dependency가 들어오지 않게 한다.

- [x] Controller/API boundary 구현 (AC: 1~13, 19~23, 70)
  - [x] `InstanceSnapshotTrendController` 후보를 `domain.instance.controller` 또는 일관된 feature package에 추가한다.
  - [x] route는 `/api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/snapshot-trend`로 고정한다.
  - [x] controller는 UUID path variable, `since`, `limit` query parameter를 service input으로 전달한다.
  - [x] invalid query는 `400`, membership failure는 `404`, missing snapshot은 `200 + points=[]`로 검증한다.
  - [x] controller에서 repository/ObjectMapper/parser/current read model service를 직접 호출하지 않는다.

- [x] Story 5.6 link handoff 보강 (AC: 72~73)
  - [x] `InstanceEvidenceReadModel.Links.snapshotTrend`가 현재 `null`인 경우, Story 5.7 endpoint path helper로 채울지 결정한다.
  - [x] 보강한다면 기존 evidence response field 의미와 current evidence 계산을 변경하지 않는다.
  - [x] evidence controller/service tests에서 snapshot trend link가 UUID path를 사용하고 `instanceName`을 사용하지 않음을 검증한다.

- [x] Scope guard와 architecture guard 확인 (AC: 52~60, 68~70, 74)
  - [x] accepted bucket, heartbeat, lifecycle, triage, endpoint priority, dashboard current, instance evidence service dependency가 없는지 focused test 또는 ArchUnit guard로 확인한다.
  - [x] raw bucket explorer, raw snapshot JSON list, endpoint timeseries, recovery marker/detail/history endpoint가 추가되지 않았음을 확인한다.
  - [x] no `application`, `port`, `adapter` package guard를 유지한다.
  - [x] JPA entity를 API response로 반환하지 않는다.

- [x] Focused tests와 regression 실행 (AC: 75~78)
  - [x] `InstanceSnapshotTrendReadModelShapeTest` 또는 동등 model test를 추가한다.
  - [x] `InstanceSnapshotTrendParserTest` 또는 service focused test로 instanceSummary parsing을 검증한다.
  - [x] `InstanceSnapshotTrendServiceTest`로 membership, horizon/limit clamp, missing snapshot empty response, ordering, forbidden dependency guard를 검증한다.
  - [x] `InstanceSnapshotTrendControllerTest`로 serialization, query validation, 404/200 mapping을 검증한다.
  - [x] `DashboardSnapshotRepositoryIntegrationTest` 또는 seeded projection integration test로 PostgreSQL JSONB/Flyway repository read를 검증한다.
  - [x] `CatalogSchemaMigrationIntegrationTest` regression을 갱신/실행한다.
  - [x] `MvcLayerBoundaryTest`를 실행한다.
  - [x] `./gradlew :observability-portal:test`와 `git diff --check`를 실행한다.

## Dev Notes

### Contract Priority

- 최우선 source는 `implementation-artifacts/spec-story-5-7-instance-snapshot-trend-contract-decisions.md`다.
- `api-surface.md`와 `read-model-contract.md`의 rough shape에서 `instanceId`가 string처럼 보이는 예시는 Story 5.7 결정 문서가 덮어쓴다. 실제 path/match identity는 `application_instances.id` UUID다.
- `database-schema.md`에는 `V005__create_dashboard_snapshots.sql` 예시가 있지만 현재 production migration에는 `V005__create_starter_heartbeat_telemetry.sql`가 존재한다. 구현 시 next Flyway version을 사용한다.
- Story 5.7은 snapshot source substrate를 소유하지만 writer/capture/marker/detail/recovery/history semantics는 닫지 않는다.

### Suggested API Shape

```http
GET /api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/snapshot-trend?since=7d&limit=168
Accept: application/json
```

```json
{
  "generatedAt": "2026-05-26T08:10:35Z",
  "application": {
    "projectId": "00000000-0000-0000-0000-000000005201",
    "applicationId": "00000000-0000-0000-0000-000000005211",
    "name": "orders-api",
    "environment": "prod",
    "links": {
      "dashboard": "/api/projects/{projectId}/applications/{applicationId}/dashboard"
    }
  },
  "instance": {
    "instanceId": "00000000-0000-0000-0000-000000005221",
    "instanceName": "orders-api-7f9c9c8c9d-x2p4k",
    "links": {
      "evidence": "/api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/evidence"
    }
  },
  "source": "dashboard_snapshots.read_model_json.instanceSummary.items",
  "horizon": {
    "since": "2026-05-19T08:10:35Z",
    "until": "2026-05-26T08:10:35Z",
    "requestedSince": "7d",
    "defaultSince": "7d",
    "maxSince": "14d",
    "limit": 168,
    "maxLimit": 336,
    "order": "capturedAt_asc"
  },
  "points": [
    {
      "snapshotId": "00000000-0000-0000-0000-000000005701",
      "capturedAt": "2026-05-26T08:00:00Z",
      "currentWindowEndUtc": "2026-05-26T08:00:00Z",
      "storedApplicationStateCode": "active",
      "captureReason": "hourly_scheduled",
      "instanceName": "orders-api-7f9c9c8c9d-x2p4k",
      "observationStatus": "observed",
      "metricData": {
        "statusSource": "accepted_bucket",
        "lastAcceptedBucketAt": "2026-05-26T07:59:30Z",
        "freshnessLabel": "current"
      },
      "starterConnection": {
        "statusSource": "starter_heartbeat",
        "lastHeartbeatAt": "2026-05-26T07:59:45Z",
        "lastHeartbeatStatus": "received",
        "connectionMeaning": "starter_connected",
        "stateImpact": "none"
      },
      "starterPercentilePoint": {
        "source": "starter_canonical_percentile",
        "scope": "instance_bucket",
        "bucketStartUtc": "2026-05-26T07:59:00Z",
        "bucketEndUtc": "2026-05-26T07:59:30Z",
        "requestCount": 820,
        "p95Ms": 210,
        "p99Ms": 360
      },
      "resourceHints": {
        "source": "accepted_bucket_latest_sample",
        "status": "available",
        "bucketEndUtc": "2026-05-26T07:59:30Z",
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
  ]
}
```

Shape 이름은 implementation 중 record/class 이름에 맞춰 조정할 수 있다. 단 source path, UUID identity, stored-only projection, bounded fields, no raw/no recalculation/no recovery semantics 계약은 유지해야 한다.

### Minimum Stored Instance Summary Shape

Story 5.7 parser가 의존하는 minimum shape는 아래 의미를 유지한다.

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

Story 5.8 writer must fill this block when snapshots are saved. It must not rename, remove, or reinterpret these minimum fields. Optional backward-compatible fields are allowed.

### Current Code State

- Active baseline은 Traditional MVC + Service/Repository Layering이다.
- `domain.snapshot.repository` package는 현재 package marker만 있다. Story 5.7은 이 package를 read-side snapshot repository로 실제화할 수 있다.
- 현재 Flyway migrations는 `V001`~`V005__create_starter_heartbeat_telemetry.sql`까지 존재한다. `dashboard_snapshots` table은 아직 없다.
- `CatalogSchemaMigrationIntegrationTest.appliesMigrationsToCleanDatabase()`는 현재 migration count 5와 `dashboard_snapshots=false`를 기대한다. Story 5.7 구현 시 갱신이 필요하다.
- `ApplicationRepository.findByIdAndProjectId`는 project/application membership 확인에 이미 있다.
- `ApplicationInstanceRepository.findByIdAndApplicationId`는 Story 5.6에서 추가되어 instance UUID membership 검증에 사용할 수 있다.
- `ApplicationEntity`는 `id()`, `projectId()`, `name()`, `environment()` accessor를 제공한다.
- `ApplicationInstanceEntity`는 `id()`, `applicationId()`, `instanceName()`, `firstSeenAt()`, `lastSeenAt()` accessor를 제공한다.
- `InstanceEvidenceController` route는 `/api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/evidence`다.
- `InstanceEvidenceReadModel.Links.snapshotTrend`는 후속 story 전까지 null을 허용하는 field로 남아 있다.
- `InstanceEvidenceReadModelService.evidenceLink(...)`는 evidence link helper로 존재한다. Story 5.7은 비슷한 snapshot trend link helper를 만들 수 있다.
- `InstanceEvidenceReadModelService`는 current evidence를 만들기 위해 accepted bucket, heartbeat, lifecycle, triage, endpoint priority service를 호출한다. Story 5.7 service는 이 service를 호출하거나 같은 dependencies를 끌어오지 않는다.
- `DashboardReadModelService`는 dashboard `instances` entry를 최대 50개 navigation hint로 제공한다. 이 live navigation list는 trend source가 아니다.
- `MvcLayerBoundaryTest`는 controller -> service -> repository, repository no controller/dto, service no controller/dto, no hexagonal package guard를 이미 포함한다.

### Existing Code / Documents To Reuse

- `_bmad/custom/project-context.md`
  - Traditional MVC + Service/Repository Layering, feature-first MVC, Spring Data JPA/Flyway 기준을 따른다.
- `implementation-artifacts/spec-story-5-7-instance-snapshot-trend-contract-decisions.md`
  - Story 5.7 source of truth다.
- `implementation-artifacts/spec-story-5-6-instance-evidence-contract-decisions.md`
  - Story 5.6 current evidence field 의미와 snapshot/trend handoff를 확인한다.
- `planning-artifacts/epics.md`
  - Epic 5는 server-computed dashboard read model/API epic이며 UI는 state/rule/p95/endpoint priority/history event를 재계산하지 않는다.
- `planning-artifacts/sprint-plan.md`
  - Epic 5/6 flow는 Project -> Application -> Dashboard -> Instance Evidence -> Instance Snapshot Trend -> Snapshot/History다.
- `planning-artifacts/contracts/read-model-contract.md`
  - instance snapshot trend candidate, bounded horizon/limit, no raw/no recalculation guard를 따른다.
- `planning-artifacts/contracts/operational-event-history.md`
  - instance snapshot trend는 operational event history와 별도 projection이며 event dedup/suppression을 수행하지 않는다.
- `planning-artifacts/api-surface.md`
  - endpoint path, query defaults, status mapping, raw explorer 금지 경계를 따른다.
- `planning-artifacts/database-schema.md`
  - `dashboard_snapshots` base table column과 index/comment 후보를 참고하되 migration 번호와 nullable `capture_reason`은 Story 5.7 결정 문서를 우선한다.
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/repository/ApplicationRepository.java`
  - project/application membership lookup을 재사용한다.
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/repository/ApplicationInstanceRepository.java`
  - instance UUID membership lookup을 재사용한다.
- `observability-portal/src/main/java/com/observation/portal/domain/instance/controller/InstanceEvidenceController.java`
  - sibling instance-scoped controller style과 404 mapping pattern을 참고한다.
- `observability-portal/src/main/java/com/observation/portal/domain/instance/model/InstanceEvidenceReadModel.java`
  - record validation, Korean Javadoc, bounded model pattern을 참고한다.
- `observability-portal/src/test/java/com/observation/portal/domain/catalog/repository/CatalogSchemaMigrationIntegrationTest.java`
  - Flyway/PostgreSQL/Testcontainers schema integration style을 재사용한다.
- `observability-portal/src/test/java/com/observation/portal/architecture/MvcLayerBoundaryTest.java`
  - MVC layer and forbidden package guard를 유지한다.

### Previous Story Intelligence

- Story 5.6은 current instance evidence read model을 완성했고 snapshot persistence/query/runtime dependency를 의도적으로 제외했다.
- Story 5.6은 `links.snapshotTrend`를 후속 endpoint 후보로 남겼다. Story 5.7은 endpoint 준비 후 이 link를 채울 수 있지만 current evidence 계산을 변경하지 않는다.
- Story 5.6 field 의미 중 `metricData`, `starterConnection`, `starterPercentilePoint`, `resourceHints`, `applicationTriageContribution`, `endpointEvidenceRefs`의 source axis는 Story 5.7 stored summary에서 재사용한다.
- Story 5.6은 `application_instances.id` UUID path identity를 고정했다. Story 5.7도 같은 UUID identity를 유지한다.
- Story 5.5/5.6 endpoint evidence는 raw path/query/trace/per-request sample과 endpoint p95/p99를 금지했다. Story 5.7 endpoint evidence refs도 body가 아니라 reference-only로 유지한다.
- Story 5.4 recovery는 current accepted bucket만으로 `lastHealthyAt`을 추론하지 않는 원칙을 세웠다. Story 5.7은 recovery field 자체를 projection하지 않는다.

### Architecture Constraints

- Portal package는 feature-first MVC 구조를 따른다.
- 이 프로젝트에서 `domain`은 business feature grouping namespace이며 pure DDD domain layer가 아니다.
- Controller는 service를 호출하고 repository를 직접 호출하지 않는다.
- Service는 빠른 MVC 구현을 위해 Spring Data repository와 JPA entity를 직접 사용할 수 있지만, JPA entity를 external return model로 노출하지 않는다.
- Repository는 Spring Data JPA/Jakarta Persistence + Hibernate 기반 read-only 조회만 담당한다.
- Flyway SQL migration이 schema source of truth다. Hibernate DDL auto create/update는 사용하지 않는다.
- DB view, materialized view, trigger, stored procedure에 state, rule, priority, confidence, action, p95/p99 계산을 숨기지 않는다.
- `application`, `port`, `adapter`, `adapter.in`, `adapter.out` package를 새로 만들지 않는다.
- raw project key, access token, refresh token, GitHub OAuth token, provider raw payload, secret은 response/log/error에 노출하지 않는다.
- 새 공개 클래스/메서드/API/핵심 helper의 Javadoc/comment는 AGENTS.md 지침에 따라 한국어로 작성한다.

### Developer Guardrails

- `dashboard_snapshots.read_model_json.instanceSummary.items[]`만 trend source다.
- `read_model_json.instances[]`와 dashboard current `instances` entry는 navigation hint이며 trend source가 아니다.
- `instanceName`은 표시용이며 matching fallback이 아니다.
- missing snapshot은 empty result다. 404로 바꾸지 않는다.
- malformed stored item은 해당 item skip이다. 전체 request 실패로 키우지 않는다.
- stored snapshot projection 중 live accepted bucket/heartbeat/current dashboard/current evidence를 조회하지 않는다.
- `captureReason`은 opaque metadata다. marker type/severity/event/recovery 의미로 해석하지 않는다.
- `storedApplicationStateCode`는 application-level stored state copy다. instance state나 health score가 아니다.
- `starterPercentilePoint`는 single latest stored point다. series/average/max/merge/histogram-derived percentile을 만들지 않는다.
- `endpointEvidenceRefs`는 reference-only다. endpoint evidence body나 endpoint timeseries가 아니다.
- `previousState`, `lastHealthyAt`, `recoveryMarker`, `lastRecoveredAt` 같은 recovery/history field를 끌어오지 않는다.
- snapshot writer/scheduler/capture policy/detail/marker/history를 "테스트용"으로라도 구현하지 않는다.
- Story 5.8이 minimum `instanceSummary` shape를 깨지 않도록 parser test와 Dev Note에 handoff를 남긴다.

### Test Expectations

Focused test 대상 후보:

- `InstanceSnapshotTrendReadModelShapeTest`
- `InstanceSnapshotTrendParserTest`
- `InstanceSnapshotTrendServiceTest`
- `InstanceSnapshotTrendControllerTest`
- `DashboardSnapshotRepositoryIntegrationTest`
- `CatalogSchemaMigrationIntegrationTest`
- `InstanceEvidenceReadModelServiceTest` 또는 controller test의 `snapshotTrend` link regression
- `MvcLayerBoundaryTest`

필수 scenario:

- endpoint는 UUID path와 query params를 service에 위임한다.
- invalid `since`는 400이다.
- omitted `since`/`limit`는 `7d`/`168`이다.
- `limit > 336`은 336으로 clamp한다.
- project/application/instance membership mismatch는 404다.
- horizon 안 snapshot row가 없으면 200과 `points=[]`다.
- `instanceSummary` missing 또는 unsupported schema는 skip된다.
- target `instanceId`가 없는 snapshot은 skip된다.
- item `instanceId`가 malformed이면 item만 skip된다.
- `instanceName`이 같아도 UUID가 다르면 match하지 않는다.
- unknown extra field가 있어도 parser가 실패하지 않는다.
- 50개 초과 item fixture도 request 실패 없이 bounded projection된다.
- projected point는 row metadata와 stored item block만 담는다.
- response는 `capturedAt ASC`, `snapshotId ASC`다.
- `captureReason` unknown/null value를 그대로 복사하고 filter/order/marker 의미로 쓰지 않는다.
- raw snapshot JSON, raw bucket JSON, raw endpoint JSON, endpoint p95/p99, recovery fields가 response에 없다.
- service dependency list에 accepted bucket/heartbeat/lifecycle/triage/endpoint priority/dashboard/current instance evidence service가 없다.
- repository integration test가 JSONB `read_model_json.instanceSummary.items[]` fixture를 seed하고 projection query를 검증한다.
- Flyway migration test가 `dashboard_snapshots` table, constraints, indexes, Korean comments를 확인한다.

Suggested commands:

```bash
./gradlew :observability-portal:test --tests '*InstanceSnapshotTrend*'
./gradlew :observability-portal:test --tests '*DashboardSnapshotRepository*'
./gradlew :observability-portal:test --tests com.observation.portal.domain.catalog.repository.CatalogSchemaMigrationIntegrationTest
./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest
./gradlew :observability-portal:test
git diff --check
```

## Dev Agent Record

### Implementation Plan

- `V006__create_dashboard_snapshots.sql`로 Story 5.7 read-side snapshot substrate를 추가하고, `capture_reason`은 nullable opaque metadata로 유지했다.
- `domain.snapshot`에 dashboard snapshot JPA entity/repository facade와 row projection을 추가해 newest-first bounded read만 제공했다.
- `InstanceSnapshotTrendReadModel`, `InstanceSnapshotTrendParser`, `InstanceSnapshotTrendService`, `InstanceSnapshotTrendController`를 추가해 stored `read_model_json.instanceSummary.items[]`만 projection하도록 구현했다.
- Story 5.6 `links.snapshotTrend`는 UUID path 기반 Story 5.7 endpoint로 연결하되 current evidence 계산 dependency나 의미는 변경하지 않았다.

### Debug Log

- `CatalogSchemaMigrationIntegrationTest` 첫 실행은 Docker/Testcontainers 초기화 전 상태에서 실패했고, Docker 기동 후 migration/repository integration tests는 통과했다.
- `DashboardSnapshotRepositoryIntegrationTest`, `InstanceSnapshotTrendReadModelShapeTest`, `InstanceSnapshotTrendParserTest`, `InstanceSnapshotTrendServiceTest`, `InstanceSnapshotTrendControllerTest`를 red-green 순서로 추가/통과시켰다.

### Completion Notes

- Trend source를 `dashboard_snapshots.read_model_json.instanceSummary.items[]`로 고정하고 `instances[]`, `snapshot`, current dashboard/evidence service를 trend source로 사용하지 않게 했다.
- `{instanceId}`는 `application_instances.id` UUID exact string match만 허용하며, `instanceName` fallback lookup은 만들지 않았다.
- Missing snapshot/source/item/unsupported schema/malformed item은 `points=[]`로 수렴하고, membership failure만 `404`로 매핑된다.
- `captureReason`은 row value를 nullable opaque string으로 복사할 뿐 ordering/filtering/marker/event/recovery 의미로 쓰지 않는다.
- Story 5.8 writer handoff: `InstanceSnapshotTrendParser` Javadoc과 parser tests가 `instanceSummary.items[]` minimum shape를 고정하며, Story 5.8은 이 path/field를 rename, remove, reinterpret하지 않아야 한다.

## File List

- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/stories/5-7-instance-snapshot-trend-projection.md`
- `observability-portal/src/main/resources/db/migration/V006__create_dashboard_snapshots.sql`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/entity/DashboardSnapshotEntity.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotTrendRow.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/repository/DashboardSnapshotJpaRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/repository/DashboardSnapshotRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/instance/controller/InstanceSnapshotTrendController.java`
- `observability-portal/src/main/java/com/observation/portal/domain/instance/model/InstanceSnapshotTrendReadModel.java`
- `observability-portal/src/main/java/com/observation/portal/domain/instance/service/InstanceSnapshotTrendParser.java`
- `observability-portal/src/main/java/com/observation/portal/domain/instance/service/InstanceSnapshotTrendService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/instance/service/InvalidSnapshotTrendQueryException.java`
- `observability-portal/src/main/java/com/observation/portal/domain/instance/service/InstanceEvidenceReadModelService.java`
- `observability-portal/src/test/java/com/observation/portal/domain/catalog/repository/CatalogSchemaMigrationIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/repository/DashboardSnapshotRepositoryIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/instance/controller/InstanceSnapshotTrendControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/instance/model/InstanceSnapshotTrendReadModelShapeTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/instance/service/InstanceSnapshotTrendParserTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/instance/service/InstanceSnapshotTrendServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/instance/controller/InstanceEvidenceControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/instance/model/InstanceEvidenceReadModelShapeTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/instance/service/InstanceEvidenceReadModelServiceTest.java`

## Change Log

- 2026-05-27: Story 5.7 instance snapshot trend projection 구현, migration/repository/parser/service/controller/tests 추가, Story 5.6 snapshotTrend link 보강.

## Status

review
