---
artifactType: story
storyId: "6.6"
storyKey: "6-6-instance-snapshot-trend-ui"
epic: "Epic 6. Dashboard User Flow and Demo Hardening"
title: "Instance snapshot trend UI"
architectureStyle: Traditional MVC
status: done
date: 2026-05-31
baseline_commit: 85baf1f6d66dd48c79fe42ab7ff544e4d4930cd9
---

# Story 6.6 - Instance snapshot trend UI

## Status

done

## Story

portal 사용자는 Instance Evidence 화면에서 selected instance의 bounded snapshot trend로 들어가고 싶다.

그래야 raw metric explorer나 current state 재판정 없이, 저장된 `dashboard_snapshots.read_model_json.instanceSummary.items[]` projection을 24h/7d/14d 관점으로 훑으며 문제가 없던 기간, source absence, metric data axis, starter connection axis, stored p95/p99 point, resource hint, application triage contribution 흐름을 확인할 수 있다.

## Source of Truth

아래 문서를 읽고 반영한 BMAD create-story context다. 구현 중 충돌처럼 보이는 지점은 current code/API shape와 Story 5.7 trend API 계약을 우선하고, 24h UX 요구는 backend contract를 재개하지 않는 presentation preset으로만 처리한다. `implementation-artifacts/sprint-status.yaml`은 story tracking source이며 기능/API 계약을 override하지 않는다.

1. `implementation-artifacts/sprint-status.yaml`
2. `implementation-artifacts/epic-6-context.md`
3. `planning-artifacts/epics.md`
4. `planning-artifacts/sprint-plan.md`
5. `planning-artifacts/current-product-source-of-truth.md`
6. `planning-artifacts/stories/5-7-instance-snapshot-trend-projection.md`
7. `implementation-artifacts/spec-story-5-7-instance-snapshot-trend-contract-decisions.md`
8. `planning-artifacts/stories/6-5-instance-evidence-ui.md`
9. `implementation-artifacts/spec-story-6-5-instance-evidence-ui-contract-decisions.md`
10. `planning-artifacts/contracts/read-model-contract.md`
11. `planning-artifacts/contracts/operational-event-history.md`
12. `planning-artifacts/api-surface.md`
13. `planning-artifacts/project-structure.md`
14. `planning-artifacts/prototypes/epic5-6-dashboard-flow-prototype.html`
15. `observability-portal/src/main/java/com/observation/portal/domain/instance/controller/InstanceSnapshotTrendController.java`
16. `observability-portal/src/main/java/com/observation/portal/domain/instance/model/InstanceSnapshotTrendReadModel.java`
17. `observability-portal/src/main/java/com/observation/portal/domain/instance/service/InstanceSnapshotTrendService.java`
18. `observability-portal/src/main/resources/static/dashboard/`
19. `observability-portal/src/test/java/com/observation/portal/domain/dashboard/InstanceEvidenceUiContractTest.java`
20. `AGENTS.md`
21. `_bmad/custom/project-context.md`

`planning-artifacts/contracts/dashboard-read-model.md`는 compatibility note이며 새 구현과 테스트의 상세 계약은 `planning-artifacts/contracts/read-model-contract.md`를 따른다.

## Scope / Out of Scope

포함:

- 기존 static dashboard runtime의 `dashboard-detail` 영역을 Instance Snapshot Trend mode로 전환하는 UI state
- Instance Evidence ready view의 `links.snapshotTrend` handoff action 활성화
- `links.snapshotTrend`가 내부 `/api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/snapshot-trend` shape이고 현재 selected Project/Application/Instance id와 일치할 때만 authenticated trend fetch 수행
- fixed horizon controls: `24h`, `7d`, `14d`
- `7d` 기본 horizon과 `14d` backend query, `24h`는 backend `since=24h`를 호출하지 않는 fixed display preset
- Trend ready view의 identity, generatedAt, `source`, `horizon`, point count, Application Dashboard/Instance Evidence back actions
- stored point lanes: stored application state copy, accepted bucket metric data axis, starter heartbeat connection axis, capture reason/source marker lane
- point list/detail: `capturedAt`, `currentWindowEndUtc`, `storedApplicationStateCode`, nullable opaque `captureReason`, `metricData`, `starterConnection`, nullable `starterPercentilePoint`, nullable `resourceHints`, `applicationTriageContribution`, `endpointEvidenceRefs`
- `points=[]`, selected display range empty, no concern observed, source absence를 구분하는 safe copy
- `InstanceSnapshotTrendUiContractTest` 신규 추가 또는 `InstanceEvidenceUiContractTest` 확장
- 기존 6.5 no trend fetch guard를 6.6 활성화 범위에 맞춰 좁히되 snapshot detail/history/operational events fetch 금지선 유지

