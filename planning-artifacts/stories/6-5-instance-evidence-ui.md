---
artifactType: story
storyId: "6.5"
storyKey: "6-5-instance-evidence-ui"
epic: "Epic 6. Dashboard User Flow and Demo Hardening"
title: "Instance evidence UI"
architectureStyle: Traditional MVC
status: done
date: 2026-05-29
baseline_commit: 8b01db86e7442740f9dcebdbbf03b852b725856f
---

# Story 6.5 - Instance evidence UI

## Status

done

## Story

portal 사용자는 Application Dashboard의 instance summary에서 선택한 instance의 bounded evidence detail로 들어가고 싶다.

그래야 Application Dashboard 판단을 대체하지 않으면서 selected instance의 metric data axis, starter connection axis, starter-reported percentile series, histogram distribution, resource hint, endpoint evidence subset, application triage contribution 여부를 같은 static dashboard runtime 안에서 확인할 수 있다.

## Source of Truth

아래 문서를 읽고 반영한 BMAD create-story context다. 구현 중 충돌처럼 보이는 지점은 1번 Story 6.5 pre-story contract decision, Story 5.6 instance evidence API contract, current code/API shape, `read-model-contract.md` 순서로 우선한다.

1. `implementation-artifacts/spec-story-6-5-instance-evidence-ui-contract-decisions.md`
2. `implementation-artifacts/spec-story-5-6-instance-evidence-contract-decisions.md`
3. `planning-artifacts/stories/5-6-instance-evidence-read-model.md`
4. `implementation-artifacts/spec-story-6-4-application-dashboard-ui-integration-contract-decisions.md`
5. `planning-artifacts/stories/6-4-application-dashboard-ui-integration.md`
6. `implementation-artifacts/epic-6-context.md`
7. `planning-artifacts/epics.md`
8. `planning-artifacts/contracts/read-model-contract.md`
9. `planning-artifacts/contracts/dashboard-read-model.md`
10. `planning-artifacts/contracts/operational-event-history.md`
11. `observability-portal/src/main/java/com/observation/portal/domain/instance/controller/InstanceEvidenceController.java`
12. `observability-portal/src/main/java/com/observation/portal/domain/instance/model/InstanceEvidenceReadModel.java`
13. `observability-portal/src/main/resources/static/dashboard/`
14. `AGENTS.md`
15. `_bmad/custom/project-context.md`

`planning-artifacts/contracts/dashboard-read-model.md`는 compatibility note이며 새 구현과 테스트의 상세 계약은 `planning-artifacts/contracts/read-model-contract.md`를 따른다.

## Scope / Out of Scope

포함:

- 기존 static dashboard runtime의 `dashboard-detail` 영역을 Instance Detail mode로 전환하는 UI state
- Application Dashboard `instances[]` handoff의 Evidence action 활성화
- Dashboard `instances[].links.evidence`만 source로 쓰는 authenticated Instance Evidence fetch
- 내부 `/api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/evidence` link shape, selected Project/Application/Instance identity, response identity 검증
- `idle`, `loading`, `auth-required`, `invalid-link`, `not-found`, `error`, `ready` 계열 safe state
- Instance Detail 상단 identity/generatedAt/Application Dashboard back action
- `metricData`와 `starterConnection`을 별도 axis로 표시
- `applicationTriageContribution`, `starterPercentiles`, `histogramDistribution`, `resourceHints`, `endpointEvidence`, `links.snapshotTrend` pending handoff를 계약 순서대로 표시
- `InstanceEvidenceUiContractTest` 신규 추가 또는 `ApplicationDashboardUiContractTest` 확장
- no token persistence, no URL token parsing, no frontend stack, no UI-side recomputation guard 유지

제외:

- Backend API/schema/migration/read model 확장. 필요해 보이면 구현하지 말고 risk/open item으로 남긴다.
- Instance Snapshot Trend API fetch/render
- Snapshot Detail API fetch/render
- Operational Event History API fetch/render
- 24h/7d/14d selector, trend chart, snapshot/history marker UI
- browser route, URL query/fragment state, SPA routing, direct API JSON navigation
- React, Vite, TypeScript, `package.json`, `src/main/frontend`, 별도 frontend build/deploy
- browser token persistence, localStorage/sessionStorage/cookie token 저장, URL token parsing, refresh token browser storage
- lifecycle state, starter diagnosis, instance health, rule, p95/p99, endpoint priority, snapshot/history event 재계산
- endpoint root cause/ranking surface, raw bucket explorer, raw endpoint explorer, arbitrary metric query UI
- demo fixture/seed/hardening. Story 6.8/6.9 책임이다.

## Acceptance Criteria

1. Application Dashboard `instances[]` item의 Evidence action은 같은 `dashboard-detail` 영역을 Instance Detail mode로 전환한다.
2. Instance Detail은 별도 modal, browser route, URL query/fragment state, inline multi-expansion, 네 번째 상시 panel로 구현하지 않는다.
3. Instance Detail 최상단에는 selected instance identity, evidence `generatedAt`, Application Dashboard로 돌아가는 action을 항상 표시한다.
4. Application Dashboard back action은 기존 `loadedDashboard` read model을 다시 렌더링한다.
5. Project/Application 재선택, token clear, dashboard reload, Application List reload가 발생하면 instance detail state와 pending request는 폐기된다.
6. Evidence fetch source는 Dashboard response의 clicked `instances[].links.evidence`뿐이다.
7. UI는 `projectId`, `applicationId`, `instanceId`로 evidence path를 재구성하지 않는다.
8. UI는 `instanceName`만으로 evidence를 조회하지 않는다.
9. Evidence link는 내부 `/api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/evidence` shape여야 한다.
10. Evidence link 검증은 현재 selected project id, selected application id, clicked instance id가 모두 일치해야 통과한다.
11. 검증 실패 시 API call을 만들지 않고 `invalid-link` safe state를 표시한다.
12. 검증된 evidence link만 in-memory access token으로 `Authorization: Bearer <access_token>` header를 붙여 `fetch`한다.
13. Token이 없으면 `auth-required` state를 표시하고 provider token, service token, refresh token, secret, raw payload를 노출하지 않는다.
14. `401`은 auth-required 흐름으로 수렴하고 stale response가 현재 화면을 덮지 않는다.
15. `404`는 project/application/instance scope mismatch 또는 missing scope로 표현하고 instance down, application down, deleted instance를 단정하지 않는다.
16. Generic error는 backend detail, stack trace, token, provider raw payload 없이 다시 시도 copy만 표시한다.
17. Malformed response는 일부 block을 렌더링하지 않고 fail-closed error state로 수렴한다.
18. Response `application.projectId`, `application.applicationId`, `instance.instanceId`가 selected context와 다르면 identity mismatch로 fail-closed 처리한다.
19. Overlapping request, token clear, Project/Application/Instance 재선택 상황에서 stale evidence response가 현재 화면을 덮지 않는다.
20. Ready state는 current `InstanceEvidenceReadModel` top-level field만 소비한다: `generatedAt`, `application`, `instance`, `metricData`, `starterConnection`, `starterPercentiles`, `histogramDistribution`, `resourceHints`, `applicationTriageContribution`, `endpointEvidence`, `links`.
21. Instance Detail reading order는 identity/back action -> `metricData`/`starterConnection` -> `applicationTriageContribution` -> `starterPercentiles` -> `histogramDistribution` -> `resourceHints` -> `endpointEvidence` -> `links.snapshotTrend` pending handoff 순서다.
22. Missing/empty/insufficient block은 section을 숨기지 않고 source absence 또는 evidence 부족으로 표현한다.
23. `metricData.statusSource=accepted_bucket`을 화면 또는 static contract guard에서 보존한다.
24. `starterConnection.statusSource=starter_heartbeat`와 `stateImpact=none`을 화면 또는 static contract guard에서 보존한다.
25. `metricData`와 `starterConnection`은 별도 axis로 표시하고 하나의 `instanceHealth`, `hostStatus`, `processDown`, `applicationDown`, `connectedAndHealthy` copy/helper로 합치지 않는다.
26. Recent heartbeat와 missing/stale accepted bucket 조합은 starter 연결과 metric data 대기/idle을 분리해 표현하고 host application down을 확정하지 않는다.
27. `applicationTriageContribution.contributed=false`는 "문제 없음"이 아니라 "기여 evidence 없음" 또는 source absence로 표현한다.
28. `starterPercentiles.window=current_15m`, `bucketDurationSeconds=30`, `maxPointCount=30`, `displayPolicy=source_scoped_series`, `aggregatePolicy=no_average_no_max_no_merge_no_histogram_recalculation`을 표시 또는 test guard에서 보존한다.
29. `starterPercentiles.points[]`는 시간 오름차순 current 15분 30초 bucket series로 표시한다.
30. 각 percentile point는 `bucketStartUtc`, `bucketEndUtc`, `requestCount`, `p95Ms`, `p99Ms`를 함께 표시한다.
31. UI는 percentile point 평균, 최댓값, 병합, 보간, synthetic point 생성을 하지 않는다.
32. `histogramDistribution`은 selected instance current 15분 distribution evidence로만 표시한다.
33. UI는 histogram bucket에서 p95/p99, avg/max latency, latency score, health score를 계산하지 않는다.
34. `resourceHints`는 latest accepted bucket sample 기반 CPU/heap/datasource ratio hint로만 표시하고 degraded/down 판단이나 score로 합성하지 않는다.
35. `endpointEvidence.source=accepted_metric_buckets.endpoints_json`과 `scope=instance_current_15m`을 표시 또는 test guard에서 보존한다.
36. `endpointEvidence.selectionPolicy`와 `displayOrderingPolicy`는 server-provided contract value로 보존한다.
37. `endpointEvidence.items[]`는 최대 5개 bounded evidence subset으로 표시한다.
38. `relatedApplicationPriorityRank`는 Application Dashboard endpoint priority 참조 값이고, `localDisplayOrder`는 Instance Detail 안의 표시 순서일 뿐 priority/root cause/action 순위가 아니다.
39. `presenceOnSelectedInstance=observed`는 selected instance current window에서 관찰됨으로 표현한다.
40. `presenceOnSelectedInstance=not_observed`는 문제 없음이 아니라 selected instance current window에 해당 endpoint evidence가 없다는 뜻으로 표현한다.
41. `presenceOnSelectedInstance=insufficient`는 endpoint evidence 신뢰 부족 상태로 표현한다.
42. `endpointEvidence.status=suppressed`와 `reason=application_freshness_not_current`는 stale/down 직전 endpoint evidence를 current concern처럼 보이지 않게 한다.
43. UI는 endpoint priority ranking, root cause, load balancer misconfiguration, instance fault, recommended action, endpoint p95/p99를 새로 만들지 않는다.
44. raw `endpoints_json`, raw path, query string, query key/value, trace id, per-request sample은 표시하지 않는다.
45. `links.snapshotTrend`는 pending handoff action 또는 data attribute로만 보존한다.
46. Story 6.5는 Instance Snapshot Trend API, Snapshot Detail API, Operational Event History API를 fetch/render하지 않는다.
47. Story 6.5는 24h/7d/14d selector, trend chart, raw instance timeseries, endpoint timeseries UI를 만들지 않는다.
48. Browser token persistence, URL token parsing, SPA routing, React/Vite/TypeScript/frontend build stack을 도입하지 않는다.
49. UI는 lifecycle state, starter diagnosis, instance health, p95/p99, endpoint priority, rule, snapshot/history event를 재계산하지 않는다.
50. 새 public class/method/helper/test 또는 동작을 바로 이해하기 어려운 내부 helper에는 `AGENTS.md` 지침에 따라 한국어 Javadoc/comment를 작성한다.
51. `InstanceEvidenceUiContractTest`를 새로 추가하거나 `ApplicationDashboardUiContractTest`를 확장해 실제 `app.js` state machine을 Node VM 또는 동등 구조 검증으로 검증한다.
52. Static UI contract test는 Instance Evidence link authenticated fetch와 `Authorization: Bearer <access_token>` header를 검증한다.
53. Static UI contract test는 invalid link, no-token, `401`, `404`, generic error, malformed response, stale response, response identity mismatch safe state를 검증한다.
54. Static UI contract test는 Application Dashboard back action, metricData/starterConnection axis separation, no UI-side recomputation, no trend/snapshot/history fetch를 검증한다.
55. Suggested verification과 `git diff --check`가 통과해야 implementation completion으로 볼 수 있다.

