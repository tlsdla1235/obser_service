---
artifactType: story
storyId: "6.7"
storyKey: "6-7-snapshot-history-marker-ui-and-deep-link"
epic: "Epic 6. Dashboard User Flow and Demo Hardening"
title: "Snapshot/history marker UI and deep link"
architectureStyle: Traditional MVC
status: done
date: 2026-05-31
baseline_commit: f880a125934c8316cdc9e973cb1a266626dd5dd6
---

# Story 6.7 - Snapshot/history marker UI and deep link

## Status

done

## Story

portal 사용자는 Application Dashboard와 Instance Snapshot Trend 흐름에서 최근 snapshot/history marker timeline을 보고, 관심 있는 marker나 operational event에서 stored snapshot detail로 들어가고 싶다.

그래야 raw snapshot explorer, arbitrary time-series query, current state 재판정 없이 저장된 `dashboard_snapshots` 기반 운영 맥락, event period, marker 의미, endpoint evidence anchor를 같은 static dashboard runtime 안에서 확인할 수 있다.

## Source of Truth

아래 문서를 읽고 반영한 BMAD create-story context다. 구현 중 충돌처럼 보이는 지점은 Story 5.8-b snapshot detail/marker 계약, Story 5.9-b operational event history 계약, current code/API shape 순서로 우선한다. `implementation-artifacts/sprint-status.yaml`은 story tracking source이며 기능/API 계약을 override하지 않는다.

1. `implementation-artifacts/sprint-status.yaml`
2. `implementation-artifacts/epic-6-context.md`
3. `planning-artifacts/epics.md`
4. `planning-artifacts/sprint-plan.md`
5. `planning-artifacts/current-product-source-of-truth.md`
6. `planning-artifacts/stories/6-6-instance-snapshot-trend-ui.md`
7. `planning-artifacts/stories/5-8-b-snapshot-marker-detail-recovery-source.md`
8. `planning-artifacts/stories/5-9-a-operational-event-history-api-skeleton-and-source-boundary.md`
9. `planning-artifacts/stories/5-9-b-operational-event-promotion-suppression-and-period-folding.md`
10. `implementation-artifacts/spec-story-5-8-b-snapshot-marker-detail-recovery-contract-decisions.md`
11. `implementation-artifacts/spec-story-5-9-operational-event-history-api-contract-decisions.md`
12. `planning-artifacts/contracts/operational-event-history.md`
13. `planning-artifacts/contracts/read-model-contract.md`
14. `planning-artifacts/api-surface.md`
15. `planning-artifacts/acceptance-traceability.md`
16. `planning-artifacts/project-structure.md`
17. `observability-portal/src/main/java/com/observation/portal/domain/snapshot/controller/DashboardSnapshotController.java`
18. `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotMarkerReadModel.java`
19. `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotDetailReadModel.java`
20. `observability-portal/src/main/java/com/observation/portal/domain/history/controller/OperationalEventHistoryController.java`
21. `observability-portal/src/main/java/com/observation/portal/domain/history/model/OperationalEventHistoryReadModel.java`
22. `observability-portal/src/main/java/com/observation/portal/domain/history/model/OperationalEventItem.java`
23. `observability-portal/src/main/resources/static/dashboard/`
24. `observability-portal/src/test/java/com/observation/portal/domain/dashboard/InstanceSnapshotTrendUiContractTest.java`
25. `observability-portal/src/test/java/com/observation/portal/domain/dashboard/ApplicationDashboardUiContractTest.java`
26. `AGENTS.md`
27. `_bmad/custom/project-context.md`

`planning-artifacts/contracts/dashboard-read-model.md`는 compatibility note이며 새 구현과 테스트의 상세 계약은 `planning-artifacts/contracts/read-model-contract.md`, `operational-event-history.md`, Story 5.8-b/5.9-b 산출물을 따른다.

## Scope / Out of Scope

포함:

- 기존 static dashboard runtime의 `dashboard-detail` 영역을 Snapshot/History mode와 Snapshot Detail mode로 전환하는 UI state
- Application Dashboard ready view에서 current selected Project/Application 기준 Snapshot/History action 활성화
- Instance Snapshot Trend ready view의 stored point 또는 endpoint evidence ref에서 snapshot detail deep link action 제공
- Authenticated fixed history fetch:
  - `GET /api/projects/{projectId}/applications/{applicationId}/operational-events?since=24h&limit=50`
  - `GET /api/projects/{projectId}/applications/{applicationId}/dashboard/snapshot-markers?since=24h&limit=50`
  - `7d`, `14d` fixed preset에 대해서만 bounded query 확장