제외:

- Backend API/schema/migration/read model 확장. 특히 `since=24h` backend support는 Story 6.6에서 추가하지 않는다.
- `InstanceSnapshotTrendController`, `InstanceSnapshotTrendReadModel`, `InstanceSnapshotTrendService` public contract 변경
- Snapshot Detail API fetch/render
- Operational Event History API fetch/render
- Story 6.7 Snapshot/history marker timeline과 snapshot detail deep link UX
- demo fixture/seed/hardening. Story 6.8/6.9 책임이다.
- browser route, URL query/fragment state, SPA routing, direct API JSON navigation
- React, Vite, TypeScript, `package.json`, `src/main/frontend`, 별도 frontend build/deploy
- browser token persistence, localStorage/sessionStorage/cookie token 저장, URL token parsing, refresh token browser storage
- lifecycle state, starter diagnosis, instance health score, rule, p95/p99, endpoint priority, operational event, recovery marker 재계산
- raw bucket explorer, raw dashboard snapshot JSON explorer, arbitrary time-series query, endpoint timeseries UI
- `captureReason`을 marker type/severity/event meaning/recovery meaning으로 분류하는 UI logic

## Acceptance Criteria

1. Instance Evidence ready view의 Snapshot Trend action은 같은 `dashboard-detail` 영역을 Instance Snapshot Trend mode로 전환한다.
2. Snapshot Trend는 별도 modal, browser route, URL query/fragment state, inline multi-expansion, 새 상시 panel로 구현하지 않는다.
3. Trend view 최상단에는 selected Project/Application/Instance identity, trend `generatedAt`, Application Dashboard로 돌아가는 action, Instance Evidence로 돌아가는 action을 항상 표시한다.
4. Instance Evidence back action은 `loadedInstanceEvidence`를 다시 렌더링하고 evidence API를 다시 fetch하지 않는다.
5. Application Dashboard back action은 `loadedDashboard`를 다시 렌더링하고 dashboard API를 다시 fetch하지 않는다.
6. Project/Application 재선택, token clear, dashboard reload, Application List reload, Instance Evidence reload가 발생하면 trend state와 pending request는 폐기된다.
7. Trend fetch source는 `InstanceEvidenceReadModel.links.snapshotTrend`뿐이다.
8. UI는 `projectId`, `applicationId`, `instanceId`로 trend path를 재구성하지 않는다.
9. UI는 `instanceName`만으로 trend를 조회하지 않는다.
10. Trend link는 내부 `/api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/snapshot-trend` shape여야 한다.
11. Trend link 검증은 현재 selected project id, selected application id, selected instance id가 모두 일치해야 통과한다.
12. 검증 실패 시 API call을 만들지 않고 `invalid-link` safe state를 표시한다.
13. 검증된 trend link만 in-memory access token으로 `Authorization: Bearer <access_token>` header를 붙여 `fetch`한다.
14. Query parameter는 fixed preset만 추가한다. arbitrary user input, custom date picker, raw query editor를 만들지 않는다.
15. `7d` preset은 `since=7d&limit=168` 또는 backend default와 동등한 bounded query를 사용한다.
16. `14d` preset은 `since=14d&limit=336`을 사용한다.
17. `24h` preset은 현재 backend가 `since=24h`를 지원하지 않는다는 Story 5.7 계약을 재개하지 않는다.
18. `24h` preset 구현은 `since=7d&limit=168` 응답 안에서 fixed display range만 좁히거나, backend 지원 전까지 disabled/pending state로 둔다. 어느 쪽이든 `fetch(...since=24h...)`를 만들지 않는다.
19. `24h` display filter를 구현한다면 response `horizon.until`과 point `capturedAt`만 사용하고 missing hourly snapshot을 보간하지 않는다.
20. Token이 없으면 `auth-required` state를 표시하고 provider token, service token, refresh token, secret, raw payload를 노출하지 않는다.
21. `401`은 auth-required 흐름으로 수렴하고 stale response가 현재 화면을 덮지 않는다.
22. `400`은 fixed query contract mismatch로 처리하며 backend error body를 노출하지 않는다.
23. `404`는 project/application/instance scope mismatch 또는 missing scope로 표현하고 instance down, application down, deleted instance를 단정하지 않는다.
24. Generic error는 backend detail, stack trace, token, provider raw payload 없이 다시 시도 copy만 표시한다.
25. Malformed response는 일부 block을 렌더링하지 않고 fail-closed error state로 수렴한다.
26. Response `application.projectId`, `application.applicationId`, `instance.instanceId`가 selected context와 다르면 identity mismatch로 fail-closed 처리한다.
27. Overlapping request, token clear, Project/Application/Instance 재선택 상황에서 stale trend response가 현재 화면을 덮지 않는다.
28. Ready state는 current `InstanceSnapshotTrendReadModel` top-level field만 소비한다: `generatedAt`, `application`, `instance`, `source`, `horizon`, `points`.
29. `source`는 `dashboard_snapshots.read_model_json.instanceSummary.items`로 표시 또는 static contract guard에서 보존한다.
30. `horizon.requestedSince`, `defaultSince=7d`, `maxSince=14d`, `limit`, `maxLimit=336`, `order=capturedAt_asc`를 표시 또는 test guard에서 보존한다.
31. Response point order가 `capturedAt ASC`임을 UI/test가 전제하고, UI가 임의 재정렬로 의미를 바꾸지 않는다.
32. Point list/detail은 `snapshotId`, `capturedAt`, `currentWindowEndUtc`, `storedApplicationStateCode`, nullable `captureReason`, `instanceName`, `observationStatus`를 표시한다.
33. `storedApplicationStateCode`는 application-level stored state copy로만 표현하고 instance lifecycle state나 instance health로 번역하지 않는다.
34. `captureReason`은 nullable opaque metadata로 표시하고 marker type, severity, operational event, recovery semantics로 해석하지 않는다.
35. Trend lane은 stored application state copy, accepted bucket metric data axis, starter heartbeat connection axis, capture reason/source marker lane을 분리한다.
36. `metricData.statusSource=accepted_bucket`을 화면 또는 static contract guard에서 보존한다.
37. `starterConnection.statusSource=starter_heartbeat`와 `stateImpact=none`을 화면 또는 static contract guard에서 보존한다.
38. Metric data lane과 starter connection lane은 하나의 `instanceHealth`, `hostStatus`, `processDown`, `applicationDown`, `connectedAndHealthy` copy/helper로 합치지 않는다.
39. Recent heartbeat와 missing/stale accepted bucket 조합은 starter 연결과 metric data 대기/idle을 분리해 표현하고 host application down을 확정하지 않는다.
40. `starterPercentilePoint`는 저장된 단일 starter canonical percentile point로 표시한다.
41. UI는 stored percentile point 평균, 최댓값, 병합, 보간, synthetic point 생성, histogram-derived p95/p99 계산을 하지 않는다.
42. `resourceHints`는 stored latest accepted bucket sample hint로만 표시하고 degraded/down 판단이나 score로 합성하지 않는다.
43. `applicationTriageContribution.contributed=false`는 "문제 없음"이 아니라 "저장된 application triage contribution 없음" 또는 source absence로 표현한다.
44. `endpointEvidenceRefs`는 bounded reference list로만 표시한다.
45. Endpoint ref는 `endpointKey`, optional `method`, optional `route`, optional `relatedApplicationPriorityRank`, optional `relatedRuleIds`, optional `snapshotDetailAnchor`만 소비한다.
46. UI는 endpoint body, duration buckets, confidence/score/recommended action, endpoint p95/p99, raw `endpoints_json`, raw path/query/trace/per-request sample을 만들거나 표시하지 않는다.
47. `points=[]`는 snapshot source absence, retention gap, target instance absence일 수 있음을 표시하고 "문제 없음", "정상", "복구 완료"로 표현하지 않는다.
48. Selected display range 안에 points는 있지만 concern/contribution/marker 후보가 없으면 "no concern observed"를 stored point 관찰 문구로만 표시하고 application/instance health proof처럼 표현하지 않는다.
49. Missing hourly snapshot과 retention gap은 보간하지 않고 gap/source absence로 표현한다.
50. Trend view는 Snapshot Detail API, Operational Event History API, raw snapshot JSON, endpoint timeseries, arbitrary metric query를 fetch/render하지 않는다.
51. Browser token persistence, URL token parsing, SPA routing, React/Vite/TypeScript/frontend build stack을 도입하지 않는다.
52. 새 public class/method/helper/test 또는 동작을 바로 이해하기 어려운 내부 helper에는 `AGENTS.md` 지침에 따라 한국어 Javadoc/comment를 작성한다.
53. `InstanceSnapshotTrendUiContractTest`를 새로 추가하거나 기존 static UI contract test를 확장해 실제 `app.js` state machine을 Node VM 또는 동등 구조 검증으로 검증한다.
54. Static UI contract test는 snapshotTrend handoff authenticated fetch와 `Authorization: Bearer <access_token>` header를 검증한다.
55. Static UI contract test는 invalid link, no-token, `401`, `400`, `404`, generic error, malformed response, stale response, response identity mismatch safe state를 검증한다.
56. Static UI contract test는 24h/7d/14d controls, no `since=24h` backend call, horizon metadata, empty trend, no concern observed copy를 검증한다.
57. Static UI contract test는 metricData/starterConnection lane separation, no UI-side recomputation, no snapshot detail/history/operational events fetch를 검증한다.
58. Suggested verification과 `git diff --check`가 통과해야 implementation completion으로 볼 수 있다.