## Tasks / Subtasks

- [x] Instance Detail mode와 back navigation 구현 (AC: 1~5, 21)
  - [x] `dashboard-detail`을 Application Dashboard ready mode와 Instance Detail mode로 전환할 수 있게 state를 추가한다.
  - [x] Instance Detail 상단에 selected instance identity, generatedAt, Application Dashboard back action을 항상 둔다.
  - [x] Back action은 `loadedDashboard`를 다시 렌더링하고 evidence response를 다시 fetch하지 않는다.
  - [x] Project/Application 재선택, token clear, dashboard reload, Application List reload에서 instance detail state와 request sequence를 reset한다.

- [x] Evidence action과 safe link validation 활성화 (AC: 6~12, 48)
  - [x] `instanceHandoffMarkup()`의 disabled pending action을 authenticated Evidence action으로 전환한다.
  - [x] `instanceId`, `instanceName`, `links.evidence`를 data attribute로 보존한다.
  - [x] `isInstanceEvidenceLink()` 또는 동등 helper는 내부 evidence path shape와 selected Project/Application/Instance id 일치를 검증한다.
  - [x] UI에서 evidence path를 template string으로 재구성하지 않고 clicked `links.evidence`만 사용한다.
  - [x] direct `<a href="/api/projects/.../evidence">`, `window.location`, browser API JSON navigation을 만들지 않는다.