- Snapshot marker timeline과 operational event feed를 같은 Snapshot/History mode에서 표시
- Event/marker `links.snapshot` 또는 validated selected context + trend point `snapshotId`로 snapshot detail fetch 수행
- Snapshot detail ready view에서 read semantics, snapshot metadata, marker, previousState, lastHealthyAt, recoveryMarker, bounded readModel summary, snapshotEndpointEvidence, instanceSummary를 typed/bounded UI로 표시
- Event evidence `snapshotDetailAnchor` 또는 trend ref `snapshotDetailAnchor`가 있으면 detail view 안에서 해당 anchor를 보존하거나 highlighted anchor state로 표시
- `auth-required`, `invalid-link`, `bad-query`, `not-found-or-expired`, `error`, `ready`, `empty` safe state
- `RecentHistoryUiContractTest` 신규 추가 또는 기존 static UI contract test 확장
- 6.6의 broad no snapshot/history fetch guard를 6.7 허용 범위에 맞춰 좁히되 raw explorer, endpoint timeseries, operational event 재계산 fetch 금지선 유지

제외:

- Backend API/schema/migration/read model 확장. `DashboardSnapshotController`, `OperationalEventHistoryController`의 public contract를 바꾸지 않는다.
- `ApplicationDashboardReadModel`, `InstanceSnapshotTrendReadModel`, `DashboardSnapshotMarkerReadModel`, `OperationalEventHistoryReadModel`, `DashboardSnapshotDetailReadModel` public response shape 변경
- Snapshot marker/event type, severity, `resolvedAt`, suppression, period folding, anchor resolution을 UI에서 재계산
- `operational_events` table/repository/entity, endpoint timeseries table/API, materialized view, Redis/outbox, 새 Flyway migration
- raw snapshot JSON explorer, raw bucket explorer, arbitrary metric/time-series query UI
- alert delivery log, incident acknowledgement, owner assignment, comment workflow, Discord/notification UI
- browser route, URL query/fragment state, SPA routing, direct API JSON navigation
- React, Vite, TypeScript, `package.json`, `src/main/frontend`, 별도 frontend build/deploy
- browser token persistence, localStorage/sessionStorage/cookie token 저장, URL token parsing, refresh token browser storage
- lifecycle state, starter diagnosis, instance/application health score, rule, p95/p99, endpoint priority, marker/event/recovery meaning 재계산

## Acceptance Criteria