## Tasks / Subtasks

- [x] Snapshot Trend handoff와 mode 전환 구현 (AC: 1~6)
  - [x] `snapshotTrendPendingHandoffMarkup()`의 disabled pending action을 validated active action으로 전환한다.
  - [x] 같은 `dashboard-detail` 영역 안에 Instance Snapshot Trend mode를 추가한다.
  - [x] Trend header에 Application Dashboard back action과 Instance Evidence back action을 모두 둔다.
  - [x] Project/Application/Dashboard/Instance Evidence reload와 token clear 시 trend state와 request sequence를 reset한다.

- [x] Safe trend link validation과 authenticated fetch 추가 (AC: 7~27)
  - [x] `selectedInstanceSnapshotTrendContext` 또는 동등 state를 추가하되 path source는 `links.snapshotTrend`로 제한한다.
  - [x] `isInstanceSnapshotTrendLink()` 검증을 재사용하거나 더 명확히 분리한다.
  - [x] fixed preset query builder는 검증된 base link에만 `since`/`limit`를 붙인다.
  - [x] `24h`는 backend `since=24h` 호출 없이 fixed presentation preset으로 처리한다.
  - [x] `idle`, `loading`, `auth-required`, `invalid-link`, `bad-query`, `not-found`, `error`, `ready` safe state를 구현한다.
  - [x] `401`, `400`, `404`, generic error, malformed response, identity mismatch, stale response를 fail-closed 처리한다.