- [x] Instance Evidence fetch state machine 추가 (AC: 13~20)
  - [x] Dashboard request와 별도인 evidence request sequence guard를 둔다.
  - [x] 검증된 evidence link만 `projectRequestHeaders()` 또는 동등 header helper로 Bearer token authenticated fetch한다.
  - [x] `idle`, `loading`, `auth-required`, `invalid-link`, `not-found`, `error`, `ready` safe state를 구현한다.
  - [x] `401`, `404`, generic error, malformed response, identity mismatch, stale response를 fail-closed로 처리한다.
  - [x] error body, exception detail, token, provider raw payload는 화면에 표시하지 않는다.

- [x] Instance Detail read model rendering (AC: 20~45)
  - [x] Ready state는 `InstanceEvidenceReadModel` top-level field만 소비하고 response identity mismatch를 렌더링 전에 거부한다.
  - [x] Reading order를 identity/back action -> `metricData`/`starterConnection` -> `applicationTriageContribution` -> `starterPercentiles` -> `histogramDistribution` -> `resourceHints` -> `endpointEvidence` -> `links.snapshotTrend` pending handoff로 고정한다.
  - [x] `metricData.statusSource=accepted_bucket`과 `starterConnection.statusSource=starter_heartbeat/stateImpact=none`을 별도 axis로 표시한다.
  - [x] `applicationTriageContribution`, percentile missing/insufficient, histogram missing/unavailable, resource hint missing 상태를 source-aware empty copy로 표시한다.
  - [x] `starterPercentiles`는 current 15분 30초 bucket 최대 30 point series로 표시하고 평균/최댓값/병합/보간을 만들지 않는다.
  - [x] `histogramDistribution`과 endpoint `durationBuckets`는 bucket distribution evidence로만 표시하고 p95/p99를 계산하지 않는다.
  - [x] `endpointEvidence`는 bounded subset으로 표시하고 rank/root cause/recommendedAction/endpoint percentile을 만들지 않는다.
  - [x] `links.snapshotTrend`는 pending handoff로만 보존한다.