1. Application Dashboard ready view에는 Snapshot/History action이 표시되고, action은 같은 `dashboard-detail` 영역을 Snapshot/History mode로 전환한다.
2. Snapshot/History는 별도 modal, browser route, URL query/fragment state, inline raw expansion, 새 상시 panel로 구현하지 않는다.
3. Snapshot/History header에는 selected Project/Application identity, generatedAt 또는 조회 기준 시각, Application Dashboard로 돌아가는 action을 항상 표시한다.
4. Application Dashboard back action은 `loadedDashboard`를 다시 렌더링하고 dashboard API를 다시 fetch하지 않는다.
5. Project/Application 재선택, token clear, dashboard reload, Application List reload가 발생하면 history/detail state와 pending request는 폐기된다.
6. Snapshot/History default preset은 `24h`이며 operational event query는 `since=24h&limit=50`, marker query는 `since=24h&limit=50`만 사용한다.
7. `7d` preset은 operational event query `since=7d&limit=100`, marker query `since=7d&limit=168`을 사용한다.
8. `14d` preset은 operational event query `since=14d&limit=100`, marker query `since=14d&limit=336`을 사용한다.
9. History preset은 `24h`, `7d`, `14d` fixed buttons만 제공한다. custom date picker, raw query editor, arbitrary user input은 만들지 않는다.
10. History endpoint helper는 현재 selected Project/Application id와 validated dashboard context가 있을 때만 canonical internal path를 만든다.
11. UI는 DOM dataset이나 user input에서 받은 arbitrary history URL을 fetch하지 않는다.
12. History fetch는 in-memory access token으로 `Authorization: Bearer <access_token>` header를 붙인다.
13. Token이 없으면 API call을 만들지 않고 `auth-required` safe state를 표시한다.
14. `401`은 auth-required 흐름으로 수렴하고 stale response가 현재 화면을 덮지 않는다.
15. `400`은 fixed query contract mismatch로 처리하며 backend error body를 노출하지 않는다.
16. `404`는 project/application scope mismatch로 표현하고 application down, deleted application, 문제가 없음을 단정하지 않는다.
17. Generic error는 backend detail, stack trace, token, provider raw payload 없이 다시 시도 copy만 표시한다.
18. Operational event response source는 `dashboard_snapshots`여야 하고, marker response source도 `dashboard_snapshots`여야 한다.
19. Operational event response `applicationId`와 marker response `applicationId`는 selected Application id와 일치해야 한다.
20. Operational event horizon은 `defaultSince=24h`, `maxSince=14d`, `maxLimit=100`, `order=occurredAt_desc`를 표시 또는 static contract guard에서 보존한다.
21. Marker horizon은 `defaultSince=24h`, `maxSince=14d`, `maxLimit=336`, `order=capturedAt_asc`를 표시 또는 static contract guard에서 보존한다.
22. Operational events는 server-provided `occurredAt DESC`, `eventId ASC` order를 보존하고 UI가 의미를 바꾸는 재정렬을 하지 않는다.
23. Markers는 server-provided `capturedAt ASC`, `snapshotId ASC` order를 보존하고 UI가 의미를 바꾸는 재정렬을 하지 않는다.
24. `events=[]`와 `markers=[]`는 retention/source absence 또는 event 후보 없음으로 표현하고 "현재 문제 없음", "정상", "복구 완료"로 표현하지 않는다.
25. Operational event item은 `eventId`, `type`, `severity`, `title`, `summary`, `occurredAt`, nullable `resolvedAt`, `stateCode`, nullable `confidence`, `snapshotId`, `evidence`, `links.snapshot`만 소비한다.
26. UI는 `scheduled_snapshot`, `query_fallback_snapshot`, `stored_snapshot`, 단순 `state_observation`을 operational event로 새로 승격하지 않는다.
27. UI는 `short_strong_spike`를 별도 event type으로 만들지 않는다.
28. Event severity는 server-provided value를 표시만 하고 marker severity나 confidence 값으로 다시 계산하지 않는다.
29. Event copy는 server-provided title/summary를 escape해서 표시하고, UI가 root cause/recommended action을 새로 만들지 않는다.
30. Event `resolvedAt`은 nullable stored period field로만 표시한다. `resolvedAt`이 있어도 "복구 완료", "장애 해결 완료", "앱 정상 확정" copy를 만들지 않는다.
31. Event evidence는 bounded field만 표시한다: `ruleId`, `endpointKey`, optional `method`, optional `route`, optional `snapshotDetailAnchor`, `anchorStatus`.
32. Event evidence는 raw snapshot JSON, raw bucket list, raw endpoint JSON, endpoint p95/p99, trace id, per-request sample, query string, query key/value를 표시하지 않는다.
33. Marker item은 `markerId`, `snapshotId`, `capturedAt`, `currentWindowEndUtc`, `type`, `severity`, `readMeaning`, `captureReason`, `storedApplicationStateCode`, `previousState`, title/summary/recommendedAction, helper confidence/rule/endpoint field, `links.snapshot`만 소비한다.
34. Marker `readMeaning=stored_read_model_point`를 표시 또는 static contract guard에서 보존한다.
35. Marker type은 server-provided enum만 표시한다. UI가 `captureReason`에서 marker type을 분류하지 않는다.
36. Marker severity는 server-provided enum만 표시한다. UI가 stored state, confidence, capture reason으로 severity를 재계산하지 않는다.
37. `captureReason`은 persisted opaque metadata로 표시하고 event/recovery/severity 의미로 해석하지 않는다.
38. Recovery marker copy는 "회복 관찰 중" 계열만 허용하며 "복구 완료", "장애 해결", host application down/process down 확정을 말하지 않는다.
39. Operational event와 marker action은 `links.snapshot`이 내부 `/api/projects/{projectId}/applications/{applicationId}/dashboard/snapshots/{snapshotId}` shape이고 현재 selected Project/Application id와 일치할 때만 detail fetch를 수행한다.
40. Trend point detail action은 selected Project/Application context와 point `snapshotId`가 있을 때만 canonical snapshot detail path를 만들고, arbitrary path/user input을 사용하지 않는다.
41. Invalid snapshot detail link 또는 invalid snapshot id는 API call 없이 `invalid-link` safe state 또는 bounded invalid detail state로 표시한다.
42. Snapshot detail fetch는 in-memory access token으로 `Authorization: Bearer <access_token>` header를 붙인다.
43. Snapshot detail `400 invalid_snapshot_id`는 invalid-link/bad-detail state로 처리하고 backend body를 노출하지 않는다.
44. Snapshot detail `404 snapshot_not_found_or_expired`는 retention/detail absence copy로 표시하고 current dashboard fallback을 만들지 않는다.
45. Snapshot detail generic error는 backend detail, stack trace, token, provider raw payload 없이 다시 시도 copy만 표시한다.
46. Snapshot detail response source는 `dashboard_snapshots`여야 한다.
47. Snapshot detail `readSemantics.mode=stored_snapshot_detail`, `currentStateRecalculated=false`, `liveSourcesJoined=[]`, `rawReadModelJsonExposed=false`를 화면 또는 static contract guard에서 보존한다.
48. Snapshot detail `snapshot.snapshotId`는 requested snapshot id와 일치하고, `links.self`는 현재 selected Project/Application/Snapshot id와 일치하는 internal path여야 한다.
49. Snapshot detail ready view는 raw `read_model_json` 전체 dump를 표시하지 않는다.
50. Snapshot detail ready view는 bounded top-level summary만 표시한다: snapshot metadata, marker, previousState, lastHealthyAt, recoveryMarker, readModel block presence/source labels, snapshotEndpointEvidence, instanceSummary.
51. `snapshotEndpointEvidence.items[]`는 stored order와 `anchorId=endpoint-evidence-{n}`를 보존한다.
52. Event evidence 또는 trend endpoint ref의 `snapshotDetailAnchor`가 detail response 안에 있으면 matching anchor section에 `data-active-anchor` 또는 동등 active 표시를 둔다.
53. Missing anchor는 detail response나 event/trend를 invalid로 만들지 않고 `anchorStatus=missing` 또는 anchor source absence로 표현한다.
54. `instanceSummary.items[]`는 Story 5.7 minimum field meaning을 표시만 하고 instance health, lifecycle state, endpoint priority, marker severity를 만들지 않는다.
55. Snapshot detail `readModel` block의 `state`, `triageCards`, `endpointPriority`, `recovery`는 stored projection summary로만 표시하고 current state를 재판정하지 않는다.
56. Snapshot/History mode는 Instance Snapshot Trend API를 새로 fetch하지 않는다. 이미 loaded trend에서 detail action을 누르는 경우에도 trend refetch를 만들지 않는다.
57. Snapshot Detail mode에서 Application Dashboard back action은 cached `loadedDashboard`로 돌아가고 dashboard API를 refetch하지 않는다.
58. Snapshot Detail mode에서 Snapshot/History back action은 cached loaded history/markers를 다시 렌더링하고 history APIs를 refetch하지 않는다.
59. Overlapping history request, overlapping detail request, token clear, Project/Application 재선택 상황에서 stale response가 현재 화면을 덮지 않는다.
60. Browser token persistence, URL token parsing, SPA routing, React/Vite/TypeScript/frontend build stack을 도입하지 않는다.
61. UI는 lifecycle state, starter diagnosis, rule, p95/p99, endpoint priority, event promotion, suppression, period folding, marker type/severity를 계산하는 helper를 만들지 않는다.
62. Static UI contract test는 authenticated history fetch, marker fetch, detail fetch, Authorization header를 검증한다.
63. Static UI contract test는 fixed 24h/7d/14d query, invalid link, no token, `401`, `400`, `404`, generic error, malformed response, stale response, response identity mismatch safe state를 검증한다.
64. Static UI contract test는 events/markers empty copy, event/marker rendering, snapshot detail rendering, anchor preservation, cached back actions를 검증한다.
65. Static UI contract test는 no raw JSON explorer, no endpoint timeseries, no UI-side event/marker recalculation, no token persistence, no frontend stack을 검증한다.
66. Existing backend tests for `DashboardSnapshotController`, `DashboardSnapshotMarkerService`, `DashboardSnapshotDetailService`, `OperationalEventHistoryController`, `OperationalEventHistoryService`, `OperationalEventHistoryProjector`를 regression으로 유지한다.
67. 새 public class/method/helper/test 또는 동작을 바로 이해하기 어려운 내부 helper에는 `AGENTS.md` 지침에 따라 한국어 Javadoc/comment를 작성한다.
68. Suggested verification과 `git diff --check`가 통과해야 implementation completion으로 볼 수 있다.