- [x] Instance Snapshot Trend read model rendering (AC: 28~49)
  - [x] Ready state는 `InstanceSnapshotTrendReadModel` top-level field만 소비하고 response identity mismatch를 렌더링 전에 거부한다.
  - [x] Reading order를 identity/back actions -> horizon controls/metadata -> stored trend lanes -> point list -> selected point detail로 고정한다.
  - [x] `source`와 `horizon` metadata를 화면 또는 test guard에서 보존한다.
  - [x] Stored application state copy, metric data axis, starter connection axis, capture reason/source marker lane을 분리한다.
  - [x] Point detail은 stored row metadata와 stored instance summary block만 표시한다.
  - [x] `points=[]`, selected display range empty, no concern observed를 서로 다른 source-aware copy로 표시한다.

- [x] Recalculation/raw/history guard 유지 (AC: 33~51)
  - [x] `storedApplicationStateCode`를 instance health/state badge로 번역하지 않는다.
  - [x] `captureReason`을 marker type/severity/event/recovery로 분류하지 않는다.
  - [x] percentile/resource/triage/endpoint refs를 표시만 하고 평균/최댓값/병합/score/root cause/recommended action을 만들지 않는다.
  - [x] Snapshot Detail, Operational Event History, raw snapshot, endpoint timeseries, arbitrary metric query API를 fetch하지 않는다.
  - [x] localStorage/sessionStorage/cookie/URL token parsing/SPA routing/frontend stack 부재를 유지한다.