- [x] 후속 API와 frontend architecture guard 유지 (AC: 46~49)
  - [x] Instance Snapshot Trend, Snapshot Detail, Operational Event History API를 fetch하지 않는다.
  - [x] `dashboard/snapshots`, `snapshot-trend`, `operational-events`, `history` fetch가 Story 6.5 implementation에 생기지 않게 test guard를 둔다.
  - [x] localStorage/sessionStorage/cookie/URL token parsing/SPA routing/React/Vite/TypeScript/frontend build stack 부재를 유지한다.
  - [x] Backend API/schema/migration 확장이 필요해 보이면 구현하지 말고 Open Decisions/Risks에 남긴다.

- [x] Static UI contract tests 작성/갱신 (AC: 51~55)
  - [x] `InstanceEvidenceUiContractTest`를 신규 추가하거나 `ApplicationDashboardUiContractTest`를 확장한다.
  - [x] Node VM harness가 실제 `app.js` click/fetch/render sequence를 검증하게 한다.
  - [x] authenticated evidence fetch, invalid/no-token/401/404/error/malformed/stale/identity mismatch safe state를 검증한다.
  - [x] Application Dashboard back action을 검증한다.
  - [x] metricData/starterConnection axis separation, no UI-side recomputation, no trend/snapshot/history fetch guard를 검증한다.
  - [x] 새 test class/helper에는 한국어 Javadoc/comment를 작성한다.

- [x] Focused backend regression과 final verification 유지 (AC: 20, 50, 55)
  - [x] `InstanceEvidenceControllerTest`의 route/status/serialization/no raw field guard를 유지한다.
  - [x] `InstanceEvidenceReadModelShapeTest`와 service focused test의 bounded response shape guard를 유지한다.
  - [x] `ApplicationDashboardUiContractTest` 기존 dashboard guard가 6.5 action 활성화와 충돌하면 scope를 좁히되 후속 API fetch 금지선은 유지한다.
  - [x] focused Gradle commands와 full portal test, `git diff --check`를 실행한다.

## Dev Notes

### Current Code State

- `observability-portal/src/main/resources/static/dashboard/index.html`에는 `#dashboard-detail`, `#selected-application-label`, `#dashboard-generated-at`가 이미 있다. Story 6.5는 같은 `dashboard-detail` 영역의 mode를 전환한다.
- `observability-portal/src/main/resources/static/dashboard/app.js`에는 `selectedDashboardContext`, `loadedDashboard`, `dashboardRequestSequence`, `DASHBOARD_VIEW_STATE`, `renderDashboardReady()`가 있다. Instance Detail state는 이 흐름과 별도로 stale response를 막아야 한다.
- 현재 `instanceHandoffMarkup()`은 `instances[]`를 표시하고 `data-evidence-link`를 보존하지만 button은 `disabled`인 `Evidence pending` 상태다.
- 현재 Dashboard static guard는 Instance Evidence, trend, snapshot detail, history API를 fetch하지 않는 것을 검증한다. 6.5에서는 Instance Evidence fetch만 허용하고 trend/snapshot/history fetch 금지는 유지하도록 test scope를 좁혀야 한다.
- `InstanceEvidenceController`는 `GET /api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/evidence`를 제공하며 controller는 path/status mapping과 service 위임만 담당한다.
- `InstanceEvidenceReadModel` top-level shape는 `generatedAt`, `application`, `instance`, `metricData`, `starterConnection`, `starterPercentiles`, `histogramDistribution`, `resourceHints`, `applicationTriageContribution`, `endpointEvidence`, `links`다.
- `InstanceEvidenceReadModel.Links.snapshotTrend`는 후속 story link 후보일 수 있다. Story 6.5는 이 link를 pending handoff로만 보존한다.
- `ApplicationDashboardUiContractTest`는 실제 `app.js`를 Node VM에서 실행하는 harness를 이미 갖고 있다. Story 6.5의 static UI test는 이 harness를 재사용하거나 공통화하는 편이 안전하다.