## Tasks / Subtasks

- [x] Snapshot/History mode와 state lifecycle 추가 (AC: 1~5, 56~59)
  - [x] `SNAPSHOT_HISTORY_VIEW_STATE`와 `SNAPSHOT_DETAIL_VIEW_STATE` 또는 동등 state를 추가한다.
  - [x] `selectedSnapshotHistoryContext`, `loadedOperationalEvents`, `loadedSnapshotMarkers`, `loadedSnapshotDetail`, request sequence guard를 추가한다.
  - [x] Application Dashboard ready view에서 Snapshot/History action을 활성화한다.
  - [x] Project/Application/Dashboard reload와 token clear 시 history/detail state와 pending request를 reset한다.
  - [x] Application Dashboard, Snapshot/History, Snapshot Detail back action은 cached state를 우선 사용한다.

- [x] Fixed history fetch와 safe state 구현 (AC: 6~24, 59)
  - [x] selected Project/Application context에서만 canonical operational event URL과 marker URL을 만든다.
  - [x] `24h`, `7d`, `14d` fixed preset query helper를 구현한다.
  - [x] 두 fetch 모두 Bearer token header를 붙이고, no-token/401/400/404/generic error를 safe state로 처리한다.
  - [x] operational event response와 marker response source/application/horizon/order shape를 fail-closed 검증한다.
  - [x] event/marker request stale guard를 추가한다.