- [x] Static UI contract tests 작성/갱신 (AC: 53~58)
  - [x] `InstanceSnapshotTrendUiContractTest` 신규 추가 또는 `InstanceEvidenceUiContractTest` 확장으로 실제 `app.js` click/fetch/render sequence를 검증한다.
  - [x] authenticated trend fetch, fixed horizon query, no `since=24h`, invalid/no-token/401/400/404/error/malformed/stale/identity mismatch safe state를 검증한다.
  - [x] back actions가 cached `loadedDashboard`/`loadedInstanceEvidence`를 사용하고 불필요한 refetch를 만들지 않는지 검증한다.
  - [x] lane separation, source/horizon metadata, empty/no-concern copy, no UI recomputation, no snapshot/history fetch guard를 검증한다.
  - [x] 6.5의 broad no trend fetch assertion이 있다면 6.6 정상 fetch 허용 범위로 좁히고, snapshot detail/history/operational event fetch 금지는 유지한다.

- [x] Focused backend regression과 final verification 유지 (AC: 28~31, 50, 52, 58)
  - [x] `InstanceSnapshotTrendControllerTest` route/status/serialization/no raw field guard를 유지한다.
  - [x] `InstanceSnapshotTrendReadModelShapeTest` source/horizon/raw-field absence guard를 유지한다.
  - [x] `InstanceSnapshotTrendServiceTest` membership/query clamp/missing snapshot empty response/forbidden dependency guard를 유지한다.
  - [x] `InstanceSnapshotTrendParserTest`와 snapshot repository projection tests를 필요 시 regression으로 실행한다.
  - [x] focused Gradle commands와 full portal test, `git diff --check`를 실행한다.

## Dev Notes

### Current Code State

- 현재 `sprint-status.yaml` 기준 Epic 6은 `in-progress`, Story 6.1~6.5는 `done`, Story 6.6은 `ready-for-dev`, Story 6.7~6.9는 `backlog`다. Epic 1~5와 Story 6.1~6.5의 완료 상태는 되돌리지 않는다.
- 현재 static dashboard는 `observability-portal/src/main/resources/static/dashboard/index.html`, `styles.css`, `app.js` 세 파일로 구성된다.
- 현재 `app.js`는 `window.observationPortalAuth.setAccessToken(accessToken)`과 `clearAccessToken()` in-memory hook만 제공한다. Browser token persistence, URL token parsing, cookie session은 없다.
- 현재 Project/Application/Dashboard/Instance Evidence fetch는 `projectRequestHeaders()`로 `Authorization: Bearer <access_token>` header를 붙인다.
- Story 6.5 구현 후 `selectedDashboardContext`, `loadedDashboard`, `selectedInstanceEvidenceContext`, `loadedInstanceEvidence`, `instanceEvidenceRequestSequence`, `INSTANCE_EVIDENCE_VIEW_STATE`가 존재한다.
- 현재 Instance Evidence ready view는 `snapshotTrendPendingHandoffMarkup(evidence.links)`로 `links.snapshotTrend`를 `data-snapshot-trend-link`에 보존하지만 button은 disabled `Snapshot trend pending`이다. Story 6.6은 이 handoff를 활성화한다.
- 현재 `isInstanceSnapshotTrendLink(snapshotTrendLink, projectId, applicationId, instanceId)`와 `safeSnapshotTrendLink(candidate)`가 존재한다. Story 6.6은 이 검증을 fetch boundary에도 재사용할 수 있다.
- 현재 `isValidInstanceEvidenceLinks()`는 `links.snapshotTrend`가 있으면 현재 selected Project/Application/Instance와 일치하는지 검증한다. Trend view도 같은 identity fail-closed 원칙을 유지해야 한다.
- 현재 `InstanceEvidenceUiContractTest`는 Node VM harness와 project/application/dashboard/evidence fixtures를 갖고 있다. Story 6.6 test는 이 harness를 재사용하거나 snapshot trend fixture를 추가해 공통화하는 편이 안전하다.