### Implementation Guardrails

- Instance Evidence UI는 Application Dashboard보다 강한 판단을 만들지 않는다.
- `links.evidence`는 Dashboard response에서 내려온 handoff source다. UI가 UUID path를 조립하면 contract 위반이다.
- `application.links.dashboard`와 `links.dashboard`는 back action의 source/context로만 사용한다.
- `metricData` accepted bucket axis와 `starterConnection` heartbeat axis를 합치지 않는다.
- heartbeat success는 metric freshness, lifecycle state, recovery source, p95/p99, endpoint evidence source가 아니다.
- heartbeat missing은 host application down 확정이 아니다.
- `starterPercentiles`는 selected instance current 15분 series다. 24h/7d/14d처럼 보이는 chart나 selector를 만들지 않는다.
- Histogram bucket은 distribution display source다. p95/p99 scalar를 만들지 않는다.
- `endpointEvidence`는 application concern과 selected instance evidence의 bounded 연결 surface다. Instance endpoint priority/root cause surface가 아니다.
- `not_observed`는 selected instance current window에서 endpoint evidence가 없다는 뜻이며 정상/문제 없음이 아니다.
- `application_freshness_not_current`는 stale/down 직전 endpoint evidence가 current concern처럼 보이지 않게 하는 suppression이다.
- Empty/missing/insufficient는 숨기지 않고 source absence/evidence 부족으로 표현한다.

### Architecture Constraints

- Active baseline은 Traditional MVC + Service/Repository Layering이다.
- Portal package는 feature-first MVC 구조이며 `domain`은 business feature namespace다.
- 금지 package는 `application`, `port`, `adapter`, `adapter.in`, `adapter.out`다.
- Controller는 repository를 직접 호출하지 않고 service에 위임한다.
- Repository는 controller/dto에 의존하지 않는다.
- JPA entity를 API response, public DTO, service external result로 노출하지 않는다.
- Flyway migration이 physical schema source of truth다. Story 6.5는 schema/migration 변경을 기대하지 않는다.
- Static dashboard UI는 portal runtime 안에서 제공되며 `observability-portal/src/main/resources/static/dashboard/` 안에서 구현한다.
- UI는 server read model을 표시만 한다. UI/controller/repository는 lifecycle state, starter diagnosis, zeroInsight, recovery, rule, p95/p99, endpoint priority, snapshot/history event를 계산하지 않는다.
- Browser token persistence, SPA routing, frontend build stack 도입은 open decision이며 Story 6.5에서 닫지 않는다.
- 새 공개 class/method/helper/test에는 `AGENTS.md` 기준으로 한국어 Javadoc/comment를 작성한다.

## Open Decisions / Risks

- Backend API/schema/migration 확장이 필요해 보이면 Story 6.5에서 구현하지 말고 correct-course 또는 별도 contract decision으로 올린다.
- Browser token persistence, refresh token browser storage, logout/session persistence는 여전히 open decision이다. Story 6.5에서 닫지 않는다.
- React/Vite/TypeScript/SPA routing 전환 시점과 source/build output 구조는 open decision이다. Story 6.5에서 정하지 않는다.
- Instance Snapshot Trend UI는 Story 6.6 책임이다. 6.5는 `links.snapshotTrend` pending handoff만 보존한다.
- Snapshot/History marker UI는 Story 6.7 책임이다. 6.5에서 snapshot/detail/history fetch를 열지 않는다.
- Demo green path/failure-recovery fixture와 seed strategy는 Story 6.8/6.9 책임이다.
- 가장 큰 구현 위험은 static JS helper가 formatting helper를 넘어 instance health, percentile, endpoint priority, root cause, trend/history event 계산 helper가 되는 것이다.