- [x] Operational event feed와 marker timeline rendering (AC: 25~38, 61)
  - [x] Event feed는 server-provided event item field만 표시한다.
  - [x] Marker timeline은 server-provided marker item field만 표시한다.
  - [x] empty events/markers copy는 source absence/event 후보 없음으로 제한한다.
  - [x] event severity, marker severity, marker type, recovery/copy를 UI에서 재계산하지 않는다.
  - [x] event/marker snapshot detail action은 `links.snapshot`만 사용한다.

- [x] Snapshot detail deep link와 anchor handling (AC: 39~55, 57~59)
  - [x] `isSnapshotDetailLink()` 또는 동등 helper로 internal snapshot detail path와 selected Project/Application id 일치를 검증한다.
  - [x] marker/event `links.snapshot` detail action을 fetch boundary로 연결한다.
  - [x] trend point/ref detail action은 selected Project/Application + point `snapshotId`에서만 canonical path를 만들고 anchor를 보존한다.
  - [x] Snapshot detail response source/readSemantics/link identity를 fail-closed 검증한다.
  - [x] Snapshot detail ready view는 typed/bounded sections만 렌더링하고 raw JSON dump를 만들지 않는다.
  - [x] `snapshotDetailAnchor` matching, missing anchor safe copy, cached Snapshot/History back action을 구현한다.

- [x] Existing static guard 조정과 regression 유지 (AC: 60~68)
  - [x] `InstanceSnapshotTrendUiContractTest`와 `ApplicationDashboardUiContractTest`의 broad no `dashboard/snapshots`/`operational-events` guard를 6.7 허용 fetch boundary에 맞춰 좁힌다.
  - [x] `RecentHistoryUiContractTest`를 새로 추가하거나 기존 harness를 공통화한다.
  - [x] token persistence, frontend stack, raw explorer, endpoint timeseries, UI-side recomputation helper 부재를 guard한다.
  - [x] Backend snapshot/history focused tests와 MVC boundary tests를 regression으로 실행한다.

## Dev Notes

### Current Code State