### Current Snapshot Trend API Contract

- `InstanceSnapshotTrendController` route는 `GET /api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/snapshot-trend`다.
- Query parameter는 optional `since`, `limit`이다.
- 현재 `InstanceSnapshotTrendService`가 지원하는 `since` token은 `7d`, `14d`뿐이다. Blank, `24h`, 다른 token은 `400`이다.
- `limit` 생략 기본은 `168`, maximum은 `336` clamp다.
- Membership mismatch는 `404`이고, snapshot/source/item absence는 valid `200 + points=[]`다.
- Response top-level shape는 `generatedAt`, `application`, `instance`, `source`, `horizon`, `points`다.
- Response `source` literal은 `dashboard_snapshots.read_model_json.instanceSummary.items`다.
- `horizon`은 `since`, `until`, `requestedSince`, `defaultSince=7d`, `maxSince=14d`, `limit`, `maxLimit=336`, `order=capturedAt_asc`를 포함한다.
- `points[]`는 `capturedAt ASC`, tie-breaker `snapshotId ASC`로 반환된다.
- `storedApplicationStateCode`는 application-level stored state copy일 뿐 instance lifecycle state나 health score가 아니다.
- `captureReason`은 nullable opaque metadata다. UI는 trimming, enum mapping, marker severity/event/recovery 해석을 하지 않는다.

### Previous Story Intelligence

- Story 6.5는 Instance Evidence 화면을 같은 `dashboard-detail` 영역의 mode 전환으로 구현했고, Application Dashboard back action은 cached `loadedDashboard`를 다시 렌더링했다. Story 6.6도 새 route/SPA 없이 같은 mode 전환 패턴을 따른다.
- Story 6.5는 `links.snapshotTrend`를 pending handoff로만 보존했다. Story 6.6은 이 handoff를 fetch source로 승격하되 current evidence 의미를 변경하지 않는다.
- Story 6.5 closeout hardening은 malformed nested evidence, suppressed endpoint, percentile point duration/current window를 fail-closed로 막았다. Trend response도 malformed nested point를 일부 렌더링하지 말고 fail-closed해야 한다.
- Story 6.4와 6.5의 핵심 학습은 static UI contract test가 실제 `app.js` click/fetch/render sequence를 검증해야 한다는 점이다. Broad string assertion만으로는 stale response, identity mismatch, token loss 회귀를 막기 어렵다.
- 최근 git history는 `feat: add instance evidence dashboard UI`가 static asset과 `InstanceEvidenceUiContractTest`를 함께 변경한 흐름을 보여준다. Story 6.6도 먼저 contract test를 세우고 UI state machine을 구현하는 순서가 안전하다.

### Architecture Constraints

- Active baseline은 Traditional MVC + Service/Repository Layering이다.
- Portal package는 feature-first MVC 구조이며 `domain`은 business feature namespace다.
- 금지 package는 `application`, `port`, `adapter`, `adapter.in`, `adapter.out`다.
- Controller는 repository를 직접 호출하지 않고 service에 위임한다.
- Repository는 controller/dto에 의존하지 않는다.
- JPA entity를 API response, public DTO, service external result로 노출하지 않는다.
- Flyway migration이 physical schema source of truth다. Story 6.6은 schema/migration 변경을 기대하지 않는다.
- Static dashboard UI는 portal runtime 안에서 제공되며 `observability-portal/src/main/resources/static/dashboard/` 안에서 구현한다.
- UI는 server read model을 표시만 한다. UI/controller/repository는 lifecycle state, starter diagnosis, zeroInsight, recovery, rule, p95/p99, endpoint priority, snapshot/history event를 계산하지 않는다.
- Browser token persistence, SPA routing, frontend build stack 도입은 open decision이며 Story 6.6에서 닫지 않는다.
- 새 공개 class/method/helper/test에는 `AGENTS.md` 기준으로 한국어 Javadoc/comment를 작성한다.

### UI Copy Guardrails