## Testing / Suggested commands

Focused test 대상 후보:

- `InstanceEvidenceUiContractTest` 신규 추가
- `ApplicationDashboardUiContractTest` 갱신
- `ApplicationListUiContractTest` regression 유지
- `ProjectSelectionUiContractTest` regression 유지
- `ProjectEntryUiContractTest` regression 유지
- `InstanceEvidenceControllerTest`
- `InstanceEvidenceReadModelShapeTest`
- `InstanceEvidenceReadModelServiceTest`
- `MvcLayerBoundaryTest`

필수 scenario:

- Dashboard instance Evidence action은 `instances[].links.evidence` 기반 authenticated fetch로 이어진다.
- Evidence fetch는 `Authorization: Bearer <access_token>` header를 사용한다.
- Invalid link, no token, `401`, `404`, generic error, malformed response, stale response, response identity mismatch가 fail-closed safe state로 렌더링된다.
- Application Dashboard back action이 항상 존재하고 `loadedDashboard`를 다시 표시한다.
- Instance Detail reading order가 계약 순서를 따른다.
- `metricData.statusSource=accepted_bucket`, `starterConnection.statusSource=starter_heartbeat`, `stateImpact=none`이 별도 axis로 표시된다.
- p95/p99는 `starterPercentiles.points[]`를 표시만 하고 평균/최댓값/병합/보간/히스토그램 재계산이 없다.
- Histogram과 endpoint duration bucket은 distribution evidence로만 표시된다.
- endpointEvidence는 bounded subset으로 표시되고 endpoint ranking/root cause/action priority가 아니다.
- `links.snapshotTrend`는 pending handoff로만 보존되고 trend/snapshot/history API fetch가 없다.
- Static UI가 token persistence, URL token parsing, SPA routing, frontend build stack을 만들지 않는다.
- Static UI가 lifecycle state, starter diagnosis, instance health, rule, p95/p99, endpoint priority, snapshot/history event helper를 만들지 않는다.

Suggested commands:

```bash
./gradlew :observability-portal:test --tests '*InstanceEvidenceUiContract*'
./gradlew :observability-portal:test --tests '*ApplicationDashboardUiContract*'
./gradlew :observability-portal:test --tests '*ApplicationListUiContract*'
./gradlew :observability-portal:test --tests '*ProjectSelectionUiContract*'
./gradlew :observability-portal:test --tests '*ProjectEntryUiContract*'
./gradlew :observability-portal:test --tests '*InstanceEvidenceController*'
./gradlew :observability-portal:test --tests '*InstanceEvidenceReadModelShape*'
./gradlew :observability-portal:test --tests '*InstanceEvidenceReadModelService*'
./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest
./gradlew :observability-portal:test
git diff --check
```

테스트 이름은 구현 시점의 실제 class 이름에 맞게 조정할 수 있다. 단, static UI contract guard가 Instance Evidence link authenticated fetch, safe state, stale/identity mismatch guard, Application Dashboard back action, source-axis separation, no UI-side recomputation, no trend/snapshot/history fetch를 검증해야 한다.

## Dev Agent Record

### Agent Model Used

GPT-5 Codex (Codex desktop)

### Debug Log References