- 현재 `sprint-status.yaml` 기준 Epic 6은 `in-progress`, Story 6.1~6.6은 `done`, Story 6.7은 이 create-story 작업으로 `ready-for-dev`, Story 6.8~6.9는 `backlog`다. Epic 1~5 완료 상태는 되돌리지 않는다.
- 현재 static dashboard는 `observability-portal/src/main/resources/static/dashboard/index.html`, `styles.css`, `app.js` 세 파일로 구성된다.
- 현재 `app.js`는 in-memory `window.observationPortalAuth.setAccessToken(accessToken)`과 `clearAccessToken()`만 제공한다. Browser token persistence, URL token parsing, cookie session은 없다.
- 현재 Project/Application/Dashboard/Instance Evidence/Instance Snapshot Trend fetch는 `projectRequestHeaders()`로 `Authorization: Bearer <access_token>` header를 붙인다.
- Story 6.6 이후 `selectedInstanceSnapshotTrendContext`, `loadedInstanceSnapshotTrend`, `instanceSnapshotTrendRequestSequence`, `INSTANCE_SNAPSHOT_TREND_VIEW_STATE`가 존재한다.
- 현재 Instance Snapshot Trend ready view는 stored point list와 `endpointEvidenceRefs[].snapshotDetailAnchor` text를 표시하지만 Snapshot Detail API를 fetch하지 않는다.
- 현재 `snapshotHandoffMarkup(dashboard.snapshot)`은 Application Dashboard 안의 optional snapshot handoff를 보존하되 button은 disabled `Snapshot pending` 상태다.
- 현재 `ApplicationDashboardReadModel.snapshot`은 `Object snapshot` field로 존재하지만 `DashboardReadModelService.buildDashboard(...)`는 current response에 `null`을 넣는다. Story 6.7은 backend contract를 바꾸지 않고 current selected application context에서 history actions를 제공해야 한다.
- 현재 `InstanceSnapshotTrendUiContractTest.snapshotTrendStaticGuardsForbidTokenPersistenceFollowupFetchAndUiRecomputation()`는 `dashboard/snapshots`와 `operational-events` fetch를 금지한다. Story 6.7 구현 시 해당 guard를 6.7 action/fetch helper 범위만 허용하도록 정교화해야 한다.

### Current Backend API Contracts

- Snapshot detail route는 `GET /api/projects/{projectId}/applications/{applicationId}/dashboard/snapshots/{snapshotId}`다.
- Snapshot marker route는 `GET /api/projects/{projectId}/applications/{applicationId}/dashboard/snapshot-markers?since=24h&limit=50`다.
- Operational event route는 `GET /api/projects/{projectId}/applications/{applicationId}/operational-events?since=24h&limit=50`다.
- Snapshot marker supported `since` token은 `24h`, `7d`, `14d`이고 `limit` max는 `336`이다.
- Operational event supported `since` parser는 positive integer + `h|d` token이며 max `14d`, `limit` max `100`이다.
- Snapshot detail `400`은 invalid UUID, `404`는 membership mismatch 또는 retention/detail absence, `500`은 stored projection failure다.
- Snapshot detail source는 `dashboard_snapshots`, read semantics는 `stored_snapshot_detail/currentStateRecalculated=false/liveSourcesJoined=[]/rawReadModelJsonExposed=false`다.
- Marker source는 `dashboard_snapshots`, order는 `capturedAt_asc`, empty horizon은 `200 + markers=[] + emptyState.reasonCode=no_snapshots_in_retention`이다.
- Operational event source는 `dashboard_snapshots`, order는 `occurredAt_desc`, empty response는 `200 + events=[]`이다.

### Previous Story Intelligence

- Story 6.6은 Instance Snapshot Trend를 같은 `dashboard-detail` 영역의 mode 전환으로 구현했다. Story 6.7도 새 route/SPA 없이 같은 state-machine 패턴을 따른다.
- Story 6.6은 `24h` trend preset을 backend `since=24h` 호출로 열지 않고 disabled/pending으로 유지했다. Story 6.7의 history API는 별도 계약이라 `24h`가 기본값으로 허용된다.
- Story 6.6 static contract test는 실제 `app.js` click/fetch/render sequence를 Node VM에서 검증한다. Story 6.7도 broad string assertion보다 request URL/header, stale response, identity mismatch, back action을 Node VM으로 검증하는 편이 안전하다.
- Story 5.8-b는 marker/detail source와 recovery copy를 닫았다. UI는 marker type/severity/recovery를 다시 분류하지 않고 response를 표시해야 한다.
- Story 5.9-b는 event promotion/suppression/period folding을 service layer에서 구현했다. UI는 `OperationalEventHistoryReadModel`을 표시만 하고 event를 새로 만들면 안 된다.
- 최근 git history는 `feat: implement instance snapshot trend UI`, `feat: add instance evidence dashboard UI`가 static asset과 UI contract test를 함께 변경한 흐름을 보여준다. Story 6.7도 UI contract red test를 먼저 세우는 순서가 안전하다.

### Architecture Constraints