- Instance Snapshot Trend는 application dashboard나 instance health dashboard가 아니다. Stored snapshot/read model projection을 표시하는 보조 화면이다.
- `storedApplicationStateCode=active`는 selected instance 정상 확정이 아니다.
- `points=[]`는 retention/source/item absence일 수 있으며 "문제 없음" 또는 "정상"이 아니다.
- "no concern observed"는 selected stored points에서 concern/contribution marker가 관찰되지 않았다는 뜻으로만 사용한다. 현재 정상, 복구 완료, host health success처럼 말하지 않는다.
- Recent heartbeat와 missing/stale accepted bucket은 starter connection과 metric data axis를 분리해서 표현한다.
- `captureReason`은 marker 후보처럼 보일 수 있지만 Story 6.6에서는 opaque stored metadata다. Marker timeline과 snapshot detail deep link는 Story 6.7 책임이다.
- Endpoint refs는 snapshot detail anchor 후보일 뿐 endpoint root cause/action ranking이 아니다.

## Open Decisions / Risks

- `24h`는 제품 UX 후보지만 current backend contract는 `since=7d|14d`만 지원한다. Story 6.6은 backend contract를 바꾸지 않고 fixed display preset 또는 disabled state로 처리한다. `since=24h` 지원이 필요하면 별도 contract decision/correct-course가 필요하다.
- Browser token persistence, refresh token browser storage, logout/session persistence는 여전히 open decision이다. Story 6.6에서 닫지 않는다.
- React/Vite/TypeScript/SPA routing 전환 시점과 source/build output 구조는 open decision이다. Story 6.6에서 정하지 않는다.
- Snapshot/History marker UI와 snapshot detail deep link는 Story 6.7 책임이다. Story 6.6에서 `captureReason`과 endpoint anchor를 marker/detail UX로 승격하면 scope가 흐려진다.
- Demo green path/failure-recovery fixture와 seed strategy는 Story 6.8/6.9 책임이다.
- 가장 큰 구현 위험은 fixed trend UI가 예쁘게 보이려다가 stored point를 합성해 instance health score, percentile trend calculation, marker severity, endpoint root cause를 만드는 것이다.

## Testing / Suggested commands

Focused test 대상 후보:

- `InstanceSnapshotTrendUiContractTest` 신규 추가
- `InstanceEvidenceUiContractTest` 갱신
- `ApplicationDashboardUiContractTest` regression 유지
- `ApplicationListUiContractTest` regression 유지
- `ProjectSelectionUiContractTest` regression 유지
- `ProjectEntryUiContractTest` regression 유지
- `InstanceSnapshotTrendControllerTest`
- `InstanceSnapshotTrendReadModelShapeTest`
- `InstanceSnapshotTrendServiceTest`
- `InstanceSnapshotTrendParserTest`
- `MvcLayerBoundaryTest`

필수 scenario:

- Instance Evidence `links.snapshotTrend` handoff가 validated authenticated fetch로 이어진다.
- Trend fetch는 `Authorization: Bearer <access_token>` header를 사용한다.
- 7d/14d preset은 bounded query를 사용하고, 24h preset은 `since=24h` backend call을 만들지 않는다.
- Invalid link, no token, `401`, `400`, `404`, generic error, malformed response, stale response, response identity mismatch가 fail-closed safe state로 렌더링된다.
- Application Dashboard와 Instance Evidence back action이 refetch 없이 cached read model을 다시 표시한다.
- Ready view가 `source`, `horizon`, `points[]` field만 소비하고 response identity mismatch를 거부한다.
- Stored application state, metric data, starter connection, capture reason/source marker lane이 분리된다.
- `metricData.statusSource=accepted_bucket`, `starterConnection.statusSource=starter_heartbeat`, `stateImpact=none`이 별도 axis로 표시된다.
- `storedApplicationStateCode`, `captureReason`, `starterPercentilePoint`, `resourceHints`, `applicationTriageContribution`, `endpointEvidenceRefs`가 새 state/score/rule/event로 해석되지 않는다.
- `points=[]`, display range empty, no concern observed copy가 health/recovery completion을 단정하지 않는다.
- Snapshot Detail, Operational Event History, raw snapshot JSON, endpoint timeseries, arbitrary metric query API를 fetch하지 않는다.
- Static UI가 token persistence, URL token parsing, SPA routing, frontend build stack을 만들지 않는다.

Suggested commands:

```bash
./gradlew :observability-portal:test --tests '*InstanceSnapshotTrendUiContract*'
./gradlew :observability-portal:test --tests '*InstanceEvidenceUiContract*'
./gradlew :observability-portal:test --tests '*ApplicationDashboardUiContract*'
./gradlew :observability-portal:test --tests '*ApplicationListUiContract*'
./gradlew :observability-portal:test --tests '*ProjectSelectionUiContract*'
./gradlew :observability-portal:test --tests '*ProjectEntryUiContract*'
./gradlew :observability-portal:test --tests '*InstanceSnapshotTrendController*'
./gradlew :observability-portal:test --tests '*InstanceSnapshotTrendReadModelShape*'
./gradlew :observability-portal:test --tests '*InstanceSnapshotTrendService*'
./gradlew :observability-portal:test --tests '*InstanceSnapshotTrendParser*'
./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest
./gradlew :observability-portal:test
git diff --check
```

테스트 이름은 구현 시점의 실제 class 이름에 맞게 조정할 수 있다. 단, static UI contract guard가 snapshotTrend link authenticated fetch, fixed horizon behavior, safe states, stale/identity mismatch guard, back actions, lane separation, no UI-side recomputation, no snapshot detail/history fetch를 검증해야 한다.

## Dev Agent Record

### Agent Model Used

GPT-5 Codex (Codex desktop)

### Debug Log References

- 2026-05-31: BMAD dev-story workflow customization, project context, Story 6.6/6.5/5.7, Story 5.7/6.5 contract decisions, read-model/history/API contracts, AGENTS.md를 확인했다.
- 2026-05-31: `InstanceSnapshotTrendUiContractTest`를 추가하고 red 상태를 확인했다.
- 2026-05-31: `app.js`에 Instance Snapshot Trend mode, `links.snapshotTrend` handoff activation, request sequence/cache, fixed 7d/14d query, 24h pending preset, safe state rendering, response identity/malformed/stale guard를 구현했다.
- 2026-05-31: Trend ready view에 source/horizon metadata, stored application state lane, metric data axis, starter connection axis, capture reason/source marker lane, point detail, empty/no-concern/source absence copy를 추가했다.
- 2026-05-31: `InstanceEvidenceUiContractTest`와 `ApplicationDashboardUiContractTest`의 broad follow-up fetch guard를 6.6 정상 trend fetch 허용 범위에 맞게 좁혔다.
- 2026-05-31: Focused UI/backend regression, full portal test, `git diff --check`를 실행했다.

### Completion Notes List

- Instance Evidence ready view의 `links.snapshotTrend` handoff를 active action으로 바꾸고 같은 `dashboard-detail` 영역에서 Instance Snapshot Trend mode로 전환하도록 구현했다.
- Trend fetch는 Evidence read model에서 받은 `links.snapshotTrend`만 사용하며, 현재 Project/Application/Instance identity와 내부 path shape가 일치할 때만 Bearer token fetch를 수행한다.
- `7d`는 `since=7d&limit=168`, `14d`는 `since=14d&limit=336`으로 호출하고, `24h`는 disabled/pending preset으로 두어 backend `since=24h` 호출을 만들지 않는다.
- no-token, 401, 400, 404, generic error, malformed response, stale response, identity mismatch를 fail-closed safe state로 수렴시켰다.
- Trend ready view는 top-level `generatedAt/application/instance/source/horizon/points`만 소비하고, stored state/metric/starter/capture reason lane과 point detail을 표시만 한다.
- Application Dashboard back action은 cached `loadedDashboard`, Instance Evidence back action은 cached `loadedInstanceEvidence`를 다시 렌더링하며 API refetch를 만들지 않는다.
- Snapshot Detail, Operational Event History, raw snapshot, endpoint timeseries, arbitrary metric query fetch와 token persistence/SPA routing/frontend stack 도입은 열지 않았다.

### File List

- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/stories/6-6-instance-snapshot-trend-ui.md`
- `observability-portal/src/main/resources/static/dashboard/app.js`
- `observability-portal/src/main/resources/static/dashboard/styles.css`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/InstanceSnapshotTrendUiContractTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/InstanceEvidenceUiContractTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/ApplicationDashboardUiContractTest.java`

### Change Log

- 2026-05-31: Story 6.6 Instance Snapshot Trend UI 구현, static UI contract test 추가, focused/backend/full verification 통과.