- 2026-05-29: BMAD create-story workflow와 customization을 확인했다.
- 2026-05-29: Story 6.5 contract decision, Story 5.6 instance evidence contract/story, Story 6.4 dashboard UI contract/story, Epic 6 context, epics, read model/history contracts, current static dashboard runtime, InstanceEvidenceController/ReadModel, AGENTS.md를 확인했다.
- 2026-05-29: Story 6.5 create-story 산출물을 `planning-artifacts/stories/6-5-instance-evidence-ui.md`에 생성했다.
- 2026-05-29: `InstanceEvidenceUiContractTest`를 추가하고 red 상태를 확인했다.
- 2026-05-29: `app.js`에 Instance Evidence detail mode, evidence request sequence guard, safe link validation, authenticated fetch, safe state rendering, response identity/malformed guard, Application Dashboard back action을 구현했다.
- 2026-05-29: Instance Evidence rendering을 계약 순서대로 구성하고 metric data/starter connection axis, triage contribution, starter percentile series, histogram/resource/endpoint evidence, snapshotTrend pending handoff를 표시했다.
- 2026-05-29: Backend API/schema/migration 확장은 필요하지 않아 구현하지 않았다.
- 2026-05-29: Suggested focused commands, full portal test, `git diff --check`를 실행했다.
- 2026-05-29: Code review findings를 quick-dev patch로 닫았다. Evidence 401은 Instance Detail auth-required shell로 수렴시키고, nested malformed evidence와 suppressed endpoint item이 ready로 렌더링되지 않도록 fail-closed validator/test를 보강했다.
- 2026-05-29: Closeout review 후 `starterPercentiles.points[]`가 selected metric window 안의 30초 bucket일 때만 ready로 통과하도록 quick-dev hardening을 추가했다.

### Completion Notes List

- `dashboard-detail` 안에서 Application Dashboard ready view와 Instance Evidence detail view를 전환하는 static runtime state를 추가했다.
- Dashboard `instances[].links.evidence`만 fetch source로 사용하며, 현재 Project/Application/clicked Instance id와 내부 evidence path shape가 맞을 때만 Bearer token fetch를 수행한다.
- Instance Evidence safe states(`loading`, `auth-required`, `invalid-link`, `not-found`, `error`, `ready`)와 stale response guard를 추가했고, token/error body/provider payload를 화면에 노출하지 않도록 유지했다.
- Ready view는 `InstanceEvidenceReadModel` top-level block만 소비하고, malformed response 또는 response identity mismatch는 fail-closed generic error로 처리한다.
- Metric data axis와 starter connection axis를 분리해 표시하고, percentile/histogram/endpoint/resource evidence는 서버 제공 값을 표시만 하도록 guard했다.
- `links.snapshotTrend`는 pending handoff data/button으로만 보존하며 Instance Snapshot Trend, Snapshot Detail, Operational Event History API fetch/render는 열지 않았다.
- Review patch에서 evidence 401, malformed nested block, suppressed endpoint evidence, invalid snapshotTrend handoff를 fail-closed로 고정하고 Node VM contract test를 확장했다.
- Closeout quick-dev에서 percentile point duration과 current window membership을 fail-closed로 고정했다.

### File List

- `observability-portal/src/main/resources/static/dashboard/app.js`
- `observability-portal/src/main/resources/static/dashboard/styles.css`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/InstanceEvidenceUiContractTest.java`
- `planning-artifacts/stories/6-5-instance-evidence-ui.md`
- `implementation-artifacts/sprint-status.yaml`

### Change Log

- 2026-05-29: Story 6.5 Instance evidence UI create-story 산출물을 생성하고 ready-for-dev 상태로 전환했다.
- 2026-05-29: Instance Evidence UI를 구현하고 static contract/backend regression/full portal verification을 통과해 review 상태로 전환했다.
- 2026-05-29: Review hardening patch와 full portal verification을 통과해 done 상태로 전환했다.
- 2026-05-29: Closeout quick-dev로 percentile 30초 bucket/current window fail-closed guard를 추가하고 focused UI contract verification을 통과했다.