- Active baseline은 Traditional MVC + Service/Repository Layering이다.
- Portal package는 feature-first MVC 구조이며 `domain`은 business feature namespace다.
- 금지 package는 `application`, `port`, `adapter`, `adapter.in`, `adapter.out`다.
- Controller는 repository를 직접 호출하지 않고 service에 위임한다.
- Repository는 controller/dto에 의존하지 않는다.
- JPA entity를 API response, public DTO, service external result로 노출하지 않는다.
- Flyway migration이 physical schema source of truth다. Story 6.7은 schema/migration 변경을 기대하지 않는다.
- Static dashboard UI는 portal runtime 안에서 제공되며 `observability-portal/src/main/resources/static/dashboard/` 안에서 구현한다.
- UI는 server read model을 표시만 한다. UI/controller/repository는 lifecycle state, starter diagnosis, zeroInsight, recovery, rule, p95/p99, endpoint priority, marker/event를 계산하지 않는다.
- Browser token persistence, SPA routing, frontend build stack 도입은 open decision이며 Story 6.7에서 닫지 않는다.
- 새 공개 class/method/helper/test에는 `AGENTS.md` 기준으로 한국어 Javadoc/comment를 작성한다.

### UI Copy Guardrails

- Snapshot/History는 alert delivery log, incident tracker, acknowledgement workflow가 아니다.
- `events=[]`나 `markers=[]`는 현재 정상 증명이 아니다.
- Snapshot detail은 "그 시점에 저장된 dashboard read model"이며 현재 상태 재판정이 아니다.
- Recovery 계열은 "회복 관찰 중"이지 "복구 완료"나 "장애 해결 완료"가 아니다.
- `down_entered`는 metric data freshness/state boundary를 표현할 뿐 host application down 원인을 확정하지 않는다.
- Marker `captureReason`은 저장 이유/opaque metadata이며 event type, severity, recovery 완료 의미로 해석하지 않는다.
- Event `resolvedAt`은 stored period folding 결과이며 UI가 현재 회복 성공을 단정하는 근거가 아니다.
- Endpoint evidence anchor는 snapshot detail 안의 bounded evidence 위치다. Endpoint root cause, recommended action, p95/p99, raw request sample로 확장하지 않는다.

## Open Decisions / Risks

- Alert/Discord delivery log, incident workflow, owner/ack/comment 기능은 Story 6.7 범위가 아니다.
- Public project creation/onboarding, browser token persistence, React/Vite/TypeScript/SPA routing 전환 시점은 여전히 open decision이다.
- Current dashboard response의 `snapshot` field는 현재 null일 수 있다. Snapshot/History entry는 selected application context에서 history APIs를 조회해야 하며, current dashboard response shape를 억지로 확장하지 않는다.
- Trend point에서 snapshot detail path를 만들 때는 selected Project/Application context와 server-provided point `snapshotId`만 사용해야 한다. 임의 DOM path나 URL token을 받아들이면 raw API navigation과 secret exposure 위험이 생긴다.
- 가장 큰 구현 위험은 UI가 marker/event를 더 예쁘게 보이려다가 type/severity/promotion/suppression/recovery/endpoint priority를 client-side로 재계산하는 것이다.

## Testing / Suggested commands

Focused test 대상 후보:

- `RecentHistoryUiContractTest` 신규 추가
- `InstanceSnapshotTrendUiContractTest` 갱신
- `ApplicationDashboardUiContractTest` 갱신
- `ApplicationListUiContractTest` regression 유지
- `ProjectSelectionUiContractTest` regression 유지
- `ProjectEntryUiContractTest` regression 유지
- `DashboardSnapshotControllerTest`
- `DashboardSnapshotMarkerServiceTest`
- `DashboardSnapshotDetailServiceTest`
- `OperationalEventHistoryControllerTest`
- `OperationalEventHistoryServiceTest`
- `OperationalEventHistoryProjectorTest`
- `OperationalEventHistoryReadModelTest`
- `MvcLayerBoundaryTest`

필수 scenario:

- Application Dashboard Snapshot/History action이 authenticated operational event + marker fetch로 이어진다.
- Fetch는 `Authorization: Bearer <access_token>` header를 사용한다.
- `24h`, `7d`, `14d` preset이 fixed bounded query만 만든다.
- Invalid link, no token, `401`, `400`, `404`, generic error, malformed response, stale response, response identity mismatch가 fail-closed safe state로 렌더링된다.
- Events와 markers empty state가 health/recovery completion을 단정하지 않는다.
- Event feed와 marker timeline이 server-provided type/severity/title/summary/evidence/link만 표시한다.
- Snapshot detail action은 validated `links.snapshot` 또는 selected context + trend point `snapshotId`만 사용한다.
- Snapshot detail readSemantics/source/link identity를 검증하고 raw JSON dump를 표시하지 않는다.
- Anchor resolved/missing 상태가 detail/trend/event를 invalid로 만들지 않는다.
- Application Dashboard back action과 Snapshot/History back action이 cached read model을 다시 표시하고 refetch를 만들지 않는다.
- Static UI가 token persistence, URL token parsing, SPA routing, frontend build stack을 만들지 않는다.
- Static UI가 lifecycle state/rule/p95/p99/endpoint priority/event promotion/marker severity helper를 만들지 않는다.

Suggested commands:

```bash
./gradlew :observability-portal:test --tests '*RecentHistoryUiContract*'
./gradlew :observability-portal:test --tests '*InstanceSnapshotTrendUiContract*'
./gradlew :observability-portal:test --tests '*ApplicationDashboardUiContract*'
./gradlew :observability-portal:test --tests '*ApplicationListUiContract*'
./gradlew :observability-portal:test --tests '*ProjectSelectionUiContract*'
./gradlew :observability-portal:test --tests '*ProjectEntryUiContract*'
./gradlew :observability-portal:test --tests '*DashboardSnapshotController*'
./gradlew :observability-portal:test --tests '*DashboardSnapshotMarkerService*'
./gradlew :observability-portal:test --tests '*DashboardSnapshotDetailService*'
./gradlew :observability-portal:test --tests '*OperationalEvent*'
./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest
./gradlew :observability-portal:test
git diff --check
```

테스트 이름은 구현 시점의 실제 class 이름에 맞게 조정할 수 있다. 단, static UI contract guard가 history/marker/detail authenticated fetch, fixed preset behavior, safe states, stale/identity mismatch guard, cached back actions, anchor handling, no UI-side recomputation, no raw explorer를 검증해야 한다.

## Dev Agent Record

### Agent Model Used

GPT-5 Codex (Codex desktop)

### Debug Log References

- 2026-05-31: `./gradlew :observability-portal:test --tests '*RecentHistoryUiContract*'` RED 확인 후 구현, 최종 통과.
- 2026-05-31: `./gradlew :observability-portal:test --tests '*InstanceSnapshotTrendUiContract*'`, `*ApplicationDashboardUiContract*`, `*InstanceEvidenceUiContract*`, Project/Application UI contract 회귀 통과.
- 2026-05-31: `./gradlew :observability-portal:test --tests '*OperationalEvent*'`, `*DashboardSnapshot*`, `com.observation.portal.architecture.MvcLayerBoundaryTest` 통과.
- 2026-05-31: `./gradlew :observability-portal:test` 전체 포털 테스트 통과.
- 2026-05-31: `git diff --check` 통과.

### Completion Notes List

- 기존 static dashboard runtime에 Snapshot/History mode와 Snapshot Detail mode를 추가하고, Application Dashboard/History/Detail back action은 cached read model을 우선 렌더링하도록 연결했다.
- History fetch는 selected Project/Application context에서만 `24h`, `7d`, `14d` fixed query를 만들고 operational event/marker response source, identity, horizon, order를 fail-closed 검증한다.
- Event/marker/detail rendering은 server-provided bounded field만 표시하며 raw snapshot/read model explorer, endpoint timeseries, token persistence, URL state, frontend build stack을 도입하지 않았다.
- Snapshot Detail은 validated `links.snapshot` 또는 selected context + trend point `snapshotId`로만 canonical detail path를 만들며 readSemantics, self link, source, anchor resolved/missing 상태를 검증한다.

### File List

- `observability-portal/src/main/resources/static/dashboard/app.js`
- `observability-portal/src/main/resources/static/dashboard/styles.css`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/RecentHistoryUiContractTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/ApplicationDashboardUiContractTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/InstanceEvidenceUiContractTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/InstanceSnapshotTrendUiContractTest.java`
- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/stories/6-7-snapshot-history-marker-ui-and-deep-link.md`

### Change Log

- 2026-05-31: Story 6.7 Snapshot/history marker UI and deep link create-story 산출물을 생성했다.
- 2026-05-31: Snapshot/History 및 Snapshot Detail static UI, fixed query, safe state, deep link/anchor handling, contract tests를 구현했다.
- 2026-05-31: Story 6.7 구현 완료 후 상태를 review로 변경했다.
